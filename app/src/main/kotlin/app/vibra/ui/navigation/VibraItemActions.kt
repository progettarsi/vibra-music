/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import app.vibra.ui.haptics.VibraHaptics
import app.vibra.ui.haptics.rememberVibraHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.playback.queues.YouTubeAlbumRadio
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.PlaylistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist

/**
 * One place that knows how to *open*, *play* and *show the menu for* any item the engine
 * can return — local library entities ([LocalItem]) and YouTube Music results
 * ([YTItem]). Every Vibra surface (home cards, profile, search) calls these, so a song
 * tapped anywhere behaves identically and there is no per-screen copy of this logic.
 *
 * Semantics (matching the engine's existing screens):
 *  - Song ......... open = play it with an endless radio after it (or pause/resume if it
 *                   is already the current track); local files play as-is.
 *  - Album ........ open = album screen; play = whole album.
 *  - Artist ....... open = artist screen; play = artist radio.
 *  - Playlist ..... open = playlist screen; play = whole playlist.
 */
@Stable
class VibraItemActions internal constructor(
    private val navController: NavController,
    private val connection: PlayerConnection?,
    private val database: MusicDatabase,
    private val menuState: MenuState,
    private val scope: CoroutineScope,
    private val haptics: VibraHaptics,
) {
    // ── YouTube Music items ─────────────────────────────────────────────────────────

    fun open(item: YTItem) {
        when (item) {
            is SongItem -> playSong(item)
            is AlbumItem -> navController.navigateToAlbum(item.id)
            is ArtistItem -> navController.navigateToArtist(item.id)
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
            is PodcastItem -> navController.navigate("podcast/${Uri.encode(item.browseId)}")
            is EpisodeItem -> play(item)
        }
    }

    fun play(item: YTItem) {
        val player = connection ?: return
        haptics.press()
        when (item) {
            is SongItem -> playSong(item)
            is AlbumItem -> player.playQueue(YouTubeAlbumRadio(item.playlistId))
            is ArtistItem -> item.radioEndpoint?.let { player.playQueue(YouTubeQueue(it)) }
            is PlaylistItem -> item.playEndpoint?.let { player.playQueue(YouTubeQueue.playlist(it)) }
            is EpisodeItem ->
                player.playQueue(
                    ListQueue(title = item.podcast?.name ?: item.title, items = listOf(item.toMediaItem())),
                )

            is PodcastItem -> open(item)
        }
    }

    fun showMenu(item: YTItem) {
        haptics.longPress()
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
                is PodcastItem, is EpisodeItem -> menuState.dismiss()
            }
        }
    }

    private fun playSong(item: SongItem) {
        val player = connection ?: return
        if (player.mediaMetadata.value?.id == item.id) {
            player.player.togglePlayPause()
        } else {
            player.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
        }
    }

    // ── Local library items ─────────────────────────────────────────────────────────

    fun open(item: LocalItem) {
        when (item) {
            is Song -> playSongs(listOf(item), 0)
            is Album -> navController.navigateToAlbum(item.id)
            is Artist -> navController.navigateToArtist(item.id)
            is Playlist -> navController.navigate("local_playlist/${item.id}")
        }
    }

    fun play(item: LocalItem) {
        val player = connection ?: return
        haptics.press()
        when (item) {
            is Song -> playSongs(listOf(item), 0)
            is Album ->
                scope.launch {
                    val albumWithSongs = withContext(Dispatchers.IO) { database.albumWithSongs(item.id).first() }
                    albumWithSongs?.let { player.playQueue(LocalAlbumRadio(it)) }
                }

            is Artist -> navController.navigateToArtist(item.id)
            is Playlist ->
                scope.launch {
                    val songs =
                        withContext(Dispatchers.IO) {
                            database.playlistSongs(item.id).first().map { it.song }
                        }
                    if (songs.isEmpty()) {
                        haptics.reject()
                    } else {
                        player.playQueue(
                            ListQueue(title = item.playlist.name, items = songs.map { it.toMediaItem() }),
                        )
                    }
                }
        }
    }

    /**
     * Plays [songs] starting at [startIndex]. A single streaming song continues with a
     * radio (like everywhere else in the engine); lists play exactly as given.
     */
    fun playSongs(
        songs: List<Song>,
        startIndex: Int,
        title: String? = null,
    ) {
        val player = connection ?: return
        val start = songs.getOrNull(startIndex) ?: return
        when {
            player.mediaMetadata.value?.id == start.id -> player.player.togglePlayPause()
            songs.size == 1 && !start.song.isLocal -> player.playQueue(YouTubeQueue.radio(start.toMediaMetadata()))
            else -> player.playQueue(ListQueue(title = title, items = songs.map { it.toMediaItem() }, startIndex = startIndex))
        }
    }

    fun showMenu(item: LocalItem) {
        haptics.longPress()
        menuState.show {
            when (item) {
                is Song -> SongMenu(originalSong = item, navController = navController, onDismiss = menuState::dismiss)
                is Album -> AlbumMenu(originalAlbum = item, navController = navController, onDismiss = menuState::dismiss)
                is Artist -> ArtistMenu(originalArtist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
                is Playlist -> PlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
            }
        }
    }
}

@Composable
fun rememberVibraItemActions(navController: NavController): VibraItemActions {
    val connection = LocalPlayerConnection.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val haptics = rememberVibraHaptics()
    return remember(navController, connection, database, menuState, scope, haptics) {
        VibraItemActions(navController, connection, database, menuState, scope, haptics)
    }
}
