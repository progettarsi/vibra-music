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
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.LocalAnimationsDisabled
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize
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
 *  Sfondo: estensione continua dei bordi, con espansione laterale adattiva e nessun riflesso. Il blur è uniforme; il piano supera lo schermo anche sui formati alti.
 *  I lati a tinta unita continuano nel loro colore. Solo una fascia stretta ai bordi della
 *  copertina nitida sfuma nello sfondo, preservando scritte e composizione del quadrato.
 *  Lo scurimento inferiore segue la posizione misurata del titolo, non metà copertina.
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
 * [bottomScrim] segue la stessa rampa dello sfondo, ancorata al titolo misurato
 * ([titleFraction] rispetto alla copertina), anche se i controlli si sovrappongono al quadrato.
 *
 * The opacity follows a smootherstep curve sampled in [FadeSteps] stops: a linear ramp has a
 * visible start and end ("the mask"); an eased one has none.
 */
fun Modifier.coverEdgeFade(
    edge: () -> Float,
    bottomScrim: () -> Float = { 0f },
    titleFraction: () -> Float = { 1.06f },
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val e = edge()
            val scrim = bottomScrim() * (1f - e)
            if (scrim > 0.005f) {
                val cover = Rect(Offset.Zero, size)
                drawRect(Brush.verticalGradient(colorStops = scrimStops(cover, size.height, top = 0f, bottom = scrim, titleY = size.height * titleFraction())))
            }
            drawRect(
                brush = Brush.verticalGradient(colorStops = edgeRamp { a -> Color.Black.copy(alpha = e + (1f - e) * a) }),
                blendMode = BlendMode.DstIn,
            )
        }

/**
 * Colour stops over the cover's height: [colour] of the cover's opacity a (0 at the edges,
 * 1 between [EdgeFadeTop] and [EdgeFadeBottom]) eased with smootherstep.
 */
private inline fun edgeRamp(colour: (Float) -> Color): Array<Pair<Float, Color>> =
    Array(FadeSteps * 2 + 2) { i ->
        if (i <= FadeSteps) {
            val f = i / FadeSteps.toFloat()
            (f * EdgeFadeTop).let { it to colour(coverOpacity(it)) }
        } else {
            val f = (i - FadeSteps - 1) / FadeSteps.toFloat()
            (EdgeFadeBottom + f * (1f - EdgeFadeBottom)).let { it to colour(coverOpacity(it)) }
        }
    }

/** Solo i bordi estremi si dissolvono: il contenuto centrale del quadrato rimane intatto. */
internal fun coverOpacity(fraction: Float): Float = when {
    fraction < EdgeFadeTop -> smootherstep(fraction / EdgeFadeTop)
    fraction > EdgeFadeBottom -> smootherstep((1f - fraction) / (1f - EdgeFadeBottom))
    else -> 1f
}

/** 0..1 → 0..1 with zero slope at both ends. */
internal fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Like [smoothstep], with zero curvature at both ends too: no visible start or end of a ramp. */
internal fun smootherstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * t * (t * (t * 6f - 15f) + 10f)
}

/**
 * [url]'s analysis, if its cover was already analysed (it is, by the time the player shows
 * it: PrefetchCovers and the backdrop bake load it first); else null.
 */
fun cachedCoverCrop(url: String?): CoverCrop? = url?.let { CoverImages[it]?.second }

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
        from?.let { drawStretched(it) }
        to?.let { drawStretched(it, alpha = mix.value) }
    }
}

/** [image] (a backdrop baked at [BakeScale]) stretched over the whole canvas. */
private fun DrawScope.drawStretched(
    image: ImageBitmap,
    alpha: Float = 1f,
) = drawImage(
    image,
    srcSize = IntSize(image.width, image.height),
    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    alpha = alpha,
)

/**
 * Renders the backdrop of a cover once and keeps the picture, instead of blurring the cover
 * again on every frame. The blurs are the costly part of the player (three full-screen
 * blur passes per frame made the lyrics, which keep the player redrawing, run at a third
 * of the speed); a baked backdrop is one textured rectangle. Everything in it is blurred, so
 * it is made and kept at [BakeScale] of the screen and stretched back when drawn.
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
    private var geometry: Triple<Rect, IntSize, Float>? = null

    /** The baked backdrop of [url], or null while it is being made. */
    fun bitmap(url: String?): ImageBitmap? = url?.let { baked[it] }

    /** Drops every bake if the geometry changed, then bakes [urls] (current track first). */
    suspend fun bake(
        slot: Rect,
        size: IntSize,
        urls: List<String?>,
        titleY: Float = slot.bottom + slot.height * 0.06f,
    ) {
        if (size.width <= 0 || size.height <= 0 || slot.width <= 0f || slot.height <= 0f) return
        val nextGeometry = Triple(slot, size, titleY)
        if (geometry != nextGeometry) {
            geometry = nextGeometry
            generation++
            baked.clear()
            order.clear()
        }
        val wanted = urls.filterNotNull().distinct()
        wanted.forEach { url -> bakeOne(url, slot, size, titleY, keep = wanted) }
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
        titleY: Float,
        keep: List<String>,
    ) {
        if (baked.containsKey(url)) return
        val started = generation
        val (image, crop) = loadCover(platformContext, url) ?: return
        val bitmap = mutex.withLock { render(image, crop, slot, size, titleY) }
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

    /** Campo continuo, lati uniformi e scurimento, composti una sola volta a [BakeScale]. */
    private suspend fun render(
        image: ImageBitmap,
        crop: CoverCrop,
        slot: Rect,
        size: IntSize,
        titleY: Float,
    ): ImageBitmap {
        val pictureLayer = graphicsContext.createGraphicsLayer()
        val root = graphicsContext.createGraphicsLayer()
        try {
            val small = IntSize((size.width * BakeScale).roundToInt().coerceAtLeast(1), (size.height * BakeScale).roundToInt().coerceAtLeast(1))
            val blur = with(density) { BackdropBlur.toPx() }
            root.record(density, LayoutDirection.Ltr, small) {
                drawBackdrop(pictureLayer, slot, size.toSize(), BakeScale, blur, crop.plainTop, crop.plainBottom, crop.topScrim, crop.bottomScrim, titleY, crop.topSpread, crop.bottomSpread) { rect, source ->
                    drawImage(
                        image,
                        srcOffset = IntOffset(
                            crop.square.left + (source.left * crop.square.width).toInt(),
                            crop.square.top + (source.top * crop.square.height).toInt(),
                        ),
                        srcSize = IntSize(
                            (source.width * crop.square.width).toInt().coerceAtLeast(1),
                            (source.height * crop.square.height).toInt().coerceAtLeast(1),
                        ),
                        dstOffset = rect.topLeft.round(),
                        dstSize = IntSize(
                            (rect.right.roundToInt() - rect.left.roundToInt()).coerceAtLeast(1),
                            (rect.bottom.roundToInt() - rect.top.roundToInt()).coerceAtLeast(1),
                        ),
                    )
                }
            }
            return root.toImageBitmap()
        } finally {
            graphicsContext.releaseGraphicsLayer(pictureLayer)
            graphicsContext.releaseGraphicsLayer(root)
        }
    }
}

/** Blur uniforme, espresso nello spazio dello schermo prima del rendering ridotto. */
internal val BackdropBlur = 48.dp

/**
 * Un campo continuo dai bordi della copertina, composto e sfocato a risoluzione ridotta. [blur] arriva già in
 * pixel: leggere la densità dentro GraphicsLayer.record annidati causa StackOverflow.
 * Copertine e video usano la stessa geometria, senza lettura dei frame dalla GPU.
 */
internal fun DrawScope.drawBackdrop(
    pictureLayer: GraphicsLayer,
    slot: Rect,
    canvas: Size,
    surfaceScale: Float,
    blur: Float,
    plainTop: PlainEdge?,
    plainBottom: PlainEdge?,
    topScrim: Float,
    bottomScrim: Float,
    titleY: Float,
    topSpread: Float = 0.2f,
    bottomSpread: Float = 0.2f,
    picture: DrawScope.(Rect, Rect) -> Unit,
) = scale(surfaceScale, Offset.Zero) {
    val small = IntSize((canvas.width * surfaceScale).roundToInt().coerceAtLeast(1), (canvas.height * surfaceScale).roundToInt().coerceAtLeast(1))
    val radius = blur * surfaceScale
    pictureLayer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
    pictureLayer.record(small) {
        scale(surfaceScale, Offset.Zero) {
            drawArtworkField(slot, canvas, topSpread, bottomSpread, picture)
        }
    }
    scale(1f / surfaceScale, Offset.Zero) { drawLayer(pictureLayer) }
    drawPlainSides(slot, canvas, plainTop, plainBottom)
    drawBackdropScrims(slot, canvas, topScrim, bottomScrim, titleY)
}

/** A plain background's colour above and below the cover, reaching under the cover's dissolving edges. */
private fun DrawScope.drawPlainSides(
    slot: Rect,
    canvas: Size,
    top: PlainEdge?,
    bottom: PlainEdge?,
) {
    val overlap = slot.height * PlainOverlap
    // Anche dietro le lyrics il passaggio al colore pieno è morbido, senza rettangoli visibili.
    top?.let { edge ->
        drawRect(Brush.verticalGradient(
            colors = listOf(edge.color.copy(alpha = edge.weight), edge.color.copy(alpha = 0f)),
            startY = slot.top, endY = slot.top + overlap,
        ), size = canvas)
    }
    bottom?.let { edge ->
        drawRect(Brush.verticalGradient(
            colors = listOf(edge.color.copy(alpha = 0f), edge.color.copy(alpha = edge.weight)),
            startY = slot.bottom - overlap, endY = slot.bottom,
        ), size = canvas)
    }
}

/**
 * Darkening where text sits, on a [canvas] with the cover at [slot]: [top] under the status
 * bar and [bottom] behind the title and controls (see [scrimAlpha]). Nowhere else: the
 * picture keeps its own brightness.
 */
internal fun DrawScope.drawBackdropScrims(
    slot: Rect,
    canvas: Size,
    top: Float,
    bottom: Float,
    titleY: Float,
) {
    drawRect(Brush.verticalGradient(colorStops = scrimStops(slot, canvas.height, top, bottom, titleY), startY = 0f, endY = canvas.height), size = canvas)
}

/** Rampa più ampia dal profilo SVG, con intensità all'85% e ancora sul titolo misurato. */
internal fun scrimAlpha(
    y: Float,
    slot: Rect,
    height: Float,
    top: Float,
    bottom: Float,
    titleY: Float = slot.bottom + slot.height * 0.06f,
): Float {
    val upper = if (slot.top > 0f) top * (1f - smootherstep(y / slot.top)) else 0f
    val title = titleY.coerceAtLeast(1f)
    val from = title - slot.height * BottomScrimLead
    val to = title + slot.height * BottomScrimTail
    // Profilo dello SVG: crescita lenta all'inizio, senza gradini tra gli stop.
    val lower = bottom * BottomScrimOpacity * backdropGradient((y.coerceIn(0f, height) - from) / (to - from))
    return 1f - (1f - upper) * (1f - lower.coerceAtMost(1f))
}

private fun scrimStops(
    slot: Rect,
    height: Float,
    top: Float,
    bottom: Float,
    titleY: Float,
): Array<Pair<Float, Color>> {
    // Il titolo può stare fuori dalla superficie della copertina: non anticipare la rampa
    // tagliandone l'ancora al fondo del quadrato. Solo gli stop di disegno vengono limitati.
    val title = titleY.coerceAtLeast(1f)
    val from = title - slot.height * BottomScrimLead
    val to = title + slot.height * BottomScrimTail
    val ys =
        buildList {
            add(0f)
            if (slot.top > 0f) for (i in 0..FadeSteps) add(slot.top * i / FadeSteps)
            for (i in 0..FadeSteps) add(from + (to - from) * i / FadeSteps)
            add(height)
        }
    return alphaStops(ys, height) { y -> scrimAlpha(y, slot, height, top, bottom, titleY) }
}

/** Gradient stops over [height] with black of [alpha] at each of [ys] (kept within the canvas). */
private inline fun alphaStops(
    ys: List<Float>,
    height: Float,
    alpha: (Float) -> Float,
): Array<Pair<Float, Color>> =
    ys
        .map { it.coerceIn(0f, height) }
        .distinct()
        .sorted()
        .map { y -> (y / height) to Color.Black.copy(alpha = alpha(y).coerceIn(0f, 1f)) }
        .toTypedArray()

/**
 * [url]'s analysis (see CoverAnalysis) once it is known, loading the cover if needed. Keyed on
 * the URL: a new cover never shows the previous one's analysis while its own loads.
 */
@Composable
fun rememberCoverCrop(url: String?): CoverCrop? {
    val context = LocalPlatformContext.current
    val crop = remember(url) { mutableStateOf(cachedCoverCrop(url)) }
    LaunchedEffect(url) {
        if (crop.value == null && url != null) crop.value = loadCover(context, url)?.second
    }
    return crop.value
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
    titleY: Float? = null,
) {
    val density = LocalDensity.current
    val crop = rememberCoverCrop(url)
    val zoom = cachedCoverZoom(url)
    val content = rememberGraphicsLayer()
    var ready by remember { mutableStateOf(false) }
    Box(Modifier
        .offset { IntOffset(slot.left.roundToInt(), slot.top.roundToInt()) }
        .size(with(density) { slot.width.toDp() }, with(density) { slot.height.toDp() })
        .then(if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) Modifier.coverEdgeFade(
            edge = { 0f }, bottomScrim = { crop?.bottomScrim ?: 0f },
            titleFraction = { titleY?.let { (it - slot.top) / slot.height } ?: 1.06f },
        ) else Modifier)
        .drawWithContent {
            content.record { this@drawWithContent.drawContent() }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) drawLayer(content)
            if (!ready) ready = true
        },
    ) {
        VibraArtwork(
            url = url, shape = RectangleShape, sizePx = PlayerArtworkPx, placeholderSizePx = SmallArtworkPx,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.shiftX * size.width
                translationY = zoom.shiftY * size.height
            },
        )
    }
    if (ready && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) UnifiedCoverField(content, slot, titleY ?: (slot.bottom + slot.height * 0.06f), crop, { 1f }, offsetX)
}

/** Recent covers, already analysed (see CoverAnalysis), so neighbours and re-bakes are free. */
private object CoverImages {
    private val cache = LruCache<String, Pair<ImageBitmap, CoverCrop>>(8)
    private val locks = LruCache<String, Mutex>(16)

    operator fun get(url: String): Pair<ImageBitmap, CoverCrop>? = cache.get(url)

    /** One lock per cover: callers asking for it at the same time load and analyse it once. */
    fun lock(url: String): Mutex = synchronized(locks) { locks.get(url) ?: Mutex().also { locks.put(url, it) } }

    operator fun set(
        url: String,
        cover: Pair<ImageBitmap, CoverCrop>,
    ) {
        cache.put(url, cover)
    }
}

/**
 * Loads [url]'s small cover and analyses it (letterbox, plain sides, brightness). The image is
 * a software bitmap so its pixels can be read; the analysis runs off the main thread.
 */
private suspend fun loadCover(
    context: PlatformContext,
    url: String,
): Pair<ImageBitmap, CoverCrop>? {
    CoverImages[url]?.let { return it }
    return CoverImages.lock(url).withLock {
        CoverImages[url]?.let { return@withLock it }
        val request =
            ImageRequest
                .Builder(context)
                .data(url.resize(SmallArtworkPx, SmallArtworkPx))
                .allowHardware(false)
                .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@withLock null
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
        (image to crop).also { CoverImages[url] = it }
    }
}

/** Resolution of a baked backdrop relative to the screen (all of it is blurred). */
private const val BakeScale = 1f / 4f

/** La sfumatura inizia poco prima del titolo (un decimo del lato della copertina). */
private const val BottomScrimLead = 0.22f

/** La rampa prosegue sotto il titolo, invece di concentrarsi tutta prima del testo. */
private const val BottomScrimTail = 0.03f

/** How far (share of the cover's height) a plain side's colour reaches under the cover. */
private const val PlainOverlap = 0.3f

/** Longest wait for a new cover before the old one dissolves anyway. */
private const val DrawnTimeoutMillis = 1_200L

/** How long a track change takes to dissolve the backdrop: quick, but not a cut. */
internal const val DissolveMillis = 300

/** Backdrops kept (current, neighbours, and a couple of recent ones). */
private const val MaxBakes = 6

/** Stops per eased ramp (sampled finely enough to look continuous). */
private const val FadeSteps = 12

/** Where a full-bleed cover's top is fully opaque, and where its bottom starts to fade. */
private const val EdgeFadeTop = 0.08f
private const val EdgeFadeBottom = 0.90f

/** Opacità relativa richiesta da Augusto; lo SVG guida la curva, non una tinta fissa. */
internal const val BottomScrimOpacity = 0.85f
