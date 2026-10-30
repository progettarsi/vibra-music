/*
 * Vibra (2026) — playback attempt identity and terminal error regression tests.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.*
import org.junit.Test

class PlaybackAttemptControllerTest {
    private fun error() = PlaybackFailure(2004, "final")

    @Test fun allIntermediateStagesStaySilentAndKeepOneDeadline() {
        val controller = PlaybackAttemptController()
        val attempt = controller.begin("track-a")
        val stages = mutableListOf<PlaybackStage>()
        repeat(4) { index ->
            val state = controller.advance(attempt.id, "track-a", true, 100L + index)!!
            stages += state.stage
            assertNull(state.error)
            assertTrue(state.isLoading)
            assertEquals(30_100L, state.deadlineMs)
        }
        assertEquals(listOf(PlaybackStage.REFRESHED, PlaybackStage.ANONYMOUS,
            PlaybackStage.INVIDIOUS, PlaybackStage.PIPED), stages)
        assertNull(controller.advance(attempt.id, "track-a", true, 200L))
        val finalError = error()
        controller.fail(attempt.id, "track-a", finalError)
        assertSame(finalError, controller.state.value.error)
        controller.ready(attempt.id, "track-a")
        assertSame(finalError, controller.state.value.error)
    }

    @Test fun anonymousPlaybackDoesNotRetryAnIdenticalAnonymousStage() {
        val controller = PlaybackAttemptController()
        val a = controller.begin("a")
        controller.advance(a.id, "a", false, 0L)
        assertEquals(PlaybackStage.INVIDIOUS, controller.advance(a.id, "a", false, 1L)?.stage)
    }

    @Test fun timeoutCannotBeExtendedByMovingToAnotherProvider() {
        val controller = PlaybackAttemptController()
        val a = controller.begin("a")
        controller.advance(a.id, "a", true, 100L)
        assertNull(controller.advance(a.id, "a", true, 30_100L))
    }

    @Test fun oldFailuresAndSuccessesCannotAffectTheNewTrack() {
        val controller = PlaybackAttemptController()
        val old = controller.begin("a")
        val current = controller.begin("b")
        controller.fail(old.id, "a", error())
        controller.ready(old.id, "a")
        assertNull(controller.advance(old.id, "a", true, 0L))
        assertEquals(current, controller.state.value)
    }

    @Test fun retryingTheSameTrackInvalidatesItsPreviousAttempt() {
        val controller = PlaybackAttemptController()
        val old = controller.begin("a")
        controller.fail(old.id, "a", error())
        val retry = controller.begin("a")
        assertTrue(retry.id > old.id)
        assertEquals(PlaybackStage.DIRECT, retry.stage)
        assertNull(retry.error)
        controller.fail(old.id, "a", error())
        assertEquals(retry, controller.state.value)
    }

    @Test fun dismissalIsStickyAndDoesNotRestartTheChain() {
        val controller = PlaybackAttemptController()
        val a = controller.begin("a")
        controller.fail(a.id, "a", error())
        controller.dismiss()
        controller.ready(a.id, "a")
        controller.fail(a.id, "a", error())
        assertNull(controller.state.value.error)
        assertEquals(PlaybackAttemptPhase.FAILED, controller.state.value.phase)
    }

    @Test fun cancelInvalidatesEveryOutstandingCallback() {
        val controller = PlaybackAttemptController()
        val a = controller.begin("a")
        controller.cancel()
        controller.fail(a.id, "a", error())
        assertEquals(PlaybackAttemptPhase.IDLE, controller.state.value.phase)
        assertNull(controller.state.value.error)
    }
}
