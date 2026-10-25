/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Vibra: extracted from Queue.kt so the legacy queue and the Vibra queue share one
 * implementation of drag-to-reorder semantics.
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.runtime.Immutable
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.playback.PlayerConnection

/**
 * Stable, Bundle-saveable list key for a queue entry. `Timeline.Window.uid` itself is an
 * opaque object that lazy lists cannot save, so it is folded with the media id into a Long.
 */
internal val Timeline.Window.queueItemKey: Long
    get() =
        (uid.hashCode().toLong() shl Int.SIZE_BITS) xor
            (mediaItem.mediaId.hashCode().toLong() and UInt.MAX_VALUE.toLong())

/**
 * An in-flight queue drag. The destination is stored relative to a neighbour's uid (not
 * an index) so it stays valid if the timeline changes while the finger is down.
 */
@Immutable
internal data class QueueDragInfo(
    val draggedItemUid: Any,
    val destination: QueueDragDestination,
)

@Immutable
internal sealed interface QueueDragDestination {
    data object Start : QueueDragDestination

    data class After(
        val itemUid: Any,
    ) : QueueDragDestination
}

/** Resolves the destination to an index in the *current* [queueWindows]. */
internal fun QueueDragDestination.resolveIndex(
    queueWindows: List<Timeline.Window>,
    sourceIndex: Int,
): Int? =
    when (this) {
        QueueDragDestination.Start -> if (queueWindows.isEmpty()) null else 0
        is QueueDragDestination.After -> {
            val anchorIndex = queueWindows.indexOfFirst { it.uid == itemUid }
            when {
                sourceIndex !in queueWindows.indices -> null
                anchorIndex == -1 -> null
                sourceIndex < anchorIndex -> anchorIndex
                else -> (anchorIndex + 1).coerceAtMost(queueWindows.lastIndex)
            }
        }
    }

/**
 * Builds the destination for an item dropped at [toIndex] of the locally reordered list.
 */
internal fun queueDragDestinationFor(
    reorderedWindows: List<Timeline.Window>,
    toIndex: Int,
): QueueDragDestination =
    if (toIndex == 0) {
        QueueDragDestination.Start
    } else {
        QueueDragDestination.After(itemUid = reorderedWindows[toIndex - 1].uid)
    }

/**
 * Applies a completed drag to the real player. Without shuffle the timeline itself is
 * reordered; with shuffle the visible order *is* the shuffle order, so a new shuffle
 * order is installed instead (the underlying timeline is left untouched).
 *
 * @return true when the player was changed.
 */
internal fun PlayerConnection.commitQueueDrag(
    queueWindows: List<Timeline.Window>,
    drag: QueueDragInfo,
): Boolean {
    val sourceIndex = queueWindows.indexOfFirst { it.uid == drag.draggedItemUid }
    val destinationIndex = drag.destination.resolveIndex(queueWindows, sourceIndex)
    if (sourceIndex == -1 || destinationIndex == null || sourceIndex == destinationIndex) return false

    if (!player.shuffleModeEnabled) {
        player.moveMediaItem(sourceIndex, destinationIndex)
    } else {
        localPlayer.setShuffleOrder(
            DefaultShuffleOrder(
                queueWindows
                    .map { it.firstPeriodIndex }
                    .toMutableList()
                    .move(sourceIndex, destinationIndex)
                    .toIntArray(),
                System.currentTimeMillis(),
            ),
        )
    }
    return true
}
