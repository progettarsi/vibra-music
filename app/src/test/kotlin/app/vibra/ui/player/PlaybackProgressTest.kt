/*
 * Vibra (2026) — GPL-3.0.
 */

package app.vibra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressTest {
    @Test
    fun fraction_isPositionOverDuration() {
        assertEquals(0.25f, PlaybackProgress(positionMs = 30_000, durationMs = 120_000).fraction, 1e-6f)
    }

    @Test
    fun fraction_isZeroWhenDurationUnknown() {
        assertEquals(0f, PlaybackProgress(positionMs = 5_000, durationMs = 0).fraction, 0f)
    }

    @Test
    fun fraction_isClampedWhenPositionOvershoots() {
        assertEquals(1f, PlaybackProgress(positionMs = 200_000, durationMs = 120_000).fraction, 0f)
    }
}
