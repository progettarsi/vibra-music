/*
 * Vibra (2026) — GPL-3.0.
 */

package app.vibra.ui.player

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The three hard cases of the cover's extension into the player backdrop (CoverAnalysis.kt). */
class CoverAnalysisTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val mid = 0xFF6A4C93.toInt()
    private val dark = 0xFF202020.toInt()

    private fun picture(
        width: Int,
        height: Int,
        colour: (x: Int, y: Int) -> Int,
    ) = IntArray(width * height) { colour(it % width, it / width) }

    @Test
    fun plainSquareCover_usesItsOwnEdgeRows() {
        val crop = analyzeCover(picture(100, 100) { _, _ -> mid }, 100, 100)
        assertEquals(IntRect(0, 0, 100, 100), crop.square)
        assertEquals(IntRect(0, 0, 100, 6), crop.topStrip)
        assertEquals(IntRect(0, 94, 100, 100), crop.bottomStrip)
    }

    @Test
    fun letterboxedThumbnail_trimsTheBlackBandsBeforeTakingTheSquare() {
        // 160×120 (4:3) with a 16:9 picture: 15 black rows above and below.
        val crop = analyzeCover(picture(160, 120) { _, y -> if (y < 15 || y >= 105) black else mid }, 160, 120)
        assertEquals(15, crop.square.top)
        assertEquals(105, crop.square.bottom)
        assertTrue("strips must come from the picture, not the bands", crop.topStrip.top >= 15 && crop.bottomStrip.bottom <= 105)
    }

    @Test
    fun allDarkPicture_isKeptWhole() {
        val crop = analyzeCover(picture(100, 100) { _, _ -> black }, 100, 100)
        assertEquals(IntRect(0, 0, 100, 100), crop.square)
    }

    @Test
    fun logoAtTheBottom_isSteppedOver() {
        // A white label in the bottom-left corner (25 % wide, 10 % tall) on a dark cover.
        val crop = analyzeCover(picture(100, 100) { x, y -> if (x < 25 && y >= 90) white else dark }, 100, 100)
        assertTrue("bottom strip must sit above the label", crop.bottomStrip.bottom <= 90)
        assertEquals(IntRect(0, 0, 100, 6), crop.topStrip)
    }

    @Test
    fun aColourChangeAcrossTheWholeWidth_isNotALogo() {
        val crop = analyzeCover(picture(100, 100) { _, y -> if (y >= 90) white else dark }, 100, 100)
        assertEquals(IntRect(0, 94, 100, 100), crop.bottomStrip)
    }

    @Test
    fun darkCoverWithWhiteBottom_isDarkenedForTheTextBelowIt() {
        val crop = analyzeCover(picture(100, 100) { _, y -> if (y >= 90) white else dark }, 100, 100)
        val plainDark = analyzeCover(picture(100, 100) { _, _ -> dark }, 100, 100)
        assertEquals(0f, plainDark.dim)
        assertTrue(crop.dim > 0.4f)
    }
}
