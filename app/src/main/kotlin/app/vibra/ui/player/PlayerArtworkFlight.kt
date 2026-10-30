/*
 * Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.ui.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import kotlin.math.max
import kotlin.math.round

internal data class ArtworkFlightFrame(
    val progress: Float,
    val tail: Float,
    val videoOpacity: Float,
    val angle: Float,
)

/** Solo la fase ha memoria: geometria e opacità dipendono esclusivamente da p. */
internal class PlayerArtworkFlight {
    private var rawAngle = Float.NaN
    private var phase = 0f
    private var anchor: Float? = null

    fun sample(value: Float, spin: Float): ArtworkFlightFrame {
        val p = value.coerceIn(0f, 1f)
        if (rawAngle.isNaN()) phase = spin
        else phase += ((spin - rawAngle + 540f) % 360f) - 180f
        rawAngle = spin
        val base = anchor ?: (round(phase / 30f) * 30f).also { anchor = it }
        val tail = closingCrop(1f - p)
        val result = ArtworkFlightFrame(p, tail, artworkVideoOpacity(p), (phase - base) * tail)
        if (p <= HandoffProgress || p >= 0.9999f) anchor = null
        return result
    }
}

/** Posa comune nei due sensi. La scala è uniforme: il quadrato non diventa un rettangolo. */
internal data class ArtworkFlightPose(
    val scale: Float,
    val translation: Offset,
    val crop: Rect,
    val cropInSource: Rect,
    val field: Rect,
)

internal fun playerContainerRect(pill: Rect, viewport: Rect, progress: Float): Rect =
    lerp(pill, viewport, progress.coerceIn(0f, 1f))

internal fun artworkFlightPose(slot: Rect, cookie: Rect, viewport: Rect, container: Rect, progress: Float): ArtworkFlightPose {
    val p = progress.coerceIn(0f, 1f)
    val q = closingCrop(1f - p)
    val flying = lerp(cookie, slot, p)
    val fillScale = max(container.width / viewport.width, container.height / viewport.height)
    val fillOffset = container.center - viewport.center * fillScale
    val coverScale = flying.width / slot.width
    val coverOffset = flying.center - slot.center * coverScale
    val scale = fillScale + (coverScale - fillScale) * q
    val translation = lerp(fillOffset, coverOffset, q)
    val crop = lerp(container, flying, q)
    fun inverse(point: Offset) = (point - translation) / scale
    return ArtworkFlightPose(
        scale, translation, crop, Rect(inverse(crop.topLeft), inverse(crop.bottomRight)),
        Rect(viewport.topLeft * scale + translation, viewport.bottomRight * scale + translation),
    )
}

internal const val HandoffProgress = 0.001f
internal fun artworkInMiniPlayer(progress: Float): Boolean = progress <= HandoffProgress
internal fun closingCrop(c: Float): Float = smootherstep((c - 0.5f) / 0.5f)
internal fun artworkVideoOpacity(p: Float): Float = smootherstep((p - 0.5f) / 0.5f)
