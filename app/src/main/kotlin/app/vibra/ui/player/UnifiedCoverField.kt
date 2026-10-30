/*
 * Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Stesse coordinate per immagine nitida e blur: la foto continua oltre il vecchio riquadro. */
internal fun DrawScope.drawArtworkField(
    slot: Rect,
    canvas: Size,
    topSpread: Float,
    bottomSpread: Float,
    picture: DrawScope.(Rect, Rect) -> Unit,
) {
    // Una fascia centrale non deformata; raccordi più fitti solo vicino ai bordi.
    val topRoll = 0.04f + 0.06f * ((topSpread - 0.1f) / 0.25f).coerceIn(0f, 1f)
    val bottomRoll = fieldBottomRoll(bottomSpread)
    val ys = buildList {
        val topEnd = slot.top + slot.height * topRoll
        val bottomStart = slot.top + slot.height * bottomRoll
        for (i in 0..24) add(topEnd * i / 24f)
        add(bottomStart)
        for (i in 1..32) add(bottomStart + (canvas.height - bottomStart) * i / 32f)
    }
    for (i in 0 until ys.lastIndex) {
        val y0 = ys[i]
        val y1 = ys[i + 1]
        val sy0 = fieldSourceY((y0 - slot.top) / slot.height, topSpread, bottomSpread)
        val sy1 = fieldSourceY((y1 - slot.top) / slot.height, topSpread, bottomSpread)
        if (sy1 <= sy0 || y1 <= y0) continue
        val centre = (y0 + y1) / 2f
        val outside = if (centre < slot.top) (slot.top - centre) / slot.height
            else ((centre - slot.bottom) / slot.height).coerceAtLeast(0f)
        val x = extensionSource(outside, centre < slot.top, if (centre < slot.top) topSpread else bottomSpread)
        picture(Rect(slot.left, y0, slot.right, y1), Rect(x.left, sy0, x.right, sy1))
    }
}

/** Il fuoco si attenua sullo stesso campo, senza tagliare il disegno al fondo del quadrato. */
@Composable
internal fun UnifiedCoverField(
    content: GraphicsLayer,
    slot: Rect,
    titleY: Float,
    crop: CoverCrop?,
    visibility: () -> Float,
    offsetX: () -> Float = { 0f },
) {
    val focused = rememberGraphicsLayer()
    Canvas(Modifier.fillMaxSize().graphicsLayer {
        alpha = visibility()
        translationX = offsetX()
    }) {
        if (content.size.width <= 0 || content.size.height <= 0) return@Canvas
        val canvas = size
        val topSpread = crop?.topSpread ?: 0.2f
        val bottomSpread = crop?.bottomSpread ?: 0.2f
        focused.compositingStrategy = CompositingStrategy.Offscreen
        focused.record(IntSize(canvas.width.roundToInt(), canvas.height.roundToInt())) {
            drawArtworkField(slot, canvas, topSpread, bottomSpread) { target, source ->
                drawCoverContent(content, target, source, IntSize(slot.width.roundToInt(), slot.height.roundToInt()))
            }
            drawBackdropScrims(slot, canvas, crop?.topScrim ?: MinTopScrim, crop?.bottomScrim ?: MinBottomScrim, titleY)
            val stops = Array(65) { i ->
                val y = canvas.height * i / 64f
                i/64f to Color.Black.copy(alpha = fieldFocus(y, slot, titleY, bottomSpread, topSpread))
            }
            drawRect(Brush.verticalGradient(colorStops = stops, endY = canvas.height), blendMode = BlendMode.DstIn)
        }
        drawLayer(focused)
    }
}
