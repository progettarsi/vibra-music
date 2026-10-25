/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalAnimationsDisabled

/**
 * Single-line text that scrolls only when it does not fit (mini-player and player titles).
 *
 * Behaviour:
 *  - Fits: plain text, no fades, no motion.
 *  - Overflows: rests for [initialDelayMillis], then scrolls at about [velocity] (easing
 *    in and out, so it never jerks into motion) until a second copy (after [gap]) reaches
 *    the start, rests again, and so on.
 *  - Edge fades are dynamic: the trailing fade is shown whenever text continues past the
 *    edge; the leading fade grows with the distance the text has travelled (it is fully on
 *    only once a whole fade width has slid under the edge) and shrinks the same way as the
 *    copy arrives — so a still title is never cut on the left and the fade never pops.
 *
 * Fades are alpha masks (DstOut on an offscreen layer), so they work on any background.
 */
@Composable
fun VibraMarquee(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    fadeWidth: Dp = 20.dp,
    gap: Dp = 56.dp,
    velocity: Dp = 26.dp,
    initialDelayMillis: Long = 2_200,
) {
    val density = LocalDensity.current
    val animationsDisabled = LocalAnimationsDisabled.current
    var textWidth by remember { mutableIntStateOf(0) }
    var containerWidth by remember { mutableIntStateOf(0) }
    val offset = remember(text) { Animatable(0f) }
    val gapPx = with(density) { gap.toPx() }
    val overflow = textWidth > containerWidth && containerWidth > 0

    LaunchedEffect(text, overflow, textWidth, animationsDisabled) {
        offset.snapTo(0f)
        if (!overflow || animationsDisabled) return@LaunchedEffect
        val travel = textWidth + gapPx
        // The eased curve averages ~15 % slower than linear; stretch to keep the speed.
        val durationMs = (travel / with(density) { velocity.toPx() } * 1150f).roundToInt()
        while (true) {
            delay(initialDelayMillis)
            offset.animateTo(-travel, tween(durationMs, easing = MarqueeEasing))
            // The copy now sits exactly where the original started: jumping back is invisible.
            offset.snapTo(0f)
        }
    }

    val trailingFade = if (overflow) 1f else 0f

    Layout(
        content = {
            Text(text = text, style = style, color = color, fontWeight = fontWeight, maxLines = 1, softWrap = false)
            Text(text = text, style = style, color = color, fontWeight = fontWeight, maxLines = 1, softWrap = false)
        },
        modifier =
            modifier
                .clipToBounds()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fadePx = fadeWidth.toPx().coerceAtMost(size.width / 3f)
                    // Read in draw: follows the scroll without recomposing.
                    val travelled = -offset.value
                    val remaining = textWidth + gapPx - travelled
                    val leadingFade =
                        if (overflow) (minOf(travelled, remaining) / fadePx).coerceIn(0f, 1f) else 0f
                    if (leadingFade > 0f) {
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    0f to Color.Black.copy(alpha = leadingFade),
                                    1f to Color.Transparent,
                                    startX = 0f,
                                    endX = fadePx,
                                ),
                            size = Size(fadePx, size.height),
                            blendMode = BlendMode.DstOut,
                        )
                    }
                    if (trailingFade > 0f) {
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = trailingFade),
                                    startX = size.width - fadePx,
                                    endX = size.width,
                                ),
                            topLeft = Offset(size.width - fadePx, 0f),
                            size = Size(fadePx, size.height),
                            blendMode = BlendMode.DstOut,
                        )
                    }
                },
    ) { measurables, constraints ->
        val free = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val first = measurables[0].measure(free)
        val second = measurables[1].measure(free)
        textWidth = first.width
        containerWidth = constraints.maxWidth
        val width = constraints.constrainWidth(first.width)
        layout(width, first.height) {
            // Reading the offset here keeps scrolling in the layout phase (no recomposition).
            val x = offset.value.roundToInt()
            first.place(x, 0)
            if (textWidth > containerWidth) second.place(x + first.width + gapPx.roundToInt(), 0)
        }
    }
}

/** Gentle ease-in-out (sine-like) for the scroll loop. */
private val MarqueeEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
