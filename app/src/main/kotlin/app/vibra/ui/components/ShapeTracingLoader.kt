/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibra.ui.motion.Liquid

/**
 * Indeterminate progress that runs *along the outline* of [shape] — Vibra's take on the
 * classic circular spinner, used by the cookie play button while buffering.
 *
 * Apply it inside the element that is clipped to the same shape (and rotated with it): the
 * stroke is centred on the outline, so the clip leaves exactly its inner half visible and
 * the indicator hugs the edge at the shape's current position and rotation.
 *
 * Motion mirrors M3's circular indicator: the arc travels round the outline while its
 * length breathes between short and long. Like M3's indicator it keeps one even thickness
 * end to end, with round caps.
 */
fun Modifier.shapeTracingLoader(
    state: ShapeTracingLoaderState?,
    shape: Shape,
    color: Color,
    strokeWidth: Dp = 3.dp,
): Modifier =
    if (state == null) {
        this
    } else {
        drawShapeTracingLoader(state, shape, color, strokeWidth)
    }

private fun Modifier.drawShapeTracingLoader(
    state: ShapeTracingLoaderState,
    shape: Shape,
    color: Color,
    strokeWidth: Dp,
): Modifier =
    drawWithContent {
        drawContent()
        val alpha = state.visibility()
        if (alpha <= 0.01f) return@drawWithContent
        val outlinePath = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
        val measure = PathMeasure().apply { setPath(outlinePath, forceClosed = true) }
        val length = measure.length
        if (length <= 0f) return@drawWithContent

        val sweep = length * (MinSweep + (MaxSweep - MinSweep) * state.breath())
        val start = state.phase() * length
        val segment = Path()
        val from = start % length
        val to = from + sweep
        if (to <= length) {
            measure.getSegment(from, to, segment, startWithMoveTo = true)
        } else {
            measure.getSegment(from, length, segment, startWithMoveTo = true)
            measure.getSegment(0f, to - length, segment, startWithMoveTo = false)
        }
        drawPath(
            path = segment,
            color = color,
            alpha = alpha,
            // Doubled: half of the stroke is clipped away by the shape.
            style = Stroke(width = strokeWidth.toPx() * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

/**
 * Determinate sibling of [shapeTracingLoader]: the outline of [shape] is traced from its
 * start up to [progress] (0‥1, read in draw), e.g. how far a pull-to-refresh has gone.
 * Same placement rule: apply it inside the element clipped to [shape].
 */
fun Modifier.shapeProgress(
    progress: () -> Float,
    shape: Shape,
    color: Color,
    strokeWidth: Dp = 3.dp,
): Modifier =
    drawWithContent {
        drawContent()
        val fraction = progress().coerceIn(0f, 1f)
        if (fraction <= 0.005f) return@drawWithContent
        val outlinePath = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
        val measure = PathMeasure().apply { setPath(outlinePath, forceClosed = true) }
        val segment = Path()
        measure.getSegment(0f, measure.length * fraction, segment, startWithMoveTo = true)
        drawPath(
            path = segment,
            color = color,
            alpha = (fraction * 3f).coerceAtMost(1f),
            style = Stroke(width = strokeWidth.toPx() * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

/**
 * Animated inputs for [shapeTracingLoader], or null while hidden — the infinite animation
 * only exists while the loader is visible, so idle buttons cost no frames.
 */
@Composable
fun rememberShapeTracingLoaderState(active: Boolean): ShapeTracingLoaderState? {
    val visibility =
        animateFloatAsState(
            targetValue = if (active) 1f else 0f,
            animationSpec = Liquid.effects(),
            label = "shapeLoaderVisibility",
        )
    val fadingOut by remember { derivedStateOf { visibility.value > 0.01f } }
    if (!active && !fadingOut) return null
    val transition = rememberInfiniteTransition(label = "shapeLoader")
    val phase =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_500, easing = LinearEasing), RepeatMode.Restart),
            label = "shapeLoaderPhase",
        )
    val breath =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "shapeLoaderBreath",
        )
    return remember(phase, breath, visibility) {
        ShapeTracingLoaderState(
            visibility = { visibility.value },
            phase = { phase.value },
            breath = { breath.value },
        )
    }
}

/** Frame-time readers for [shapeTracingLoader]. */
class ShapeTracingLoaderState(
    val visibility: () -> Float,
    val phase: () -> Float,
    val breath: () -> Float,
)

private const val MinSweep = 0.10f
private const val MaxSweep = 0.38f
