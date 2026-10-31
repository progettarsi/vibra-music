package app.vibra.playback
import org.junit.Assert.*
import org.junit.Test

class DownloadRecoveryPlanTest {
    @Test fun anonymousDownloadTriesEachStageOnceAndStops() {
        val plan = DownloadRecoveryPlan(false)
        val seen = mutableListOf(plan.stage)
        while (plan.advance()) seen += plan.stage
        assertEquals(listOf(PlaybackStage.DIRECT, PlaybackStage.REFRESHED, PlaybackStage.INVIDIOUS, PlaybackStage.PIPED), seen)
        assertTrue(plan.exhausted)
        assertFalse(plan.advance())
    }
    @Test fun signedInDownloadHasOneAnonymousRetryAndIndependentState() {
        val first = DownloadRecoveryPlan(true)
        val second = DownloadRecoveryPlan(true)
        assertTrue(first.advance()); assertTrue(first.advance())
        assertEquals(PlaybackStage.ANONYMOUS, first.stage)
        assertEquals(PlaybackStage.DIRECT, second.stage)
        assertFalse(second.exhausted)
    }
}
