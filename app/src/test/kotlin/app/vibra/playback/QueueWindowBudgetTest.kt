/* Vibra (2026) - GPL-3.0. */
package app.vibra.playback

import org.junit.Assert.*
import org.junit.Test

class QueueWindowBudgetTest {
    @Test
    fun fullSourceNeverExceedsItemCap() {
        val range = boundedWindowRange(10000, 5000, 100, 48000, 2000) { 100 }
        assertEquals(100, range.count())
        assertEquals(5000, range.first)
    }

    @Test
    fun payloadBudgetIncludesOverheadAndCurrentOccurrence() {
        val range = boundedWindowRange(100, 50, 100, 48000, 2000) { 5000 }
        assertEquals(50..58, range)
        assertTrue(2000 + range.count() * 5000 <= 48000)
    }

    @Test
    fun endOfSourceUsesPrecedingItemsWithinTheBudget() {
        assertEquals(91..99, boundedWindowRange(100, 99, 100, 48000, 2000) { 5000 })
    }

    @Test
    fun aLargeNeighbourIsNotSkippedOrAddedPastTheBudget() {
        val range = boundedWindowRange(10, 5, 100, 1000, 100) { if (it == 6) 900 else 100 }
        assertEquals(0..5, range)
    }

    @Test
    fun individuallyOversizedCurrentItemRetainsExistingCastFallback() {
        assertEquals(5..5, boundedWindowRange(10, 5, 100, 1000, 100) { 2000 })
    }

    @Test
    fun emptySourceHasNoWindow() {
        assertTrue(boundedWindowRange(0, 0, 100, 1000, 100) { error("No item") }.isEmpty())
    }
}
