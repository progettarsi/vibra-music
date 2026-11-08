/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.motion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.vibra.ui.components.VibraArtwork
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import moe.rukamori.archivetune.LocalAnimationsDisabled
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalGraphicsContext
import app.vibra.ui.shell.LocalPageFlight
import app.vibra.ui.shell.LocalPageMotion
import kotlin.math.max

/** A visible instance, never an entity ID shared by several list occurrences. */
class ExpansionSource internal constructor(internal val owner: String, internal val id: String) {
    internal var color = Color.Transparent
    internal var shape: Shape? = null
    internal val focus = FocusRequester()
    internal var coordinates: LayoutCoordinates? = null
    internal val bounds: Rect? get() = coordinates?.takeIf { it.isAttached }?.boundsInWindow()?.takeIf { it.width > 0 && it.height > 0 }
}

/** Transient rendering resources: only the selected source allocates a layer. */
class ExpansionSession internal constructor(source: ExpansionSource, var bounds: Rect, val entry: String) {
    internal var source by mutableStateOf(source)
    internal var layer: GraphicsLayer? = null
    internal var release: (() -> Unit)? = null
    internal var progress: () -> Float by mutableStateOf({ 0f })
    internal var background = Color.Transparent
    internal var startCorner = 0f
    internal var endCorner = 0f
    internal var pairsPrepared = false
    internal fun preparePairs(elements: List<ExpansionElement>) {
        if (pairsPrepared) return
        val candidates = elements.filter { it.owner == source.owner && it.coordinates?.isAttached == true && it.coordinates!!.boundsInWindow().overlaps(bounds) }
        candidates.forEach { from ->
            val target = elements.filter { it.owner == entry && it.key == from.key && it.role == from.role && it.restBounds() != null }.singleOrNull()
            if (target != null) parts.add(ExpansionPart(from, target, from.coordinates!!.boundsInWindow(), target.restBounds()!!))
        }
        pairsPrepared = true
    }
    internal var recordingBody = false
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
        motion.expansion?.takeIf { session -> session.source.id == source.id }?.bounds = it.boundsInWindow()
    }.drawWithContent {
        val session = motion.expansion
        if (session?.source !== source) { drawContent(); return@drawWithContent }
        if (session.layer == null) {
            val layer = context.createGraphicsLayer()
            val outline = source.shape?.createOutline(size, layoutDirection, this)
            session.startCorner = when (outline) {
                is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                is Outline.Rectangle -> 0f
                else -> minOf(size.minDimension / 2f, 28.dp.toPx())
            }
            session.preparePairs(motion.expansionElements)
            session.recordingBody = true
            try { layer.record { this@drawWithContent.drawContent() } }
            finally { session.recordingBody = false }
            session.layer = layer
            session.release = { context.releaseGraphicsLayer(layer) }
        }
        // The overlay owns the visible drawing until the transition ends.
    }
}

/** Registration wraps the actual click, including keyboard and accessibility activation. */
internal fun Modifier.expandingClick(onClick: () -> Unit, shape: Shape? = null, color: Color = Color.Transparent, build: @Composable Modifier.(()->Unit) -> Modifier): Modifier = composed {
    val source = rememberExpansionSource()
    val motion = LocalPageMotion.current
    source?.shape = shape
    source?.color = color
    this.expansionSource(source).build {
        if (source != null) motion?.prepareExpansion(source)
        onClick()
    }
}

/** Draw-only overlay; no input handlers or duplicate accessibility nodes. */
@Composable
fun ExpansionOverlay(session: ExpansionSession?, offset: () -> Offset, modifier: Modifier = Modifier) {
    val path = remember(session) { Path() }
    val background = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize()) {
    Canvas(Modifier.fillMaxSize()) {
        if (session == null) return@Canvas
        val p = session.progress().coerceIn(0f, 1f)
        val layer = session.layer ?: return@Canvas
        val alpha = ExpansionPose.sourceAlpha(p)
        val start = session.bounds.translate(-offset())
        val rect = Rect(
            start.left * (1-p), start.top * (1-p),
            start.right + (size.width-start.right)*p, start.bottom + (size.height-start.bottom)*p,
        )
        val scale = max(rect.width / layer.size.width.coerceAtLeast(1), rect.height / layer.size.height.coerceAtLeast(1))
        val radius = session.startCorner + (session.endCorner - session.startCorner)*p
        path.reset()
        path.addRoundRect(RoundRect(rect, CornerRadius(radius.coerceAtLeast(0f))))
        clipPath(path) {
            val surface = androidx.compose.ui.graphics.lerp(session.source.color, background, p)
            drawRect(surface)
            translate(rect.center.x-layer.size.width*scale/2, rect.center.y-layer.size.height*scale/2) {
                scale(scale, scale, Offset.Zero) {
                    layer.alpha = alpha
                    drawLayer(layer)
                }
            }
        }
        session.parts.filter { it.source.artwork == null }.forEach { part ->
            val from = part.from.translate(-offset())
            val to = part.to.translate(-offset())
            val bounds = ExpansionPose.interpolate(from, to, p)
            val targetAlpha = if (part.targetLayer == null) 0f else ((p - 0.35f) / 0.35f).coerceIn(0f, 1f)
            val sourceAlpha = (1f - p / 0.35f).coerceIn(0f, 1f)
            part.sourceLayer?.let { drawExpansionLayer(it, bounds, sourceAlpha) }
            part.targetLayer?.let { drawExpansionLayer(it, bounds, targetAlpha) }
        }
    }
    session?.parts?.filter { it.source.artwork != null }?.forEach { part ->
        ExpansionArtwork(part, session, offset)
    }
    }
}

@Composable
private fun ExpansionArtwork(part: ExpansionPart, session: ExpansionSession, offset: () -> Offset) {
    val density = LocalDensity.current
    val source = part.source.artwork ?: return
    val target = part.target.artwork ?: source
    val width = with(density) { part.to.width.toDp() }
    val height = with(density) { part.to.height.toDp() }
    // Independent cached painters survive the source page being detached. Recording an
    // AsyncImage's borrowed child layers would lose its pixels when that node is hidden.
    for (targetMask in listOf(false, true)) {
        VibraArtwork(
            url = source.url,
            shape = if (targetMask) target.shape else source.shape,
            modifier = Modifier.size(width, height).clearAndSetSemantics { }.graphicsLayer {
                val p = session.progress().coerceIn(0f, 1f)
                val bounds = ExpansionPose.interpolate(part.from, part.to, p).translate(-offset())
                val t = ((p - 0.25f) / 0.5f).coerceIn(0f, 1f)
                alpha = if (targetMask) t else 1f - t
                transformOrigin = TransformOrigin(0f, 0f)
                val factor = minOf(bounds.width / size.width.coerceAtLeast(1f), bounds.height / size.height.coerceAtLeast(1f))
                scaleX = factor; scaleY = factor
                translationX = bounds.center.x - size.width * factor / 2
                translationY = bounds.center.y - size.height * factor / 2
            },
        )
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
data class ExpansionArtworkContent(val url: String, val shape: Shape)

internal class ExpansionElement(val owner: String, val key: String, val role: String) {
    val pairingKey = "$role:$key"
    var artwork: ExpansionArtworkContent? = null
    var coordinates: LayoutCoordinates? = null
    var restBounds: (() -> Rect?) = { null }
}

internal class ExpansionPart(
    val source: ExpansionElement,
    val target: ExpansionElement,
    val from: Rect,
    val to: Rect,
) { var sourceLayer: GraphicsLayer? = null; var targetLayer: GraphicsLayer? = null }

/**
 * Marks matching visual content. Only unique endpoints on the selected destination match;
 * repeated artwork in a list never steals the selected card's flight. Missing endpoints
 * remain in the source-body dissolve, with no late attachment after capture.
 */
fun Modifier.expansionElement(key: String?, role: String, artwork: ExpansionArtworkContent? = null): Modifier = composed {
    val page = LocalPageFlight.current
    val motion = LocalPageMotion.current
    val context = LocalGraphicsContext.current
    if (key == null || page == null || motion == null || LocalAnimationsDisabled.current || (motion.ownerView != null && motion.ownerView !== LocalView.current)) return@composed this
    val element = remember(page, key, role) { ExpansionElement(page.entryId, key, role) }
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
            if (part.source.artwork != null && part.target.artwork != null) return@drawWithContent
            val target = part.target === element
            val layer = if (target) part.targetLayer else part.sourceLayer
            if (layer == null) {
                val captured = context.createGraphicsLayer()
                captured.record { this@drawWithContent.drawContent() }
                if (target) part.targetLayer = captured else part.sourceLayer = captured
                session.resources.add { context.releaseGraphicsLayer(captured) }
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
    fun sourceAlpha(progress: Float): Float = (1f - progress / 0.52f).coerceIn(0f, 1f)
}
