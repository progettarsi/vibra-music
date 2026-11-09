/* Vibra (2026) — GPL-3.0. Derived from ArchiveTune © Rukamori. */
package app.vibra.playback

import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.models.PersistPlayerState
import moe.rukamori.archivetune.models.PersistQueue

data class QueueContentV2(
    val version: Int = 2,
    val content: QueueContent,
    val continuation: PersistQueue,
) : Serializable {
    companion object {
        private const val serialVersionUID = 2L
    }
}

data class QueueCheckpointV2(
    val version: Int = 2,
    val file: String?,
    val currentUid: Long?,
    val player: PersistPlayerState?,
) : Serializable {
    companion object {
        private const val serialVersionUID = 2L
    }
}

data class RestoredPlayback(val content: QueueContentV2, val checkpoint: QueueCheckpointV2)

/** Disk protocol: publish the small pointer only after the immutable content is durable. */
class QueuePersistenceDisk(private val directory: File) {
    private val durableContent = mutableSetOf<String>()
    private val checkpoint
        get() = File(directory, "queue-checkpoint-v2.data")

    private val previous
        get() = File(directory, "queue-checkpoint-v2.previous")

    fun exists() = checkpoint.isFile

    fun isCleared(): Boolean =
        runCatching {
                (readObject(checkpoint) as QueueCheckpointV2).let {
                    it.version == 2 && it.file == null
                }
            }
            .getOrDefault(false)

    fun read(): RestoredPlayback? {
        if (isCleared()) return null
        for (pointer in listOf(checkpoint, previous)) {
            val restored =
                runCatching {
                        val state = readObject(pointer) as QueueCheckpointV2
                        require(state.version == 2)
                        val name = state.file ?: return@runCatching null
                        require(name.startsWith("queue-content-v2-") && File(name).name == name)
                        val content = readObject(File(directory, name)) as QueueContentV2
                        require(content.version == 2)
                        val ids = content.content.canonical.map { it.uid }.toHashSet()
                        require(
                            ids.size == content.content.canonical.size &&
                                content.content.order.size == ids.size &&
                                content.content.order.toSet() == ids
                        )
                        require(state.currentUid == null || state.currentUid in ids)
                        RestoredPlayback(content, state)
                    }
                    .getOrNull()
            if (restored != null) return restored
        }
        return null
    }

    fun write(
        content: QueueContentV2,
        uid: Long?,
        player: PersistPlayerState,
        mayCommit: () -> Boolean = { true },
    ) {
        directory.mkdirs()
        val name =
            "queue-content-v2-${content.content.queueId}-${content.content.revision.toString().padStart(20, '0')}.data"
        val target = File(directory, name)
        if (name !in durableContent) {
            val reusable =
                target.isFile &&
                    runCatching {
                            val stored = readObject(target) as QueueContentV2
                            stored.version == 2 &&
                                stored.content.queueId == content.content.queueId &&
                                stored.content.revision == content.content.revision
                        }
                        .getOrDefault(false)
            if (!reusable) atomicWrite(target, content)
            durableContent += name
        }
        if (!mayCommit()) return
        val old = runCatching { readObject(checkpoint) as QueueCheckpointV2 }.getOrNull()
        if (old != null && old.file != name) atomicWrite(previous, old)
        atomicWrite(checkpoint, QueueCheckpointV2(file = name, currentUid = uid, player = player))
        val previousName =
            runCatching { (readObject(previous) as QueueCheckpointV2).file }.getOrNull()
        val keep = setOfNotNull(name, previousName)
        durableContent.retainAll(keep)
        directory
            .listFiles { file ->
                file.name.startsWith("queue-content-v2-") && file.extension == "data"
            }
            .orEmpty()
            .filter { it.name !in keep }
            .forEach { it.delete() }
    }

    fun clear() {
        durableContent.clear()
        directory.mkdirs()
        atomicWrite(checkpoint, QueueCheckpointV2(file = null, currentUid = null, player = null))
        listOf("persistent_queue.data", "persistent_player_state.data", "persistent_automix.data")
            .forEach { File(directory, it).delete() }
        directory
            .listFiles { file -> file.name.startsWith("queue-content-v2-") }
            .orEmpty()
            .forEach { it.delete() }
    }

    private fun readObject(file: File): Any =
        file.inputStream().buffered().use { stream ->
            ObjectInputStream(stream).use { it.readObject() }
        }

    private fun atomicWrite(file: File, value: Serializable) {
        val temp = File(file.path + ".tmp")
        FileOutputStream(temp).use { stream ->
            val output = ObjectOutputStream(BufferedOutputStream(stream))
            output.writeObject(value)
            output.flush()
            stream.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/** Survives a service instance, not the application process; never retains a Player or Context. */
class QueuePersistenceWriter private constructor(directory: File) {
    private val disk = QueuePersistenceDisk(directory)
    private val epoch = AtomicLong()
    private val submitted = AtomicLong()
    private var clearedEpoch = 0L
    private val completed = MutableStateFlow(0L)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class Request(
        val sequence: Long,
        val epoch: Long,
        val content: QueueContentV2?,
        val uid: Long?,
        val state: PersistPlayerState?,
    )

    private val requests = Channel<Request>(Channel.CONFLATED)
    @Volatile
    var lastFailure: Throwable? = null
        private set

    init {
        scope.launch {
            for (first in requests) {
                var latest = first
                if (latest.content != null) {
                    delay(250)
                    while (true) latest = requests.tryReceive().getOrNull() ?: break
                }
                try {
                    if (latest.epoch == epoch.get()) {
                        if (latest.epoch > clearedEpoch) {
                            disk.clear()
                            clearedEpoch = latest.epoch
                        }
                        if (latest.content != null)
                            disk.write(latest.content, latest.uid, latest.state!!) {
                                latest.epoch == epoch.get()
                            }
                    }
                    lastFailure = null
                } catch (error: Exception) {
                    lastFailure = error
                }
                completed.value = maxOf(completed.value, latest.sequence)
            }
        }
    }

    fun submit(content: QueueContentV2, uid: Long?, state: PersistPlayerState) {
        requests.trySend(Request(submitted.incrementAndGet(), epoch.get(), content, uid, state))
    }

    fun clear() {
        val next = epoch.incrementAndGet()
        requests.trySend(Request(submitted.incrementAndGet(), next, null, null, null))
    }

    suspend fun flush(): Boolean {
        val target = submitted.get()
        return withTimeoutOrNull(2_000) {
            completed.first { it >= target }
            lastFailure == null
        } ?: false
    }

    companion object {
        private val writers = mutableMapOf<String, QueuePersistenceWriter>()

        @Synchronized
        fun get(directory: File) =
            writers.getOrPut(directory.absolutePath) { QueuePersistenceWriter(directory) }
    }
}
