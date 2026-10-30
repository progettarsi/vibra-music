/*
 * Vibra (2026) — GPL-3.0.
 */

package app.vibra.ui.player

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the player backdrop learns from a cover (CoverAnalysis.kt). */
class CoverAnalysisTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val mid = 0xFF6A4C93.toInt()
    private val dark = 0xFF202020.toInt()
    private val red = 0xFFE01822.toInt()
    private val purple = 0xFF7E06E8.toInt()
    private val grey = 0xFF404040.toInt()

    private fun picture(
        width: Int,
        height: Int,
        colour: (x: Int, y: Int) -> Int,
    ) = IntArray(width * height) { colour(it % width, it / width) }

    /** A different colour for every column. */
    private fun varied(x: Int) = 0xFF000000.toInt() or ((x * 37 % 256) shl 16) or ((x * 91 % 256) shl 8) or (x * 53 % 256)

    private fun analyze(
        size: Int = 100,
        colour: (x: Int, y: Int) -> Int,
    ) = analyzeCover(picture(size, size, colour), size, size)

    private fun assertColour(
        expected: Int,
        edge: PlainEdge?,
    ) {
        assertNotNull(edge)
        assertEquals(((expected shr 16) and 0xFF) / 255f, edge!!.color.red, 0.02f)
        assertEquals(((expected shr 8) and 0xFF) / 255f, edge.color.green, 0.02f)
        assertEquals((expected and 0xFF) / 255f, edge.color.blue, 0.02f)
    }

    @Test
    fun plainSquareCover_isAPlainBackgroundOnBothSides() {
        val crop = analyze { _, _ -> mid }
        assertEquals(IntRect(0, 0, 100, 100), crop.square)
        assertColour(mid, crop.plainTop)
        assertColour(mid, crop.plainBottom)
        assertEquals(1f, crop.plainTop!!.weight, 0.001f)
    }

    @Test
    fun letterboxedThumbnail_trimsTheBlackBandsBeforeTakingTheSquare() {
        // 160×120 (4:3) with a 16:9 picture: 15 black rows above and below.
        val crop = analyzeCover(picture(160, 120) { _, y -> if (y < 15 || y >= 105) black else mid }, 160, 120)
        assertEquals(15, crop.square.top)
        assertEquals(105, crop.square.bottom)
        assertColour(mid, crop.plainTop)
    }

    @Test
    fun pillarboxedSquare_trimsTheSideBands() {
        // 160×90 (16:9) with a square picture: 35 black columns left and right.
        val crop = analyzeCover(picture(160, 90) { x, _ -> if (x < 35 || x >= 125) black else red }, 160, 90)
        assertEquals(IntRect(35, 0, 125, 90), crop.square)
    }

    @Test
    fun allDarkPicture_isKeptWhole() {
        val crop = analyze { _, _ -> black }
        assertEquals(IntRect(0, 0, 100, 100), crop.square)
    }

    @Test
    fun cinemaLetterboxInAWideFrame_isTrimmed() {
        // A 2.39:1 music video inside a 16:9 thumbnail: 23 black rows above and below.
        val crop = analyzeCover(picture(320, 180) { _, y -> if (y < 23 || y >= 157) black else mid }, 320, 180)
        assertEquals(23, crop.square.top)
        assertEquals(157, crop.square.bottom)
    }

    @Test
    fun squareArtwork_keepsIntentionalBlackBandsAndTheirComposition() {
        // Anche un bordo nero netto può far parte di una copertina, non di un frame video.
        val crop = analyze { _, y -> if (y < 22 || y >= 78) black else mid }
        assertEquals(IntRect(0, 0, 100, 100), crop.square)
    }

    @Test
    fun figureOnABlackBackground_isNotALetterbox() {
        // Figures in the middle of square covers, as tall as a 2:1, a 16:9 and a 4:3 picture.
        for ((top, bottom) in listOf(25 to 75, 22 to 78, 12 to 88)) {
            val crop = analyze { x, y -> if (x in 30 until 70 && y in top until bottom) mid else black }
            assertEquals(IntRect(0, 0, 100, 100), crop.square)
        }
    }

    @Test
    fun darkColouredSky_isNotALetterbox() {
        // Very dark red bands that would leave a 16:9 picture: dark, but not letterbox black.
        val crop = analyzeCover(picture(160, 120) { _, y -> if (y < 15 || y >= 105) 0xFF1C0105.toInt() else 0xFF6A1020.toInt() }, 160, 120)
        assertEquals(IntRect(20, 0, 140, 120), crop.square)
    }

    @Test
    fun figureOnARedField_continuesAsTheRedFieldEvenWhereItTouchesTheEdge() {
        // A dark figure in the middle reaching the bottom edge (30 % of its width).
        val crop = analyze { x, y -> if (x in 35 until 65 && y >= 20) dark else red }
        assertColour(red, crop.plainTop)
        assertColour(red, crop.plainBottom)
        assertEquals(1f, crop.plainBottom!!.weight, 0.001f)
    }

    @Test
    fun aPhoto_hasNoPlainSides() {
        // Every column a different grey: no colour takes the edge.
        val crop = analyze { x, y -> 0xFF000000.toInt() or ((x * 2 + y / 4) * 0x010101) }
        assertNull(crop.plainTop)
        assertNull(crop.plainBottom)
    }

    @Test
    fun vividBackgroundCrowdedAtTheEdge_isStillPlain() {
        // Objects of every colour fill 70 % of the top band, but the purple is most of the picture.
        val crop = analyze { x, y -> if (y < 14 && x % 20 < 14) varied(x) else purple }
        assertColour(purple, crop.plainTop)
        assertTrue(crop.plainTop!!.weight > 0.9f)
    }

    @Test
    fun aFewSpecksOfTheMainColour_doNotMakeABusyEdgePlain() {
        // A purple cover whose top band is a different colour in every column but one in twenty.
        val crop = analyze { x, y -> if (y < 14 && x % 20 != 0) varied(x) else purple }
        assertNull(crop.plainTop)
        assertColour(purple, crop.plainBottom)
    }

    @Test
    fun aVividColourAtOneEndOfTheEdge_isNotPlain() {
        // A blue sky over the left half of a photo, and a red/blue split cover.
        val sky = analyze { x, y -> if (x < 50 && y < 40) 0xFF2060E0.toInt() else 0xFF000000.toInt() or ((x * 2 + y / 4) * 0x010101) }
        assertNull(sky.plainTop)
        val split = analyze { x, _ -> if (x < 50) red else 0xFF2040E0.toInt() }
        assertNull(split.plainTop)
        assertNull(split.plainBottom)
    }

    @Test
    fun greyBackgroundCrowdedAtTheEdge_isNot() {
        // The same picture in grey: a dark/grey area is a background only when it fills the edge.
        val crop = analyze { x, y -> if (y < 14 && x < 60) (if (x % 10 < 5) white else red) else grey }
        assertNull(crop.plainTop)
        assertNotNull(crop.plainBottom)
    }

    @Test
    fun detailedEdges_expandLessThanSoftPhotoEdges() {
        val soft = analyze { x, _ -> 0xFF000000.toInt() or ((x*2)*0x010101) }
        val busy = analyze { x, _ -> varied(x) }
        assertTrue(busy.topSpread < soft.topSpread)
        assertTrue(busy.bottomSpread < soft.bottomSpread)
    }

    @Test
    fun cinemaAnalysis_keepsTheUncroppedContentAspectForVideoBands() {
        val crop = analyzeCover(picture(320, 180) { _, y -> if(y<23 || y>=157) black else mid },320,180)
        assertEquals(320f/134f, crop.contentAspectRatio!!, 0.001f)
    }

    @Test
    fun whiteCover_isDarkenedOnlyBehindTheText_asMuchAsItNeeds() {
        val white = analyze { _, _ -> white }
        assertEquals(MaxTopScrim, white.topScrim, 0.001f)
        assertTrue(white.bottomScrim in 0.5f..MaxBottomScrim)
        val darkCover = analyze { _, _ -> dark }
        assertEquals(MinTopScrim, darkCover.topScrim, 0.001f)
        assertEquals(MinBottomScrim, darkCover.bottomScrim, 0.001f)
        assertEquals(0f, darkCover.dim)
    }

    @Test
    fun darkCoverWithWhiteBottom_isDarkenedBehindTheControlsOnly() {
        val crop = analyze { _, y -> if (y >= 80) white else dark }
        assertTrue(crop.bottomScrim > 0.4f)
        assertEquals(MinTopScrim, crop.topScrim, 0.001f)
    }
}
