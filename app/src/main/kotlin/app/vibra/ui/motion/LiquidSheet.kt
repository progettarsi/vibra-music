/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A 0‥1 progress value that can be driven by a finger *and* by springs, with momentum
 * handed over seamlessly between the two. 0 = collapsed, 1 = expanded.
 *
 * Used for the player (mini-player ↔ full screen) and for the queue panel inside it.
 *
 *   drag  ─►  dragBy(deltaPx)        progress follows the finger 1:1 over [dragRangePx]
 *   lift  ─►  settle(velocityPx)     fling decides the side; the spring starts with the
 *                                    finger's velocity, so there is no hitch on release.
 *   code  ─►  expand() / collapse()  same springs, e.g. tapping the mini-player.
 *
 * The spring comes from the caller (by default Liquid.container(): no overshoot, since a
 * bouncing full-screen surface reads as a glitch) so reduced-motion settings apply.
 */
@Stable
class LiquidSheetState internal constructor(
    initialProgress: Float,
    private val scope: CoroutineScope,
) {
    private val animatable = Animatable(initialProgress)

    /** Current progress, 0 (collapsed) … 1 (expanded). Read in layout/draw for best perf. */
    val progress: Float get() = animatable.value

    /** Distance in px that corresponds to a full 0→1 drag. Set by the layout. */
    var dragRangePx by mutableFloatStateOf(1f)

    /** The value the sheet is heading to (or resting at). */
    var target by mutableFloatStateOf(initialProgress)
        private set

    val isExpanded by derivedStateOf { target == 1f }

    /** True while visibly open or opening — use to gate back handling and system bars. */
    val isOpenOrOpening by derivedStateOf { target == 1f || animatable.value > 0.001f }

    /** Spring used by expand/collapse/settle; injected by the composable that owns it. */
    internal var spec: AnimationSpec<Float>? = null

    /**
     * Drag position accumulated synchronously. Snapping the Animatable happens in a
     * coroutine, so reading `animatable.value` between two quick drag events would lose
     * deltas; this field is the source of truth while a finger is down.
     */
    private var dragValue = Float.NaN

    /** Moves progress by a raw drag delta (positive = finger moving down = collapsing). */
    fun dragBy(deltaPx: Float) {
        // First event of a drag: grab the sheet where it currently is (also halts a
        // running spring, because snapTo cancels any ongoing animation).
        val start = if (dragValue.isNaN()) animatable.value else dragValue
        val next = (start - deltaPx / dragRangePx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        dragValue = next
        target = if (next > 0.5f) 1f else 0f
        scope.launch { animatable.snapTo(next) }
    }

    /**
     * Ends a drag. A fast fling decides the direction regardless of position; a slow
     * release snaps to the nearest end.
     *
     * @param velocityPx finger velocity in px/s (positive = downwards).
     */
    fun settle(velocityPx: Float) {
        val position = if (dragValue.isNaN()) animatable.value else dragValue
        dragValue = Float.NaN
        val toExpanded =
            when {
                velocityPx < -FlingVelocityPx -> true
                velocityPx > FlingVelocityPx -> false
                else -> position > 0.5f
            }
        animateTo(if (toExpanded) 1f else 0f, initialVelocity = -velocityPx / dragRangePx.coerceAtLeast(1f))
    }

    fun expand() = animateTo(1f)

    fun collapse() = animateTo(0f)

    /** Instantly jumps to [value] (state restoration, reduced motion). */
    fun snapTo(value: Float) {
        target = value
        scope.launch { animatable.snapTo(value) }
    }

    /** Lets a predictive-back gesture scrub the sheet: [backProgress] 0 → 1 while swiping. */
    fun previewBack(backProgress: Float) {
        scope.launch { animatable.snapTo(1f - backProgress * BackPreviewTravel) }
    }

    private fun animateTo(
        value: Float,
        initialVelocity: Float = 0f,
    ) {
        dragValue = Float.NaN
        target = value
        scope.launch {
            val animationSpec = spec
            if (animationSpec == null) {
                animatable.snapTo(value)
            } else {
                animatable.animateTo(value, animationSpec, initialVelocity)
            }
        }
    }

    internal companion object {
        /** ~ M3 "fling" threshold; below it, position decides. */
        const val FlingVelocityPx = 1200f

        /** How far a predictive back swipe previews the collapse (fraction of travel). */
        const val BackPreviewTravel = 0.18f

        fun saver(scope: CoroutineScope) =
            Saver<LiquidSheetState, Float>(
                save = { it.target },
                restore = { LiquidSheetState(it, scope) },
            )
    }
}

/** Remembers a [LiquidSheetState] that survives configuration changes. */
@Composable
fun rememberLiquidSheetState(
    initialProgress: Float = 0f,
    spec: AnimationSpec<Float> = Liquid.container(),
): LiquidSheetState {
    val scope = rememberCoroutineScope()
    val state =
        rememberSaveable(saver = LiquidSheetState.saver(scope)) {
            LiquidSheetState(initialProgress, scope)
        }
    state.spec = spec
    return state
}
