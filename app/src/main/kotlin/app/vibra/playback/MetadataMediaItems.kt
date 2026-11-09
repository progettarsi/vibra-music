/* Vibra (2026) — GPL-3.0. Derived from ArchiveTune © Rukamori. */
package app.vibra.playback

import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata

/** No MediaItem/Bundle graph is allocated when a UI submits a large source. */
abstract class MetadataMediaItems : AbstractList<MediaItem>() {
    abstract val sourceIdentity: Any

    abstract fun metadataAt(index: Int): MediaMetadata

    final override fun get(index: Int): MediaItem = metadataAt(index).toMediaItem()
}

fun <T> List<T>.lazyMediaItems(metadata: (T) -> MediaMetadata): MetadataMediaItems {
    val source = this
    return object : MetadataMediaItems() {
        override val sourceIdentity: Any = source
        override val size
            get() = source.size

        override fun metadataAt(index: Int) = metadata(source[index])
    }
}

fun List<MediaItem>.metadataAt(index: Int): MediaMetadata? =
    if (this is MetadataMediaItems) metadataAt(index)
    else
        get(index).let { item ->
            item.metadata
                ?: MediaMetadata(
                    id = item.mediaId,
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artists =
                        item.mediaMetadata.artist
                            ?.toString()
                            ?.let { listOf(MediaMetadata.Artist(null, it)) }
                            .orEmpty(),
                    duration = -1,
                    thumbnailUrl = item.mediaMetadata.artworkUri?.toString(),
                )
        }

fun metadataOf(song: Song) = song.toMediaMetadata()

fun metadataOf(song: SongItem) = song.toMediaMetadata()

fun metadataOf(episode: EpisodeItem) = episode.toMediaMetadata()

fun metadataOf(metadata: MediaMetadata) = metadata

fun List<MediaItem>.filterMetadata(keep: (MediaMetadata) -> Boolean): List<MediaItem> {
    if (this !is MetadataMediaItems) return filter { it.metadata?.let(keep) != false }
    val source = this
    val indices = indices.filter { keep(source.metadataAt(it)) }
    if (indices.size == size) return this
    return object : MetadataMediaItems() {
        override val sourceIdentity: Any = this
        override val size
            get() = indices.size

        override fun metadataAt(index: Int) = source.metadataAt(indices[index])
    }
}
