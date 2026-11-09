/* Vibra (2026) — GPL-3.0. Derived from ArchiveTune © Rukamori. */
package app.vibra.playback

import java.io.Serializable
import java.util.concurrent.atomic.AtomicLong
import moe.rukamori.archivetune.models.MediaMetadata

data class QueueEntry(val uid: Long, val metadata: MediaMetadata) : Serializable {
    companion object {
        private const val serialVersionUID = 2L
    }
}

data class QueueContent(
    val queueId: Long,
    val revision: Long,
    val canonical: List<QueueEntry>,
    val order: List<Long>,
    val shuffled: Boolean,
) : Serializable {
    companion object {
        private const val serialVersionUID = 2L
    }
}

/** Android-free occurrence model. All published lists are immutable snapshots. */
class LogicalQueue {
    companion object {
        private val ids = AtomicLong(System.currentTimeMillis())
    }

    var content = QueueContent(ids.incrementAndGet(), 0, emptyList(), emptyList(), false)
        private set

    var currentUid: Long? = null
    private var byId = emptyMap<Long, QueueEntry>()
    private var indices = emptyMap<Long, Int>()
    val size
        get() = content.order.size

    val index
        get() = indices[currentUid] ?: 0

    var rows: List<QueueEntry> = emptyList()
        private set

    fun indexOf(uid: Long) = indices[uid] ?: -1

    fun indexSnapshot(): Map<Long, Int> = indices

    fun entry(uid: Long) = byId[uid]

    fun select(index: Int) {
        currentUid = content.order.getOrNull(index)
    }

    fun reset(metadata: List<MediaMetadata>, index: Int) {
        val entries = metadata.map { QueueEntry(ids.incrementAndGet(), it) }
        publish(QueueContent(ids.incrementAndGet(), 0, entries, entries.map { it.uid }, false))
        select(index.coerceIn(0, (size - 1).coerceAtLeast(0)))
    }

    fun restore(content: QueueContent, selectedUid: Long?) {
        require(content.canonical.map { it.uid }.toSet().size == content.canonical.size)
        require(
            content.order.toSet() == content.canonical.map { it.uid }.toSet() &&
                content.order.size == content.canonical.size
        )
        ids.updateAndGet {
            maxOf(it, content.canonical.maxOfOrNull { entry -> entry.uid } ?: 0, content.queueId)
        }
        publish(content)
        currentUid = selectedUid?.takeIf { it in byId } ?: content.order.firstOrNull()
    }

    private fun publish(next: QueueContent) {
        content = next
        byId = next.canonical.associateBy { it.uid }
        indices = next.order.withIndex().associate { it.value to it.index }
        val entries = byId
        rows =
            object : AbstractList<QueueEntry>() {
                override val size
                    get() = next.order.size

                override fun get(index: Int) = entries.getValue(next.order[index])
            }
    }

    fun setShuffle(enabled: Boolean, random: kotlin.random.Random = kotlin.random.Random.Default) {
        if (enabled == content.shuffled || size == 0) return
        val pending =
            content.order.drop(index + 1).takeWhile { byId.getValue(it).metadata.userQueued }
        val pendingSet = pending.toHashSet()
        val rest = content.canonical.map { it.uid }.filterNot { it in pendingSet }
        val order =
            if (enabled) {
                listOfNotNull(currentUid) +
                    pending +
                    rest.filterNot { it == currentUid }.shuffled(random)
            } else {
                val at = rest.indexOf(currentUid).coerceAtLeast(0) + 1
                rest.take(at) + pending + rest.drop(at)
            }
        publish(content.copy(revision = content.revision + 1, order = order, shuffled = enabled))
    }

    fun move(from: Int, to: Int) {
        if (from !in 0 until size || to !in 0 until size || from == to) return
        val order = content.order.toMutableList().apply { add(to, removeAt(from)) }
        val canonical = if (content.shuffled) content.canonical else order.map { byId.getValue(it) }
        publish(content.copy(revision = content.revision + 1, canonical = canonical, order = order))
    }

    fun moveRange(from: Int, until: Int, destination: Int) {
        require(from in 0..until && until <= size)
        val selected = content.order.subList(from, until)
        val remaining = content.order.take(from) + content.order.drop(until)
        val at = destination.coerceIn(0, remaining.size)
        val order = remaining.take(at) + selected + remaining.drop(at)
        val canonical = if (content.shuffled) content.canonical else order.map { byId.getValue(it) }
        publish(content.copy(revision = content.revision + 1, canonical = canonical, order = order))
    }

    fun replace(from: Int, until: Int, metadata: List<MediaMetadata>) {
        require(from in 0..until && until <= size)
        val old = content.order.subList(from, until).map { byId.getValue(it) }
        val selected = currentUid
        val added =
            metadata.mapIndexed { index, data ->
                old.getOrNull(index)?.takeIf { it.metadata.id == data.id }?.copy(metadata = data)
                    ?: QueueEntry(ids.incrementAndGet(), data)
            }
        val replaced = old.map { it.uid }.toHashSet()
        val order = content.order.take(from) + added.map { it.uid } + content.order.drop(until)
        val canonical =
            if (!content.shuffled) {
                val all = byId + added.associateBy { it.uid }
                order.map { all.getValue(it) }
            } else {
                val at =
                    old.firstOrNull()?.let { item ->
                        content.canonical.indexOfFirst { it.uid == item.uid }
                    } ?: content.canonical.size
                val remaining = content.canonical.filterNot { it.uid in replaced }
                remaining.take(at) + added + remaining.drop(at)
            }
        publish(content.copy(revision = content.revision + 1, canonical = canonical, order = order))
        currentUid =
            selected?.takeIf { it in byId }
                ?: order.getOrNull(from.coerceAtMost((size - 1).coerceAtLeast(0)))
    }

    fun moveBefore(uids: List<Long>, anchor: Long?) {
        val selected = uids.filter { it in byId }
        val selectedSet = selected.toHashSet()
        val rest = content.order.filterNot { it in selectedSet }
        val at = anchor?.let { rest.indexOf(it).takeIf { index -> index >= 0 } } ?: rest.size
        val order = rest.take(at) + selected + rest.drop(at)
        val canonical = if (content.shuffled) content.canonical else order.map { byId.getValue(it) }
        publish(content.copy(revision = content.revision + 1, canonical = canonical, order = order))
    }

    fun insert(metadata: List<MediaMetadata>, at: Int, userQueued: Boolean = false) {
        if (metadata.isEmpty()) return
        val added =
            metadata.map {
                QueueEntry(
                    ids.incrementAndGet(),
                    if (userQueued) it.copy(userQueued = true) else it,
                )
            }
        val index = at.coerceIn(0, size)
        val order = content.order.take(index) + added.map { it.uid } + content.order.drop(index)
        val canonicalAt =
            content.order.getOrNull(index)?.let { uid ->
                content.canonical.indexOfFirst { it.uid == uid }
            } ?: content.canonical.size
        val canonical =
            content.canonical.take(canonicalAt) + added + content.canonical.drop(canonicalAt)
        publish(content.copy(revision = content.revision + 1, canonical = canonical, order = order))
        if (currentUid == null) select(0)
    }

    fun insertUser(metadata: List<MediaMetadata>, next: Boolean) {
        val at = if (size == 0) 0 else index + 1 + if (next) 0 else userCount()
        insert(metadata, at, true)
    }

    fun userCount(): Int =
        content.order
            .asSequence()
            .drop(index + 1)
            .takeWhile { byId.getValue(it).metadata.userQueued }
            .count()

    data class Removal(val original: QueueContent, val removed: Set<Long>, val currentUid: Long?)

    fun remove(uids: Set<Long>): Removal? {
        val removed = uids.intersect(byId.keys)
        if (removed.isEmpty()) return null
        val old = content
        val oldIndex = index
        val oldUid = currentUid
        val nextUid =
            old.order.asSequence().drop(oldIndex + 1).firstOrNull { it !in removed }
                ?: old.order.asSequence().take(oldIndex).lastOrNull { it !in removed }
        publish(
            old.copy(
                revision = old.revision + 1,
                canonical = old.canonical.filterNot { it.uid in removed },
                order = old.order.filterNot { it in removed },
            )
        )
        if (currentUid !in byId) currentUid = nextUid
        return Removal(old, removed, oldUid)
    }

    fun retain(keep: (MediaMetadata) -> Boolean) {
        val rejected = rows.filterNot { keep(it.metadata) }.mapTo(mutableSetOf()) { it.uid }
        if (rejected.isNotEmpty()) remove(rejected)
    }

    fun undo(removal: Removal): Boolean {
        if (removal.original.queueId != content.queueId || removal.removed.any { it in byId })
            return false
        fun merge(original: List<Long>, now: List<Long>): List<Long> {
            val survivors = now.toHashSet()
            val after = mutableMapOf<Long?, MutableList<Long>>()
            var preceding: Long? = null
            original.forEach { uid ->
                if (uid in removal.removed) after.getOrPut(preceding) { mutableListOf() }.add(uid)
                else if (uid in survivors) preceding = uid
            }
            return buildList {
                addAll(after[null].orEmpty())
                now.forEach { uid ->
                    add(uid)
                    addAll(after[uid].orEmpty())
                }
            }
        }

        val restored =
            removal.original.canonical.filter { it.uid in removal.removed }.associateBy { it.uid }
        val all = byId + restored
        val canonical =
            merge(removal.original.canonical.map { it.uid }, content.canonical.map { it.uid }).map {
                all.getValue(it)
            }
        publish(
            content.copy(
                revision = content.revision + 1,
                canonical = canonical,
                order = merge(removal.original.order, content.order),
            )
        )
        if (currentUid == null)
            currentUid = removal.currentUid?.takeIf { it in byId } ?: content.order.firstOrNull()
        return true
    }

    /** At most 100 occurrences in traversal order, including wrap only for repeat-all. */
    fun window(repeatAll: Boolean): List<Long> {
        if (size <= 100) return content.order
        return if (repeatAll)
            List(100) { offset -> content.order[Math.floorMod(index - 20 + offset, size)] }
        else {
            val start = (index - 20).coerceIn(0, size - 100)
            content.order.subList(start, start + 100)
        }
    }
}
