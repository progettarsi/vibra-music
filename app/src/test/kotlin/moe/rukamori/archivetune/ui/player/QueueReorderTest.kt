/*
 * Vibra (2026) — tests for the queue reorder semantics shared by the legacy and the
 * Vibra queue (QueueReorder.kt). GPL-3.0.
 */

package moe.rukamori.archivetune.ui.player

import androidx.media3.common.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueReorderTest {
    private fun windows(vararg uids: String): List<Timeline.Window> =
        uids.map { uid -> Timeline.Window().apply { this.uid = uid } }

    @Test
    fun dropAtTop_resolvesToStart() {
        val queue = windows("a", "b", "c")
        val destination = queueDragDestinationFor(queue, toIndex = 0)

        assertEquals(QueueDragDestination.Start, destination)
        assertEquals(0, destination.resolveIndex(queue, sourceIndex = 2))
    }

    @Test
    fun movingDown_landsOnTheAnchorSlot() {
        // Local list after dragging "a" below "c": b, c, a → anchor is "c".
        val reordered = windows("b", "c", "a")
        val destination = queueDragDestinationFor(reordered, toIndex = 2)
        val playerQueue = windows("a", "b", "c")

        assertEquals(QueueDragDestination.After("c"), destination)
        assertEquals(2, destination.resolveIndex(playerQueue, sourceIndex = 0))
    }

    @Test
    fun movingUp_landsAfterTheAnchor() {
        // Local list after dragging "c" below "a": a, c, b → anchor is "a".
        val reordered = windows("a", "c", "b")
        val destination = queueDragDestinationFor(reordered, toIndex = 1)
        val playerQueue = windows("a", "b", "c")

        assertEquals(1, destination.resolveIndex(playerQueue, sourceIndex = 2))
    }

    @Test
    fun anchorRemovedMeanwhile_resolvesToNull() {
        val destination = QueueDragDestination.After("gone")

        assertNull(destination.resolveIndex(windows("a", "b"), sourceIndex = 0))
    }

    @Test
    fun invalidSource_resolvesToNull() {
        assertNull(QueueDragDestination.After("a").resolveIndex(windows("a", "b"), sourceIndex = -1))
    }
}
