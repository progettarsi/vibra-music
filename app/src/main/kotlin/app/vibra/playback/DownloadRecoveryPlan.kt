/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.playback

/** Per-download state, never shared with the foreground player's attempt controller. */
internal class DownloadRecoveryPlan(signedIn: Boolean) {
    private val stages = listOf(PlaybackStage.DIRECT, PlaybackStage.REFRESHED) +
        (if (signedIn) listOf(PlaybackStage.ANONYMOUS) else emptyList()) +
        listOf(PlaybackStage.INVIDIOUS, PlaybackStage.PIPED)
    private var index = 0
    @Volatile var exhausted = false
        private set
    val stage: PlaybackStage get() = stages[index]
    fun advance(): Boolean {
        if (exhausted) return false
        if (index == stages.lastIndex) { exhausted = true; return false }
        index++
        return true
    }
}
