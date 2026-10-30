/*
 * Vibra (2026) — GPL-3.0.
 */

package app.vibra.ui.player

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Geometry of the player backdrop (CoverStage.kt): the enlarged levels and the scrims. */
class CoverExtensionTest {
    // A 1080 × 2400 phone: the full-bleed cover just below the top bar.
    private val slot = Rect(0f, 270f, 1080f, 1350f)
    private val height = 2400f

    @Test
    fun cover_keepsArtworkAndSubtitlesSharpInsideTheSquare() {
        for (fraction in listOf(0.08f, 0.10f, 0.5f, 0.8f, 0.9f)) {
            assertEquals("content at $fraction", 1f, coverOpacity(fraction), 0.001f)
        }
        assertEquals(0f, coverOpacity(0f), 0.001f)
        assertEquals(0f, coverOpacity(1f), 0.001f)
    }

    @Test
    fun extension_matchesTheEdgeWithoutSamplingSubjectsOrFlippingRows() {
        for (top in listOf(true, false)) for (distance in listOf(0f, 0.01f, 0.5f, 2f)) {
            val source = extensionSource(distance, top, 0.35f)
            assertTrue(source.width > 0f && source.height > 0f)
            assertTrue(source.left >= 0f && source.right <= 1f)
            if (top) assertEquals(0f, source.top, 0.001f)
            else assertEquals(1f, source.bottom, 0.001f)
            assertTrue(source.height < 0.02f)
        }
    }

    @Test
    fun extension_hasNoZoomJumpWhereTheCoverEnds() {
        val edge = extensionSource(0f, true, 0.35f)
        val near = extensionSource(0.001f, true, 0.35f)
        assertEquals(0f, edge.left, 0.00001f)
        assertEquals(edge.left, near.left, 0.00001f)
    }

    @Test
    fun gradient_keepsTheSvgStopAndRisesContinuously() {
        assertEquals(0f, backdropGradient(0f), 0f)
        assertEquals(0.158697f, backdropGradient(0.461602f), 0.00001f)
        assertEquals(1f, backdropGradient(1f), 0f)
        var last = 0f
        for (i in 0..1000) {
            val value = backdropGradient(i/1000f)
            assertTrue(value >= last - 0.00001f)
            last = value
        }
    }

    @Test
    fun videoBandZoom_removesOnlyCinemaBandsSuggestedByTheThumbnail() {
        assertEquals(1f, videoBandZoom(16f/9f, 16f/9f), 0f)
        assertEquals(1f, videoBandZoom(16f/9f, null), 0f)
        assertEquals(1f, videoBandZoom(16f/9f, 1f), 0f)
        assertEquals(2.39f/(16f/9f), videoBandZoom(16f/9f, 2.39f), 0.001f)
    }

    @Test
    fun field_preservesTheCentreAndNeverReversesOutsideTheSquare() {
        for (p in listOf(0.2f, 0.4f, 0.7f)) assertEquals(p, fieldSourceY(p, 0.35f, 0.35f), 0.00001f)
        var previous = 0f
        for (i in 0..2500) {
            val value = fieldSourceY(i/1000f - 0.5f, 0.35f, 0.35f)
            assertTrue(value in 0f..1f && value >= previous)
            previous = value
        }
    }

    @Test
    fun field_hasNoHardClampAtTheFormerCoverEdge() {
        val before = fieldSourceY(0.999f, 0.35f, 0.35f)
        val at = fieldSourceY(1f, 0.35f, 0.35f)
        val after = fieldSourceY(1.001f, 0.35f, 0.35f)
        assertTrue(before < at && at < after && after < 1f)
        assertEquals(at-before, after-at, 0.00001f)
    }

    @Test
    fun focus_extendsPastTheSquareInsteadOfEndingOnItsBoundary() {
        val title = slot.bottom + 65f
        val atEdge = fieldFocus(slot.bottom, slot, title, 0.35f)
        assertTrue(atEdge > 0.1f && atEdge < 0.9f)
        assertEquals(0f, fieldFocus(title + slot.height, slot, title, 0.35f), 0f)
    }

    @Test
    fun field_protectsFineEdgeTextMoreThanSmoothPhotoEdges() {
        val fine = fieldSourceY(0.95f, 0.1f, 0.1f)
        val photo = fieldSourceY(0.95f, 0.35f, 0.35f)
        assertTrue(0.95f-fine < 0.001f)
        assertTrue(fine > photo)
    }

    @Test
    fun scrims_darkenOnlyWhereTextSits() {
        val top = 0.3f
        val bottom = 0.5f
        assertEquals(top, scrimAlpha(0f, slot, height, top, bottom), 0.001f)
        // None over the cover's top and middle.
        assertEquals(0f, scrimAlpha(slot.top, slot, height, top, bottom), 0.001f)
        assertEquals(0f, scrimAlpha(slot.center.y, slot, height, top, bottom), 0.001f)
        // Completo al titolo e costante fino al fondo, senza ulteriore scurimento.
        assertEquals(bottom * 0.85f, scrimAlpha(slot.bottom + slot.height * 0.12f, slot, height, top, bottom), 0.001f)
        assertEquals(bottom * 0.85f, scrimAlpha(height, slot, height, top, bottom), 0.001f)
    }

    @Test
    fun scrim_followsTheMeasuredTitleInsteadOfCoverOrScreenHeight() {
        for (title in listOf(1120f, 1600f, 1900f)) {
            val from = title - slot.height * 0.22f
            assertEquals(0f, scrimAlpha(from, slot, height, 0.3f, 0.5f, title), 0.001f)
            val atTitle = scrimAlpha(title, slot, height, 0.3f, 0.5f, title)
            assertTrue(atTitle in 0.35f..0.425f)
            assertEquals(0.5f * 0.85f, scrimAlpha(title + slot.height * 0.03f, slot, height, 0.3f, 0.5f, title), 0.001f)
            assertEquals(0.5f * 0.85f, scrimAlpha(height, slot, height, 0.3f, 0.5f, title), 0.001f)
        }
    }

    @Test
    fun scrim_doesNotMoveUpWhenTheTitleIsBelowTheCoverSurface() {
        val local = Rect(0f, 0f, slot.width, slot.height)
        val title = 1600f
        for (localY in listOf(900f, 1000f, 1080f)) {
            val screenAlpha = scrimAlpha(slot.top + localY, slot, height, 0f, 0.5f, title)
            val coverAlpha = scrimAlpha(localY, local, slot.height, 0f, 0.5f, title - slot.top)
            assertEquals(screenAlpha, coverAlpha, 0.001f)
        }
    }

    @Test
    fun scrims_neverGetLighterGoingDownFromTheCoverCentre() {
        var previous = 0f
        var y = slot.center.y
        while (y <= height) {
            val alpha = scrimAlpha(y, slot, height, 0.3f, 0.5f)
            assertTrue("lighter at $y", alpha >= previous - 1e-6f)
            previous = alpha
            y += 10f
        }
    }
}
