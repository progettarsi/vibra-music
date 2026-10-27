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
 * A light bounce when an element is tapped: pressed and lifted in place, before a long press.
 * Song rows and tiles use it, so starting a song gets a small physical answer wherever it is.
 *
 * It only observes the pointer (initial pass, nothing consumed), so the element's own
 * click/long-click, ripple, swipe and drag handling run unchanged. A part of the element that
 * has its own action (a ⋮ button, a checkbox) is marked with [noTapBounce] and doesn't bounce it.
 */
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
                bounce.skippedPointer = null
                val up =
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        awaitTapUp(down, viewConfiguration.touchSlop)
                    }
                if (up != null && bounce.skippedPointer != down.id) bounce.nudge.kick()
            }
        }

/** Taps that start on this part of a [tapBounce] element leave the element still. */
fun Modifier.noTapBounce(bounce: TapBounce): Modifier =
    pointerInput(bounce) {
        awaitEachGesture {
            bounce.skippedPointer = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).id
        }
    }

/** The lift of [down], or null once the finger travels past [slop] (a scroll or swipe). */
private suspend fun AwaitPointerEventScope.awaitTapUp(
    down: PointerInputChange,
    slop: Float,
): PointerInputChange? {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return null
        if (change.changedToUpIgnoreConsumed()) return change
        if ((change.position - down.position).getDistance() > slop) return null
    }
}

private const val TapSquash = 0.04f
