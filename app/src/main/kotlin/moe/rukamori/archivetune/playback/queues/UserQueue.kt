/*
 * Vibra (2026) — GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package moe.rukamori.archivetune.playback.queues

import android.os.Bundle
import androidx.media3.common.MediaItem

const val VIBRA_USER_QUEUED = "VIBRA_USER_QUEUED"

/** Marks an explicit choice of the user (Add to queue / Play next); survives queue restore. */
fun MediaItem.isUserQueued(): Boolean = mediaMetadata.extras?.getBoolean(VIBRA_USER_QUEUED, false) == true

internal fun MediaItem.asUserQueued(): MediaItem = buildUpon().setMediaMetadata(
    mediaMetadata.buildUpon().setExtras(Bundle(mediaMetadata.extras ?: Bundle()).apply {
        putBoolean(VIBRA_USER_QUEUED, true)
    }).build(),
).build()

/** Counts only the consecutive run after the current track, without wrapping around. */
fun userQueueRegionSize(playOrder: List<Int>, currentIndex: Int, userQueued: List<Boolean>): Int {
    return userQueueRegionSize(playOrder, currentIndex) { userQueued.getOrElse(it) { false } }
}

/** Variant that reads flags lazily, for the player's frequent notifications. */
internal fun userQueueRegionSize(playOrder: List<Int>, currentIndex: Int, isUserQueued: (Int) -> Boolean): Int {
    val current = playOrder.indexOf(currentIndex)
    if (current < 0) return 0
    return playOrder.asSequence().drop(current + 1).takeWhile(isUserQueued).count()
}

internal data class UserQueueInsertion(val index: Int, val playOrder: List<Int>)

/** Keeps the previous order and inserts the batch, contiguous, into the user region (start for Play next, end for Add to queue). */
internal fun userQueueInsertion(
    playOrder: List<Int>, currentIndex: Int, userQueued: List<Boolean>, count: Int,
    playNext: Boolean, shuffled: Boolean,
): UserQueueInsertion {
    require(count >= 0)
    val current = playOrder.indexOf(currentIndex)
    val after = if (current < 0) 0 else current + 1 +
        if (playNext) 0 else userQueueRegionSize(playOrder, currentIndex, userQueued)
    val index = when {
        current < 0 -> 0
        shuffled -> currentIndex + 1
        else -> playOrder[after - 1] + 1
    }
    val adjusted = playOrder.map { if (it >= index) it + count else it }
    return UserQueueInsertion(index, adjusted.take(after) + (index until index + count) + adjusted.drop(after))
}
