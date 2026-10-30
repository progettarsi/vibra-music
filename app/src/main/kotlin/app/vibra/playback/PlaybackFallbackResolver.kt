/*
 * Vibra (2026) — one bounded foreground resolution across native and public providers.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.playback

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.stream.AudioStreamRequest
import moe.rukamori.archivetune.playback.stream.ResolvedAudioStream
import moe.rukamori.archivetune.playback.stream.StreamResolutionPriority
import moe.rukamori.archivetune.playback.stream.YoutubeiStreamRepository
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class PlaybackSourcesExhausted : IOException("No playable source available")

@Singleton
class PlaybackFallbackResolver @Inject constructor(
    private val native: YoutubeiStreamRepository,
    private val publicStreams: PublicStreamRepository,
    private val attempts: PlaybackAttemptController,
) {
    internal suspend fun resolve(request: AudioStreamRequest, priority: StreamResolutionPriority): ResolvedAudioStream {
        if (request.playbackAttemptId == 0L) {
            return native.resolve(request.copy(authState = YTPlayerUtils.ensureYoutubeiPoTokensForPlayback(
                request.mediaId, request.authState,
            )), priority)
        }
        val attempt = attempts.current(request.mediaId)?.takeIf {
            it.id == request.playbackAttemptId && it.stage == request.recoveryStage &&
                attempts.isCurrent(it.id, request.mediaId)
        } ?: throw CancellationException("Playback attempt changed")
        val operation: suspend () -> ResolvedAudioStream = {
            attempts.beforeResolution(attempt.stage)
            when (attempt.stage) {
                PlaybackStage.INVIDIOUS, PlaybackStage.PIPED -> publicStreams.resolve(request, attempt.stage)
                else -> {
                    if (attempt.stage == PlaybackStage.REFRESHED) {
                        BotGuardTokenGenerator.invalidateAll()
                        native.invalidateSessions()
                    }
                    val auth = if (attempt.stage == PlaybackStage.ANONYMOUS) PlaybackAuthState() else request.authState
                    val state = YTPlayerUtils.ensureYoutubeiPoTokensForPlayback(request.mediaId, auth)
                    native.resolveSingle(request.copy(authState = state, allowCatalogReplacement = false), priority)
                        .also { if (attempt.stage != PlaybackStage.DIRECT) probeNative(it) }
                }
            }
        }
        val stream = try {
            attempt.deadlineMs?.let { deadline ->
                withTimeout((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)) { operation() }
            } ?: operation()
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            throw PlaybackSourcesExhausted()
        }
        if (!attempts.isCurrent(attempt.id, request.mediaId)) throw CancellationException("Playback attempt changed")
        return stream.copy(playbackAttemptId = attempt.id, recoveryStage = attempt.stage)
    }

    private suspend fun probeNative(stream: ResolvedAudioStream) = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().proxy(YouTube.streamOkHttpProxy).dns(YouTube.dns)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(7, TimeUnit.SECONDS)
            .apply {
                if (!YouTube.proxyUsername.isNullOrBlank() && !YouTube.proxyPassword.isNullOrBlank()) {
                    proxyAuthenticator { _, response ->
                        if (response.request.header("Proxy-Authorization") != null) null
                        else response.request.newBuilder().header("Proxy-Authorization",
                            okhttp3.Credentials.basic(YouTube.proxyUsername!!, YouTube.proxyPassword!!)).build()
                    }
                }
            }.build()
        try {
            val request = Request.Builder().url(stream.url).apply {
                stream.requestHeaders.forEach { (name, value) -> header(name, value) }
            }.header("Range", "bytes=0-1023").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.body.byteStream().read() == -1) throw IOException()
            }
        } catch (_: IOException) {
            throw IOException("Native stream rejected")
        } finally {
            client.connectionPool.evictAll()
        }
    }
}
