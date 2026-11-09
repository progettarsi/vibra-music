/* Vibra (2026) — GPL-3.0. Derived from ArchiveTune © Rukamori. */
package app.vibra.playback

import android.os.Bundle
import android.util.LruCache
import androidx.media3.common.*
import androidx.media3.common.FlagSet
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata

/** Full logical playlist for the engine; the wrapped Cast/Exo player only owns <=100 items. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ProgressivePlayer(
    private val backend: Player,
    private val windowPolicy: (List<MediaItem>, Int) -> List<MediaItem> = { items, _ -> items },
) : ForwardingPlayer(backend) {
    var queue = LogicalQueue()
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val edits = Mutex()
    private val cache = LruCache<Long, MediaItem>(200)
    private val listeners = linkedMapOf<Player.Listener, Player.Listener>()
    private var window = emptyList<Long>()
    private var refilling = false

    private var backendUpdateJob: Job? = null
    private var pendingPosition: Long? = null
    private var pendingSelection = false
    private var expectedBackendUid: Long? = null
    private var pendingForce = false
    private var pendingPrepare = false
    private var source: Any? = null
    private var sourceGeneration = 0L
    private var sourceJob: Job? = null
    var isPreparingSource = false
        private set

    private var logicalRepeat = Player.REPEAT_MODE_OFF
    private var timeline: Timeline = Timeline.EMPTY
    private var windowsView: List<Timeline.Window> = emptyList()
    private var released = false
    private var requestedPlayWhenReady = backend.playWhenReady

    init {
        backend.addListener(
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                    if (!isPreparingSource && !pendingSelection) requestedPlayWhenReady = ready
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    if (refilling || released || queue.size == 0) return
                    val expected = expectedBackendUid
                    if (expected != null && explicitUid(backend.currentMediaItem) != expected)
                        return
                    expectedBackendUid = null
                    val actual =
                        List(backend.mediaItemCount) { index ->
                            explicitUid(backend.getMediaItemAt(index))
                        }
                    if (
                        actual.isNotEmpty() && actual.all { it != null && queue.indexOf(it) >= 0 }
                    ) {
                        window = actual.filterNotNull()
                        scheduleBackendUpdate()
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val incoming = uid(mediaItem)
                    val actual = explicitUid(backend.currentMediaItem)
                    if (actual != null && incoming != actual) return
                    if (expectedBackendUid != null && incoming != expectedBackendUid) return
                    expectedBackendUid = null
                    if (!pendingSelection)
                        incoming?.takeIf { queue.indexOf(it) >= 0 }?.let { queue.currentUid = it }
                    if (!refilling && queue.size > 100) scheduleBackendUpdate()
                }
            }
        )
    }

    private fun explicitUid(item: MediaItem?): Long? =
        item?.mediaMetadata?.extras?.getLong(ENTRY_UID, -1L)?.takeIf { it != -1L }

    private fun uid(item: MediaItem?): Long? =
        explicitUid(item) ?: window.getOrNull(backend.currentMediaItemIndex)

    private fun item(entry: QueueEntry): MediaItem =
        cache.get(entry.uid)
            ?: entry.metadata.toMediaItem().let { original ->
                original
                    .buildUpon()
                    .setMediaMetadata(
                        original.mediaMetadata
                            .buildUpon()
                            .setExtras(
                                Bundle(original.mediaMetadata.extras ?: Bundle()).apply {
                                    putLong(ENTRY_UID, entry.uid)
                                }
                            )
                            .build()
                    )
                    .build()
                    .also { cache.put(entry.uid, it) }
            }

    val metadataSnapshot: List<MediaMetadata>
        get() =
            object : AbstractList<MediaMetadata>() {
                private val rows = queue.rows
                override val size
                    get() = rows.size

                override fun get(index: Int) = rows[index].metadata
            }

    /** Lazy occurrence windows: callers can address a distant row without allocating all rows. */
    fun queueWindows(): List<Timeline.Window> = windowsView

    private fun publish(position: Long? = null) {
        val rows = queue.rows
        val snapshotIndices = queue.indexSnapshot()
        timeline =
            if (rows.isEmpty()) Timeline.EMPTY
            else
                object : Timeline() {
                    private val indices = snapshotIndices

                    override fun getWindowCount() = rows.size

                    override fun getPeriodCount() = rows.size

                    override fun getWindow(
                        index: Int,
                        target: Window,
                        defaultPositionProjectionUs: Long,
                    ): Window {
                        val entry = rows[index]
                        val duration =
                            entry.metadata.duration.takeIf { it > 0 }?.times(1_000_000L)
                                ?: C.TIME_UNSET
                        return target.set(
                            entry.uid,
                            item(entry),
                            null,
                            C.TIME_UNSET,
                            C.TIME_UNSET,
                            C.TIME_UNSET,
                            true,
                            false,
                            null,
                            0L,
                            duration,
                            index,
                            index,
                            0L,
                        )
                    }

                    override fun getPeriod(index: Int, target: Period, setIds: Boolean): Period =
                        target.set(
                            if (setIds) rows[index].uid else null,
                            if (setIds) rows[index].uid else null,
                            index,
                            rows[index].metadata.duration.takeIf { it > 0 }?.times(1_000_000L)
                                ?: C.TIME_UNSET,
                            0L,
                        )

                    override fun getIndexOfPeriod(uid: Any) = indices[uid] ?: C.INDEX_UNSET

                    override fun getUidOfPeriod(index: Int): Any = rows[index].uid
                }
        val snapshot = timeline
        windowsView =
            object : AbstractList<Timeline.Window>() {
                override val size
                    get() = snapshot.windowCount

                override fun get(index: Int) = snapshot.getWindow(index, Timeline.Window())
            }
        scheduleBackendUpdate(position, force = true)
        listeners.keys.toList().forEach {
            it.onTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        }
        events(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_AVAILABLE_COMMANDS_CHANGED)
    }

    private fun events(vararg flags: Int) {
        val events = Player.Events(FlagSet.Builder().apply { flags.forEach { add(it) } }.build())
        listeners.keys.toList().forEach { it.onEvents(this, events) }
    }

    private fun refill(force: Boolean = false, position: Long? = null) {
        if (queue.size == 0) {
            window = emptyList()
            backend.clearMediaItems()
            return
        }
        val selected = queue.currentUid ?: return
        val at = window.indexOf(selected)
        if (!force && at in 10 until window.size - 10) return
        val candidates = queue.window(logicalRepeat == Player.REPEAT_MODE_ALL)
        val prepared =
            windowPolicy(candidates.map { item(queue.entry(it)!!) }, candidates.indexOf(selected))
        val next = prepared.map { explicitUid(it)!! }
        if (
            next == window &&
                position == null &&
                backend.mediaItemCount == prepared.size &&
                prepared.indices.all { backend.getMediaItemAt(it) == prepared[it] }
        )
            return
        val existingUid = uid(backend.currentMediaItem)
        window = next
        val current = next.indexOf(selected)
        refilling = true
        try {
            backend.shuffleModeEnabled = false
            backend.repeatMode =
                if (logicalRepeat == Player.REPEAT_MODE_ALL && queue.size > 100)
                    Player.REPEAT_MODE_OFF
                else logicalRepeat
            if (existingUid == selected && backend.currentMediaItem != null && position == null) {
                val physical = backend.currentMediaItemIndex
                if (backend.currentMediaItem != prepared[current])
                    backend.replaceMediaItem(physical, prepared[current])
                backend.removeMediaItems(physical + 1, backend.mediaItemCount)
                backend.removeMediaItems(0, physical)
                backend.addMediaItems(0, prepared.take(current))
                backend.addMediaItems(prepared.drop(current + 1))
            } else {
                backend.setMediaItems(prepared, current, position ?: 0L)
            }
            check(backend.mediaItemCount <= 100)
        } finally {
            refilling = false
        }
    }

    suspend fun load(
        items: List<MediaItem>,
        startIndex: Int,
        position: Long,
        playWhenReady: Boolean? = null,
        shuffle: Boolean = false,
        reservedGeneration: Long? = null,
    ) {
        val generation = reservedGeneration ?: ++sourceGeneration
        if (generation != sourceGeneration) return
        playWhenReady?.let { requestedPlayWhenReady = it }
        val identity = (items as? MetadataMediaItems)?.sourceIdentity
        if (identity != null && identity === source && !queue.content.shuffled) {
            isPreparingSource = false
            seekTo(startIndex, position)
            return
        }
        cancelPendingSeek()
        isPreparingSource = true
        try {
            val prepared =
                withContext(Dispatchers.Default) {
                    val metadata =
                        List(items.size) { i ->
                            ensureActive()
                            items.metadataAt(i) ?: error("Queue item has no metadata")
                        }
                    LogicalQueue().apply {
                        reset(metadata, startIndex)
                        if (shuffle) setShuffle(true)
                    }
                }
            if (generation != sourceGeneration || released) return
            queue = prepared
            source = identity
            cache.evictAll()
            publish(position)
            scheduleBackendUpdate(prepare = true)
        } finally {
            if (generation == sourceGeneration) isPreparingSource = false
        }
    }

    /** Retain the already playing preload occurrence while preparing its full source off Main. */
    suspend fun expandAroundCurrent(items: List<MediaItem>, selectedIndex: Int) {
        val generation = ++sourceGeneration
        val original = queue.content
        val selected = queue.currentUid
        val mediaId = selected?.let(queue::entry)?.metadata?.id
        isPreparingSource = true
        try {
            val prepared =
                withContext(Dispatchers.Default) {
                    val metadata =
                        List(items.size) { i ->
                            ensureActive()
                            items.metadataAt(i)!!
                        }
                    val at = selectedIndex.coerceIn(0, metadata.size)
                    LogicalQueue().apply {
                        restore(original, selected)
                        insert(metadata.take(at).filterNot { it.id == mediaId }, 0)
                        insert(metadata.drop(at).filterNot { it.id == mediaId }, size)
                    }
                }
            if (generation != sourceGeneration || released) return
            queue = prepared
            source = null
            publish()
        } finally {
            if (generation == sourceGeneration) isPreparingSource = false
        }
    }

    suspend fun restore(
        content: QueueContent,
        selectedUid: Long?,
        position: Long,
        keep: (MediaMetadata) -> Boolean = { true },
    ): Boolean {
        sourceJob?.cancel()
        val generation = ++sourceGeneration
        cancelPendingSeek()
        isPreparingSource = true
        try {
            val restored =
                withContext(Dispatchers.Default) {
                    LogicalQueue().apply {
                        restore(content, selectedUid)
                        retain(keep)
                    }
                }
            if (generation != sourceGeneration || released) return false
            queue = restored
            cache.evictAll()
            source = null
            val retainedCurrent = queue.currentUid == selectedUid
            publish(if (retainedCurrent) position else 0L)
            scheduleBackendUpdate(prepare = true)
            return retainedCurrent
        } finally {
            if (generation == sourceGeneration) isPreparingSource = false
        }
    }

    override fun setMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        val generation = ++sourceGeneration
        sourceJob?.cancel()
        // Main.immediate can enqueue a launch while its unconfined loop is active.
        // Reserve the generation and loading state before returning to the caller.
        isPreparingSource = true
        val shuffle = shuffleModeEnabled
        sourceJob =
            scope.launch {
                load(
                    items,
                    startIndex,
                    startPositionMs.takeUnless { it == C.TIME_UNSET } ?: 0L,
                    shuffle = shuffle,
                    reservedGeneration = generation,
                )
            }
    }

    override fun setMediaItems(items: List<MediaItem>) = setMediaItems(items, 0, 0L)

    override fun setMediaItems(items: List<MediaItem>, resetPosition: Boolean) =
        setMediaItems(
            items,
            if (resetPosition) 0 else queue.index,
            if (resetPosition) 0 else currentPosition,
        )

    override fun setMediaItem(item: MediaItem) = setMediaItems(listOf(item))

    override fun setMediaItem(item: MediaItem, startPositionMs: Long) =
        setMediaItems(listOf(item), 0, startPositionMs)

    override fun setMediaItem(item: MediaItem, resetPosition: Boolean) =
        setMediaItems(listOf(item), resetPosition)

    override fun setPlayWhenReady(value: Boolean) {
        requestedPlayWhenReady = value
        if (!isPreparingSource && !pendingSelection) backend.playWhenReady = value
    }

    override fun getPlayWhenReady() =
        if (isPreparingSource || pendingSelection) requestedPlayWhenReady else backend.playWhenReady

    override fun play() = setPlayWhenReady(true)

    override fun pause() = setPlayWhenReady(false)

    override fun prepare() {
        if (!isPreparingSource) {
            if (pendingSelection) scheduleBackendUpdate(prepare = true) else backend.prepare()
        }
    }

    private fun edit(change: suspend () -> Unit) {
        val generation = sourceGeneration
        scope.launch {
            edits.withLock {
                while (isPreparingSource && generation == sourceGeneration) delay(5)
                if (generation != sourceGeneration) return@withLock
                change()
                source = null
                publish()
            }
        }
    }

    private suspend fun editMetadata(items: List<MediaItem>): List<MediaMetadata> {
        val generation = sourceGeneration
        val metadata =
            withContext(Dispatchers.Default) { List(items.size) { items.metadataAt(it)!! } }
        if (generation != sourceGeneration) throw CancellationException("Queue source replaced")
        return metadata
    }

    fun insertUser(items: List<MediaItem>, next: Boolean) = edit {
        val metadata = editMetadata(items)
        queue.insertUser(metadata, next)
    }

    override fun addMediaItems(index: Int, items: List<MediaItem>) = edit {
        val metadata = editMetadata(items)
        queue.insert(metadata, index)
    }

    override fun addMediaItems(items: List<MediaItem>) = addMediaItems(queue.size, items)

    override fun addMediaItem(item: MediaItem) = addMediaItems(listOf(item))

    override fun addMediaItem(index: Int, item: MediaItem) = addMediaItems(index, listOf(item))

    override fun moveMediaItem(fromIndex: Int, toIndex: Int) {
        queue.move(fromIndex, toIndex)
        source = null
        publish()
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        queue.moveRange(fromIndex, toIndex, newIndex)
        source = null
        publish()
    }

    override fun replaceMediaItem(index: Int, item: MediaItem) =
        replaceMediaItems(index, index + 1, listOf(item))

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, items: List<MediaItem>) = edit {
        val metadata = editMetadata(items)
        queue.replace(fromIndex, toIndex, metadata)
        cache.evictAll()
    }

    fun removeOccurrences(uids: Set<Long>): LogicalQueue.Removal? =
        queue.remove(uids)?.also {
            source = null
            publish()
        }

    fun restoreOccurrences(removal: LogicalQueue.Removal): Boolean =
        queue.undo(removal).also { if (it) publish() }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        removeOccurrences(queue.content.order.subList(fromIndex, toIndex).toSet())
    }

    override fun removeMediaItem(index: Int) = removeMediaItems(index, index + 1)

    override fun clearMediaItems() {
        sourceJob?.cancel()
        sourceGeneration++
        cancelPendingSeek()
        isPreparingSource = false
        queue.reset(emptyList(), 0)
        source = null
        cache.evictAll()
        publish()
    }

    override fun stop() {
        sourceJob?.cancel()
        sourceGeneration++
        isPreparingSource = false
        val position = currentPosition
        cancelPendingSeek()
        backend.stop()
        scheduleBackendUpdate(position, force = true)
    }

    override fun getCurrentTimeline(): Timeline = timeline

    override fun getMediaItemCount() = queue.size

    override fun getMediaItemAt(index: Int) = item(queue.rows[index])

    override fun getCurrentMediaItemIndex() = queue.index

    override fun getCurrentWindowIndex() = queue.index

    override fun getCurrentPeriodIndex() = queue.index

    override fun getCurrentMediaItem(): MediaItem? =
        queue.currentUid?.let(queue::entry)?.let(::item)

    override fun getShuffleModeEnabled() = queue.content.shuffled

    override fun setShuffleModeEnabled(enabled: Boolean) {
        if (enabled == queue.content.shuffled) return
        queue.setShuffle(enabled)
        source = null
        publish()
        listeners.keys.toList().forEach { it.onShuffleModeEnabledChanged(enabled) }
        events(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
    }

    override fun getRepeatMode() = logicalRepeat

    override fun setRepeatMode(mode: Int) {
        if (mode == logicalRepeat) return
        logicalRepeat = mode
        scheduleBackendUpdate(force = true)
        listeners.keys.toList().forEach { it.onRepeatModeChanged(mode) }
        events(Player.EVENT_REPEAT_MODE_CHANGED)
    }

    override fun getNextMediaItemIndex(): Int =
        when {
            queue.size == 0 -> C.INDEX_UNSET
            queue.index < queue.size - 1 -> queue.index + 1
            logicalRepeat == Player.REPEAT_MODE_ALL -> 0
            else -> C.INDEX_UNSET
        }

    override fun getPreviousMediaItemIndex(): Int =
        when {
            queue.size == 0 -> C.INDEX_UNSET
            queue.index > 0 -> queue.index - 1
            logicalRepeat == Player.REPEAT_MODE_ALL -> queue.size - 1
            else -> C.INDEX_UNSET
        }

    override fun getNextWindowIndex() = nextMediaItemIndex

    override fun getPreviousWindowIndex() = previousMediaItemIndex

    override fun hasNextMediaItem() = nextMediaItemIndex != C.INDEX_UNSET

    override fun hasPreviousMediaItem() = previousMediaItemIndex != C.INDEX_UNSET

    override fun seekToDefaultPosition() = seekTo(queue.index, 0L)

    override fun seekToDefaultPosition(index: Int) = seekTo(index, 0L)

    private fun cancelPendingSeek() {
        backendUpdateJob?.cancel()
        backendUpdateJob = null
        pendingPosition = null
        pendingSelection = false
        pendingForce = false
        pendingPrepare = false
        expectedBackendUid = null
    }

    /** One physical edit per main-loop turn, including source swaps, Undo and rapid transport. */
    private fun scheduleBackendUpdate(
        position: Long? = null,
        force: Boolean = false,
        prepare: Boolean = false,
    ) {
        if (released) return
        if (position != null) pendingPosition = position
        pendingForce = pendingForce || force
        pendingPrepare = pendingPrepare || prepare
        pendingSelection = pendingSelection || force || position != null
        if (backendUpdateJob?.isActive == true) return
        backendUpdateJob =
            scope.launch {
                yield()
                val at = pendingPosition
                val forceEdit = pendingForce
                val preparePlayer = pendingPrepare
                pendingPosition = null
                pendingForce = false
                pendingPrepare = false
                pendingSelection = false
                backendUpdateJob = null
                if (released) return@launch
                if (forceEdit || at != null) expectedBackendUid = queue.currentUid
                val physical = window.indexOf(queue.currentUid)
                if (!forceEdit && at != null && physical >= 0) backend.seekTo(physical, at)
                else refill(forceEdit, at)
                if (preparePlayer && queue.size > 0) backend.prepare()
                backend.playWhenReady = requestedPlayWhenReady
                if (explicitUid(backend.currentMediaItem) == expectedBackendUid)
                    expectedBackendUid = null
            }
    }

    suspend fun awaitBackendUpdate() {
        withTimeout(5_000) {
            while (true) {
                if (isPreparingSource) {
                    delay(1)
                    continue
                }
                backendUpdateJob?.join()
                if (backendUpdateJob == null && expectedBackendUid == null) return@withTimeout
                delay(1)
            }
        }
    }

    override fun getCurrentPosition() = pendingPosition ?: backend.currentPosition

    override fun seekTo(positionMs: Long) {
        if (queue.size > 0) scheduleBackendUpdate(positionMs.coerceAtLeast(0L))
    }

    override fun seekTo(index: Int, positionMs: Long) {
        if (index !in 0 until queue.size) return
        queue.select(index)
        scheduleBackendUpdate(positionMs.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L)
    }

    override fun seekToNextMediaItem() {
        if (hasNextMediaItem()) seekToDefaultPosition(nextMediaItemIndex)
    }

    override fun seekToPreviousMediaItem() {
        if (hasPreviousMediaItem()) seekToDefaultPosition(previousMediaItemIndex)
    }

    override fun seekToNext() = seekToNextMediaItem()

    override fun seekToPrevious() {
        if (currentPosition > maxSeekToPreviousPosition) seekTo(0L) else seekToPreviousMediaItem()
    }

    override fun getAvailableCommands(): Player.Commands =
        backend.availableCommands
            .buildUpon()
            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .add(Player.COMMAND_SET_MEDIA_ITEM)
            .addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNextMediaItem())
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, hasPreviousMediaItem())
            .build()

    override fun isCommandAvailable(command: Int) = availableCommands.contains(command)

    override fun addListener(listener: Player.Listener) {
        if (listener in listeners) return
        var previousUid = queue.currentUid
        val proxy =
            object : ForwardingPlayerListener(listener) {
                override fun onEvents(player: Player, events: Player.Events) =
                    listener.onEvents(this@ProgressivePlayer, events)

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    /* Logical edits publish once. */
                }

                override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                    /* Backend shuffle is always off. */
                }

                override fun onRepeatModeChanged(mode: Int) {
                    /* Backend repeat may differ at window edges. */
                }

                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    val current = queue.currentUid
                    if (
                        current != previousUid ||
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                    ) {
                        previousUid = current
                        listener.onMediaItemTransition(currentMediaItem, reason)
                    }
                }

                override fun onPositionDiscontinuity(
                    old: Player.PositionInfo,
                    new: Player.PositionInfo,
                    reason: Int,
                ) {
                    fun logical(info: Player.PositionInfo): Player.PositionInfo {
                        val id = uid(info.mediaItem)
                        val index = id?.let(queue::indexOf)?.coerceAtLeast(0) ?: queue.index
                        return Player.PositionInfo(
                            id,
                            index,
                            info.mediaItem,
                            id,
                            index,
                            info.positionMs,
                            info.contentPositionMs,
                            info.adGroupIndex,
                            info.adIndexInAdGroup,
                        )
                    }
                    listener.onPositionDiscontinuity(logical(old), logical(new), reason)
                }
            }
        listeners[listener] = proxy
        backend.addListener(proxy)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)?.let(backend::removeListener)
    }

    override fun release() {
        released = true
        scope.cancel()
        listeners.values.forEach(backend::removeListener)
        listeners.clear()
        backend.release()
    }

    private fun setControllerItems(items: List<MediaItem>, index: Int, position: Long) {
        val selected = items.getOrNull(index)?.let(::explicitUid)
        if (
            selected != null &&
                queue.indexOf(selected) >= 0 &&
                items.all { explicitUid(it)?.let(queue::indexOf)?.let { i -> i >= 0 } == true }
        ) {
            seekTo(queue.indexOf(selected), position)
        } else setMediaItems(items, index, position)
    }

    /** System controllers see only physical indices; transport still traverses the full source. */
    val sessionPlayer: Player =
        object : ForwardingPlayer(backend) {
            override fun play() = this@ProgressivePlayer.play()

            override fun pause() = this@ProgressivePlayer.pause()

            override fun stop() = this@ProgressivePlayer.stop()

            override fun prepare() = this@ProgressivePlayer.prepare()

            override fun setPlayWhenReady(value: Boolean) =
                this@ProgressivePlayer.setPlayWhenReady(value)

            override fun seekToNext() = this@ProgressivePlayer.seekToNext()

            override fun seekToNextMediaItem() = this@ProgressivePlayer.seekToNextMediaItem()

            override fun seekToPrevious() = this@ProgressivePlayer.seekToPrevious()

            override fun seekToPreviousMediaItem() =
                this@ProgressivePlayer.seekToPreviousMediaItem()

            override fun setShuffleModeEnabled(enabled: Boolean) =
                this@ProgressivePlayer.setShuffleModeEnabled(enabled)

            override fun getShuffleModeEnabled() = this@ProgressivePlayer.shuffleModeEnabled

            override fun setRepeatMode(mode: Int) = this@ProgressivePlayer.setRepeatMode(mode)

            override fun getRepeatMode() = logicalRepeat

            override fun seekTo(positionMs: Long) = this@ProgressivePlayer.seekTo(positionMs)

            override fun seekTo(index: Int, positionMs: Long) {
                window.getOrNull(index)?.let {
                    this@ProgressivePlayer.seekTo(queue.indexOf(it), positionMs)
                }
            }

            override fun seekToDefaultPosition(index: Int) = seekTo(index, 0L)

            override fun addMediaItems(index: Int, items: List<MediaItem>) =
                this@ProgressivePlayer.addMediaItems(
                    window.getOrNull(index)?.let(queue::indexOf) ?: queue.size,
                    items,
                )

            override fun addMediaItems(items: List<MediaItem>) =
                this@ProgressivePlayer.addMediaItems(items)

            override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
                removeOccurrences(window.subList(fromIndex, toIndex).toSet())
            }

            override fun removeMediaItem(index: Int) = removeMediaItems(index, index + 1)

            override fun moveMediaItem(fromIndex: Int, toIndex: Int) {
                val from = window.getOrNull(fromIndex)?.let(queue::indexOf) ?: return
                val to = window.getOrNull(toIndex)?.let(queue::indexOf) ?: return
                this@ProgressivePlayer.moveMediaItem(from, to)
            }

            override fun clearMediaItems() = this@ProgressivePlayer.clearMediaItems()

            override fun setMediaItems(
                items: List<MediaItem>,
                startIndex: Int,
                startPositionMs: Long,
            ) = setControllerItems(items, startIndex, startPositionMs)

            override fun setMediaItems(items: List<MediaItem>) = setControllerItems(items, 0, 0L)

            override fun setMediaItems(items: List<MediaItem>, resetPosition: Boolean) =
                setControllerItems(
                    items,
                    if (resetPosition) 0 else backend.currentMediaItemIndex,
                    if (resetPosition) 0L else currentPosition,
                )

            override fun setMediaItem(item: MediaItem) = this@ProgressivePlayer.setMediaItem(item)

            override fun setMediaItem(item: MediaItem, positionMs: Long) =
                this@ProgressivePlayer.setMediaItem(item, positionMs)

            override fun setMediaItem(item: MediaItem, resetPosition: Boolean) =
                this@ProgressivePlayer.setMediaItem(item, resetPosition)

            override fun addMediaItem(item: MediaItem) = this@ProgressivePlayer.addMediaItem(item)

            override fun addMediaItem(index: Int, item: MediaItem) =
                addMediaItems(index, listOf(item))

            override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
                val selected = window.subList(fromIndex, toIndex).toList()
                if (selected.isEmpty()) return
                val from = queue.indexOf(selected.first())
                val destination =
                    window.getOrNull(newIndex)?.let(queue::indexOf) ?: queue.size - selected.size
                if (
                    selected.zipWithNext().all { (a, b) ->
                        queue.indexOf(b) == queue.indexOf(a) + 1
                    }
                ) {
                    this@ProgressivePlayer.moveMediaItems(from, from + selected.size, destination)
                } else {
                    val selectedSet = selected.toHashSet()
                    val anchor = window.filterNot { it in selectedSet }.getOrNull(newIndex)
                    queue.moveBefore(selected, anchor)
                    source = null
                    publish()
                }
            }

            override fun replaceMediaItem(index: Int, item: MediaItem) {
                val logicalIndex = window.getOrNull(index)?.let(queue::indexOf) ?: return
                this@ProgressivePlayer.replaceMediaItem(logicalIndex, item)
            }

            override fun replaceMediaItems(fromIndex: Int, toIndex: Int, items: List<MediaItem>) {
                val from = window.getOrNull(fromIndex)?.let(queue::indexOf) ?: queue.size
                val selected = window.subList(fromIndex, toIndex)
                // A repeat-all window can cross the end of the logical source.
                if (
                    selected.zipWithNext().all { (a, b) ->
                        queue.indexOf(b) == queue.indexOf(a) + 1
                    }
                ) {
                    this@ProgressivePlayer.replaceMediaItems(from, from + selected.size, items)
                } else {
                    removeOccurrences(selected.toSet())
                    this@ProgressivePlayer.addMediaItems(from.coerceAtMost(queue.size), items)
                }
            }

            override fun getAvailableCommands() = this@ProgressivePlayer.availableCommands

            override fun isCommandAvailable(command: Int) = availableCommands.contains(command)
        }

    companion object {
        const val ENTRY_UID = "VIBRA_QUEUE_ENTRY_UID"
    }
}
