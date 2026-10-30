/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.innertube.PlaybackAuthState

enum class StreamPurpose {
    PLAYBACK,
    DOWNLOAD,
}

enum class StreamSource {
    YOUTUBEI,
    INVIDIOUS,
    PIPED,
}

data class AudioStreamRequest(
    val mediaId: String,
    val playlistId: String? = null,
    val quality: AudioQuality,
    val networkMetered: Boolean,
    val purpose: StreamPurpose,
    val authState: PlaybackAuthState,
    val pinnedFormatId: Int? = null,
    val requiresSongMetadata: Boolean = false,
    // Vibra: a music video — also resolve its picture for the player (see ArtworkVideoStreams).
    val artworkVideo: Boolean = false,
    // Vibra: a nonzero identity enables the foreground-only fallback chain.
    val playbackAttemptId: Long = 0,
    val recoveryStage: app.vibra.playback.PlaybackStage = app.vibra.playback.PlaybackStage.DIRECT,
    val allowCatalogReplacement: Boolean = true,
)

data class ResolvedAudioStream(
    val url: String,
    val requestHeaders: Map<String, String>,
    val formatId: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long,
    val expiresAtMs: Long,
    val authFingerprint: String,
    val source: StreamSource,
    val runtimeVersion: String? = null,
    val title: String? = null,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val loudnessDb: Double? = null,
    val perceptualLoudnessDb: Double? = null,
    val playbackTrackingUrl: String? = null,
    val playbackAttemptId: Long = 0,
    val recoveryStage: app.vibra.playback.PlaybackStage = app.vibra.playback.PlaybackStage.DIRECT,
    val mixedVideo: Boolean = false,
)
