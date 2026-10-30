/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  COVER ANALYSIS — how a picture continues into the player's backdrop
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  Lo sfondo riprende la copertina ingrandita con blur uniforme (CoverStage). L'analisi ricava:
 *
 *   • the SQUARE to show. Letterboxed thumbnails (videos: a 16:9 picture inside a 4:3 frame
 *     with black bands) would carry their bands into the backdrop.
 *     → black bands at the edges are trimmed before the centre square is taken — only real
 *       ones: neutral black, as wide on both sides, ending where the picture clearly starts,
 *       leaving a picture of a usual shape (1:1 to 2.39:1). A black background around a figure,
 *       or a night sky, fails these (trimming them zoomed such covers in and pushed their
 *       content to an edge). Le copertine già quadrate mantengono sempre il loro bordo originale.
 *
 *   • whether a side is PLAIN. Many covers are a figure on a flat background (a red field, a
 *     purple sky); enlarged and blurred, whatever touches the edge became stains on it.
 *     → when the band along the top (or bottom) edge is mostly one colour, all along it, that
 *       colour fills the backdrop on that side. A vivid colour counts even with objects along
 *       the edge, if it is also the colour of much of the picture; a dark or grey one only when
 *       it fills the edge almost alone (the shadows of a photo are not a background). A colour
 *       filling one end of the edge only (a sky over half the picture) is not one either.
 *
 *   • how much to DARKEN behind the text: the status bar above the cover, the title and the
 *     controls below it. It is computed from what fills those areas — the picture's top and
 *     bottom, or the plain colour — so a bright cover is darkened only where the text is, and
 *     only as much as white text needs. The whole picture's darkening is kept for the lyrics,
 *     which sit over its middle.
 *
 *  All of it is a pure function of the pixels (tested in CoverAnalysisTest), run once per
 *  cover on a small (~200 px) copy, off the main thread.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/** A side of the backdrop filled with the cover's background [color], at opacity [weight]. */
@Immutable
data class PlainEdge(
    val color: Color,
    val weight: Float,
)

/**
 * What the backdrop takes from a picture: the [square] shown at the cover's slot, the plain
 * background colour continuing it above ([plainTop]) and below ([plainBottom]), if any, the
 * darkening behind the status bar ([topScrim]) and behind the title and controls
 * ([bottomScrim]), and the darkening the whole picture needs for white text ([dim]).
 */
@Immutable
data class CoverCrop(
    val square: IntRect,
    val plainTop: PlainEdge?,
    val plainBottom: PlainEdge?,
    val topScrim: Float,
    val bottomScrim: Float,
    val dim: Float,
    val topSpread: Float = 0.2f,
    val bottomSpread: Float = 0.2f,
    val contentAspectRatio: Float? = null,
) {
    companion object {
        /** The plain centre square, with the lightest darkening (no pixels to look at, e.g. a video). */
        fun centered(
            width: Int,
            height: Int,
        ): CoverCrop {
            val side = min(width, height).coerceAtLeast(1)
            val square = IntRect(IntOffset((width - side) / 2, (height - side) / 2), IntSize(side, side))
            return CoverCrop(square, plainTop = null, plainBottom = null, topScrim = MinTopScrim, bottomScrim = MinBottomScrim, dim = 0f)
        }
    }
}

/**
 * Analyses an ARGB picture of [width] × [height] (row-major [pixels]) — see the file header.
 */
fun analyzeCover(
    pixels: IntArray,
    width: Int,
    height: Int,
): CoverCrop {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return CoverCrop.centered(width, height)
    val picture = Picture(pixels, width, height)

    // 1. Trim letterbox / pillarbox bands.
    // Le copertine quadrate hanno già una composizione intenzionale: bande e scritte vanno
    // preservate. Solo le miniature rettangolari dei video richiedono il ritaglio del letterbox.
    val (top, bottom) = if (width == height) 0 to 0 else picture.bands(rows = true)
    val (left, right) = if (width == height) 0 to 0 else picture.bands(rows = false)
    val area = IntRect(left, top, width - right, height - bottom)

    // 2. The centre square of what is left.
    val side = min(area.width, area.height)
    val square = IntRect(IntOffset(area.left + (area.width - side) / 2, area.top + (area.height - side) / 2), IntSize(side, side))

    // 3. Plain sides: the colour of the band along each edge, if it is a background.
    val grid = picture.grid(square)
    val n = grid.size
    val band = (n * EdgeBand).roundToInt().coerceIn(1, n)
    val whole = grid.dominant(0 until n)
    val plainTop = plainEdge(grid, 0 until band, whole)
    val plainBottom = plainEdge(grid, n - band until n, whole)

    // 4. Darkening behind the text, from what fills each area.
    val centre = n / 4 until n - n / 4
    val topLuminance = behind(grid.meanLuminance(0 until (n * TopTextRows).roundToInt().coerceAtLeast(1), centre), plainTop)
    val bottomLuminance = behind(grid.meanLuminance((n * BottomTextFrom).roundToInt().coerceAtMost(n - 1) until n, centre), plainBottom)
    return CoverCrop(
        square = square,
        plainTop = plainTop,
        plainBottom = plainBottom,
        topScrim = dimFor(topLuminance).coerceIn(MinTopScrim, MaxTopScrim),
        bottomScrim = dimFor(bottomLuminance).coerceIn(MinBottomScrim, MaxBottomScrim),
        dim = dimFor(grid.meanLuminance(0 until n, 0 until n)),
        topSpread = grid.extensionSpread(0 until band, plainTop),
        bottomSpread = grid.extensionSpread(n - band until n, plainBottom),
        contentAspectRatio = if (width != height) area.width.toFloat() / area.height else null,
    )
}

/**
 * The darkening that brings a backdrop of mean relative [luminance] (WCAG) down to
 * [TargetLuminance], which keeps white text above a 4.5 : 1 contrast ratio
 * (1.05 / (0.16 + 0.05) = 5.0). A black overlay of alpha a scales sRGB channels by (1 - a),
 * i.e. luminance by roughly (1 - a)^2.2. Bright patches matter more than the mean (white text
 * on a light corner), so the luminance is leaned up a little first.
 */
internal fun dimFor(luminance: Double): Float {
    val leaned = luminance * 1.15
    if (leaned <= TargetLuminance) return 0f
    return (1.0 - (TargetLuminance / leaned).pow(1.0 / 2.2)).toFloat().coerceIn(0f, MaxContrastDim)
}

/**
 * The plain colour continuing the edge whose band is [rows] of [grid], or null. A vivid colour is
 * a background at a lower share than a dark or grey one, and also when it is the colour of much of
 * the picture ([whole]) though objects crowd the edge (as long as it still holds a fair part of
 * it). Either way it must reach both ends of the edge (a figure may stand in the middle).
 */
private fun plainEdge(
    grid: Grid,
    rows: IntRange,
    whole: Dominant,
): PlainEdge? {
    val edge = grid.dominant(rows)
    val vivid = smoothstep((edge.chroma - VividFrom) / VividRamp)
    val threshold = NeutralPlainShare + (VividPlainShare - NeutralPlainShare) * vivid
    val sameAsWhole =
        edge.share >= WholeColourFloor &&
            distance(edge.red, edge.green, edge.blue, whole.red, whole.green, whole.blue) <= PlainTolerance * 2
    val share = if (sameAsWhole) max(edge.share, whole.share * vivid) else edge.share
    val n = grid.size
    val ends = min(grid.shareNear(rows, 0 until n / 3, edge), grid.shareNear(rows, n - n / 3 until n, edge))
    val weight = smoothstep((share - threshold) / PlainRamp) * smoothstep(ends / (threshold * EndShare))
    if (weight < MinPlainWeight) return null
    return PlainEdge(Color(edge.red / 255f, edge.green / 255f, edge.blue / 255f), weight)
}

/** [luminance] of the picture where the text sits, or of the [plain] colour covering it. */
private fun behind(
    luminance: Double,
    plain: PlainEdge?,
): Double {
    plain ?: return luminance
    return luminance + (relativeLuminance(plain.color) - luminance) * plain.weight
}

/** The main colour of some pixels (0..255 channels) and the share of them close to it. */
private class Dominant(
    val red: Float,
    val green: Float,
    val blue: Float,
    val share: Float,
) {
    val chroma get() = (max(red, max(green, blue)) - min(red, min(green, blue))) / 255f
}

/** Read helpers over a row-major ARGB buffer, sampling sparsely (pictures are small). */
private class Picture(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
) {
    /**
     * The letterbox ([rows]) or pillarbox bands to trim at the start and end of that axis: lines
     * of nearly only band-black samples, at most [MaxBand] of the picture each, as wide on both
     * sides, each ending on a line where the picture clearly starts (across most of it, on a
     * square picture), and leaving a picture of a usual shape. Anything else is the picture's
     * own dark area: (0, 0).
     */
    fun bands(rows: Boolean): Pair<Int, Int> {
        val count = if (rows) height else width
        val cap = (count * MaxBand).toInt()
        val start = band(rows, cap) { it } ?: return 0 to 0
        val end = band(rows, cap) { count - 1 - it } ?: return 0 to 0
        if (abs(start - end) > count * BandAsymmetry + 2) return 0 to 0
        val rest = (count - start - end).toFloat()
        val shape = if (rows) width / rest else rest / height
        if (UsualShapes.none { abs(shape / it - 1f) <= ShapeTolerance }) return 0 to 0
        return start to end
    }

    /** How many lines from an edge ([line] maps 0, 1, … to them) form a band; null if none. */
    private inline fun band(
        rows: Boolean,
        cap: Int,
        line: (Int) -> Int,
    ): Int? {
        var n = 0
        while (n < cap && blackShare(rows, line(n)) >= DarkShare) n++
        if (n == 0 || n >= cap) return null
        // The picture must start on the next line, not fade in (a figure on a black background).
        return n.takeIf { 1f - blackShare(rows, line(n)) >= BandEdge }
    }

    /** Share of the samples of row (or column) [index] that are band-black. */
    private fun blackShare(
        rows: Boolean,
        index: Int,
    ): Float {
        val length = if (rows) width else height
        val step = maxOf(1, length / LineSamples)
        var black = 0
        var total = 0
        var i = 0
        while (i < length) {
            if (isBandBlack(if (rows) pixels[index * width + i] else pixels[i * width + index])) black++
            total++
            i += step
        }
        return black.toFloat() / total
    }

    /** Near-black and neutral: letterbox black, not a dark colour of the picture. */
    private fun isBandBlack(argb: Int): Boolean =
        relativeLuminance(argb) < DarkLuminance && max((argb shr 16) and 0xFF, max((argb shr 8) and 0xFF, argb and 0xFF)) <= BandMaxChannel

    /** [square] averaged down to at most [GridSize] cells a side: small, and free of grain. */
    fun grid(square: IntRect): Grid {
        val n = min(GridSize, square.width).coerceAtLeast(1)
        val cells = IntArray(n * n)
        for (gy in 0 until n) {
            val y0 = square.top + gy * square.height / n
            val y1 = max(y0 + 1, square.top + (gy + 1) * square.height / n)
            for (gx in 0 until n) {
                val x0 = square.left + gx * square.width / n
                val x1 = max(x0 + 1, square.left + (gx + 1) * square.width / n)
                var r = 0
                var g = 0
                var b = 0
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        val c = pixels[y * width + x]
                        r += (c shr 16) and 0xFF
                        g += (c shr 8) and 0xFF
                        b += c and 0xFF
                    }
                }
                val count = (y1 - y0) * (x1 - x0)
                cells[gy * n + gx] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
        return Grid(cells, n)
    }
}

/** A square picture of [size] × [size] averaged cells. */
private class Grid(
    val cells: IntArray,
    val size: Int,
) {
    /**
     * The main colour of [rows]: the fullest bin of a coarse histogram, refined to the mean of
     * the cells within [PlainTolerance] of it.
     */
    fun dominant(rows: IntRange): Dominant {
        val counts = IntArray(4096)
        for (y in rows) for (x in 0 until size) counts[bin(cells[y * size + x])]++
        val mode = counts.indices.maxBy { counts[it] }
        var mean = mean(rows) { bin(it) == mode }
        var share = 0f
        repeat(2) {
            val (r, g, b) = mean
            val near = mean(rows) { distance(red(it), green(it), blue(it), r, g, b) <= PlainTolerance }
            share = near[3] / (rows.count() * size)
            if (near[3] > 0f) mean = near
        }
        return Dominant(mean[0], mean[1], mean[2], share)
    }

    /** Share of the cells of [rows] × [columns] within [PlainTolerance] of [colour]. */
    fun shareNear(
        rows: IntRange,
        columns: IntRange,
        colour: Dominant,
    ): Float {
        var near = 0
        var count = 0
        for (y in rows) {
            for (x in columns) {
                val c = cells[y * size + x]
                if (distance(red(c), green(c), blue(c), colour.red, colour.green, colour.blue) <= PlainTolerance) near++
                count++
            }
        }
        return near.toFloat() / count.coerceAtLeast(1)
    }

    /** Le forme dettagliate si allargano meno; i fondali semplici ammettono un'espansione maggiore. */
    fun extensionSpread(rows: IntRange, plain: PlainEdge?): Float {
        var change = 0f
        var count = 0
        for (y in rows) for (x in 1 until size) {
            val a = cells[y * size + x - 1]
            val b = cells[y * size + x]
            change += distance(red(a), green(a), blue(a), red(b), green(b), blue(b))
            count++
        }
        val detail = (change / count.coerceAtLeast(1) / 50f).coerceIn(0f, 1f)
        return (0.35f - 0.25f * detail) * (1f - (plain?.weight ?: 0f) * 0.5f)
    }

    fun meanLuminance(
        rows: IntRange,
        columns: IntRange,
    ): Double {
        var sum = 0.0
        var count = 0
        for (y in rows) {
            for (x in columns) {
                sum += relativeLuminance(cells[y * size + x])
                count++
            }
        }
        return sum / count.coerceAtLeast(1)
    }

    /** Mean red, green, blue of the cells of [rows] that match [take], and how many matched. */
    private inline fun mean(
        rows: IntRange,
        take: (Int) -> Boolean,
    ): FloatArray {
        var r = 0f
        var g = 0f
        var b = 0f
        var count = 0
        for (y in rows) {
            for (x in 0 until size) {
                val c = cells[y * size + x]
                if (!take(c)) continue
                r += red(c)
                g += green(c)
                b += blue(c)
                count++
            }
        }
        val n = count.coerceAtLeast(1)
        return floatArrayOf(r / n, g / n, b / n, count.toFloat())
    }

    private fun bin(c: Int) = (((c shr 20) and 0xF) shl 8) or (((c shr 12) and 0xF) shl 4) or ((c shr 4) and 0xF)
}

private fun red(c: Int) = ((c shr 16) and 0xFF).toFloat()

private fun green(c: Int) = ((c shr 8) and 0xFF).toFloat()

private fun blue(c: Int) = (c and 0xFF).toFloat()

private fun distance(
    r1: Float,
    g1: Float,
    b1: Float,
    r2: Float,
    g2: Float,
    b2: Float,
): Float = sqrt((r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2))

/** WCAG relative luminance of an ARGB colour. */
internal fun relativeLuminance(argb: Int): Double {
    fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)
}

private fun relativeLuminance(color: Color): Double =
    relativeLuminance((0xFF shl 24) or ((color.red * 255).roundToInt() shl 16) or ((color.green * 255).roundToInt() shl 8) or (color.blue * 255).roundToInt())

internal const val TargetLuminance = 0.16
internal const val MaxContrastDim = 0.6f

/** Darkening behind the status bar: always a little (white icons), at most this much. */
internal const val MinTopScrim = 0.16f
internal const val MaxTopScrim = 0.42f

/** Darkening behind the title and controls: always some, at most this much. */
internal const val MinBottomScrim = 0.12f
internal const val MaxBottomScrim = 0.56f

/** The picture's rows behind the status bar (from the top) and behind the title (from this share down). */
private const val TopTextRows = 0.2f
private const val BottomTextFrom = 0.78f

/** A band is trimmed only up to this share of the picture on each side. */
private const val MaxBand = 0.3f

/** Luminance below which a sample can be band-black… */
private const val DarkLuminance = 0.012

/** …if no channel is above this (0..255) either… */
private const val BandMaxChannel = 24

/** …and the share of a line's samples that must be band-black. */
private const val DarkShare = 0.97f

/** Share of the first line inside a band that must be picture: where the picture clearly starts… */
private const val BandEdge = 0.25f

/** How much wider (share of the axis, plus 2 lines) one band may be than the other. */
private const val BandAsymmetry = 0.04f

/** The shapes (width / height) a letterboxed picture has, within [ShapeTolerance]. */
private val UsualShapes = floatArrayOf(1f, 4f / 3f, 16f / 9f, 1.85f, 2f, 2.39f)
private const val ShapeTolerance = 0.05f

/** Samples taken along a line. */
private const val LineSamples = 48

/** Cells a side of the averaged copy the colours are read from. */
private const val GridSize = 96

/** Share of the square's side, along the top and bottom edges, whose main colour may be a background. */
private const val EdgeBand = 0.14f

/** Colour distance (0..255 channels) within which a cell has the main colour. */
private const val PlainTolerance = 14f

/** Share of the edge band in the main colour at which a grey/dark side starts to be plain… */
private const val NeutralPlainShare = 0.55f

/** …and a vivid one; the plain colour then fades in over [PlainRamp] more. */
private const val VividPlainShare = 0.22f
private const val PlainRamp = 0.2f

/** Share of the edge a vivid colour must hold itself for the whole picture's colour to count. */
private const val WholeColourFloor = 0.25f

/** The outer thirds of the edge band must each hold the colour at this share of the threshold. */
private const val EndShare = 0.6f

/** Chroma (0..1) from which a colour starts to count as vivid, fully over [VividRamp] more. */
private const val VividFrom = 0.2f
private const val VividRamp = 0.3f

/** Below this weight a plain colour is not drawn at all. */
private const val MinPlainWeight = 0.01f
