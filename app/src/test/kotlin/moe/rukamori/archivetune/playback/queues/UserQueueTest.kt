/*
 * Vibra (2026) — GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package moe.rukamori.archivetune.playback.queues

import org.junit.Assert.assertEquals
import org.junit.Test

class UserQueueTest {
    @Test fun emptyRegionInsertsBeforeContext() {
        assertEquals(UserQueueInsertion(1, listOf(0, 1, 2, 3)),
            userQueueInsertion(listOf(0, 1, 2), 0, listOf(false, false, false), 1, false, false))
        assertEquals(0, userQueueRegionSize(emptyList(), -1, emptyList()))
    }
    @Test fun severalAddsThenPlayNext() {
        val flags = listOf(false, true, true, false)
        assertEquals(2, userQueueRegionSize(listOf(0, 1, 2, 3), 0, flags))
        assertEquals(3, userQueueInsertion(listOf(0, 1, 2, 3), 0, flags, 2, false, false).index)
        assertEquals(1, userQueueInsertion(listOf(0, 1, 2, 3), 0, flags, 2, true, false).index)
        assertEquals(1, userQueueRegionSize(listOf(0, 1, 2, 3), 1, flags))
    }
    @Test fun shuffleKeepsRegionAndContextOrder() {
        val order = listOf(3, 0, 4, 1, 2)
        val flags = listOf(false, true, false, false, true)
        assertEquals(2, userQueueRegionSize(order, 0, flags))
        assertEquals(listOf(5, 0, 6, 3, 1, 2, 4), userQueueInsertion(order, 0, flags, 2, false, true).playOrder)
        assertEquals(listOf(5, 0, 1, 2, 6, 3, 4), userQueueInsertion(order, 0, flags, 2, true, true).playOrder)
    }
    @Test fun additionsAreFifoAndPlayNextPrecedesThem() {
        val labels = mutableListOf("current", "context")
        var order = listOf(0, 1)
        fun add(label: String, next: Boolean) {
            val flags = labels.map { it.startsWith("user") }
            val insertion = userQueueInsertion(order, labels.indexOf("current"), flags, 1, next, true)
            labels.add(insertion.index, label)
            order = insertion.playOrder
        }
        add("user A", false)
        add("user B", false)
        add("user next", true)
        assertEquals(listOf("current", "user next", "user A", "user B", "context"), order.map { labels[it] })
    }
    @Test fun serializedMetadataRetainsUserFlag() {
        val metadata = moe.rukamori.archivetune.models.MediaMetadata("id", "title", emptyList(), 1, userQueued = true)
        val bytes = java.io.ByteArrayOutputStream()
        java.io.ObjectOutputStream(bytes).use { it.writeObject(metadata) }
        val restored = java.io.ObjectInputStream(java.io.ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
        assertEquals(metadata, restored)
    }
    @Test fun stopsAtFirstContextItemAndNeverWraps() {
        assertEquals(0, userQueueRegionSize(listOf(2, 0, 1), 0, listOf(false, false, true)))
        assertEquals(0, userQueueRegionSize(listOf(2, 0, 1), 1, listOf(true, true, true)))
        assertEquals(UserQueueInsertion(0, listOf(0, 1)), userQueueInsertion(emptyList(), -1, emptyList(), 2, false, false))
    }
}
