/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.canvas

import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class CanvasResponse(val code: Int, val body: String?) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** The only network surface of the lookups: cancellable, bounded, and replaceable in tests. */
interface CanvasHttp {
    /** The body of a GET; non-2xx are returned, not thrown. Transport failures throw [IOException]. */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): CanvasResponse

    /** Whether the resource currently answers (HEAD, or a one-byte ranged GET). Never throws. */
    suspend fun reachable(url: String): Boolean
}

object CanvasUrls {
    private val EXTENSIONS = setOf("mp4", "m4v", "mov", "webm", "m3u8")
    private val PRIVATE_V4 = Regex("^(10\\.|127\\.|169\\.254\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.|0\\.)")

    /** HTTPS, a public host, and a container Media3 plays. The manifest and the APIs are untrusted. */
    fun isAcceptable(url: String?): Boolean {
        if (url.isNullOrBlank() || url.length > 2048) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.userInfo != null) return false
        val host = uri.host?.lowercase() ?: return false
        if (!host.contains('.') || host.endsWith(".local") || host.endsWith(".internal") ||
            host.endsWith(".localhost") || host == "localhost" || host.contains(':') ||
            PRIVATE_V4.containsMatchIn(host)
        ) {
            return false
        }
        val extension = uri.path.orEmpty().substringAfterLast('.', "").lowercase()
        return extension in EXTENSIONS
    }
}

internal object OkHttpCanvasHttp : CanvasHttp {
    private const val MAX_BODY_BYTES = 8L * 1024 * 1024
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun get(url: String, headers: Map<String, String>): CanvasResponse {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        return client.newCall(request).await().use { response ->
            val body =
                if (response.isSuccessful) {
                    val source = response.body.source()
                    source.request(MAX_BODY_BYTES + 1)
                    if (source.buffer.size > MAX_BODY_BYTES) throw IOException("Canvas response too large")
                    source.readUtf8()
                } else {
                    null
                }
            CanvasResponse(response.code, body)
        }
    }

    override suspend fun reachable(url: String): Boolean =
        try {
            val head = Request.Builder().url(url).head().build()
            val code = client.newCall(head).await().use { it.code }
            when {
                code in 200..299 -> true
                code == 405 || code == 403 -> {
                    val ranged = Request.Builder().url(url).header("Range", "bytes=0-0").build()
                    client.newCall(ranged).await().use { it.code == 200 || it.code == 206 }
                }
                else -> false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }

    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!continuation.isActive) response.close()
                        else continuation.resume(response) { _, value, _ -> value.close() }
                    }
                },
            )
        }
}
