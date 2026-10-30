/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection

/**
 * Immutable snapshot of what the player is doing, assembled from the engine's single
 * source of truth ([PlayerConnection], backed by the Media3 session in MusicService).
 * The Vibra UI never keeps its own copy of playback state; it only renders this.
 *
 * Playback *position* is deliberately not part of this snapshot: it changes every frame
 * and is exposed separately by [rememberPlaybackPosition] so only the progress bar
 * recomposes.
 */
@Immutable
data class NowPlaying(
    val metadata: MediaMetadata?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val queueTitle: String?,
) {
    val hasMedia: Boolean get() = metadata != null
}

/** Collects the engine flows into a [NowPlaying] snapshot (lifecycle-aware). */
@Composable
fun PlayerConnection.collectNowPlaying(): NowPlaying {
    val metadata by mediaMetadata.collectAsStateWithLifecycle()
    val playing by isPlaying.collectAsStateWithLifecycle()
    val state by playbackState.collectAsStateWithLifecycle()
    val attempt by playbackAttempt.collectAsStateWithLifecycle()
    val repeat by repeatMode.collectAsStateWithLifecycle()
    val shuffle by shuffleModeEnabled.collectAsStateWithLifecycle()
    val canPrevious by canSkipPrevious.collectAsStateWithLifecycle()
    val canNext by canSkipNext.collectAsStateWithLifecycle()
    val title by queueTitle.collectAsStateWithLifecycle()
    return NowPlaying(
        metadata = metadata,
        isPlaying = playing,
        // Buffering only matters to the user when they asked to play.
        isBuffering = (state == Player.STATE_BUFFERING || attempt.isLoading) && player.playWhenReady,
        repeatMode = repeat,
        shuffleEnabled = shuffle,
        canSkipPrevious = canPrevious,
        canSkipNext = canNext,
        queueTitle = title,
    )
}

/** Position/duration pair in milliseconds. */
@Immutable
data class PlaybackProgress(
    val positionMs: Long,
    val durationMs: Long,
) {
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Holds the sampled playback position. Media3 has no position callback, so the value is
 * polled while playing (250 ms is enough for the seconds display; the bar animates
 * between samples). While paused nothing runs; call [resample] after a seek.
 */
@Stable
class PlaybackPositionState internal constructor(
    private val player: Player,
) {
    var progress by mutableStateOf(sample())
        private set

    fun resample() {
        progress = sample()
    }

    private fun sample() =
        PlaybackProgress(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
        )
}

/** @param active false while nobody can see the position (e.g. the closed player): no sampling. */
@Composable
fun rememberPlaybackPosition(
    player: Player,
    isPlaying: Boolean,
    mediaId: String?,
    active: () -> Boolean = { true },
): PlaybackPositionState {
    val state = remember(player) { PlaybackPositionState(player) }
    val shown by rememberUpdatedState(active)
    LaunchedEffect(state, isPlaying, mediaId) {
        snapshotFlow { shown() }.collectLatest { activeNow ->
            if (!activeNow) return@collectLatest
            state.resample()
            while (isPlaying) {
                delay(250)
                state.resample()
            }
        }
    }
    return state
}
