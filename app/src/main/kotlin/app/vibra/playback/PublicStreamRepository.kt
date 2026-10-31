/*
 * Vibra (2026) — bounded public relay requests, used for playback and explicit download recovery.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.playback

import kotlinx.coroutines.suspendCancellableCoroutine
import moe.rukamori.archivetune.playback.stream.AudioStreamRequest
import moe.rukamori.archivetune.playback.stream.ResolvedAudioStream
import moe.rukamori.archivetune.playback.stream.StreamSource
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PublicStreamFailure(val httpStatus: Int? = null) : IOException("Public playback source unavailable")

/** No cookie jar, Google authentication headers, player-script execution or automatic instance crawling. */
@Singleton
class PublicStreamRepository @Inject constructor() {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(7, TimeUnit.SECONDS).build()
    private val unavailableUntil = ConcurrentHashMap<String, Long>()

    suspend fun resolve(request: AudioStreamRequest, stage: PlaybackStage): ResolvedAudioStream {
        val source = if (stage == PlaybackStage.INVIDIOUS) StreamSource.INVIDIOUS else StreamSource.PIPED
        val now = System.currentTimeMillis()
        val instance = INSTANCES.firstOrNull { it.source == source && (unavailableUntil[it.api] ?: 0L) <= now }
            ?: throw PublicStreamFailure()
        return resolveAt(request, instance, now)
    }

    /** Download recovery tries each configured instance once, without borrowing playback state. */
    suspend fun resolveDownload(request: AudioStreamRequest, stage: PlaybackStage): ResolvedAudioStream {
        val source = if (stage == PlaybackStage.INVIDIOUS) StreamSource.INVIDIOUS else StreamSource.PIPED
        val now = System.currentTimeMillis()
        for (instance in INSTANCES.filter { it.source == source && (unavailableUntil[it.api] ?: 0L) <= now }) {
            try { return resolveAt(request, instance, now) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: java.io.IOException) { /* Continue to the next configured instance. */ }
        }
        throw PublicStreamFailure()
    }

    private suspend fun resolveAt(request: AudioStreamRequest, instance: PublicStreamInstance, now: Long): ResolvedAudioStream {
        val source = instance.source
        var transportFailure = true
        return try {
            val url = instance.api.toHttpUrl().newBuilder().apply {
                if (source == StreamSource.INVIDIOUS) {
                    addPathSegments("api/v1/videos").addPathSegment(request.mediaId).addQueryParameter("local", "true")
                } else addPathSegment("streams").addPathSegment(request.mediaId)
            }.build()
            val body = fetch(url, instance, probe = false)
            // An unavailable song/format does not make the instance unavailable for every other song.
            transportFailure = false
            val stream = PublicStreamParser.parse(body.decodeToString(), request, instance, now)
            transportFailure = true
            fetch(stream.url.toHttpUrl(), instance, probe = true)
            stream
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            if (transportFailure && (failure as? PublicStreamFailure)?.httpStatus !in setOf(404, 410)) {
                unavailableUntil[instance.api] = now + 5 * 60_000L
            }
            // Exception messages from HTTP libraries may contain signed URLs; never propagate them.
            throw failure as? PublicStreamFailure ?: PublicStreamFailure()
        }
    }

    private suspend fun fetch(initial: HttpUrl, instance: PublicStreamInstance, probe: Boolean): ByteArray {
        var url = initial
        repeat(4) {
            if (!instance.accepts(url)) throw PublicStreamFailure()
            val request = Request.Builder().url(url).header("User-Agent", "VIBRA/1.3")
                .header("Accept", if (probe) "*/*" else "application/json").apply {
                    if (probe) header("Range", "bytes=0-1023")
                }.build()
            val response = awaitResponse(client.newCall(request))
            response.use {
                if (it.isRedirect) {
                    url = url.resolve(it.header("Location").orEmpty()) ?: throw PublicStreamFailure()
                } else {
                    if (!it.isSuccessful) throw PublicStreamFailure(it.code)
                    val body = it.body
                    if (probe) {
                        val type = body.contentType()?.toString().orEmpty()
                        if (!type.startsWith("audio/") && !type.startsWith("video/") &&
                            !type.startsWith("application/octet-stream")) throw PublicStreamFailure()
                        val bytes = readBounded(body.byteStream(), 1024)
                        if (bytes.isEmpty()) throw PublicStreamFailure()
                        return bytes
                    }
                    val bytes = readBounded(body.byteStream(), MAX_METADATA_BYTES + 1)
                    if (bytes.size > MAX_METADATA_BYTES) throw PublicStreamFailure()
                    return bytes
                }
            }
        }
        throw PublicStreamFailure()
    }

    private fun readBounded(stream: java.io.InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(minOf(limit, 8192))
        while (output.size() < limit) {
            val count = stream.read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private suspend fun awaitResponse(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(PublicStreamFailure())
            }
            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) response.close()
                else continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    companion object {
        private const val MAX_METADATA_BYTES = 2 * 1024 * 1024
        // Official registries, checked 2026-10-30. Failed hosts cool down; at most one per provider/attempt.
        // https://docs.invidious.io/instances/ and TeamPiped/documentation public-instances.
        val INSTANCES = listOf(
            PublicStreamInstance("https://yt.chocolatemoo53.com", "chocolatemoo53.com", StreamSource.INVIDIOUS),
            PublicStreamInstance("https://invidious.tiekoetter.com", "invidious.tiekoetter.com", StreamSource.INVIDIOUS),
            PublicStreamInstance("https://api.piped.private.coffee", "piped.private.coffee", StreamSource.PIPED),
            PublicStreamInstance("https://pipedapi.ducks.party", "ducks.party", StreamSource.PIPED),
        )
    }
}
