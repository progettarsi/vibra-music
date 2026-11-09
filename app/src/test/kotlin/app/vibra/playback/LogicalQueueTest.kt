/* Vibra (2026) — GPL-3.0. */
package app.vibra.playback

import moe.rukamori.archivetune.models.MediaMetadata
import org.junit.Assert.*
import org.junit.Test

class LogicalQueueTest {
    private fun songs(size: Int) =
        List(size) { MediaMetadata("id$it", "Song $it", emptyList(), 60) }

    @Test
    fun boundedWindowsCoverEveryPositionAndWrapWithoutDuplicatingOccurrences() {
        for (size in listOf(0, 1, 100, 1000, 10000)) {
            val queue = LogicalQueue().apply { reset(songs(size), 0) }
            for (index in
                listOf(0, 1, 19, 20, 99, 100, size / 2, size - 1).filter { it in 0 until size }) {
                queue.select(index)
                for (repeat in listOf(false, true)) {
                    val window = queue.window(repeat)
                    assertEquals(minOf(size, 100), window.size)
                    assertEquals(window.size, window.toSet().size)
                    assertTrue(queue.currentUid in window)
                }
            }
        }
    }

    @Test
    fun adjacentRefillKeepsCurrentOccurrence() {
        val queue = LogicalQueue().apply { reset(songs(10000), 0) }
        for (index in 0 until 10000) {
            queue.select(index)
            val window = queue.window(false)
            assertTrue(queue.currentUid in window)
            assertTrue(window.size <= 100)
        }
    }

    @Test
    fun duplicatesHaveSeparateKeysAndUndoRestoresOnlyTheRemovedOccurrence() {
        val duplicate = songs(1).single()
        val queue = LogicalQueue().apply { reset(listOf(duplicate, duplicate, duplicate), 0) }
        val old = queue.content.order
        assertEquals(3, old.toSet().size)
        val removal = queue.remove(setOf(old[1]))!!
        queue.insert(songs(1), 1)
        val added = queue.content.order[1]
        assertTrue(queue.undo(removal))
        assertEquals(listOf(old[0], old[1], added, old[2]), queue.content.order)
    }

    @Test
    fun undoFromAnotherSourceIsRejected() {
        val queue = LogicalQueue().apply { reset(songs(3), 0) }
        val removal = queue.remove(setOf(queue.content.order[1]))!!
        queue.reset(songs(4), 1)
        assertFalse(queue.undo(removal))
        assertEquals(4, queue.size)
    }

    @Test
    fun shuffleIsGlobalKeepsCurrentAndPreservesPendingUserPriority() {
        val queue =
            LogicalQueue().apply {
                reset(songs(1000), 500)
                insertUser(songs(2), true)
            }
        val current = queue.currentUid
        val pending = queue.content.order.drop(queue.index + 1).take(2)
        val all = queue.content.order.toSet()
        queue.setShuffle(true, kotlin.random.Random(12))
        assertEquals(current, queue.content.order.first())
        assertEquals(pending, queue.content.order.drop(1).take(2))
        assertEquals(all, queue.content.order.toSet())
        queue.setShuffle(false)
        assertEquals(current, queue.currentUid)
        assertEquals(pending, queue.content.order.drop(queue.index + 1).take(2))
    }

    @Test
    fun shuffledDragDoesNotOverwriteCanonicalOrder() {
        val queue =
            LogicalQueue().apply {
                reset(songs(1000), 500)
                setShuffle(true, kotlin.random.Random(1))
            }
        val canonical = queue.content.canonical
        val moved = queue.content.order[800]
        queue.move(800, 1)
        assertEquals(moved, queue.content.order[1])
        assertEquals(canonical, queue.content.canonical)
    }

    @Test
    fun removingCurrentSelectsNextAndRemovingEverythingIsEmpty() {
        val queue = LogicalQueue().apply { reset(songs(3), 1) }
        val next = queue.content.order[2]
        queue.remove(setOf(queue.currentUid!!))
        assertEquals(next, queue.currentUid)
        queue.remove(queue.content.order.toSet())
        assertEquals(0, queue.size)
        assertNull(queue.currentUid)
    }

    @Test
    fun restorePreservesOrderOccurrencesAndCursor() {
        val original =
            LogicalQueue().apply {
                reset(songs(10000), 7777)
                setShuffle(true, kotlin.random.Random(42))
                move(3, 8)
            }
        val restored = LogicalQueue().apply { restore(original.content, original.currentUid) }
        assertEquals(original.content, restored.content)
        assertEquals(original.currentUid, restored.currentUid)
        assertEquals(original.window(true), restored.window(true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun corruptDuplicateOrderCannotBeRestored() {
        val queue = LogicalQueue().apply { reset(songs(2), 0) }
        queue.restore(
            queue.content.copy(order = listOf(queue.content.order[0], queue.content.order[0])),
            null,
        )
    }

    @Test
    fun bulkMovePreservesRelativeOrderAndCurrentOccurrence() {
        val queue = LogicalQueue().apply { reset(songs(10000), 5000) }
        val old = queue.content.order
        val current = queue.currentUid
        queue.moveRange(2000, 8000, 0)
        assertEquals(old.subList(2000, 8000), queue.content.order.take(6000))
        assertEquals(old.toSet(), queue.content.order.toSet())
        assertEquals(current, queue.currentUid)
    }

    @Test
    fun replacingMetadataKeepsTheCurrentOccurrenceButReplacingASongDoesNot() {
        val queue = LogicalQueue().apply { reset(songs(3), 1) }
        val uid = queue.currentUid
        queue.replace(1, 2, listOf(songs(3)[1].copy(title = "Edited")))
        assertEquals(uid, queue.currentUid)
        assertEquals("Edited", queue.rows[1].metadata.title)
        queue.replace(1, 2, listOf(songs(3)[2]))
        assertNotEquals(uid, queue.currentUid)
        assertEquals("id2", queue.rows[1].metadata.id)
    }

    @Test
    fun undoAfterLargeRemovalMergesWithSubsequentEdits() {
        val queue = LogicalQueue().apply { reset(songs(10000), 100) }
        val original = queue.content.order
        val removal = queue.remove(original.subList(1000, 9000).toSet())!!
        queue.insert(songs(1), 2)
        val inserted = queue.content.order[2]
        assertTrue(queue.undo(removal))
        assertEquals(10001, queue.size)
        assertEquals(original, queue.content.order.filterNot { it == inserted })
    }

    @Test
    fun navigationReusesTheSameImmutableRowsSnapshot() {
        val queue = LogicalQueue().apply { reset(songs(10000), 0) }
        val rows = queue.rows
        repeat(1000) { queue.select(it) }
        assertSame(rows, queue.rows)
        queue.move(9999, 9998)
        assertNotSame(rows, queue.rows)
        assertEquals("id9999", rows[9999].metadata.id)
    }

    @Test
    fun undoAfterRemovingEverythingRestoresTheSelectedOccurrence() {
        val queue = LogicalQueue().apply { reset(songs(10000), 7777) }
        val selected = queue.currentUid
        val removal = queue.remove(queue.content.order.toSet())!!
        assertNull(queue.currentUid)
        assertTrue(queue.undo(removal))
        assertEquals(selected, queue.currentUid)
        assertEquals(7777, queue.index)
    }

    @Test
    fun removingCurrentAndEarlierRowsSelectsItsOriginalNextSurvivor() {
        val queue = LogicalQueue().apply { reset(songs(10), 5) }
        val order = queue.content.order
        queue.remove(setOf(order[0], order[3], order[5]))
        assertEquals(order[6], queue.currentUid)
    }

    @Test
    fun restoredFiltersKeepOccurrenceKeysAndSelectAnAllowedSuccessor() {
        val original =
            LogicalQueue().apply {
                reset(songs(10000), 5000)
                setShuffle(true, kotlin.random.Random(4))
            }
        val selected = original.currentUid
        val allowed = original.content.order.drop(1).take(5000).toSet()
        val allowedIds = original.rows.filter { it.uid in allowed }.map { it.metadata.id }.toSet()
        val restored = LogicalQueue().apply { restore(original.content, selected) }
        restored.retain { it.id in allowedIds }
        assertEquals(allowed, restored.content.order.toSet())
        assertEquals(original.content.order[1], restored.currentUid)
        assertTrue(restored.content.shuffled)
    }
}
