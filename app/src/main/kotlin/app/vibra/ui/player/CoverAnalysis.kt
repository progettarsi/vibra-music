/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  COVER ANALYSIS — which part of a picture continues into the player's backdrop
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  The player's backdrop "extends" the cover: the cover's top and bottom rows are stretched
 *  to the screen edges and blurred (CoverStage). Three kinds of pictures broke that:
 *
 *   • LETTERBOXED thumbnails (videos: a 16:9 picture inside a 4:3 frame with black bands).
 *     The bands were part of the square, so the backdrop turned into two black slabs.
 *     → uniform dark bands at the edges are trimmed before the square is taken.
 *
 *   • A LOGO at an edge (the Parental Advisory label at the bottom, a label mark at the top).
 *     Its black-and-white rows were stretched into hard streaks across the screen.
 *     → if the edge rows differ sharply from the rows just inside them on a *part* of the
 *       width only (an object, not a change of colour across the picture), the stretched
 *       strip is taken from just inside, above/below the logo.
 *
 *   • BRIGHT covers (much white). The darkening for the white text used the cover's mean
 *     brightness, but the areas behind the text are the stretched strips, not the cover's
 *     centre: a dark cover with a white bottom left the controls unreadable.
 *     → the darkening is computed from the strips that actually fill the backdrop, and from
 *       the whole picture, whichever needs more.
 *
 *  All of it is a pure function of the pixels (tested in CoverAnalysisTest), run once per
 *  cover on a small (~200 px) copy, off the main thread.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/**
 * The parts of a picture used to continue the cover: the [square] shown at the cover's slot,
 * the rows stretched above it ([topStrip]) and below it ([bottomStrip]), and how much the
 * backdrop must be darkened for white text ([dim], 0..[MaxContrastDim]).
 */
@Immutable
data class CoverCrop(
    val square: IntRect,
    val topStrip: IntRect,
    val bottomStrip: IntRect,
    val dim: Float,
) {
    companion object {
        /** The plain centre square with its own edge rows (no pixels to look at, e.g. a video). */
        fun centered(
            width: Int,
            height: Int,
        ): CoverCrop {
            val side = min(width, height).coerceAtLeast(1)
            val square = IntRect(IntOffset((width - side) / 2, (height - side) / 2), IntSize(side, side))
            return CoverCrop(square, edgeStrip(square, fromTop = true, inset = 0), edgeStrip(square, fromTop = false, inset = 0), dim = 0f)
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

    // 1. Trim uniform dark bands (letterbox / pillarbox), at most MaxBand of each side.
    // A band that runs into the cap is not a band: the dark area goes on into the picture.
    val maxRows = (height * MaxBand).toInt()
    val maxColumns = (width * MaxBand).toInt()
    fun band(
        count: Int,
        cap: Int,
    ) = if (count >= cap) 0 else count
    val top = band(picture.darkRows(0 until maxRows), maxRows)
    val bottom = band(picture.darkRows((height - 1) downTo (height - maxRows)), maxRows)
    val left = band(picture.darkColumns(0 until maxColumns), maxColumns)
    val right = band(picture.darkColumns((width - 1) downTo (width - maxColumns)), maxColumns)
    val area = IntRect(left, top, width - right, height - bottom)

    // 2. The centre square of what is left.
    val side = min(area.width, area.height)
    val square = IntRect(IntOffset(area.left + (area.width - side) / 2, area.top + (area.height - side) / 2), IntSize(side, side))

    // 3. Edge strips, stepping inside a logo when one sits at the edge.
    val strip = stripHeight(side)
    val logoInset = (side * LogoInset).roundToInt()
    val topStrip = edgeStrip(square, fromTop = true, inset = if (picture.hasEdgeObject(square, fromTop = true, strip, logoInset)) logoInset else 0)
    val bottomStrip = edgeStrip(square, fromTop = false, inset = if (picture.hasEdgeObject(square, fromTop = false, strip, logoInset)) logoInset else 0)

    // 4. Darkening: the brighter of the whole square and the strips that fill the backdrop.
    val luminance =
        maxOf(
            picture.meanLuminance(square),
            picture.meanLuminance(topStrip),
            picture.meanLuminance(bottomStrip),
        )
    return CoverCrop(square, topStrip, bottomStrip, dimFor(luminance))
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

/** Rows of [square] stretched beyond it: its first/last strip, moved [inset] rows inwards. */
private fun edgeStrip(
    square: IntRect,
    fromTop: Boolean,
    inset: Int,
): IntRect {
    val strip = stripHeight(square.height)
    return if (fromTop) {
        IntRect(square.left, square.top + inset, square.right, square.top + inset + strip)
    } else {
        IntRect(square.left, square.bottom - inset - strip, square.right, square.bottom - inset)
    }
}

private fun stripHeight(side: Int) = (side * EdgeStripShare).roundToInt().coerceAtLeast(1)

/** Read helpers over a row-major ARGB buffer, sampling sparsely (pictures are small). */
private class Picture(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
) {
    fun luma(
        x: Int,
        y: Int,
    ): Double = relativeLuminance(pixels[y * width + x])

    /** How many rows of [rows] (from an edge inwards) are a uniform dark band. */
    fun darkRows(rows: IntProgression): Int {
        var count = 0
        for (y in rows) {
            if (!isDarkLine(width) { x -> luma(x, y) }) break
            count++
        }
        return count
    }

    fun darkColumns(columns: IntProgression): Int {
        var count = 0
        for (x in columns) {
            if (!isDarkLine(height) { y -> luma(x, y) }) break
            count++
        }
        return count
    }

    /** Nearly every sample of the line is near-black (a band, not a dark picture with detail). */
    private inline fun isDarkLine(
        length: Int,
        sample: (Int) -> Double,
    ): Boolean {
        val step = maxOf(1, length / LineSamples)
        var dark = 0
        var total = 0
        var i = 0
        while (i < length) {
            if (sample(i) < DarkLuminance) dark++
            total++
            i += step
        }
        return dark >= total * DarkShare
    }

    /**
     * True when the [strip] rows at the edge of [square] differ sharply from the rows [inset]
     * inside it on a part of the width only: an object (a logo) sits at that edge.
     */
    fun hasEdgeObject(
        square: IntRect,
        fromTop: Boolean,
        strip: Int,
        inset: Int,
    ): Boolean {
        if (inset + strip * 2 >= square.height) return false
        val edgeY = if (fromTop) square.top + strip / 2 else square.bottom - 1 - strip / 2
        val insideY = if (fromTop) square.top + inset + strip / 2 else square.bottom - 1 - inset - strip / 2
        val step = maxOf(1, square.width / LineSamples)
        var different = 0
        var total = 0
        var x = square.left
        while (x < square.right) {
            if (abs(luma(x, edgeY) - luma(x, insideY)) > ObjectContrast) different++
            total++
            x += step
        }
        val share = different.toFloat() / total.coerceAtLeast(1)
        return share in ObjectMinShare..ObjectMaxShare
    }

    fun meanLuminance(area: IntRect): Double {
        if (area.width <= 0 || area.height <= 0) return 0.0
        val step = maxOf(1, min(area.width, area.height) / 24)
        var sum = 0.0
        var count = 0
        var y = area.top
        while (y < area.bottom.coerceAtMost(height)) {
            var x = area.left
            while (x < area.right.coerceAtMost(width)) {
                sum += luma(x, y)
                count++
                x += step
            }
            y += step
        }
        return sum / count.coerceAtLeast(1)
    }
}

/** WCAG relative luminance of an ARGB colour. */
internal fun relativeLuminance(argb: Int): Double {
    fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)
}

internal const val TargetLuminance = 0.16
internal const val MaxContrastDim = 0.6f

/** Share of the cover's height whose edge rows are stretched beyond it. */
private const val EdgeStripShare = 0.06f

/** A dark band is trimmed only up to this share of the picture on each side. */
private const val MaxBand = 0.3f

/** Luminance below which a sample counts as "black" for a band… */
private const val DarkLuminance = 0.012

/** …and the share of a line's samples that must be black. */
private const val DarkShare = 0.97f

/** Samples taken along a line. */
private const val LineSamples = 48

/** How far inside the edge (share of the side) a logo is skipped: the PA label is ~10 % tall. */
private const val LogoInset = 0.16f

/** Luminance difference that marks a sample as "part of an object". */
private const val ObjectContrast = 0.25

/** An object covers part of the width: more than a speck, less than a whole new colour band. */
private const val ObjectMinShare = 0.06f
private const val ObjectMaxShare = 0.5f
