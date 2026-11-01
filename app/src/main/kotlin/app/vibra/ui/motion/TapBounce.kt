/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.motion

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
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
 * click/long-click, ripple, swipe and drag handling run unchanged. The innermost bouncing element
 * under the finger owns the press, so nested surfaces never bounce together. A part of the
 * element that has its own action (a ⋮ button, a checkbox) is marked with [noTapBounce] and
 * doesn't bounce it.
 *
 * The scale applies to what follows it in the modifier chain: it must come before the clip and
 * background of the surface, or the surface stays still while its ripple and content shrink.
 */
class TapBounce internal constructor(
    internal val nudge: LiquidNudge,
) {
    internal var skippedPointer: PointerId? = null

    /** The pointer this bounce already follows: a second input on the same bounce stays out. */
    internal var claimedPointer: PointerId? = null
}

private val tapOwners = mutableMapOf<PointerId, Any>()

@Composable
fun rememberTapBounce(): TapBounce {
    val nudge = rememberLiquidNudge()
    return remember(nudge) { TapBounce(nudge) }
}

/** Scales the element down by [squash] at the peak of a tap and springs it back. */
fun Modifier.tapBounce(
    bounce: TapBounce,
    squash: Float = TapSquash,
): Modifier = tapBounceLayer(bounce, squash).tapBounceInput(bounce)

/** Only the scale of [bounce]; its presses come from a [tapBounceInput] further in. */
fun Modifier.tapBounceLayer(
    bounce: TapBounce?,
    squash: Float = TapSquash,
): Modifier = if (bounce != null) nudge(bounce.nudge, squash = squash) else this

/** Only the press tracking of [bounce], for a layer placed further out ([tapBounceLayer]). */
fun Modifier.tapBounceInput(bounce: TapBounce): Modifier =
    pointerInput(bounce) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (bounce.claimedPointer == down.id) return@awaitEachGesture
            bounce.claimedPointer = down.id
            tapOwners[down.id] = bounce
            bounce.skippedPointer = null
            val slop = viewConfiguration.touchSlop
            try {
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
            } finally {
                if (tapOwners[down.id] === bounce) tapOwners.remove(down.id)
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

/**
 * True where the shared bounce is switched off (a preview, a control that animates on its own).
 * Children inherit it unchanged: a surface never sets it for its own content.
 */
val LocalHasTapBounce = compositionLocalOf { false }

/**
 * The bounce of the surface whose own modifier chain is being built. A shared click modifier in
 * that chain feeds this bounce instead of scaling only what comes after it, so background, mask
 * and ripple move as one. The content of the surface sees the outer value again.
 */
private val LocalTapBounceHost = compositionLocalOf<TapBounce?> { null }

/**
 * A surface that draws clip and background before a caller-supplied click modifier (song rows,
 * engine list rows). [layout] gets the surface's bounce — null when bounce is off — to put first in
 * its chain with [tapBounceLayer]; it wraps its children in [TapBounceHostContent], so a menu
 * button or a checkbox inside keeps its own bounce.
 */
@Composable
fun TapBounceHost(layout: @Composable (bounce: TapBounce?) -> Unit) {
    val bounce = if (LocalHasTapBounce.current) null else rememberTapBounce()
    CompositionLocalProvider(LocalTapBounceHost provides bounce) { layout(bounce) }
}

/** The children of a [TapBounceHost] surface: their click modifiers bounce on their own. */
@Composable
fun TapBounceHostContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTapBounceHost provides null, content = content)
}

/** The bounce of a shared click modifier: the host's when inside one, its own otherwise. */
internal fun Modifier.sharedTapBounce(enabled: Boolean): Modifier = composed {
    val host = LocalTapBounceHost.current
    when {
        !enabled -> Modifier
        host != null -> Modifier.tapBounceInput(host)
        LocalHasTapBounce.current -> Modifier
        else -> Modifier.tapBounce(rememberTapBounce())
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

internal const val TapSquash = 0.04f

/** Android's tap timeout (ViewConfiguration.getTapTimeout): below it a press is still a "tap". */
private const val TapTimeoutMillis = 100L


/** A control with its own animation claims the pointer so its enclosing card stays still. */
fun Modifier.independentTapFeedback(): Modifier = composed {
    val owner = remember { Any() }
    pointerInput(owner) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            tapOwners[down.id] = owner
            try {
                do { val event = awaitPointerEvent(PointerEventPass.Initial) } while (event.changes.any { it.pressed })
            } finally {
                if (tapOwners[down.id] === owner) tapOwners.remove(down.id)
            }
        }
    }
}
