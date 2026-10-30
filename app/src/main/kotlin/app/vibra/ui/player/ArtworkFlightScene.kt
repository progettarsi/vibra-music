/*
 * Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import kotlin.math.roundToInt

/** Campo unico: riempie il player, poi si ritaglia nel pulsante. Stessa posa nei due sensi. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ArtworkFlightScene(
    slot: Rect,
    cookie: Rect,
    source: GraphicsLayer,
    frame: () -> ArtworkFlightFrame,
    container: () -> Rect,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val crop by remember(frame) { derivedStateOf(structuralEqualityPolicy()) { frame().tail } }
    val rectangle = remember { RoundedPolygon.rectangle(width = 1f, height = 1f, centerX = 0.5f, centerY = 0.5f) }
    val cookiePolygon = MaterialShapes.Cookie12Sided
    val morph = remember { Morph(cookiePolygon, rectangle) }
    val rectangleBounds = remember { rectangle.calculateBounds() }
    val cookieBounds = remember { cookiePolygon.calculateBounds() }
    Box(Modifier.fillMaxSize()
        .graphicsLayer {
            val f = frame()
            alpha = if (artworkInMiniPlayer(f.progress)) 0f else 1f
            compositingStrategy = if (enabled && f.progress < 0.9999f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            if (enabled) {
                val pose = artworkFlightPose(slot, cookie, Rect(Offset.Zero, size), container(), f.progress)
                scaleX = pose.scale
                scaleY = pose.scale
                translationX = pose.translation.x
                translationY = pose.translation.y
                clip = f.tail > 0f
                if (clip) shape = FlightClip(pose.cropInSource, morph, rectangleBounds, cookieBounds, f.tail, f.angle)
            } else {
                scaleX = 1f; scaleY = 1f
                translationX = 0f; translationY = 0f
                clip = false
            }
        }
        .drawWithContent {
            drawContent()
            if (enabled) {
                val tail = crop
                if (tail > 0f) {
                    // Il video è già invisibile da c=0,50: la sorgente contiene la copertina.
                    drawContext.canvas.saveLayer(slot, Paint().apply { alpha = tail })
                    drawCoverContent(source, slot, Rect(0f, 0f, 1f, 1f), IntSize(slot.width.roundToInt(), slot.height.roundToInt()))
                    drawRect(Color.Black.copy(alpha = 0.32f * tail), slot.topLeft, slot.size)
                    drawContext.canvas.restore()
                }
            }
        }, content = content)
}

/** Normalizzazione continua fra rettangolo e sagoma effettiva del mini-player. */
private class FlightClip(
    val bounds: Rect,
    val morph: Morph,
    val rectangleBounds: FloatArray,
    val cookieBounds: FloatArray,
    val tail: Float,
    val angle: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (tail <= 0f) return Outline.Rectangle(bounds)
        val path = morph.toPath(1f - tail).asComposePath()
        val b = FloatArray(4) { rectangleBounds[it] + (cookieBounds[it] - rectangleBounds[it]) * tail }
        val matrix = Matrix().apply {
            translate(bounds.center.x, bounds.center.y)
            rotateZ(angle)
            translate(-bounds.width / 2f, -bounds.height / 2f)
            scale(bounds.width / (b[2]-b[0]), bounds.height / (b[3]-b[1]))
            translate(-b[0], -b[1])
        }
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/** Stessa sagoma per fondo, immagine, controlli e verifiche debug. */
internal class PlayerContainerShape(private val bounds: Rect, private val cornerPx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(bounds, CornerRadius(cornerPx)))
}
