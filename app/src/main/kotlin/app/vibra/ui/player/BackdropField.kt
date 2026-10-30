/*
 * Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.ui.player

import androidx.compose.ui.geometry.Rect
import kotlin.math.exp

/** Campionamento del bordo, senza inversioni verticali né copie di soggetti interni. */
internal fun extensionSource(distance: Float, top: Boolean, spread: Float): Rect {
    val growth = 1f + spread * smoothstep(distance.coerceAtLeast(0f).coerceAtMost(1f))
    val inset = (1f - 1f / growth) * 0.5f
    return if (top) Rect(inset, 0f, 1f - inset, EdgeSample)
    else Rect(inset, 1f - EdgeSample, 1f - inset, 1f)
}

/** Curva monotona dai tre stop dello SVG: raccordi con pendenza continua. */
internal fun backdropGradient(fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    val x = 0.461602f
    val y = 0.158697f
    val slope = 2f / (x / y + (1f - x) / (1f - y))
    val left = t <= x
    val start = if (left) 0f else x
    val width = if (left) x else 1f - x
    val a = if (left) 0f else y
    val b = if (left) y else 1f
    val m0 = if (left) 0f else slope
    val m1 = if (left) slope else 0f
    val u = (t - start) / width
    val u2 = u * u
    val u3 = u2 * u
    return ((2f*u3 - 3f*u2 + 1f)*a + (u3 - 2f*u2 + u)*width*m0 +
        (-2f*u3 + 3f*u2)*b + (u3 - u2)*width*m1).coerceIn(0f, 1f)
}

/** Bande cinema dedotte dalla miniatura, solo se più larghe del formato reale del flusso. */
internal fun videoBandZoom(frameAspect: Float, pictureAspect: Float?): Float {
    if (pictureAspect == null || frameAspect !in 1.3f..2f || pictureAspect !in 1.8f..2.5f) return 1f
    return (pictureAspect / frameAspect).coerceIn(1f, 1.42f)
}

// Due righe sulla copia da 160 px: il blur le raccorda al bordo originale senza specchiarle.
private const val EdgeSample = 0.0125f

/** Il dettaglio preserva una porzione maggiore del quadrato prima dell'espansione. */
internal fun fieldBottomRoll(spread: Float): Float =
    0.94f - 0.14f * ((spread - 0.1f) / 0.25f).coerceIn(0f, 1f)

/** Mappa monotona con derivata continua: nessun confine fra foto e parte estesa. */
internal fun fieldSourceY(position: Float, topSpread: Float, bottomSpread: Float): Float {
    val top = 0.04f + 0.06f * ((topSpread - 0.1f) / 0.25f).coerceIn(0f, 1f)
    val bottom = fieldBottomRoll(bottomSpread)
    return when {
        position < top -> top * exp((position - top) / top)
        position > bottom -> bottom + (1f - bottom) * (1f - exp(-(position - bottom) / (1f - bottom)))
        else -> position
    }.coerceIn(0f, 1f)
}

/** Nitidezza sul campo completo: la dissolvenza termina oltre il quadrato, sotto il titolo. */
internal fun fieldFocus(y: Float, slot: Rect, titleY: Float, bottomSpread: Float, topSpread: Float = 0.2f): Float {
    val topRoll = 0.04f + 0.06f * ((topSpread - 0.1f) / 0.25f).coerceIn(0f, 1f)
    val topStart = slot.top - slot.height * 0.06f
    val topEnd = slot.top + slot.height * (topRoll + 0.08f)
    val top = smoothstep((y - topStart) / (topEnd - topStart))
    val start = slot.top + slot.height * fieldBottomRoll(bottomSpread)
    val end = maxOf(slot.bottom + slot.height * 0.12f, titleY + slot.height * 0.03f)
    return top * (1f - smoothstep((y - start) / (end - start)))
}
