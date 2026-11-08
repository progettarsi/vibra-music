/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.motion

import androidx.compose.ui.geometry.Rect
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
}
