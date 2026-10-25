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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Size
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
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.ui.utils.resize

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  COVER STAGE — the player's cover and the backdrop that continues it
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  Backdrop (CoverExtension): the cover drawn again at its own place, its top and bottom
 *  rows stretched out to the screen edges, then blurred in two strengths — a soft blur
 *  hugging the cover's edges over a heavy one everywhere — so the sharp cover, whose top
 *  and bottom dissolve (coverEdgeFade), passes through a slightly blurred copy of itself
 *  into a strongly blurred one: a gradual depth-of-field, not a sharp image on a wash.
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
            loadCoverImage(context, url)
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
 */
fun Modifier.coverEdgeFade(edge: () -> Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val e = edge()
            drawRect(
                brush =
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = e),
                        EdgeFadeTop to Color.Black,
                        EdgeFadeBottom to Color.Black,
                        1f to Color.Black.copy(alpha = e),
                    ),
                blendMode = BlendMode.DstIn,
            )
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
        val image = loadCoverImage(platformContext, url) ?: return
        val bitmap = mutex.withLock { render(image, slot, size) }
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
            heavy.record(density, direction, size) { drawExtension(image, slot) }
            // …and a soft one around the cover's edges, where the sharp cover dissolves.
            soft.renderEffect = BlurEffect(softRadius, softRadius, TileMode.Decal)
            soft.record(density, direction, size) {
                val width = this.size.width
                val height = this.size.height
                val reach = slot.height * SoftReach
                drawContext.canvas.saveLayer(Rect(Offset.Zero, this.size), Paint())
                drawExtension(image, slot)
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    ((slot.top - reach) / height).coerceIn(0f, 1f) to Color.Transparent,
                                    (slot.top / height).coerceIn(0f, 1f) to Color.Black,
                                    (slot.bottom / height).coerceIn(0f, 1f) to Color.Black,
                                    ((slot.bottom + reach) / height).coerceIn(0f, 1f) to Color.Transparent,
                                ),
                        ),
                    size = Size(width, height),
                    blendMode = BlendMode.DstIn,
                )
                drawContext.canvas.restore()
            }
            root.record(density, direction, size) {
                drawLayer(heavy)
                drawLayer(soft)
                // Contrast guard: a flat darkening only as strong as the cover needs, so the white
                // text always has enough contrast (see contrastDim).
                drawRect(Color.Black.copy(alpha = contrastDim(image)))
                // Legibility only where text sits: the status bar and the controls below the cover.
                val height = this.size.height
                drawRect(
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0f to Color.Black.copy(alpha = 0.28f),
                                (slot.top / height).coerceIn(0f, 1f) to Color.Transparent,
                                ((slot.bottom - slot.height * 0.25f) / height).coerceIn(0f, 1f) to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.45f),
                            ),
                    ),
                )
            }
            return root.toImageBitmap()
        } finally {
            graphicsContext.releaseGraphicsLayer(heavy)
            graphicsContext.releaseGraphicsLayer(soft)
            graphicsContext.releaseGraphicsLayer(root)
        }
    }
}

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
                .coverEdgeFade { 0f },
    )
}

private object CoverImages {
    private val cache = LruCache<String, ImageBitmap>(8)

    operator fun get(url: String): ImageBitmap? = cache.get(url)

    operator fun set(
        url: String,
        image: ImageBitmap,
    ) {
        cache.put(url, image)
    }
}

private suspend fun loadCoverImage(
    context: PlatformContext,
    url: String,
): ImageBitmap? {
    CoverImages[url]?.let { return it }
    val request = ImageRequest.Builder(context).data(url.resize(SmallArtworkPx, SmallArtworkPx)).build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    return result.image.toBitmap().asImageBitmap().also { CoverImages[url] = it }
}

/** The cover at [slot] plus its top/bottom rows stretched to the canvas edges. */
private fun DrawScope.drawExtension(
    image: ImageBitmap,
    slot: Rect,
    alpha: Float = 1f,
) {
    // Centre square of the image (videos have 16:9 thumbnails; covers are cropped).
    val side = min(image.width, image.height)
    val srcLeft = (image.width - side) / 2
    val srcTop = (image.height - side) / 2
    val strip = (side * EdgeStrip).roundToInt().coerceAtLeast(1)
    val left = slot.left.roundToInt()
    val width = slot.width.roundToInt()
    val top = slot.top.roundToInt()
    val bottom = slot.bottom.roundToInt()
    drawImage(image, IntOffset(srcLeft, srcTop), IntSize(side, side), IntOffset(left, top), IntSize(width, bottom - top), alpha)
    if (top > 0) {
        drawImage(image, IntOffset(srcLeft, srcTop), IntSize(side, strip), IntOffset(left, 0), IntSize(width, top), alpha)
    }
    val below = size.height.roundToInt() - bottom
    if (below > 0) {
        drawImage(
            image,
            IntOffset(srcLeft, srcTop + side - strip),
            IntSize(side, strip),
            IntOffset(left, bottom),
            IntSize(width, below),
            alpha,
        )
    }
}

/**
 * How much the backdrop must be darkened for white text to read on it: 0 for dark covers,
 * up to [MaxContrastDim] for bright ones. The cover's mean relative luminance (WCAG) is
 * brought down to [TargetLuminance], which keeps white text above a 4.5 : 1 contrast ratio
 * (1.05 / (0.16 + 0.05) = 5.0). A black overlay of alpha a scales sRGB channels by (1 - a),
 * i.e. luminance by roughly (1 - a)^2.2.
 */
internal fun contrastDim(image: ImageBitmap): Float {
    val width = image.width
    val height = image.height
    val step = maxOf(1, minOf(width, height) / 24)
    val columns = (width + step - 1) / step
    val rows = (height + step - 1) / step
    val pixels = IntArray(width * height)
    // A hardware-backed bitmap cannot always be read; then the extra darkening is skipped.
    if (runCatching { image.readPixels(pixels, 0, 0, width, height) }.isFailure) return 0f
    var sum = 0.0
    var count = 0
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val argb = pixels[(row * step) * width + column * step]
            sum += relativeLuminance(argb)
            count++
        }
    }
    // Bright patches matter more than the mean (white text on a light corner), so lean up.
    val luminance = (sum / count.coerceAtLeast(1)) * 1.15
    if (luminance <= TargetLuminance) return 0f
    return (1.0 - (TargetLuminance / luminance).pow(1.0 / 2.2)).toFloat().coerceIn(0f, MaxContrastDim)
}

private fun relativeLuminance(argb: Int): Double {
    fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)
}

private const val TargetLuminance = 0.16
private const val MaxContrastDim = 0.6f

/** Longest wait for a new cover before the old one dissolves anyway. */
private const val DrawnTimeoutMillis = 1_200L

/** How long a track change takes to dissolve the backdrop: quick, but not a cut. */
private const val DissolveMillis = 300

/** Backdrops kept (current, neighbours, and a couple of recent ones). */
private const val MaxBakes = 6

private val HeavyBlur = 44.dp
private val SoftBlur = 14.dp

/** How far beyond the cover (share of its height) the soft blur still shows. */
private const val SoftReach = 0.3f

/** Share of the cover's height whose edge rows are stretched beyond it. */
private const val EdgeStrip = 0.06f

/** Where a full-bleed cover's top is fully opaque, and where its bottom starts to fade. */
private const val EdgeFadeTop = 0.22f
private const val EdgeFadeBottom = 0.55f
