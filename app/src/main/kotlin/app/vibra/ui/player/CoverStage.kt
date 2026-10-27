/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.player

import android.os.Build
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.LocalAnimationsDisabled
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import app.vibra.ui.components.PlayerArtworkPx
import app.vibra.ui.components.SmallArtworkPx
import app.vibra.ui.components.VibraArtwork
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSpeed
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.ui.utils.resize

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  COVER STAGE — the player's cover and the backdrop that continues it
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  Backdrop (CoverExtension): the cover drawn again at its own place and continued above
 *  and below by a MIRRORED copy of its edge band (CoverAnalysis picks the band: no black
 *  letterbox bands, no Parental Advisory label), then blurred in two strengths — a soft
 *  blur hugging the cover's edges over a heavy one everywhere. A mirror matches the cover
 *  exactly at the seam and keeps its shapes and colours further out, so the continuation
 *  reads as the same picture going on (stretched edge rows made straight streaks).
 *  The sharp cover's top and bottom dissolve (coverEdgeFade) along an eased curve, and are
 *  darkened as they dissolve by the same amount as the backdrop: no visible mask line and
 *  no brightness step where the sharp picture ends.
 *
 *  Swipe: cover and backdrop are one page. While the cover is dragged sideways the
 *  neighbouring track's page (its backdrop and cover, prefetched) comes in from that
 *  side; once committed, the new track simply *is* that page — nothing slides twice.
 *
 *  Other track changes (buttons, end of a song): the old page stays fully on top of the
 *  new one until the new cover is actually drawn, then dissolves with a light blur
 *  (rememberCoverCrossfade) — the new cover never shows empty or blinks.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/** Covers of the tracks before and after the current one (null where there is none). */
fun Player.neighbourArtwork(): Pair<String?, String?> {
    fun at(index: Int) = if (index == C.INDEX_UNSET) null else getMediaItemAt(index).metadata?.thumbnailUrl
    return at(previousMediaItemIndex) to at(nextMediaItemIndex)
}

/** Loads the neighbours' covers in both sizes the stage draws, so a change never waits. */
@Composable
fun PrefetchCovers(urls: List<String?>) {
    val context = LocalPlatformContext.current
    LaunchedEffect(urls) {
        val loader = SingletonImageLoader.get(context)
        urls.filterNotNull().forEach { url ->
            loader.enqueue(ImageRequest.Builder(context).data(url.resize(PlayerArtworkPx, PlayerArtworkPx)).build())
            loadCover(context, url)
        }
    }
}

/**
 * Set by the swipe gesture right before it changes track: the incoming page is already
 * on screen, so the next cover change must not cross-fade again.
 */
class CoverHandoff {
    var fromSwipe = false
}

/** The previous cover while it dissolves after a track change, and its opacity. */
@Stable
class CoverCrossfade internal constructor(
    internal var current: String?,
) {
    var fading by mutableStateOf<String?>(null)
        internal set
    internal val alpha = Animatable(1f)

    /** The URL whose large cover is on screen (see [onCoverDrawn]). */
    internal var drawn by mutableStateOf<String?>(null)

    /** 1 → 0 while [fading] dissolves (read in draw); 1 until the new cover is drawn. */
    fun fadingAlpha(): Float = alpha.value

    /** The player's cover reports that [url]'s image is on screen. */
    fun onCoverDrawn(url: String?) {
        drawn = url
    }

    /** Suspends until [url]'s cover is on screen, at most [timeoutMillis]. */
    suspend fun awaitDrawn(
        url: String?,
        timeoutMillis: Long = DrawnTimeoutMillis,
    ) {
        withTimeoutOrNull(timeoutMillis) { snapshotFlow { drawn }.first { it == url } }
    }
}

@Composable
fun rememberCoverCrossfade(
    url: String?,
    handoff: CoverHandoff,
): CoverCrossfade {
    val crossfade = remember { CoverCrossfade(url) }
    // Decided in composition, in the same frame the URL changes: the old cover must already
    // be on top when the new one is first composed (a frame later it would blink).
    if (url != crossfade.current) {
        val previous = crossfade.current
        crossfade.current = url
        crossfade.fading = if (handoff.fromSwipe || previous == null) null else previous
        handoff.fromSwipe = false
    }
    val spec = Liquid.effects<Float>(LiquidSpeed.Slow)
    LaunchedEffect(url) {
        if (crossfade.fading == null) return@LaunchedEffect
        crossfade.alpha.snapTo(1f)
        crossfade.awaitDrawn(url)
        crossfade.alpha.animateTo(0f, spec)
        crossfade.fading = null
        crossfade.alpha.snapTo(1f)
    }
    return crossfade
}

/**
 * Dissolves a full-bleed cover's top and bottom into the backdrop. [edge] is the opacity
 * left at the very edges (1 while the cover is still flying in, 0 once it has landed).
 * [dim] is the backdrop's darkening for the text (see CoverAnalysis): the cover takes it on
 * progressively as it dissolves, so its colours meet the backdrop's at the seam instead of a
 * bright picture ending on a darker wash.
 *
 * Both ramps follow a smoothstep curve sampled in [FadeSteps] stops: a linear ramp has a
 * visible start and end ("the mask"); an eased one has none.
 */
fun Modifier.coverEdgeFade(
    edge: () -> Float,
    dim: () -> Float = { 0f },
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val e = edge()
            val d = dim() * (1f - e)
            // Darken where the cover fades (0 where it is fully opaque).
            if (d > 0.005f) {
                drawRect(brush = Brush.verticalGradient(colorStops = edgeRamp { a -> Color.Black.copy(alpha = d * (1f - a)) }))
            }
            drawRect(
                brush = Brush.verticalGradient(colorStops = edgeRamp { a -> Color.Black.copy(alpha = e + (1f - e) * a) }),
                blendMode = BlendMode.DstIn,
            )
        }

/**
 * Colour stops over the cover's height: [colour] of the cover's opacity a (0 at the edges,
 * 1 between [EdgeFadeTop] and [EdgeFadeBottom]) eased with smoothstep.
 */
private inline fun edgeRamp(colour: (Float) -> Color): Array<Pair<Float, Color>> =
    Array(FadeSteps * 2 + 2) { i ->
        if (i <= FadeSteps) {
            val f = i / FadeSteps.toFloat()
            (f * EdgeFadeTop) to colour(smoothstep(f))
        } else {
            val f = (i - FadeSteps - 1) / FadeSteps.toFloat()
            (EdgeFadeBottom + f * (1f - EdgeFadeBottom)) to colour(smoothstep(1f - f))
        }
    }

/** 0..1 → 0..1 with zero slope at both ends. */
internal fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The darkening [url]'s backdrop uses, if its cover was already analysed (it is, by the time
 * the player shows it: PrefetchCovers and the backdrop bake load it first); else 0.
 */
fun cachedCoverDim(url: String?): Float = url?.let { CoverImages[it]?.second?.dim } ?: 0f

/**
 * How the sharp cover must be zoomed so it shows the same square as the backdrop: a
 * letterboxed thumbnail is enlarged past its black bands (scale > 1, centred on the
 * picture). Identity for ordinary covers or before the analysis is known.
 */
fun cachedCoverZoom(url: String?): CoverZoom {
    val (image, crop) = url?.let { CoverImages[it] } ?: return CoverZoom.None
    val plain = CoverCrop.centered(image.width, image.height).square
    if (crop.square == plain) return CoverZoom.None
    val scale = plain.width.toFloat() / crop.square.width
    return CoverZoom(
        scale = scale,
        // Shift (share of the cover's side) that brings the crop's centre to the middle.
        shiftX = (plain.center.x - crop.square.center.x) / plain.width.toFloat() * scale,
        shiftY = (plain.center.y - crop.square.center.y) / plain.height.toFloat() * scale,
    )
}

/** See [cachedCoverZoom]. */
@Immutable
data class CoverZoom(
    val scale: Float,
    val shiftX: Float,
    val shiftY: Float,
) {
    companion object {
        val None = CoverZoom(1f, 0f, 0f)
    }
}

/**
 * The backdrop continuing the cover (see the file header), drawn from the image [baker]
 * baked for [url]. [offsetX] moves it with the cover during a swipe.
 *
 * When the baked image changes (a new track) the old one stays until the new one exists,
 * then the two dissolve over [DissolveMillis] — unless [dissolve] says the new page is
 * already on screen (a completed swipe), in which case it simply takes over.
 * Android 12+ only (the bake uses the blur render effect); below it the colour field
 * underneath stays.
 */
@Composable
fun CoverExtension(
    url: String?,
    baker: BackdropBaker,
    visibility: () -> Float,
    modifier: Modifier = Modifier,
    offsetX: () -> Float = { 0f },
    dissolve: () -> Boolean = { true },
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val baked = baker.bitmap(url)
    var from by remember { mutableStateOf<ImageBitmap?>(null) }
    var to by remember { mutableStateOf(baked) }
    val mix = remember { Animatable(1f) }
    val animationsDisabled = LocalAnimationsDisabled.current
    // Decided in the composition that changes the track: the baked image arrives later, when
    // the signals behind [dissolve] (a swipe handoff) have already been reset.
    val dissolveThisChange = remember(url) { dissolve() }
    LaunchedEffect(baked) {
        if (baked == null || baked === to) return@LaunchedEffect
        if (to != null && dissolveThisChange && !animationsDisabled) {
            from = to
            to = baked
            mix.snapTo(0f)
            mix.animateTo(1f, tween(DissolveMillis, easing = FastOutSlowInEasing))
            from = null
        } else {
            from = null
            to = baked
            mix.snapTo(1f)
        }
    }
    if (to == null) return
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = visibility()
                translationX = offsetX()
            },
    ) {
        // The new image fades in over the old one, which stays fully opaque below it.
        from?.let { drawImage(it) }
        to?.let { drawImage(it, alpha = mix.value) }
    }
}

/**
 * Renders the backdrop of a cover once and keeps the picture, instead of blurring the cover
 * again on every frame. The blurs are the costly part of the player (three full-screen
 * blur passes per frame made the lyrics, which keep the player redrawing, run at a third
 * of the speed); a baked backdrop is one textured rectangle.
 *
 * Bakes are keyed by cover URL and valid for one slot and screen size; any change of
 * those (rotation, a different layout) drops them. They are made one at a time and the
 * last few are kept.
 */
@Stable
class BackdropBaker internal constructor(
    private val graphicsContext: GraphicsContext,
    private val density: Density,
    private val platformContext: PlatformContext,
) {
    private val baked = mutableStateMapOf<String, ImageBitmap>()
    private val order = ArrayDeque<String>()
    private val mutex = Mutex()
    private var generation = 0
    private var geometry: Pair<Rect, IntSize>? = null

    /** The baked backdrop of [url], or null while it is being made. */
    fun bitmap(url: String?): ImageBitmap? = url?.let { baked[it] }

    /** Drops every bake if the geometry changed, then bakes [urls] (current track first). */
    suspend fun bake(
        slot: Rect,
        size: IntSize,
        urls: List<String?>,
    ) {
        if (size.width <= 0 || size.height <= 0) return
        if (geometry != slot to size) {
            geometry = slot to size
            generation++
            baked.clear()
            order.clear()
        }
        val wanted = urls.filterNotNull().distinct()
        wanted.forEach { url -> bakeOne(url, slot, size, keep = wanted) }
    }

    /** Drops every bake but [url]'s (the neighbours are not needed while the player is closed). */
    fun retainOnly(url: String?) {
        order.filter { it != url }.forEach { other ->
            order.remove(other)
            baked.remove(other)
        }
    }

    private suspend fun bakeOne(
        url: String,
        slot: Rect,
        size: IntSize,
        keep: List<String>,
    ) {
        if (baked.containsKey(url)) return
        val started = generation
        val (image, crop) = loadCover(platformContext, url) ?: return
        val bitmap = mutex.withLock { render(image, crop, slot, size) }
        if (started != generation) return
        // Uploaded to the GPU now, not in the first frame that shows it.
        bitmap.prepareToDraw()
        baked[url] = bitmap
        order.addLast(url)
        while (order.size > MaxBakes) {
            val oldest = order.firstOrNull { it !in keep } ?: break
            order.remove(oldest)
            baked.remove(oldest)
        }
    }

    /** The two blurs and the legibility scrim, composed into one full-size picture. */
    private suspend fun render(
        image: ImageBitmap,
        crop: CoverCrop,
        slot: Rect,
        size: IntSize,
    ): ImageBitmap {
        val heavy = graphicsContext.createGraphicsLayer()
        val soft = graphicsContext.createGraphicsLayer()
        val root = graphicsContext.createGraphicsLayer()
        val heavyRadius = with(density) { HeavyBlur.toPx() }
        val softRadius = with(density) { SoftBlur.toPx() }
        try {
            val direction = LayoutDirection.Ltr
            // Heavy blur everywhere…
            heavy.renderEffect = BlurEffect(heavyRadius, heavyRadius, TileMode.Decal)
            heavy.record(density, direction, size) { drawExtension(image, crop, slot) }
            // …and a soft one around the cover's edges, where the sharp cover dissolves.
            soft.renderEffect = BlurEffect(softRadius, softRadius, TileMode.Decal)
            soft.record(density, direction, size) { softEdge(slot, this.size) { drawExtension(image, crop, slot) } }
            root.record(density, direction, size) {
                drawLayer(heavy)
                drawLayer(soft)
                drawBackdropScrims(slot, crop.dim)
            }
            return root.toImageBitmap()
        } finally {
            graphicsContext.releaseGraphicsLayer(heavy)
            graphicsContext.releaseGraphicsLayer(soft)
            graphicsContext.releaseGraphicsLayer(root)
        }
    }
}

/**
 * Draws [extension] masked to the band around the cover where the soft blur shows (it fades
 * out [SoftReach] of the cover's height beyond its top and bottom). [canvasSize] is the
 * backdrop's full size, in the coordinates [extension] draws in.
 */
internal inline fun DrawScope.softEdge(
    slot: Rect,
    canvasSize: Size,
    extension: DrawScope.() -> Unit,
) {
    val height = canvasSize.height
    val reach = slot.height * SoftReach
    drawContext.canvas.saveLayer(Rect(Offset.Zero, canvasSize), Paint())
    extension()
    drawRect(
        // Eased like the cover's own fade, so the soft band has no visible border either.
        brush =
            Brush.verticalGradient(
                colorStops =
                    Array(FadeSteps * 2 + 2) { i ->
                        if (i <= FadeSteps) {
                            val f = i / FadeSteps.toFloat()
                            ((slot.top - reach * (1f - f)) / height).coerceIn(0f, 1f) to Color.Black.copy(alpha = smoothstep(f))
                        } else {
                            val f = (i - FadeSteps - 1) / FadeSteps.toFloat()
                            ((slot.bottom + reach * f) / height).coerceIn(0f, 1f) to Color.Black.copy(alpha = smoothstep(1f - f))
                        }
                    },
            ),
        size = canvasSize,
        blendMode = BlendMode.DstIn,
    )
    drawContext.canvas.restore()
}

/**
 * Over the blurred backdrop: a flat darkening only as strong as the picture needs ([dim], see
 * contrastDim), so the white text always has enough contrast, and legibility scrims only where
 * text sits — the status bar and the controls below the cover.
 */
internal fun DrawScope.drawBackdropScrims(
    slot: Rect,
    dim: Float,
) {
    drawRect(Color.Black.copy(alpha = dim))
    val height = size.height
    drawRect(
        Brush.verticalGradient(
            colorStops =
                arrayOf(
                    0f to Color.Black.copy(alpha = TopScrim),
                    (slot.top / height).coerceIn(0f, 1f) to Color.Transparent,
                    ((slot.bottom - slot.height * 0.25f) / height).coerceIn(0f, 1f) to Color.Transparent,
                    1f to Color.Black.copy(alpha = BottomScrim),
                ),
        ),
    )
}

/** How much [url]'s backdrop is darkened for the text (see contrastDim); 0 if it can't load. */
internal suspend fun coverContrastDim(
    context: PlatformContext,
    url: String,
): Float = loadCover(context, url)?.second?.dim ?: 0f

@Composable
fun rememberBackdropBaker(): BackdropBaker {
    val graphicsContext = LocalGraphicsContext.current
    val density = LocalDensity.current
    val context = LocalPlatformContext.current
    return remember(graphicsContext, density, context) { BackdropBaker(graphicsContext, density, context) }
}

/** A neighbouring track's sharp cover at [slot], shifted by [offsetX] (swipe). */
@Composable
fun NeighbourCover(
    url: String,
    slot: Rect,
    offsetX: () -> Float,
) {
    val density = LocalDensity.current
    VibraArtwork(
        url = url,
        shape = RectangleShape,
        sizePx = PlayerArtworkPx,
        placeholderSizePx = SmallArtworkPx,
        modifier =
            Modifier
                .offset { IntOffset((slot.left + offsetX()).roundToInt(), slot.top.roundToInt()) }
                .size(with(density) { slot.width.toDp() }, with(density) { slot.height.toDp() })
                .coverEdgeFade(edge = { 0f }, dim = { cachedCoverDim(url) }),
    )
}

/** Recent covers, already analysed (see CoverAnalysis), so neighbours and re-bakes are free. */
private object CoverImages {
    private val cache = LruCache<String, Pair<ImageBitmap, CoverCrop>>(8)

    operator fun get(url: String): Pair<ImageBitmap, CoverCrop>? = cache.get(url)

    operator fun set(
        url: String,
        cover: Pair<ImageBitmap, CoverCrop>,
    ) {
        cache.put(url, cover)
    }
}

/**
 * Loads [url]'s small cover and analyses it (letterbox, edge logos, brightness). The image is
 * a software bitmap so its pixels can be read; the analysis runs off the main thread.
 */
private suspend fun loadCover(
    context: PlatformContext,
    url: String,
): Pair<ImageBitmap, CoverCrop>? {
    CoverImages[url]?.let { return it }
    val request =
        ImageRequest
            .Builder(context)
            .data(url.resize(SmallArtworkPx, SmallArtworkPx))
            .allowHardware(false)
            .build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    val image = result.image.toBitmap().asImageBitmap()
    val crop =
        withContext(Dispatchers.Default) {
            val pixels = IntArray(image.width * image.height)
            if (runCatching { image.readPixels(pixels, 0, 0, image.width, image.height) }.isSuccess) {
                analyzeCover(pixels, image.width, image.height)
            } else {
                CoverCrop.centered(image.width, image.height)
            }
        }
    return (image to crop).also { CoverImages[url] = it }
}

/** The cover at [slot] plus its mirrored edge bands out to the canvas edges (see [extensionParts]). */
private fun DrawScope.drawExtension(
    image: ImageBitmap,
    crop: CoverCrop,
    slot: Rect,
) {
    extensionParts(crop, slot, size.height).forEach { part ->
        val target = part.target
        if (part.mirrored) {
            // Flipped upside down inside its target: the band's seam row lands on the seam.
            scale(1f, -1f, pivot = Offset(target.center.x.toFloat(), target.center.y.toFloat())) {
                drawImage(image, part.source.topLeft, part.source.size, target.topLeft, target.size)
            }
        } else {
            drawImage(image, part.source.topLeft, part.source.size, target.topLeft, target.size)
        }
    }
}

/** A piece of the backdrop: [source] of the picture drawn into [target], flipped if [mirrored]. */
@Immutable
internal data class ExtensionPart(
    val source: IntRect,
    val target: IntRect,
    val mirrored: Boolean,
)

/**
 * Where a picture goes to continue the cover at [slot] on a canvas [canvasHeight] tall: the
 * [crop]'s square at the slot; above it, the band of the square nearest its top edge, flipped
 * (a mirror at the seam); below it the same with the bottom band. The bands start at the crop's
 * strips, so a logo stepped over by the analysis stays out of the mirror too. Shared by the
 * baked and the live backdrop (the live one, a video, uses [CoverCrop.centered]).
 */
internal fun extensionParts(
    crop: CoverCrop,
    slot: Rect,
    canvasHeight: Float,
): List<ExtensionPart> {
    val left = slot.left.roundToInt()
    val right = left + slot.width.roundToInt()
    val top = slot.top.roundToInt()
    val bottom = slot.bottom.roundToInt()
    val height = canvasHeight.roundToInt()
    val square = crop.square
    val band = (square.height * MirrorBand).roundToInt().coerceAtLeast(1)
    return buildList {
        add(ExtensionPart(square, IntRect(left, top, right, bottom), mirrored = false))
        if (top > 0) {
            val from = crop.topStrip.top
            val source = IntRect(square.left, from, square.right, (from + band).coerceAtMost(square.bottom))
            add(ExtensionPart(source, IntRect(left, 0, right, top), mirrored = true))
        }
        if (height > bottom) {
            val to = crop.bottomStrip.bottom
            val source = IntRect(square.left, (to - band).coerceAtLeast(square.top), square.right, to)
            add(ExtensionPart(source, IntRect(left, bottom, right, height), mirrored = true))
        }
    }
}

/** Darkening of the backdrop under the status bar and at the bottom, behind the controls. */
private const val TopScrim = 0.28f
private const val BottomScrim = 0.45f

/** Longest wait for a new cover before the old one dissolves anyway. */
private const val DrawnTimeoutMillis = 1_200L

/** How long a track change takes to dissolve the backdrop: quick, but not a cut. */
internal const val DissolveMillis = 300

/** Backdrops kept (current, neighbours, and a couple of recent ones). */
private const val MaxBakes = 6

internal val HeavyBlur = 44.dp
internal val SoftBlur = 14.dp

/** How far beyond the cover (share of its height) the soft blur still shows. */
internal const val SoftReach = 0.3f

/** Share of the cover's side mirrored above/below it: enough shape to read as its continuation. */
private const val MirrorBand = 0.35f

/** Stops per eased ramp (smoothstep sampled finely enough to look continuous). */
private const val FadeSteps = 8

/** Where a full-bleed cover's top is fully opaque, and where its bottom starts to fade. */
private const val EdgeFadeTop = 0.22f
private const val EdgeFadeBottom = 0.55f
