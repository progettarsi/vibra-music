/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.queues

import androidx.media3.common.MediaItem
import app.vibra.playback.MetadataMediaItems
import app.vibra.playback.metadataAt
import app.vibra.playback.filterMetadata
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    suspend fun getInitialStatus(): Status

    fun shouldExpandToFullQueueWhenAutoLoadMoreDisabled(): Boolean = false

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                filterMetadataItems { !it.explicit }
            } else {
                this
            }

        fun filterVideo(enabled: Boolean = true) =
            if (enabled) {
                filterMetadataItems { !it.isMusicVideo }
            } else {
                this
            }

        fun filterBlockedArtists(blockedArtistIds: Set<String>) =
            if (blockedArtistIds.isEmpty()) {
                this
            } else {
                filterMetadataItems { metadata -> metadata.artists.none { it.id in blockedArtistIds } }
            }

        private fun filterMetadataItems(keep: (MediaMetadata) -> Boolean): Status {
            if (items.isEmpty()) return this
            val selected = mediaItemIndex.coerceIn(items.indices)
            val allowed = items.indices.filter { items.metadataAt(it)?.let(keep) != false }
            if (allowed.size == items.size) return this
            val source = items
            val filtered = object : MetadataMediaItems() {
                override val sourceIdentity: Any = this
                override val size get() = allowed.size
                override fun metadataAt(index: Int) = source.metadataAt(allowed[index])!!
            }
            return copy(items = filtered, mediaItemIndex = allowed.count { it < selected }.coerceAtMost((allowed.size - 1).coerceAtLeast(0)))
        }

    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterMetadata { !it.explicit }
    } else {
        this
    }

fun List<MediaItem>.filterVideo(enabled: Boolean = true) =
    if (enabled) {
        filterMetadata { !it.isMusicVideo }
    } else {
        this
    }

fun List<MediaItem>.filterBlockedArtists(blockedArtistIds: Set<String>) =
    if (blockedArtistIds.isEmpty()) {
        this
    } else {
        filterMetadata { metadata -> metadata.artists.none { it.id in blockedArtistIds } }
    }

fun MediaItem.hasBlockedArtist(blockedArtistIds: Set<String>): Boolean =
    metadata?.artists?.any { artist -> artist.id != null && artist.id in blockedArtistIds } == true
