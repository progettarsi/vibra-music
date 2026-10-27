/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import moe.rukamori.archivetune.LocalAnimationsDisabled

/**
 * The slow "record" rotation of the cookie play buttons, shared app-wide.
 *
 * One angle drives the mini-player cookie, the full player's button and the cover that
 * flies between them, so they always agree. The angle only advances while playing and
 * simply stops when playback pauses — it never rewinds to 0°.
 */
@Stable
class PlaybackSpin {
    /** Current rotation in degrees, 0‥360. Read it in draw/layer blocks, not composition. */
    var degrees by mutableFloatStateOf(0f)
        internal set
}

/** Provided by the app shell; components fall back to a private spin when absent. */
val LocalPlaybackSpin = staticCompositionLocalOf<PlaybackSpin?> { null }

/** Remembers a [PlaybackSpin] that turns while [isPlaying] (and animations are enabled). */
@Composable
fun rememberPlaybackSpin(isPlaying: Boolean): PlaybackSpin {
    val spin = remember { PlaybackSpin() }
    // Ambient: the spin stops in battery saver (and with animations off).
    val animationsDisabled = !ambientMotionAllowed
    LaunchedEffect(isPlaying, animationsDisabled) {
        if (!isPlaying || animationsDisabled) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                spin.degrees = (spin.degrees + (now - last) / 1_000_000_000f * DegreesPerSecond) % 360f
                last = now
            }
        }
    }
    return spin
}

/** A full turn every 24 s: perceptible, never distracting. */
private const val DegreesPerSecond = 360f / 24f
