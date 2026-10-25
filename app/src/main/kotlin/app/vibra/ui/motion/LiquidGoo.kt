/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.motion

import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * GOO SURFACE — metaball rendering for neighbouring pills.
 *
 * Children register their *animated* bounds with Modifier.gooBlob(). The host draws all
 * blobs as filled rounded rectangles into one offscreen layer, blurs that layer and then
 * applies an alpha threshold. Where two blurred blobs overlap their alpha adds up and
 * crosses the threshold, so a liquid "neck" forms between them; as they drift apart the
 * neck thins and snaps — the classic gooey effect, applied to real UI surfaces.
 *
 * Because blob bounds come from layout (usually animated by LiquidScope.liquid()), the
 * goo follows every spring automatically. Rendering only touches the draw phase: moving
 * blobs never trigger recomposition.
 *
 * API < 31 has no RenderEffect; there the blobs are drawn as plain rounded rectangles,
 * which is visually identical at rest and only loses the merge effect mid-animation.
 */

/**
 * One registered pill. Its layout coordinates are resolved against the host at draw time
 * (after placement), so registration order between host and children never matters.
 * [frame] changes on every placement, which invalidates the drawing.
 */
@Stable
internal class GooBlob(
    val coordinates: LayoutCoordinates,
    val cornerRadius: Dp,
    val frame: Long,
) {
    /** Bounds inside [host], or null while either side is detached. */
    fun boundsIn(host: LayoutCoordinates?): Rect? =
        if (host == null || !host.isAttached || !coordinates.isAttached) {
            null
        } else {
            host.localBoundingBoxOf(coordinates, clipBounds = false)
        }

    /** Corner radius in px; [Dp.Infinity] means a fully rounded (stadium) pill. */
    fun cornerRadiusPx(
        bounds: Rect,
        density: Float,
    ): Float =
        if (cornerRadius == Dp.Infinity) {
            minOf(bounds.width, bounds.height) / 2f
        } else {
            (cornerRadius.value * density).coerceAtMost(minOf(bounds.width, bounds.height) / 2f)
        }
}

/** Shared registry between a [GooSurface] host and its [gooBlob] children. */
@Stable
class GooState internal constructor() {
    internal val blobs = mutableStateMapOf<Any, GooBlob>()
    internal var hostCoordinates: LayoutCoordinates? = null
}

/**
 * Scope for children of [GooSurface]. Deliberately *not* a BoxScope: layout scopes carry
 * a DSL marker that would hide [gooBlob] inside nested Rows/Columns.
 */
@Stable
class GooScope internal constructor(
    private val state: GooState,
) {
    /**
     * Registers this element as a goo blob. Place it AFTER any animating modifier
     * (e.g. `liquid()`) so the animated bounds are reported, and BEFORE padding that
     * should not be part of the pill.
     *
     * @param key stable identity of the blob (survives recomposition and reordering).
     * @param cornerRadius corner radius of the pill; [Dp.Infinity] means fully rounded.
     */
    fun Modifier.gooBlob(
        key: Any,
        cornerRadius: Dp = Dp.Infinity,
    ): Modifier = this then GooBlobElement(state, key, cornerRadius)
}

/**
 * Hosts children whose background pills merge like liquid.
 *
 * @param color fill colour of every blob.
 * @param gooRadius blur radius that controls how far apart blobs start to bridge. Keep it
 *        below the resting gap between pills so they separate cleanly at rest.
 * @param shadowColor optional soft drop shadow drawn under the merged surface.
 */
@Composable
fun GooSurface(
    color: Color,
    modifier: Modifier = Modifier,
    gooRadius: Dp = 8.dp,
    shadowColor: Color = Color.Black.copy(alpha = 0.25f),
    shadowRadius: Dp = 14.dp,
    shadowOffsetY: Dp = 3.dp,
    content: @Composable GooScope.() -> Unit,
) {
    val state = remember { GooState() }
    val density = LocalDensity.current
    val gooRadiusPx = with(density) { gooRadius.toPx() }
    // The blurred layer must extend past the host so pills touching the edge are not
    // eaten by the blur's transparent border.
    val bleedPx = with(density) { (gooRadius * 2 + shadowRadius + shadowOffsetY).roundToPx() }
    val shadowRadiusPx = with(density) { shadowRadius.toPx() }
    val shadowOffsetPx = with(density) { shadowOffsetY.toPx() }

    Box(modifier = modifier.onPlaced { state.hostCoordinates = it }) {
        // 1) Diffuse drop shadow under the whole surface.
        if (shadowColor.alpha > 0f) {
            Canvas(modifier = Modifier.matchParentSize().bleed(bleedPx)) {
                drawBlobShadows(
                    state = state,
                    shadowColor = shadowColor,
                    radius = shadowRadiusPx,
                    offsetY = shadowOffsetPx,
                    origin = Offset(bleedPx.toFloat(), bleedPx.toFloat()),
                )
            }
        }
        // 2) Goo fill (blur + alpha threshold), drawn in an enlarged layer behind content.
        Canvas(
            modifier =
                Modifier
                    .matchParentSize()
                    .bleed(bleedPx)
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && gooRadiusPx > 0f) {
                            Modifier.graphicsLayer { renderEffect = gooEffect(gooRadiusPx).asComposeRenderEffect() }
                        } else {
                            Modifier
                        },
                    ),
        ) {
            drawBlobs(state, color, Offset(bleedPx.toFloat(), bleedPx.toFloat()))
        }
        // 3) The real content (icons, text, artwork) on top, never blurred.
        val scope = remember(state) { GooScope(state) }
        scope.content()
    }
}

/** Lays the node out larger than its parent by [px] on every side (draw bleed). */
private fun Modifier.bleed(px: Int): Modifier =
    layout { measurable, constraints ->
        val placeable =
            measurable.measure(
                constraints.copy(
                    minWidth = constraints.minWidth + px * 2,
                    maxWidth = constraints.maxWidth + px * 2,
                    minHeight = constraints.minHeight + px * 2,
                    maxHeight = constraints.maxHeight + px * 2,
                ),
            )
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-px, -px) }
    }

private fun DrawScope.drawBlobs(
    state: GooState,
    color: Color,
    origin: Offset,
) {
    state.blobs.values.forEach { blob ->
        val bounds = blob.boundsIn(state.hostCoordinates) ?: return@forEach
        drawRoundRect(
            color = color,
            topLeft = bounds.topLeft + origin,
            size = bounds.size,
            cornerRadius = CornerRadius(blob.cornerRadiusPx(bounds, density)),
        )
    }
}

/**
 * Modifier node behind [GooScope.gooBlob]: publishes the node's bounds (relative to the
 * goo host) on every placement and unregisters the blob when the node leaves the
 * composition, so a pill that disappears never leaves a stale shape behind.
 */
private data class GooBlobElement(
    val state: GooState,
    val key: Any,
    val cornerRadius: Dp,
) : ModifierNodeElement<GooBlobNode>() {
    override fun create() = GooBlobNode(state, key, cornerRadius)

    override fun InspectorInfo.inspectableProperties() {
        name = "gooBlob"
        properties["key"] = key
        properties["cornerRadius"] = cornerRadius
    }

    override fun update(node: GooBlobNode) {
        if (node.key != key) node.state.blobs.remove(node.key)
        node.state = state
        node.key = key
        node.cornerRadius = cornerRadius
    }
}

private class GooBlobNode(
    var state: GooState,
    var key: Any,
    var cornerRadius: Dp,
) : Modifier.Node(),
    LayoutAwareModifierNode {
    private var frame = 0L

    override fun onPlaced(coordinates: LayoutCoordinates) {
        state.blobs[key] = GooBlob(coordinates, cornerRadius, frame++)
    }

    override fun onDetach() {
        state.blobs.remove(key)
    }
}

/**
 * Soft drop shadow (Figma dock: 0 3 13.75 rgba(0,0,0,.25)): each blob is drawn in the
 * shadow colour through a blur mask filter (hardware accelerated from API 28) and offset
 * downwards. The opaque goo fill drawn afterwards hides the inner part.
 */
private fun DrawScope.drawBlobShadows(
    state: GooState,
    shadowColor: Color,
    radius: Float,
    offsetY: Float,
    origin: Offset,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    drawIntoCanvas { canvas ->
        val paint =
            android.graphics.Paint().apply {
                isAntiAlias = true
                color = shadowColor.toArgb()
                maskFilter = BlurMaskFilter(radius.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            }
        state.blobs.values.forEach { blob ->
            val bounds = blob.boundsIn(state.hostCoordinates) ?: return@forEach
            val r = bounds.translate(origin.x, origin.y + offsetY)
            val corner = blob.cornerRadiusPx(bounds, density)
            canvas.nativeCanvas.drawRoundRect(r.left, r.top, r.right, r.bottom, corner, corner, paint)
        }
    }
}

/**
 * Blur → alpha threshold. The colour matrix leaves RGB untouched and maps alpha with a
 * steep ramp centred near 0.45, turning the blurred halo back into a crisp edge that
 * bulges toward neighbouring blobs.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun gooEffect(blurRadiusPx: Float): RenderEffect {
    val blur = RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.DECAL)
    val threshold =
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, GooAlphaGain, -GooAlphaGain * GooAlphaCutoff * 255f,
                ),
            ),
        )
    return RenderEffect.createColorFilterEffect(threshold, blur)
}

private const val GooAlphaGain = 24f
private const val GooAlphaCutoff = 0.45f
