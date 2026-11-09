/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.motion

import android.graphics.PathMeasure
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** One continuous mask between the actual outlines of the two cover components. */
internal class ArtworkOutlineMorph(
    from: Shape,
    to: Shape,
    fromSize: Size,
    toSize: Size,
    direction: LayoutDirection,
    density: Density,
) {
    private val startOutline = normalizedPath(from.createOutline(fromSize, direction, density), fromSize)
    private val endOutline = normalizedPath(to.createOutline(toSize, direction, density), toSize)
    private val start = sample(startOutline)
    private val end = sample(endOutline)
    private val alignment = alignContours(start, end)

    fun pathAt(size: Size, progress: Float): Path {
        val path = Path()
        val endpoint = when {
            progress <= 0f -> startOutline
            progress >= 1f -> endOutline
            else -> null
        }
        if (endpoint != null) {
            path.addPath(endpoint)
            path.transform(Matrix().apply { scale(size.width, size.height) })
            return path
        }
        for (point in 0 until Samples) {
            val a = 2 * point
            val b = 2 * (alignment.start + point * alignment.direction).mod(Samples)
            val x = (start[a] + (end[b] - start[a]) * progress) * size.width
            val y = (start[a + 1] + (end[b + 1] - start[a + 1]) * progress) * size.height
            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun normalizedPath(outline: Outline, size: Size): Path = Path().apply {
        when (outline) {
            is Outline.Generic -> addPath(outline.path)
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Rectangle -> addRect(outline.rect)
        }
        transform(Matrix().apply { scale(1f / size.width, 1f / size.height) })
    }

    private fun sample(path: Path): FloatArray {
        val measure = PathMeasure(path.asAndroidPath(), true)
        val position = FloatArray(2)
        val length = measure.length
        return FloatArray(Samples * 2).also { points ->
            repeat(Samples) { index ->
                measure.getPosTan(length * index / Samples, position, null)
                points[index * 2] = position[0]
                points[index * 2 + 1] = position[1]
            }
        }
    }

    private companion object {
        const val Samples = 192
    }
}

internal data class ContourAlignment(val start: Int, val direction: Int)

/** Align winding and starting point before interpolating so the cover never twists. */
internal fun alignContours(from: FloatArray, to: FloatArray): ContourAlignment {
    val count = from.size / 2
    fun area(points: FloatArray): Float {
        var area = 0f
        repeat(count) { index ->
            val a = 2 * index
            val b = 2 * ((index + 1) % count)
            area += points[a] * points[b + 1] - points[b] * points[a + 1]
        }
        return area
    }
    val direction = if ((area(from) < 0f) == (area(to) < 0f)) 1 else -1
    var bestStart = 0
    var bestCost = Float.POSITIVE_INFINITY
    for (start in 0 until count) {
        var cost = 0f
        // A subset of the contour is enough to align the equally spaced samples.
        for (index in 0 until count step 4) {
            val a = 2 * index
            val b = 2 * (start + index * direction).mod(count)
            val dx = from[a] - to[b]
            val dy = from[a + 1] - to[b + 1]
            cost += dx * dx + dy * dy
        }
        if (cost < bestCost) {
            bestStart = start
            bestCost = cost
        }
    }
    return ContourAlignment(bestStart, direction)
}
