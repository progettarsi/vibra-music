/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.motion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import moe.rukamori.archivetune.LocalAnimationsDisabled
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalGraphicsContext
import app.vibra.ui.shell.LocalPageFlight
import app.vibra.ui.shell.LocalPageMotion
import app.vibra.ui.shell.PagePose

/** A visible instance, never an entity ID shared by several list occurrences. */
class ExpansionSource internal constructor(internal val owner: String, internal val id: String) {
    internal var color = Color.Transparent
    internal var cornerRadius: () -> Float? = { null }
    internal val focus = FocusRequester()
    internal var coordinates: LayoutCoordinates? = null
    internal val bounds: Rect? get() = coordinates?.takeIf { it.isAttached }?.boundsInWindow()?.takeIf { it.width > 0 && it.height > 0 }

    internal fun elementsInside(elements: List<ExpansionElement>, bounds: Rect): List<ExpansionElement> =
        elements.filter { element ->
            element.owner == owner && element.coordinates?.takeIf { it.isAttached }?.boundsInWindow()?.let {
                it.width > 0f && it.height > 0f && bounds.contains(it.center)
            } == true
        }
}

data class OpeningMediaPreview(
    val title: String,
    val thumbnailUrl: String,
    val sizePx: Int,
    internal val painter: Painter? = null,
)

/** Transient rendering resources: only the selected source allocates a layer. */
class ExpansionSession internal constructor(
    source: ExpansionSource,
    internal var bounds: Rect,
    val entry: String,
    initialProgress: Float = 0f,
    internal val originCornerPx: Float = 0f,
    internal val returning: Boolean = false,
) {
    internal var source by mutableStateOf(source)
    internal var layer by mutableStateOf<GraphicsLayer?>(null)
    internal var layerOrigin = bounds.topLeft
    internal var overlayDrawing by mutableStateOf(false)
    internal var recordingBody = false
    internal var release: (() -> Unit)? = null
    internal var progress: () -> Float by mutableStateOf({ initialProgress })
    internal fun preparePairs(elements: List<ExpansionElement>, allowNew: Boolean): Boolean {
        val candidates = source.elementsInside(elements, bounds)
        val targets = elements.filter { it.owner == entry && it.coordinates?.isAttached == true }
        var changed = false
        candidates.forEach { from ->
            val existing = parts.firstOrNull { it.source === from }
            if (existing?.target?.coordinates?.isAttached == true) return@forEach
            if (existing == null && !allowNew) return@forEach
            val sameRole = targets.filter { it.role == from.role }
            val hero = sameRole.singleOrNull { it.primary }
            val target = selectExpansionTarget(from, candidates, sameRole, hero) ?: return@forEach
            val sourceBounds = from.coordinates?.boundsInWindow() ?: return@forEach
            val targetBounds = target.restBounds()?.takeIf { it.width > 0f && it.height > 0f } ?: return@forEach
            if (existing == null) {
                parts.add(ExpansionPart(from, target, sourceBounds, targetBounds))
            } else {
                existing.target = target
            }
            changed = true
        }
        return changed
    }
    internal val parts = mutableStateListOf<ExpansionPart>()
    internal val candidateKeys = hashSetOf<String>()
    internal val resources = mutableListOf<() -> Unit>()
    internal fun dispose() { release?.invoke(); release = null; layer = null; resources.forEach { it() }; resources.clear(); parts.clear() }
}

@Composable
internal fun rememberExpansionSource(): ExpansionSource? {
    val page = LocalPageFlight.current ?: return null
    val id = rememberSaveable { java.util.UUID.randomUUID().toString() }
    return remember(page, id) { ExpansionSource(page.entryId, id) }
}

/** Keeps the layout placeholder intact while its recorded drawing is owned by the overlay. */
internal fun Modifier.expansionSource(source: ExpansionSource?): Modifier = composed {
    val motion = LocalPageMotion.current
    val context = LocalGraphicsContext.current
    val disabled = LocalAnimationsDisabled.current
    if (source == null || motion == null || disabled || (motion.ownerView != null && motion.ownerView !== LocalView.current)) return@composed this
    DisposableEffect(source, motion) {
        motion.registerExpansion(source)
        onDispose { source.coordinates = null; motion.unregisterExpansion(source) }
    }
    this.focusRequester(source.focus).onGloballyPositioned {
        source.coordinates = it
    }.drawWithContent {
        val session = motion.expansion
        if (session?.source !== source) { drawContent(); return@drawWithContent }
        val progress = session.progress()
        val allowNewPairs = session.returning || progress < PairingLimit
        val pairsChanged = (allowNewPairs || session.parts.isNotEmpty()) &&
            session.preparePairs(motion.expansionElements, allowNewPairs)
        if (session.returning) {
            // The real card is the landing surface and stays visible beneath the page.
            drawContent()
            return@drawWithContent
        }
        if (session.layer == null || pairsChanged) {
            val coordinates = source.coordinates?.takeIf { it.isAttached }
            if (coordinates == null) {
                drawContent()
                return@drawWithContent
            }
            val layer = session.layer ?: context.createGraphicsLayer().also { captured ->
                session.release = { context.releaseGraphicsLayer(captured) }
            }
            session.layerOrigin = coordinates.localToWindow(Offset.Zero)
            session.recordingBody = true
            try { layer.record { this@drawWithContent.drawContent() } }
            finally { session.recordingBody = false }
            session.layer = layer
        }
        // Drawing the snapshot and publishing it happen on different nodes. Leave the
        // source visible until the overlay has actually picked it up.
        if (!session.overlayDrawing) drawContent()
    }
}

/** Registration wraps the actual click, including keyboard and accessibility activation. */
internal fun Modifier.expandingClick(
    onClick: () -> Unit,
    color: Color = Color.Transparent,
    shape: Shape? = null,
    build: @Composable Modifier.(() -> Unit) -> Modifier,
): Modifier = composed {
    val source = rememberExpansionSource()
    val motion = LocalPageMotion.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    source?.color = color
    source?.cornerRadius = {
        val size = source?.coordinates?.size
        if (shape == null || size == null) {
            null
        } else {
            when (val outline = shape.createOutline(Size(size.width.toFloat(), size.height.toFloat()), direction, density)) {
                is Outline.Rectangle -> 0f
                is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                is Outline.Generic -> null
            }
        }
    }
    this.expansionSource(source).build {
        if (source != null) motion?.prepareExpansion(source)
        onClick()
    }
}

/** Draw-only overlay; no input handlers or duplicate accessibility nodes. */
@Composable
fun ExpansionOverlay(session: ExpansionSession?, offset: () -> Offset, modifier: Modifier = Modifier) {
    if (session == null) return

    val corners = rememberScreenCorners().pixels(LocalDensity.current)
    val clip = remember(session) { Path() }
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val p = session.progress().coerceIn(0f, 1f)
            val layer = session.layer
            if (p >= 1f) return@Canvas
            if (layer == null && session.parts.isEmpty()) return@Canvas
            if (!session.overlayDrawing) session.overlayDrawing = true

            // PageFrame owns the surface. Give the card a visible, bounded zoom inside it.
            val alpha = ExpansionPose.sourceAlpha(p)
            val windowOffset = offset()
            if (layer != null && alpha > 0f) {
                val start = session.bounds.translate(-windowOffset)
                val frame = PagePose.originRect(start, size, p)
                val factor = ExpansionPose.sourceScale(start.size, frame.size, p)
                val halfWidth = start.width * factor / 2f
                val halfHeight = start.height * factor / 2f
                val visible = Rect(frame.center - Offset(halfWidth, halfHeight), frame.center + Offset(halfWidth, halfHeight))
                val origin = visible.topLeft + (session.layerOrigin - session.bounds.topLeft) * factor
                clip.reset()
                clip.addRoundRect(corners.roundRect(frame, session.originCornerPx, p))
                clipPath(clip) {
                    clipRect(visible.left, visible.top, visible.right, visible.bottom) {
                        translate(origin.x, origin.y) {
                            scale(factor, factor, Offset.Zero) {
                                layer.alpha = alpha
                                drawLayer(layer)
                            }
                        }
                    }
                }
            }
            session.parts.filter { it.sourceArtwork == null }.forEach { part ->
                val bounds = ExpansionPose.interpolate(part.from, part.to, p).translate(-windowOffset)
                // Keep a single title visible, including the frame where typography changes.
                val title = if (p < 0.5f) part.sourceLayer ?: part.targetLayer else part.targetLayer ?: part.sourceLayer
                title?.let { drawExpansionLayer(it, bounds, ExpansionPose.titleOpacity(p)) }
            }
            session.parts.forEach { part ->
                drawExpansionArtwork(part, session.returning, p, windowOffset)
            }
        }
    }
}

private fun DrawScope.drawExpansionArtwork(part: ExpansionPart, returning: Boolean, progress: Float, windowOffset: Offset) {
    val source = part.sourceArtwork ?: return
    val target = part.targetArtwork ?: source
    val alpha = if (returning) (progress / 0.08f).coerceIn(0f, 1f) else 1f
    if (alpha <= 0f) return

    // Freeze the pixels and outline at the first draw. Rebinding the loaded header
    // may change a Coil painter, but it must not reset the current frame.
    if (part.firstPainter == null) {
        val sourcePainter = source.painter?.value
        val targetPainter = target.painter?.value ?: part.target.artwork?.painter?.value
        part.firstPainter = sourcePainter ?: targetPainter ?: return
        part.secondPainter = targetPainter?.takeIf { sourcePainter != null && it !== sourcePainter }
    }
    val painter = part.firstPainter ?: return
    val morph = part.artworkMorph ?: ArtworkOutlineMorph(
        source.shape, target.shape, part.from.size, part.to.size, layoutDirection, this,
    ).also { part.artworkMorph = it }
    val bounds = ExpansionPose.interpolate(part.from, part.to, progress).translate(-windowOffset)
    translate(bounds.left, bounds.top) {
        clipPath(morph.pathAt(bounds.size, progress)) {
            if (source.containerColor != Color.Transparent) {
                drawRect(source.containerColor, size = bounds.size, alpha = alpha)
            }
            drawCroppedArtwork(painter, bounds.size, alpha)
            part.secondPainter?.let {
                val blend = ((progress - 0.5f) / 0.4f).coerceIn(0f, 1f)
                drawCroppedArtwork(it, bounds.size, alpha * blend)
            }
        }
    }
    if (!part.artworkDrawn) part.artworkDrawn = true
}

private fun DrawScope.drawCroppedArtwork(painter: Painter, bounds: Size, alpha: Float) {
    if (alpha <= 0f) return
    val intrinsic = painter.intrinsicSize
    val size = if (intrinsic != Size.Unspecified && intrinsic.width > 0f && intrinsic.height > 0f) {
        val scale = maxOf(bounds.width / intrinsic.width, bounds.height / intrinsic.height)
        Size(intrinsic.width * scale, intrinsic.height * scale)
    } else {
        bounds
    }
    translate((bounds.width - size.width) / 2f, (bounds.height - size.height) / 2f) {
        with(painter) { draw(size, alpha = alpha) }
    }
}

private fun DrawScope.drawExpansionLayer(layer: GraphicsLayer, bounds: Rect, opacity: Float) {
    if (opacity <= 0f) return
    // Uniform fit keeps glyphs, artwork and pre-laid-out text undistorted.
    val factor = minOf(bounds.width / layer.size.width.coerceAtLeast(1), bounds.height / layer.size.height.coerceAtLeast(1))
    translate(bounds.center.x - layer.size.width * factor / 2, bounds.center.y - layer.size.height * factor / 2) {
        scale(factor, factor, Offset.Zero) { layer.alpha = opacity; drawLayer(layer) }
    }
}

/** A role-specific endpoint, scoped to its actual navigation entry. */
data class ExpansionArtworkContent(
    val url: String,
    val shape: Shape,
    val sizePx: Int,
    val containerColor: Color,
    val label: String? = null,
    val painter: State<Painter?>? = null,
)

internal class ExpansionElement(val owner: String, val key: String, val role: String, val primary: Boolean) {
    val pairingKey = "$role:$key"
    var artwork: ExpansionArtworkContent? = null
    var coordinates: LayoutCoordinates? = null
    var restBounds: (() -> Rect?) = { null }
}

internal class ExpansionPart(
    val source: ExpansionElement,
    target: ExpansionElement,
    val from: Rect,
    val to: Rect,
) {
    var target by mutableStateOf(target)
    val sourceArtwork = source.artwork
    val targetArtwork = target.artwork
    var firstPainter: Painter? = null
    var secondPainter: Painter? = null
    var artworkMorph: ArtworkOutlineMorph? = null
    var artworkDrawn by mutableStateOf(false)
    var sourceLayer by mutableStateOf<GraphicsLayer?>(null)
    var targetLayer by mutableStateOf<GraphicsLayer?>(null)
}

internal fun selectExpansionTarget(
    source: ExpansionElement,
    candidates: List<ExpansionElement>,
    targets: List<ExpansionElement>,
    hero: ExpansionElement?,
): ExpansionElement? {
    // The clicked card and this navigation entry already identify the media. Resized
    // URLs and refreshed labels must not break a unique card-to-hero match.
    if (hero != null && candidates.count { it.role == source.role } == 1) return hero
    return targets.singleOrNull { it.key == source.key }
}

/**
 * Marks matching visual content. Only unique endpoints on the selected destination match;
 * repeated artwork in a list never steals the selected card's flight. Missing endpoints
 * keep the card's own geometry while the destination is being measured.
 */
fun Modifier.expansionElement(
    key: String?,
    role: String,
    artwork: ExpansionArtworkContent? = null,
    primary: Boolean = false,
): Modifier = composed {
    val page = LocalPageFlight.current
    val motion = LocalPageMotion.current
    val context = LocalGraphicsContext.current
    if (key == null || page == null || motion == null || LocalAnimationsDisabled.current || (motion.ownerView != null && motion.ownerView !== LocalView.current)) return@composed this
    val element = remember(page, key, role, primary) { ExpansionElement(page.entryId, key, role, primary) }
    element.artwork = artwork
    DisposableEffect(element, motion) {
        motion.expansionElements.add(element)
        onDispose { element.coordinates = null; motion.expansionElements.remove(element) }
    }
    this.onGloballyPositioned { coordinates ->
        element.coordinates = coordinates
        element.restBounds = { page.restBoundsInWindow(coordinates) }
    }.drawWithContent {
        val session = motion.expansion
        // Only potential target endpoints follow progress. Other visible rows draw once,
        // rather than every frame of an unrelated card's transition.
        if (session != null && (element.owner == session.entry || element.owner == session.source.owner) && element.pairingKey in session.candidateKeys) session.progress()
        if (session == null) { drawContent(); return@drawWithContent }
        val part = session.parts.firstOrNull { it.target === element || it.source === element }
        if (part != null) {
            if ((!session.overlayDrawing && !session.recordingBody) || (part.target === element && session.progress() >= 1f)) {
                drawContent()
                return@drawWithContent
            }
            val target = part.target === element
            val liveLanding = session.returning && !target
            if (part.sourceArtwork != null && part.targetArtwork != null) {
                val hasPixels = part.artworkDrawn || part.sourceArtwork.painter?.value != null || part.targetArtwork.painter?.value != null
                if (liveLanding || !hasPixels || (!part.artworkDrawn && !session.recordingBody)) drawContent()
                return@drawWithContent
            }
            val layer = if (target) part.targetLayer else part.sourceLayer
            if (layer == null) {
                val captured = context.createGraphicsLayer()
                captured.record { this@drawWithContent.drawContent() }
                if (target) part.targetLayer = captured else part.sourceLayer = captured
                session.resources.add { context.releaseGraphicsLayer(captured) }
                if (!session.recordingBody) drawContent()
            }
            return@drawWithContent
        }
        drawContent()
    }
}

internal object ExpansionPose {
    fun interpolate(from: Rect, to: Rect, p: Float): Rect = Rect(
        from.left + (to.left - from.left)*p, from.top + (to.top - from.top)*p,
        from.right + (to.right - from.right)*p, from.bottom + (to.bottom - from.bottom)*p,
    )
    fun sourceAlpha(progress: Float): Float = (1f - (progress - 0.08f) / 0.47f).coerceIn(0f, 1f)

    fun titleOpacity(progress: Float): Float =
        1f - 0.15f * (1f - kotlin.math.abs(progress - 0.5f) / 0.10f).coerceIn(0f, 1f)

    fun sourceScale(source: Size, frame: Size, progress: Float): Float =
        minOf(maxOf(frame.width / source.width, frame.height / source.height), 1f + progress * 2f)
}

private const val PairingLimit = 0.55f
