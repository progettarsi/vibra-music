/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.home

import app.vibra.ui.components.ShelfEntry
import app.vibra.ui.navigation.VibraItemActions
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem

/*
 * Converters from engine items to the presentation-only [ShelfEntry]. Keeping them in one
 * file means every card/shelf labels items the same way (e.g. "Artist · 2024" for albums).
 */

/** Human subtitle for a YouTube Music item. */
fun YTItem.subtitleText(): String? =
    when (this) {
        is SongItem -> artists.joinToString { it.name }
        is AlbumItem -> listOfNotNull(artists?.joinToString { it.name }, year?.toString()).joinToString(" · ")
        is PlaylistItem -> author?.name ?: songCountText
        is ArtistItem -> null
        is PodcastItem -> author?.name
        is EpisodeItem -> podcast?.name
    }

fun YTItem.toShelfEntry(actions: VibraItemActions): ShelfEntry =
    ShelfEntry(
        key = "yt_$id",
        title = title,
        subtitle = subtitleText(),
        thumbnailUrl = thumbnail,
        circular = this is ArtistItem,
        playable = this is SongItem || this is EpisodeItem,
        onClick = { actions.open(this) },
        onLongClick = { actions.showMenu(this) },
    )

/** Human subtitle for a library item. */
fun LocalItem.subtitleText(): String? =
    when (this) {
        is Song -> artists.joinToString { it.name }
        is Album -> artists.joinToString { it.name }
        is Artist -> null
        is Playlist -> null
    }

/**
 * Artwork for any library item. Local playlists have no `thumbnailUrl` of their own (the
 * engine returns null); their cover is the custom cover or the first song's artwork.
 */
val LocalItem.coverUrl: String?
    get() = if (this is Playlist) thumbnails.firstOrNull() else thumbnailUrl

fun LocalItem.toShelfEntry(actions: VibraItemActions): ShelfEntry =
    ShelfEntry(
        key = "local_${this::class.simpleName}_$id",
        title = title,
        subtitle = subtitleText(),
        thumbnailUrl = coverUrl,
        circular = this is Artist,
        playable = this is Song,
        onClick = { actions.open(this) },
        onLongClick = { actions.showMenu(this) },
    )
