/*
 * Vibra (2026) — Canvas presentation rules: the cover is always there, the Canvas only joins it
 * after a rendered frame, and any failure leaves the cover exactly as it was.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.canvas

import app.vibra.ui.player.CanvasPresentation
import app.vibra.ui.player.CanvasStage
import app.vibra.ui.player.CanvasLookup
import app.vibra.ui.player.canvasForSurface
import app.vibra.ui.player.musicVideoAllowed
import app.vibra.ui.player.revealSnaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasPresentationTest {
    private fun canvas(mediaId: String = "a") =
        CanvasArtwork(mediaId, "https://cdn.example.test/$mediaId.mp4", null, CanvasSource.COMMUNITY)

    @Test fun theCoverStaysAloneUntilAFrameIsRendered() {
        val presentation = CanvasPresentation(listOf("one", "two"))
        assertEquals(CanvasStage.Idle, presentation.stage)
        assertFalse(presentation.showing)
        assertTrue(presentation.prepare(requested = true))
        assertEquals(CanvasStage.Preparing, presentation.stage)
        assertFalse(presentation.showing)
        assertTrue(presentation.firstFrame())
        assertEquals(CanvasStage.Ready, presentation.stage)
        assertTrue(presentation.showing)
        presentation.revealed()
        assertEquals(CanvasStage.Visible, presentation.stage)
        assertTrue(presentation.showing)
    }

    @Test fun aFrameBeforeThePlayerWasAskedForDoesNothing() {
        val presentation = CanvasPresentation(listOf("one"))
        assertFalse(presentation.firstFrame())
        assertEquals(CanvasStage.Idle, presentation.stage)
        presentation.revealed()
        assertEquals(CanvasStage.Idle, presentation.stage)
    }

    @Test fun noPlayerIsBuiltWhileTheSurfaceIsNotWanted() {
        val presentation = CanvasPresentation(listOf("one"))
        assertFalse(presentation.prepare(requested = false))
        assertEquals(CanvasStage.Idle, presentation.stage)
        assertTrue(presentation.prepare(requested = true))
        // Asking again never builds a second one.
        assertFalse(presentation.prepare(requested = true))
    }

    @Test fun anErrorWithNothingLeftToTryNeverReveals() {
        val presentation = CanvasPresentation(listOf("only"))
        presentation.prepare(true)
        assertFalse(presentation.failed())
        assertEquals(CanvasStage.Failed, presentation.stage)
        assertFalse(presentation.showing)
        assertFalse(presentation.firstFrame())
        presentation.park()
        assertEquals(CanvasStage.Failed, presentation.stage)
        assertFalse(presentation.prepare(true))
    }

    @Test fun theAlternateRenditionIsTriedExactlyOnce() {
        val presentation = CanvasPresentation(listOf("primary", "alternate"))
        presentation.prepare(true)
        assertEquals("primary", presentation.url)
        assertTrue(presentation.failed())
        assertEquals("alternate", presentation.url)
        assertEquals(CanvasStage.Preparing, presentation.stage)
        assertFalse(presentation.failed())
        assertEquals(CanvasStage.Failed, presentation.stage)
        assertFalse(presentation.showing)
    }

    @Test fun aFailureAfterRevealHidesTheCanvasAgain() {
        val presentation = CanvasPresentation(listOf("primary", "alternate"))
        presentation.prepare(true)
        presentation.firstFrame()
        presentation.revealed()
        assertTrue(presentation.failed())
        assertFalse(presentation.showing)
        assertTrue(presentation.firstFrame())
        assertTrue(presentation.showing)
    }

    @Test fun aClosedSheetReleasesTheStateButNotAFailure() {
        val presentation = CanvasPresentation(listOf("primary", "alternate"))
        presentation.prepare(true)
        presentation.failed()
        presentation.firstFrame()
        presentation.park()
        assertEquals(CanvasStage.Idle, presentation.stage)
        assertEquals("primary", presentation.url)
        assertTrue(presentation.prepare(true))
    }

    @Test fun aCanvasNeverOutlivesItsTrack() {
        val first = canvas("a")
        assertSame(first, canvasForSurface(first, "a", ambientAllowed = true))
        assertNull(canvasForSurface(first, "b", ambientAllowed = true))
        assertNull(canvasForSurface(null, "a", ambientAllowed = true))
    }

    @Test fun aVerticalCanvasPlaysOnlyItsVerticalRenditionFullScreen() {
        val clip = canvas("a").copy(fallbackUrl = "https://cdn.example.test/alt.mp4", portraitUrl = "https://cdn.example.test/tall.m3u8")
        assertEquals(listOf("https://cdn.example.test/tall.m3u8"), clip.playbackUrls(portrait = true))
        assertEquals(listOf("https://cdn.example.test/a.mp4", "https://cdn.example.test/alt.mp4"), clip.playbackUrls(portrait = false))
        // Without a vertical rendition the cover's clip is used whatever the mode.
        assertEquals(listOf("https://cdn.example.test/a.mp4"), canvas("a").playbackUrls(portrait = true))
    }

    @Test fun aCanvasComesBeforeTheMusicVideo() {
        val found = CanvasLookup(canvas("a"), settled = true)
        assertFalse(musicVideoAllowed(ambientAllowed = true, songsOnly = false, canvas = found))
        assertTrue(musicVideoAllowed(ambientAllowed = true, songsOnly = false, canvas = CanvasLookup(null, settled = true)))
        // Still looking: the video waits instead of starting and being swapped for the Canvas.
        assertFalse(musicVideoAllowed(ambientAllowed = true, songsOnly = false, canvas = CanvasLookup(null, settled = false)))
        // "Songs only" and reduced motion turn videos off.
        assertFalse(musicVideoAllowed(ambientAllowed = true, songsOnly = true, canvas = CanvasLookup(null, settled = true)))
        assertFalse(musicVideoAllowed(ambientAllowed = false, songsOnly = false, canvas = CanvasLookup(null, settled = true)))
    }

    @Test fun withoutAmbientMotionOnlyTheCoverShows() {
        assertNull(canvasForSurface(canvas("a"), "a", ambientAllowed = false))
    }

    @Test fun disabledAnimationsMakeTheRevealInstant() {
        assertTrue(revealSnaps(animationsDisabled = true, target = 1f, surfaceVisibility = 1f))
        assertTrue(revealSnaps(animationsDisabled = true, target = 0f, surfaceVisibility = 1f))
        assertFalse(revealSnaps(animationsDisabled = false, target = 1f, surfaceVisibility = 1f))
        // Not on screen yet: nothing to dissolve, the frame is simply ready when the cover appears.
        assertTrue(revealSnaps(animationsDisabled = false, target = 1f, surfaceVisibility = 0f))
        assertFalse(revealSnaps(animationsDisabled = false, target = 0f, surfaceVisibility = 0.5f))
    }
}
