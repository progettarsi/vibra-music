/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.lyrics

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.viewmodels.LyricsRenderError
import moe.rukamori.archivetune.viewmodels.LyricsRenderScreenState
import moe.rukamori.archivetune.viewmodels.LyricsRenderSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsEntranceTest {
    @Test
    fun fastResultOrClosedPaneCancelsTheDelayedSkeleton() = runBlocking {
        val entrance = LyricsEntrance(mutableStateOf(false))
        val pending = launch(start = CoroutineStart.UNDISPATCHED) { entrance.showAfterDelay() }
        assertEquals(250L, LyricsSkeletonDelayMillis)
        assertFalse(entrance.shown)
        pending.cancelAndJoin()
        assertFalse(entrance.shown)
    }

    @Test
    fun reducedMotionShowsStaticBarsWithoutRequestingVerseReveal() = runBlocking {
        val entrance = LyricsEntrance(mutableStateOf(true))
        entrance.showAfterDelay()
        assertTrue(entrance.shown)
        assertTrue(entrance.intro.value > 1000f)
        assertFalse(entrance.shouldReveal)
    }

    @Test
    fun ranksOnlyInitiallyVisibleRowsFromTopToBottom() {
        val rows = listOf(
            LyricViewportRow(25, 29, 40),
            LyricViewportRow(22, -80, 50),
            LyricViewportRow(24, -21, 50),
            LyricViewportRow(26, 69, 30),
            LyricViewportRow(27, 99, 60),
        )
        assertEquals(mapOf(24 to 0, 25 to 1, 26 to 2), lyricsVisibleRanks(rows, -20, 99))
    }

    @Test
    fun staggeredPhasesStartAndEndIndependently() {
        assertEquals(0f, lyricsPhase(61f, 62, 240), 0f)
        assertEquals(.5f, lyricsPhase(182f, 62, 240), 0f)
        assertEquals(1f, lyricsPhase(303f, 62, 240), 0f)
        assertEquals(0f, lyricsPhase(26f, 27, 260), 0f)
    }

    @Test
    fun oldTrackEmptyOrErrorNeverReplacesTheNewTrackLoadingState() {
        val states = listOf(
            LyricsRenderScreenState.Empty,
            LyricsRenderScreenState.Error(LyricsRenderError.INVALID_LYRICS),
        )
        for (state in states) {
            val snapshot = LyricsRenderSnapshot("previous", state)
            assertEquals(state, snapshot.forMedia("previous"))
            assertEquals(LyricsRenderScreenState.Loading, snapshot.forMedia("next"))
        }
    }
}
