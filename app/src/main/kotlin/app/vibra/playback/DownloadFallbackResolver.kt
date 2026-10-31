/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.playback.stream.*
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator

/** Resolves the same video for a download; has no foreground attempt/UI side effects. */
@Singleton
class DownloadFallbackResolver @Inject constructor(
    private val native: YoutubeiStreamRepository,
    private val publicStreams: PublicStreamRepository,
) {
    internal suspend fun resolve(request: AudioStreamRequest, stage: PlaybackStage): ResolvedAudioStream = withTimeout(30_000L) {
        val exact = request.copy(allowCatalogReplacement = false, artworkVideo = false, pinnedFormatId = null)
        when (stage) {
            PlaybackStage.INVIDIOUS, PlaybackStage.PIPED -> publicStreams.resolveDownload(exact, stage)
            else -> {
                if (stage == PlaybackStage.REFRESHED) {
                    BotGuardTokenGenerator.invalidateVideo(request.mediaId)
                    native.invalidateSessions()
                }
                val auth = if (stage == PlaybackStage.ANONYMOUS) PlaybackAuthState() else request.authState
                val state = YTPlayerUtils.ensureYoutubeiPoTokensForPlayback(request.mediaId, auth)
                native.resolveSingle(exact.copy(authState = state), StreamResolutionPriority.BACKGROUND)
            }
        }
    }
}
