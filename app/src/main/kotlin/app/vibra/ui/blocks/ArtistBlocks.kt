/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.blocks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.vibra.ui.home.toShelfEntry
import app.vibra.ui.navigation.VibraItemActions
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.ArtistSection
import moe.rukamori.archivetune.innertube.pages.ArtistSectionLayout
import moe.rukamori.archivetune.utils.makeTimeString

/*
 * The lower part of an artist page in the block system: the same cards as
 * home and profile instead of shelf titles and horizontal carousels. Top songs are the
 * most relevant block (a large song card); the other sections (albums, singles, videos,
 * similar artists…) follow as cover mosaics whose size decays with their position, and
 * every titled block keeps its "see all" link.
 */

/** Blocks for the remote artist page. [onSeeAll] opens a section's full list, if it has one. */
@Composable
fun artistRemoteBlocks(
    sections: List<ArtistSection>,
    actions: VibraItemActions,
    onSeeAll: (ArtistSection) -> (() -> Unit)?,
): List<VibraBlock> =
    sections
        .filter { it.items.isNotEmpty() }
        .mapIndexed { index, section ->
            val key = "artist_${index}_${section.title}"
            val onTitleClick = onSeeAll(section)
            if (section.layout == ArtistSectionLayout.LIST && section.items.all { it is SongItem }) {
                val songs =
                    section.items.filterIsInstance<SongItem>().distinctBy { it.id }.map { song ->
                        SongEntry(
                            key = song.id,
                            title = song.title,
                            subtitle = song.artists.joinToString { it.name },
                            thumbnailUrl = song.thumbnail,
                            duration = song.duration?.let { makeTimeString(it * 1000L) },
                            onClick = { actions.open(song) },
                            onLongClick = { actions.showMenu(song) },
                        )
                    }
                VibraBlock(key = key, relevance = 0.95f, minSize = BlockSize.Medium) { size, modifier ->
                    SongsBlock(songs = songs, size = size, title = section.title, onTitleClick = onTitleClick, modifier = modifier)
                }
            } else {
                val entries = section.items.distinctBy { it.id }.map { it.toShelfEntry(actions) }
                VibraBlock(key = key, relevance = (0.82f - index * 0.1f).coerceAtLeast(0.3f)) { size, modifier ->
                    MosaicBlock(title = section.title, entries = entries, size = size, onTitleClick = onTitleClick, modifier = modifier)
                }
            }
        }

/** Blocks for the artist's songs and albums in the user's library (offline / local view). */
@Composable
fun artistLibraryBlocks(
    songs: List<Song>,
    albums: List<Album>,
    actions: VibraItemActions,
    onAllSongs: () -> Unit,
    onAllAlbums: () -> Unit,
): List<VibraBlock> {
    val songsTitle = stringResource(R.string.songs)
    val albumsTitle = stringResource(R.string.albums)
    return buildList {
        if (songs.isNotEmpty()) {
            val entries =
                songs.mapIndexed { index, song ->
                    SongEntry(
                        key = song.id,
                        title = song.song.title,
                        subtitle = song.artists.joinToString { it.name },
                        thumbnailUrl = song.song.thumbnailUrl,
                        duration = song.song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
                        onClick = { actions.playSongs(songs, index) },
                        onLongClick = { actions.showMenu(song) },
                    )
                }
            add(
                VibraBlock(key = "artist_library_songs", relevance = 0.95f, minSize = BlockSize.Medium) { size, modifier ->
                    SongsBlock(songs = entries, size = size, title = songsTitle, onTitleClick = onAllSongs, modifier = modifier)
                },
            )
        }
        if (albums.isNotEmpty()) {
            val entries = albums.map { it.toShelfEntry(actions) }
            add(
                VibraBlock(key = "artist_library_albums", relevance = 0.8f) { size, modifier ->
                    MosaicBlock(title = albumsTitle, entries = entries, size = size, onTitleClick = onAllAlbums, modifier = modifier)
                },
            )
        }
    }
}
