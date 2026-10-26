/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package moe.rukamori.archivetune.playback.stream

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A music video's light, video-only stream, resolved together with its audio. The player
 * shows it in place of the cover, muted and following the audio.
 */
data class ArtworkVideoStream(
    val mediaId: String,
    val url: String,
    val requestHeaders: Map<String, String>,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val expiresAtMs: Long,
) {
    /** Width / height of the picture (16:9 when the stream did not say). */
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 16f / 9f
}

/**
 * Vibra: the artwork videos the stream resolver found, by media id. Written by the service's
 * resolutions (current and preloaded tracks), read by the player. Only the last few are kept;
 * a stream that failed to play is not offered again.
 */
object ArtworkVideoStreams {
    private val streams = MutableStateFlow<Map<String, ArtworkVideoStream>>(emptyMap())
    private val failed = MutableStateFlow<Set<String>>(emptySet())

    fun publish(stream: ArtworkVideoStream) {
        if (stream.mediaId in failed.value) return
        streams.update { current ->
            (current - stream.mediaId + (stream.mediaId to stream)).entries
                .toList()
                .takeLast(MaxStreams)
                .associate { it.key to it.value }
        }
    }

    /** The still valid stream of [mediaId], or null. */
    fun stream(mediaId: String): Flow<ArtworkVideoStream?> =
        streams
            .map { it[mediaId]?.takeIf { stream -> stream.expiresAtMs > System.currentTimeMillis() + ExpirySafetyMillis } }
            .distinctUntilChanged()

    /** [mediaId]'s video could not be played: the cover stays for good (this session). */
    fun markFailed(mediaId: String) {
        failed.update { it + mediaId }
        streams.update { it - mediaId }
    }

    private const val MaxStreams = 12
    private const val ExpirySafetyMillis = 60_000L
}
