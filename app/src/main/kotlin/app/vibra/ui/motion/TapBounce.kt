/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.motion

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A light bounce when an element is pressed. Song rows and tiles use it, so starting a song gets
 * a small physical answer wherever it is.
 *
 * - A quick tap squishes and springs back in one kick.
 * - Holding the finger down keeps the element squished (also through a long press, which opens
 *   the element's menu as usual); it springs back only when the finger lifts.
 * - If the finger starts to scroll or swipe, the element lets go at once.
 * The squish waits for the system tap timeout before showing, so a finger that is only starting a
 * scroll never dents the rows it lands on.
 *
 * It only observes the pointer (initial pass, nothing consumed), so the element's own
 * click/long-click, ripple, swipe and drag handling run unchanged. A part of the element that
 * has its own action (a ⋮ button, a checkbox) is marked with [noTapBounce] and doesn't bounce it.
 */
private val tapOwners = mutableMapOf<PointerId, TapBounce>()

class TapBounce internal constructor(
    internal val nudge: LiquidNudge,
) {
    internal var skippedPointer: PointerId? = null
}

@Composable
fun rememberTapBounce(): TapBounce {
    val nudge = rememberLiquidNudge()
    return remember(nudge) { TapBounce(nudge) }
}

/** Scales the element down by [squash] at the peak of a tap and springs it back. */
fun Modifier.tapBounce(
    bounce: TapBounce,
    squash: Float = TapSquash,
): Modifier =
    nudge(bounce.nudge, squash = squash)
        .pointerInput(bounce) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                tapOwners[down.id] = bounce
                bounce.skippedPointer = null
                val slop = viewConfiguration.touchSlop
                // Phase 1: a quick tap that lifts before the tap timeout gets the one-shot kick.
                var outcome: PressOutcome = PressOutcome.Held
                withTimeoutOrNull(TapTimeoutMillis) {
                    outcome = awaitPressEnd(down, slop)
                }
                if (tapOwners[down.id] !== bounce || bounce.skippedPointer == down.id) return@awaitEachGesture
                when (outcome) {
                    PressOutcome.Lifted -> bounce.nudge.kick()
                    PressOutcome.Moved -> Unit
                    PressOutcome.Held -> {
                        // Phase 2: the finger is still down — squish and hold until it lifts or
                        // starts scrolling. No timeout: a long press keeps the squish too.
                        bounce.nudge.press()
                        try {
                            awaitPressEnd(down, slop)
                        } finally {
                            // Also when the gesture is cancelled (element leaves the screen).
                            bounce.nudge.release()
                        }
                    }
                }
            }
        }

/** Taps that start on this part of a [tapBounce] element leave the element still. */
fun Modifier.noTapBounce(bounce: TapBounce): Modifier =
    pointerInput(bounce) {
        awaitEachGesture {
            bounce.skippedPointer = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).id
        }
    }

/** How a press of the [tapBounce] element ended (or that it is still going on). */
private enum class PressOutcome { Lifted, Moved, Held }

/**
 * Waits until the finger of [down] lifts ([PressOutcome.Lifted]) or travels past [slop], i.e. a
 * scroll or swipe took over ([PressOutcome.Moved]). A cancelled pointer counts as moved.
 */
private suspend fun AwaitPointerEventScope.awaitPressEnd(
    down: PointerInputChange,
    slop: Float,
): PressOutcome {
    while (true) {
        val change =
            awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                ?: return PressOutcome.Moved
        if (change.changedToUpIgnoreConsumed()) return PressOutcome.Lifted
        if ((change.position - down.position).getDistance() > slop) return PressOutcome.Moved
    }
}

private const val TapSquash = 0.04f

/** Android's tap timeout (ViewConfiguration.getTapTimeout): below it a press is still a "tap". */
private const val TapTimeoutMillis = 100L
