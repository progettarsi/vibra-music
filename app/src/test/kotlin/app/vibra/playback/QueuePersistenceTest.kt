/* Vibra (2026) — GPL-3.0. */
package app.vibra.playback

import java.io.File
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.models.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QueuePersistenceTest {
    @get:Rule val temp = TemporaryFolder()

    private fun content(size: Int = 10000): QueueContentV2 {
        val queue =
            LogicalQueue().apply {
                reset(List(size) { MediaMetadata("$it", "Song $it", emptyList(), 60) }, size / 2)
            }
        return QueueContentV2(
            content = queue.content,
            continuation = PersistQueue("Fixture", emptyList(), 0, 0),
        )
    }

    private fun assertContent(expected: QueueContentV2, actual: QueueContentV2) {
        assertEquals(expected.content, actual.content)
        assertEquals(expected.continuation.title, actual.continuation.title)
        assertEquals(
            expected.continuation.queueType.javaClass,
            actual.continuation.queueType.javaClass,
        )
        assertEquals(expected.continuation.queueData, actual.continuation.queueData)
    }

    private fun state(index: Int = 0) = PersistPlayerState(false, 0, false, 1f, 1234, index, 3)

    @Test
    fun fullQueueAndSmallCheckpointRoundTrip() {
        val disk = QueuePersistenceDisk(temp.root)
        val content = content()
        val uid = content.content.order[5000]
        disk.write(content, uid, state(5000))
        val restored = disk.read()!!
        assertContent(content, restored.content)
        assertEquals(uid, restored.checkpoint.currentUid)
        assertEquals(1234L, restored.checkpoint.player!!.currentPosition)
    }

    @Test
    fun rejectedGenerationCannotPublishAnOrphanContentFile() {
        val disk = QueuePersistenceDisk(temp.root)
        val first = content(3)
        disk.write(first, first.content.order[0], state())
        disk.write(content(5), null, state()) { false }
        assertContent(first, disk.read()!!.content)
    }

    @Test
    fun brokenCurrentContentFallsBackToPreviousCommittedRevision() {
        val disk = QueuePersistenceDisk(temp.root)
        val first = content(3)
        val second = content(5)
        disk.write(first, first.content.order[0], state())
        disk.write(second, second.content.order[0], state())
        temp.root
            .listFiles()!!
            .single { it.name.startsWith("queue-content-v2-${second.content.queueId}-") }
            .writeText("broken")
        assertContent(first, disk.read()!!.content)
    }

    @Test
    fun incompleteTempDoesNotReplaceCheckpoint() {
        val disk = QueuePersistenceDisk(temp.root)
        val content = content(3)
        disk.write(content, null, state())
        File(temp.root, "queue-checkpoint-v2.data.tmp").writeText("partial")
        assertContent(content, disk.read()!!.content)
    }

    @Test
    fun clearTombstoneNeverRestoresPreviousOrLegacyQueue() {
        val disk = QueuePersistenceDisk(temp.root)
        disk.write(content(3), null, state())
        File(temp.root, "persistent_queue.data").writeText("legacy")
        disk.clear()
        assertTrue(disk.isCleared())
        assertNull(disk.read())
        assertFalse(File(temp.root, "persistent_queue.data").exists())
    }

    @Test
    fun checkpointsReuseContentRatherThanSerializeItAgain() {
        val disk = QueuePersistenceDisk(temp.root)
        val content = content(3)
        disk.write(content, null, state())
        val file = temp.root.listFiles()!!.single { it.name.startsWith("queue-content-v2-") }
        val bytes = file.readBytes()
        val time = file.lastModified()
        repeat(5) { disk.write(content, content.content.order[it % 3], state(it % 3)) }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(time, file.lastModified())
        assertEquals(1, temp.root.listFiles()!!.count { it.name.startsWith("queue-content-v2-") })
    }

    @Test
    fun serviceWriterFlushesClearAfterPendingLargeSnapshot() = runBlocking {
        val writer = QueuePersistenceWriter.get(temp.root)
        writer.submit(content(), null, state())
        writer.clear()
        assertTrue(writer.flush())
        assertTrue(QueuePersistenceDisk(temp.root).isCleared())
    }

    @Test
    fun aClearFollowedImmediatelyByANewQueueStillRemovesLegacyState() = runBlocking {
        val disk = QueuePersistenceDisk(temp.root)
        disk.write(content(3), null, state())
        File(temp.root, "persistent_queue.data").writeText("legacy")
        val writer = QueuePersistenceWriter.get(temp.root)
        writer.clear()
        val next = content(7)
        writer.submit(next, next.content.order.first(), state())
        assertTrue(writer.flush())
        assertFalse(File(temp.root, "persistent_queue.data").exists())
        assertContent(next, disk.read()!!.content)
        assertEquals(1, temp.root.listFiles()!!.count { it.name.startsWith("queue-content-v2-") })
    }

    @Test
    fun aDamagedContentFileIsRepairedWhenItsRevisionIsSavedAgainAfterRestart() {
        val first = content(3)
        QueuePersistenceDisk(temp.root).write(first, null, state())
        val target = temp.root.listFiles()!!.single { it.name.startsWith("queue-content-v2-") }
        target.writeText("broken")
        val restarted = QueuePersistenceDisk(temp.root)
        restarted.write(first, null, state())
        assertContent(first, restarted.read()!!.content)
    }
}
