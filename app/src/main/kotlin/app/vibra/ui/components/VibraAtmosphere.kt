/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.components

import app.vibra.ui.motion.ambientMotionAllowed
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSpeed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.ui.screens.library.rememberArtworkGradient

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  ATMOSPHERE — soft colour fields derived from artwork
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  Apple Music paints its player with a slowly moving wash of the cover's colours rather
 *  than a blurred photo. Vibra reproduces the idea with plain radial gradients:
 *    • the cover's palette (engine PlayerColorExtractor) gives 2‥4 colours;
 *    • each colour becomes a very large radial blob; the blobs drift on slow, out-of-phase
 *      orbits, so the field breathes without ever showing a recognisable shape;
 *    • on Android 12+ a tiny, heavily blurred copy of the cover adds texture. Its blur is
 *      *unbounded* (BlurredEdgeTreatment.Unbounded) — the bounded default is what produced
 *      the hard "box" edges of the first implementation.
 *  Radial gradients are soft by construction, so the effect is identical on every API level
 *  and costs one draw pass.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/**
 * Palette of an artwork, cross-faded quickly when the track changes.
 *
 * Colours only ever come from a cover: while the next cover's palette is being extracted,
 * the previous one is held (the engine helper briefly reports a theme-coloured
 * placeholder, which used to tint the player with the app colour between two tracks).
 */
@Composable
fun rememberArtworkColors(url: String?): List<Color> {
    val fallback = MaterialTheme.colorScheme.primaryContainer
    val extracted = rememberArtworkGradient(thumbnailUrl = url, fallbackColor = fallback)
    val pending = extracted.size == 2 && extracted[0] == fallback && extracted[1] == fallback.copy(alpha = 0.5f)
    val held = remember { arrayOfNulls<List<Color>>(1) }
    if (!pending) held[0] = extracted.map { it.copy(alpha = 1f) }
    val base = held[0] ?: listOf(fallback)
    val spec = Liquid.effects<Color>(LiquidSpeed.Fast)
    val c0 by animateColorAsState(base.getOrElse(0) { fallback }, spec, label = "atmosphere0")
    val c1 by animateColorAsState(base.getOrElse(1) { c0 }, spec, label = "atmosphere1")
    val c2 by animateColorAsState(base.getOrElse(2) { lerp(c0, c1, 0.5f) }, spec, label = "atmosphere2")
    return listOf(c0, c1, c2)
}

/**
 * Accent for controls drawn on an artwork backdrop: the most colourful colour of
 * [colors], lifted to a light tone so it reads on the dimmed field (filled play button,
 * active toggles, progress). [ArtworkAccent.on] is the matching dark content colour.
 */
fun artworkAccent(colors: List<Color>): ArtworkAccent {
    val hsl = FloatArray(3)
    val vivid =
        colors.maxBy { color ->
            ColorUtils.colorToHSL(color.toArgb(), hsl)
            hsl[1] * (1f - abs(hsl[2] - 0.5f))
        }
    ColorUtils.colorToHSL(vivid.toArgb(), hsl)
    val saturation = hsl[1].coerceAtMost(0.78f)
    val accent = Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], saturation, hsl[2].coerceIn(0.74f, 0.86f))))
    val on = Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], saturation, 0.14f)))
    return ArtworkAccent(accent, on)
}

/** See [artworkAccent]. */
@Immutable
data class ArtworkAccent(
    val color: Color,
    val on: Color,
)

/**
 * Full-bleed moving colour field for a cover (player backdrop, For You card).
 *
 * @param dim 0‥1 darkening applied over the field for text legibility.
 * @param moving drift the blobs. Off by default: the drift redraws the surface (and
 *        re-applies the texture blur) on every frame, for as long as it is on screen.
 * @param textureBlur blur of the cover texture (Android 12+); smaller keeps it recognisable.
 */
@Composable
fun ArtworkAtmosphere(
    url: String?,
    modifier: Modifier = Modifier,
    dim: Float = 0.38f,
    moving: Boolean = false,
    textureAlpha: Float = 0.45f,
    textureBlur: Dp = 72.dp,
) {
    val colors = rememberArtworkColors(url)
    val drift = rememberDrift(moving && ambientMotionAllowed)
    val base = MaterialTheme.colorScheme.background
    Box(
        modifier =
            modifier.drawBehind {
                drawRect(lerp(colors[0], base, 0.55f))
                drawAtmosphereBlobs(colors, drift.value)
            },
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && url != null && textureAlpha > 0f) {
            VibraArtwork(
                url = url,
                shape = RectangleShape,
                sizePx = 96,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = textureAlpha
                            scaleX = 1.5f
                            scaleY = 1.5f
                        }.blur(textureBlur, BlurredEdgeTreatment.Unbounded),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = dim)) },
        )
    }
}

/**
 * Soft coloured halo behind hero covers: the cover's colours as radial light spilling
 * [spread] beyond the cover on every side. Place it behind the cover in the same Box,
 * matching the cover's size; it draws outside its own bounds and never clips.
 */
@Composable
fun ArtworkGlow(
    url: String?,
    modifier: Modifier = Modifier,
    spread: Dp = 56.dp,
    alpha: Float = 0.55f,
) {
    val colors = rememberArtworkColors(url)
    Box(
        modifier =
            modifier.drawBehind {
                val reach = spread.toPx()
                val radius = max(size.width, size.height) / 2f + reach
                val centre = center
                listOf(
                    colors[0] to Offset(-size.width * 0.12f, -size.height * 0.08f),
                    colors[1] to Offset(size.width * 0.14f, size.height * 0.1f),
                    colors[2] to Offset(0f, size.height * 0.16f),
                ).forEach { (color, shift) ->
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                0f to color.copy(alpha = alpha),
                                0.55f to color.copy(alpha = alpha * 0.45f),
                                1f to Color.Transparent,
                                center = centre + shift,
                                radius = radius,
                            ),
                        radius = radius,
                        center = centre + shift,
                    )
                }
            },
    )
}

/** Slow 0‥1 phase; a still value when [moving] is false. */
@Composable
private fun rememberDrift(moving: Boolean): State<Float> {
    if (!moving) return remember { mutableFloatStateOf(0.18f) }
    val transition = rememberInfiniteTransition(label = "atmosphereDrift")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(DriftPeriodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "atmosphereDriftPhase",
    )
}

/** Three large blobs on out-of-phase orbits; together they read as a single moving wash. */
private fun DrawScope.drawAtmosphereBlobs(
    colors: List<Color>,
    phase: Float,
) {
    val w = size.width
    val h = size.height
    val radius = max(w, h) * 0.75f
    val turn = phase * 2f * PI.toFloat()
    val anchors =
        listOf(
            Offset(w * (0.25f + 0.12f * cos(turn)), h * (0.22f + 0.08f * sin(turn))),
            Offset(w * (0.82f + 0.10f * sin(turn + 2.1f)), h * (0.45f + 0.10f * cos(turn + 2.1f))),
            Offset(w * (0.35f + 0.14f * cos(turn + 4.2f)), h * (0.85f + 0.06f * sin(turn + 4.2f))),
        )
    anchors.forEachIndexed { index, centre ->
        val color = colors[index % colors.size]
        drawCircle(
            brush =
                Brush.radialGradient(
                    0f to color.copy(alpha = 0.85f),
                    0.6f to color.copy(alpha = 0.3f),
                    1f to Color.Transparent,
                    center = centre,
                    radius = radius,
                ),
            radius = radius,
            center = centre,
        )
    }
}

private const val DriftPeriodMs = 26_000
