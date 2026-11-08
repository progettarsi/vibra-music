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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * By default this hugs the edge of an element clipped to [shape]. Set [insetStroke] and
 * apply it before the clip to draw the full stroke and its round caps inside the outer bounds.
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
    insetStroke: Boolean = false,
): Modifier =
    if (state == null) {
        this
    } else {
        drawOutlineProgress(
            shape = shape,
            color = color,
            strokeWidth = strokeWidth,
            insetStroke = insetStroke,
            phase = state.phase,
            sweep = { MinSweep + (MaxSweep - MinSweep) * state.breath() },
            visibility = state.visibility,
        )
    }

/**
 * Determinate sibling of [shapeTracingLoader], for pull-to-refresh. Apply it before clipping
 * the content to [shape] so the inset stroke retains its round caps.
 */
fun Modifier.shapeProgress(
    progress: () -> Float,
    shape: Shape,
    color: Color,
    strokeWidth: Dp = 3.dp,
): Modifier =
    drawOutlineProgress(
        shape = shape,
        color = color,
        strokeWidth = strokeWidth,
        insetStroke = true,
        phase = { 0f },
        sweep = progress,
        visibility = { (progress() * 3f).coerceIn(0f, 1f) },
    )

private fun Modifier.drawOutlineProgress(
    shape: Shape,
    color: Color,
    strokeWidth: Dp,
    insetStroke: Boolean,
    phase: () -> Float,
    sweep: () -> Float,
    visibility: () -> Float,
): Modifier =
    drawWithCache {
        val width = strokeWidth.toPx()
        val inset = if (insetStroke) width / 2f else 0f
        val outlineSize = Size(
            (size.width - 2f * inset).coerceAtLeast(0f),
            (size.height - 2f * inset).coerceAtLeast(0f),
        )
        val outlinePath = Path().apply {
            addOutline(shape.createOutline(outlineSize, layoutDirection, this@drawWithCache))
            if (insetStroke) translate(Offset(inset, inset))
        }
        val measure = PathMeasure().apply { setPath(outlinePath, forceClosed = true) }
        val length = measure.length
        val segment = Path()
        val stroke = Stroke(
            // The original edge treatment draws double width because the clip hides half of it.
            width = if (insetStroke) width else 2f * width,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        onDrawWithContent {
            drawContent()
            val alpha = visibility()
            val fraction = sweep().coerceIn(0f, 1f)
            if (length <= 0f || alpha <= 0.01f || fraction <= 0.005f) return@onDrawWithContent
            if (fraction == 1f) {
                drawPath(outlinePath, color, alpha = alpha, style = stroke)
                return@onDrawWithContent
            }

            val from = (phase() * length) % length
            val to = from + length * fraction
            segment.rewind()
            measure.getSegment(from, minOf(to, length), segment, startWithMoveTo = true)
            if (to > length) {
                measure.getSegment(0f, to - length, segment, startWithMoveTo = false)
            }
            drawPath(segment, color, alpha = alpha, style = stroke)
        }
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
