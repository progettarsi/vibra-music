/*
 * Vibra (2026) — GPL-3.0.
 */

package app.vibra.ui.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTimingTest {
    private val starts = intArrayOf(1_000, 4_000, 7_000, 9_500)

    @Test
    fun lineAt_isMinusOneBeforeTheFirstLine() {
        assertEquals(-1, lineAt(starts, 0))
        assertEquals(-1, lineAt(starts, 999))
    }

    @Test
    fun lineAt_isTheLastStartedLine() {
        assertEquals(0, lineAt(starts, 1_000))
        assertEquals(1, lineAt(starts, 6_999))
        assertEquals(3, lineAt(starts, 60_000))
    }

    @Test
    fun followLine_movesForward() {
        assertEquals(2, followLine(starts, current = 1, position = 7_010))
    }

    @Test
    fun followLine_ignoresAClockStepJustBeforeTheActiveLine() {
        // The clock re-aligned 40 ms back: the line that has just lit up stays lit.
        assertEquals(2, followLine(starts, current = 2, position = 6_960))
    }

    @Test
    fun followLine_followsARealSeekBack() {
        assertEquals(0, followLine(starts, current = 2, position = 2_000))
        assertEquals(-1, followLine(starts, current = 1, position = 0))
    }

    @Test
    fun wordFill_easesFromZeroToOne() {
        assertEquals(0f, wordFill(1_000, 1_400, 900), 0f)
        assertEquals(0.5f, wordFill(1_000, 1_400, 1_200), 1e-6f)
        assertEquals(1f, wordFill(1_000, 1_400, 1_500), 0f)
    }

    @Test
    fun wordFill_handlesZeroLengthWords() {
        assertEquals(1f, wordFill(1_000, 1_000, 1_001), 0f)
    }

    @Test
    fun activeLine_keepsTheBrightnessOfTheNextLine() {
        // No flash when a line becomes active: its unsung words stay where the line was.
        assertEquals(lineDim(1), lineDim(0), 0f)
        assertTrue(lineDim(2) < lineDim(1))
        assertTrue(lineDim(3) < lineDim(2))
    }

    @Test
    fun distance_treatsTheFirstLineAsNextBeforeTheSongStarts() {
        assertEquals(1, lineDistance(index = 0, active = -1))
        assertEquals(3, lineDistance(index = 2, active = -1))
        assertEquals(2, lineDistance(index = 1, active = 3))
    }

    @Test
    fun stagger_forwardDelaysOnlyTheLinesBelowTheTarget() {
        assertEquals(0L, staggerMillis(index = 3, target = 5, forward = true))
        assertEquals(0L, staggerMillis(index = 5, target = 5, forward = true))
        assertTrue(staggerMillis(index = 6, target = 5, forward = true) > 0L)
        assertTrue(staggerMillis(index = 7, target = 5, forward = true) > staggerMillis(index = 6, target = 5, forward = true))
        assertEquals(MaxStaggerMillis, staggerMillis(index = 50, target = 5, forward = true))
    }

    @Test
    fun stagger_backDelaysOnlyTheLinesAboveTheTarget() {
        assertEquals(0L, staggerMillis(index = 6, target = 5, forward = false))
        assertTrue(staggerMillis(index = 4, target = 5, forward = false) > 0L)
        assertEquals(MaxStaggerMillis, staggerMillis(index = 0, target = 40, forward = false))
    }
}
