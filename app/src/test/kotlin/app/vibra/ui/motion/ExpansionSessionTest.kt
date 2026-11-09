/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.motion

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class ExpansionSessionTest {
    @Test fun releaseIsIdempotentAfterCancellationAndDisposal() {
        val session = ExpansionSession(ExpansionSource("page", "first"), Rect(0f, 0f, 100f, 100f), "detail")
        var releases = 0
        session.release = { releases++ }
        session.dispose()
        session.dispose()
        assertEquals(1, releases)
        assertNull(session.layer)
        assertNull(session.release)
    }

    @Test fun duplicateEntitiesKeepSeparateVisibleInstances() {
        val first = ExpansionSource("page", "first")
        val second = ExpansionSource("page", "second")
        assertNotSame(first, second)
        assertNull(first.bounds)
        assertNull(second.bounds)
    }

    @Test fun destinationGeometryIsIndependentOfSourceAspectRatio() {
        val source = Rect(80f, 1800f, 1000f, 1960f)
        val destination = Rect(240f, 200f, 840f, 800f)
        assertEquals(source, ExpansionPose.interpolate(source, destination, 0f))
        assertEquals(destination, ExpansionPose.interpolate(source, destination, 1f))
        val middle = ExpansionPose.interpolate(source, destination, 0.5f)
        assertEquals(160f, middle.left, 0.001f)
        assertEquals(1380f, middle.bottom, 0.001f)
    }

    @Test fun allTransientResourcesAreReleasedExactlyOnce() {
        val session = ExpansionSession(ExpansionSource("page", "source"), Rect.Zero, "detail")
        var sourceReleases = 0
        var targetReleases = 0
        session.resources.add { sourceReleases++ }
        session.resources.add { targetReleases++ }
        session.dispose()
        session.dispose()
        assertEquals(1, sourceReleases)
        assertEquals(1, targetReleases)
        assertTrue(session.resources.isEmpty())
    }

    @Test fun uniqueTappedCoverMatchesHeaderWithDifferentArtworkUrl() {
        val card = ExpansionElement("feed", "small-cover", "artwork", false)
        val hero = ExpansionElement("playlist", "large-cover", "artwork", true)
        val track = ExpansionElement("playlist", "small-cover", "artwork", false)
        assertSame(hero, selectExpansionTarget(card, listOf(card), listOf(track, hero), hero))
        assertNull(selectExpansionTarget(card, listOf(card, card), listOf(hero), hero))
    }

    @Test fun outlineAlignmentMatchesAReversedContour() {
        val square = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        val reversed = floatArrayOf(1f, 1f, 1f, 0f, 0f, 0f, 0f, 1f)
        assertEquals(ContourAlignment(2, -1), alignContours(square, reversed))
    }

    @Test fun shortCardZoomsWithoutStretchingToTheHeightOfThePage() {
        val card = Size(950f, 180f)
        assertEquals(1f, ExpansionPose.sourceScale(card, card, 0f), 0.001f)
        assertEquals(1.5f, ExpansionPose.sourceScale(card, Size(982.5f, 735f), 0.25f), 0.001f)
    }

    @Test fun titleRemainsVisibleAtTypographyHandoff() {
        assertEquals(1f, ExpansionPose.titleOpacity(0f), 0.001f)
        assertEquals(0.85f, ExpansionPose.titleOpacity(0.5f), 0.001f)
        assertEquals(1f, ExpansionPose.titleOpacity(1f), 0.001f)
    }
}
