/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Semantic haptics for Vibra.
 *
 * Call sites describe *what happened* (a toggle turned on, a gesture crossed its commit
 * threshold…) and this class maps it to the platform primitive designed for that moment.
 * The mapping follows Android's haptics design principles: short, crisp clicks for
 * discrete actions, ticks for continuous scrubbing, and distinct confirm/reject patterns
 * so outcomes can be felt without looking.
 *
 * It wraps LocalHapticFeedback, which MainActivity already gates behind the user's
 * "Haptic feedback" preference, so every Vibra haptic respects that setting.
 * Compose falls back to the closest available primitive on older Android versions.
 */
@Immutable
class VibraHaptics internal constructor(
    private val feedback: HapticFeedback,
) {
    /** A primary button was tapped (play, skip, dock buttons). */
    fun press() = feedback.performHapticFeedback(HapticFeedbackType.VirtualKey)

    /** A two-state control changed (shuffle, like, repeat on). */
    fun toggle(on: Boolean) =
        feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** Discrete step while scrubbing, cycling a mode or snapping in a list. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

    /** A drag gesture crossed the point where releasing will commit it. */
    fun threshold() = feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

    /** A gesture ended and its result was committed (dock morph settled, item dropped). */
    fun gestureEnd() = feedback.performHapticFeedback(HapticFeedbackType.GestureEnd)

    /** A long-press opened a contextual surface (menus, reorder handles). */
    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    /** A user action succeeded (added to playlist, saved to library). */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** A user action failed or was refused (playback error, nothing to play). */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
}

/** Remembers a [VibraHaptics] bound to the current (preference-gated) haptic feedback. */
@Composable
fun rememberVibraHaptics(): VibraHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { VibraHaptics(feedback) }
}
