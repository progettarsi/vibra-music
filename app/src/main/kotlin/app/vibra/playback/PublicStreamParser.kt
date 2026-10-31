/*
 * Vibra (2026) — exact-video public stream selection, without account credentials.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.playback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.playback.stream.AudioStreamRequest
import moe.rukamori.archivetune.playback.stream.ResolvedAudioStream
import moe.rukamori.archivetune.playback.stream.StreamSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

data class PublicStreamInstance(val api: String, val streamDomain: String, val source: StreamSource) {
    fun accepts(url: HttpUrl): Boolean = url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
        url.port == 443 && (url.host == streamDomain || url.host.endsWith(".$streamDomain"))
}

/** Pure parser: no searches, alternate recordings, LBRY copies or untrusted stream origins. */
object PublicStreamParser {
    fun parse(body: String, request: AudioStreamRequest, instance: PublicStreamInstance, nowMs: Long): ResolvedAudioStream {
        require(request.mediaId.matches(Regex("[A-Za-z0-9_-]{11}")))
        val json = Json.parseToJsonElement(body) as? JsonObject ?: throw IOException("Invalid public stream response")
        val invidious = instance.source == StreamSource.INVIDIOUS
        if (invidious && json.text("videoId") != request.mediaId) throw IOException("Public stream identity mismatch")
        if (json.flag("paid") || json.flag("premium") || json.flag("livestream") || json.flag("liveNow") ||
            json.text("error")?.isNotBlank() == true
        ) throw IOException("Public stream unavailable")
        val audio = json.objects(if (invidious) "adaptiveFormats" else "audioStreams")
            .filter { it.text(if (invidious) "type" else "mimeType")?.startsWith("audio/") == true }
        val mixed = if (invidious || request.purpose == moe.rukamori.archivetune.playback.stream.StreamPurpose.DOWNLOAD) emptyList() else json.objects("videoStreams").filter {
            it.text("mimeType") == "video/mp4" && it["videoOnly"] == JsonPrimitive(false) &&
                it.text("quality")?.contains("LBRY", ignoreCase = true) != true
        }
        fun candidates(formats: List<JsonObject>) = formats.mapNotNull { format ->
            val url = format.text("url")?.toHttpUrlOrNull()?.takeIf(instance::accepts) ?: return@mapNotNull null
            if (format.text("drm") != null || format.flag("drmProtected")) return@mapNotNull null
            format to url
        }.sortedBy { (format, _) -> format.number("bitrate") ?: 0L }
        val audioCandidates = candidates(audio)
        val candidates = audioCandidates.ifEmpty { candidates(mixed).sortedBy { (format, _) ->
            format.number("height") ?: Regex("[0-9]+").find(format.text("quality").orEmpty())?.value?.toLongOrNull() ?: Long.MAX_VALUE
        } }
        val selected = (if (audioCandidates.isEmpty()) candidates.firstOrNull() else when (request.quality) {
            AudioQuality.LOW -> candidates.firstOrNull()
            AudioQuality.AUTO -> if (request.networkMetered) candidates.firstOrNull() else candidates.lastOrNull()
            else -> candidates.lastOrNull()
        }) ?: throw IOException("No public audio stream")
        val (format, url) = selected
        val rawMime = format.text(if (invidious) "type" else "mimeType") ?: "audio/mp4"
        val mime = rawMime.substringBefore(';')
        val expiry = url.queryParameter("expire")?.toLongOrNull()?.times(1000L)
            ?: (nowMs + 5 * 60_000L)
        if (expiry <= nowMs + 15_000L) throw IOException("Public stream expired")
        return ResolvedAudioStream(
            url = url.toString(), requestHeaders = mapOf("Accept" to "*/*"),
            formatId = format.number("itag")?.toInt() ?: url.queryParameter("itag")?.toIntOrNull() ?: 0,
            mimeType = mime, codecs = Regex("codecs=\"([^\"]+)\"").find(rawMime)?.groupValues?.get(1)
                ?: format.text("codec").orEmpty(),
            bitrate = (format.number("bitrate") ?: 0L).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            sampleRate = format.number("audioSampleRate")?.toInt(), contentLength = format.number("clen") ?: 0L,
            expiresAtMs = expiry, authFingerprint = "public:${instance.api}", source = instance.source,
            durationSeconds = json.number(if (invidious) "lengthSeconds" else "duration")?.toInt(),
            mixedVideo = mime.startsWith("video/"),
        )
    }

    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.number(key: String) = text(key)?.toLongOrNull()
    private fun JsonObject.flag(key: String) = text(key) == "true"
    private fun JsonObject.objects(key: String) = (get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
}
