/*
 * Vibra (2026) — shared bounded local search preview.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.search

import kotlinx.coroutines.flow.*
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LocalItem

fun observeLocalSearchPreview(database: MusicDatabase, query: String, limit: Int = 3): Flow<List<LocalItem>> =
    if (query.isBlank()) flowOf(emptyList()) else combine(
        database.searchSongs(query, limit), database.searchAlbums(query, limit),
        database.searchArtists(query, limit), database.searchPlaylists(query, limit),
    ) { songs, albums, artists, playlists ->
        songs.filter { song -> song.artists.none { it.blockedAt != null } } +
            albums.filter { album -> album.artists.none { it.blockedAt != null } } +
            artists.filter { it.artist.blockedAt == null } + playlists
    }
