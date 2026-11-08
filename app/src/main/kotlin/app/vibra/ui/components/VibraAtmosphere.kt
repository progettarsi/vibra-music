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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import app.vibra.ui.theme.MoodTone
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSpeed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.ui.screens.library.rememberArtworkGradient
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor

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
    return artworkAccent(vivid)
}

/** The accent made from one cover colour: its hue, lifted to a light tone; see [artworkAccent]. */
fun artworkAccent(vivid: Color): ArtworkAccent {
    val hsl = FloatArray(3)
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
 * The accent of the cover at [url], read from the cover's own colours: the most colourful one
 * that covers a visible part of it ([vividSwatch]). The backdrop's palette turns a cover with a
 * neutral background (black, white, a grey wall) into greys, which would grey the controls too;
 * a red jacket on a white cover still colours them here. Only a cover with no colour at all
 * falls back to that palette. The previous cover's accent is held while the next one is read.
 */
@Composable
fun rememberArtworkAccent(url: String?): ArtworkAccent {
    val colors = rememberArtworkColors(url)
    val context = LocalContext.current
    // The colour read from the current cover; null when it has none (or before the first).
    var vivid by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val request =
            ImageRequest.Builder(context)
                .data(url)
                .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                .allowHardware(false)
                .build()
        val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull() ?: return@LaunchedEffect
        vivid =
            withContext(Dispatchers.Default) {
                val palette =
                    Palette.from(bitmap)
                        .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                        .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                        .generate()
                vividSwatch(palette.swatches.map { CoverSwatch(Color(it.rgb), it.population) })
            }
    }
    val target = vivid?.let { artworkAccent(it) } ?: artworkAccent(colors)
    val spec = Liquid.effects<Color>(LiquidSpeed.Fast)
    val color by animateColorAsState(target.color, spec, label = "accent")
    val on by animateColorAsState(target.on, spec, label = "accentOn")
    return ArtworkAccent(color, on)
}

/** One colour of a cover and how many of its pixels have it. */
@Immutable
data class CoverSwatch(
    val color: Color,
    val population: Int,
)

/**
 * The cover colour the controls take: the most colourful of [swatches] (saturated and neither
 * near black nor near white) among those that cover at least a small part of the cover, so a
 * speck of colour does not win. Null when no swatch has colour.
 */
fun vividSwatch(swatches: List<CoverSwatch>): Color? {
    val total = swatches.sumOf { it.population }.coerceAtLeast(1)
    return swatches
        .filter { it.population >= total * MinAccentShare && hslSaturation(it.color) >= MinAccentSaturation }
        .maxByOrNull {
            val lightness = hslLightness(it.color)
            hslSaturation(it.color) * (1f - abs(lightness - 0.5f) * 2f) * sqrt(it.population.toFloat() / total)
        }
        ?.color
}

private fun hslLightness(color: Color) = (maxOf(color.red, color.green, color.blue) + minOf(color.red, color.green, color.blue)) / 2f

private fun hslSaturation(color: Color): Float {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val lightness = (max + min) / 2f
    return if (max == min) 0f else (max - min) / (1f - abs(2f * lightness - 1f)).coerceAtLeast(0.0001f)
}

/** A swatch must cover this share of the cover to colour the controls. */
private const val MinAccentShare = 0.02f

/** Below this saturation a swatch counts as grey. */
private const val MinAccentSaturation = 0.25f

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

/**
 * The backdrop of a mood page: the mood's shape in its colours, large, blurred and drifting
 * slowly, under the faint outlines of the profile wallpaper. It fades into the page before
 * the content below it. Below Android 12 there is no blur, so soft round washes stand in.
 */
@Composable
fun MoodAtmosphere(
    polygon: RoundedPolygon,
    tone: MoodTone,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(MoodAtmosphereHeight).clipToBounds()) {
        // Placed for a 390 dp phone and scaled with the width, so tablets keep the composition.
        val unit = maxWidth / 390f
        val bright = listOf(tone.bright, tone.middle, tone.deep)
        val deep = listOf(tone.deep, tone.middle, tone.bright)
        MoodBlob(polygon, bright, side = unit * 400, x = unit * -110, y = unit * -90, alpha = 0.72f, drift = Offset(46f, 34f), driftMs = 21_000, turnMs = 140_000)
        MoodBlob(polygon, deep, side = unit * 300, x = unit * 190, y = unit * 150, alpha = 0.55f, drift = Offset(-40f, -26f), driftMs = 27_000, turnMs = -180_000)
        MoodBlob(polygon, bright, side = unit * 170, x = unit * 10, y = unit * 380, alpha = 0.55f, drift = Offset(30f, -40f), driftMs = 17_000, turnMs = 140_000, blur = 28.dp)
        ShapeWallpaper(polygon = polygon, color = tone.accent.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth().height(MoodWallpaperHeight))
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(MoodFadeHeight)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to background.copy(alpha = 0.75f), 1f to background)),
        )
    }
}

/**
 * One blurred shape. Only the outer layer moves: the blurred layer inside it is drawn once
 * and reused, so the drift costs no blur per frame.
 *
 * @param drift how far it wanders, in dp; it goes out and comes back once per [driftMs].
 * @param turnMs one revolution; negative turns the other way.
 */
@Composable
private fun MoodBlob(
    polygon: RoundedPolygon,
    colors: List<Color>,
    side: Dp,
    x: Dp,
    y: Dp,
    alpha: Float,
    drift: Offset,
    driftMs: Int,
    turnMs: Int,
    blur: Dp = 46.dp,
) {
    val moving = ambientMotionAllowed
    val wander = rememberDrift(moving, driftMs)
    val turn = rememberDrift(moving, abs(turnMs))
    val direction = if (turnMs < 0) -1f else 1f
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        Modifier
            .offset(x, y)
            .size(side)
            .graphicsLayer {
                val wave = (1f - cos(wander.value * 2f * PI.toFloat())) / 2f
                translationX = drift.x.dp.toPx() * wave
                translationY = drift.y.dp.toPx() * wave
                rotationZ = turn.value * 360f * direction
            }.then(if (canBlur) Modifier.blur(blur, BlurredEdgeTreatment.Unbounded) else Modifier)
            .drawWithCache {
                val path =
                    polygon.toPath().asComposePath().apply {
                        transform(Matrix().apply { scale(size.width, size.height) })
                    }
                val sharp = Brush.linearGradient(colors, Offset.Zero, Offset(size.width, size.height))
                val soft = Brush.radialGradient(listOf(colors[1], Color.Transparent))
                onDrawBehind {
                    if (canBlur) drawPath(path, sharp, alpha = alpha) else drawCircle(soft, alpha = alpha)
                }
            },
    )
}

/**
 * Wallpaper: large, faint outlines of a shape that drift and turn very slowly (one
 * revolution every few minutes), so a page feels alive without distracting.
 */
@Composable
fun ShapeWallpaper(
    polygon: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val path = remember(polygon) { polygon.toPath().asComposePath() }
    val turn =
        if (!ambientMotionAllowed) {
            null
        } else {
            rememberInfiniteTransition(label = "wallpaper").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(180_000, easing = LinearEasing), RepeatMode.Restart),
                label = "wallpaperTurn",
            )
        }
    // (x, y, size) as fractions of the width — follows the Figma blob placement.
    val placements = remember { listOf(Triple(-0.12f, -0.1f, 0.42f), Triple(0.78f, 0.62f, 0.26f), Triple(0.8f, -0.04f, 0.3f), Triple(-0.06f, 0.62f, 0.34f)) }
    Canvas(modifier = modifier) {
        val angle = turn?.value ?: 0f
        placements.forEachIndexed { index, (fx, fy, fs) ->
            val side = size.width * fs
            val matrix = Matrix().apply { scale(side, side) }
            val scaled = androidx.compose.ui.graphics.Path().apply { addPath(path) }.also { it.transform(matrix) }
            translate(left = size.width * fx, top = size.width * fy) {
                rotate(degrees = if (index % 2 == 0) angle else -angle, pivot = Offset(side / 2f, side / 2f)) {
                    drawPath(scaled, color = color, style = Stroke(width = 3.dp.toPx()))
                }
            }
        }
    }
}

private val MoodAtmosphereHeight = 600.dp
private val MoodWallpaperHeight = 380.dp
private val MoodFadeHeight = 300.dp

/** Slow 0‥1 phase; a still value when [moving] is false. */
@Composable
private fun rememberDrift(
    moving: Boolean,
    periodMs: Int = DriftPeriodMs,
): State<Float> {
    if (!moving) return remember { mutableFloatStateOf(0.18f) }
    val transition = rememberInfiniteTransition(label = "atmosphereDrift")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
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
