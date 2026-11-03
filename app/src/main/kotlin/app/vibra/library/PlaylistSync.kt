/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.library

import android.content.Context
import android.widget.Toast
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.ui.menu.removeSongFromRemotePlaylist

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  PLAYLIST MEMBERSHIP — local first, YouTube Music in the background
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  Adding a song to a playlist (or taking it out) changes the local database at once, so
 *  the tick and the library react immediately and the user is never held by the network.
 *  A synced playlist is then mirrored to YouTube Music in the background, one change at a
 *  time per playlist (a quick add-then-remove stays in order), in a scope of its own that
 *  outlives the dialog. If YouTube refuses, the local change is undone and a toast says so.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */
object PlaylistSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Adds [songIds] to [playlist] (or removes them with [remove]). [onApplied] runs on the main
     * thread once the local database has the change, with the number of songs added; [onFailed]
     * runs on the main thread if the change could not be made or was undone.
     *
     * @param remoteAllowed false when a synced playlist cannot reach YouTube Music (signed out):
     *   nothing changes, so the two never drift apart.
     */
    fun toggle(
        context: Context,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        playlist: Playlist,
        songIds: List<String>,
        remove: Boolean,
        remoteAllowed: Boolean,
        onApplied: (added: Int) -> Unit,
        onFailed: () -> Unit,
    ) {
        val appContext = context.applicationContext
        val browseId = playlist.playlist.browseId
        val name = playlist.playlist.name
        scope.launch {
            val failed = suspend {
                withContext(Dispatchers.Main) {
                    onFailed()
                    Toast.makeText(appContext, appContext.getString(R.string.vibra_playlist_sync_failed, name), Toast.LENGTH_SHORT).show()
                }
            }
            try {
                check(browseId == null || remoteAllowed)
                if (remove) {
                    val removed = removeLocally(database, playlist.id, songIds.toSet())
                    withContext(Dispatchers.Main) { onApplied(0) }
                    if (browseId != null && removed.isNotEmpty()) {
                        lockFor(playlist.id).withLock {
                            val pending = removed.toMutableList()
                            try {
                                while (pending.isNotEmpty()) {
                                    removeSongFromRemotePlaylist(browseId, pending.first()).getOrThrow()
                                    pending.removeAt(0)
                                }
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                restoreLocally(database, playlist.id, pending)
                                failed()
                            }
                        }
                    }
                } else {
                    val snapshot = downloadUtil.playlistDownloadSnapshot(playlist.id)
                    val added = addLocally(database, playlist.id, songIds.distinct())
                    if (added.isNotEmpty()) downloadUtil.downloadPlaylistAdditions(snapshot, added)
                    withContext(Dispatchers.Main) { onApplied(added.size) }
                    if (browseId != null && added.isNotEmpty()) {
                        lockFor(playlist.id).withLock {
                            val pending = added.toMutableList()
                            try {
                                while (pending.isNotEmpty()) {
                                    val songId = pending.first()
                                    val setVideoId = YouTube.addToPlaylist(browseId, songId).getOrThrow()
                                    database.withTransaction {
                                        getPlaylistSongs(playlist.id)
                                            .firstOrNull { it.map.songId == songId && it.map.setVideoId == null }
                                            ?.let { update(it.map.copy(setVideoId = setVideoId)) }
                                    }
                                    pending.removeAt(0)
                                }
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                removeLocally(database, playlist.id, pending.toSet())
                                failed()
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failed()
            }
        }
    }

    private fun lockFor(playlistId: String) = locks.getOrPut(playlistId) { Mutex() }

    /** Appends the songs not yet in the playlist; returns the ones added. */
    private suspend fun addLocally(
        database: MusicDatabase,
        playlistId: String,
        songIds: List<String>,
    ): List<String> =
        database.withTransaction {
            val missing = songIds.filter { checkInPlaylist(playlistId, it) == 0 }
            val current = getPlaylistById(playlistId) ?: error("Playlist removed")
            addSongEntriesToPlaylist(current, missing.map { it to null })
            missing
        }

    /** Takes the songs out (closing the gaps they leave); returns the rows removed. */
    private suspend fun removeLocally(
        database: MusicDatabase,
        playlistId: String,
        songIds: Set<String>,
    ): List<PlaylistSongMap> =
        database.withTransaction {
            val entries =
                getPlaylistSongs(playlistId)
                    .map { it.map }
                    .filter { it.songId in songIds }
                    .sortedByDescending { it.position }
            entries.forEach { map ->
                move(playlistId, map.position, Int.MAX_VALUE)
                delete(map.copy(position = Int.MAX_VALUE))
            }
            if (entries.isNotEmpty()) {
                getPlaylistById(playlistId)?.let { update(it.playlist.copy(lastUpdateTime = LocalDateTime.now())) }
            }
            entries
        }

    /** Puts rows whose remote removal failed back at the end of the playlist. */
    private suspend fun restoreLocally(
        database: MusicDatabase,
        playlistId: String,
        maps: List<PlaylistSongMap>,
    ) {
        database.withTransaction {
            val current = getPlaylistById(playlistId) ?: return@withTransaction
            addSongEntriesToPlaylist(current, maps.sortedBy { it.position }.map { it.songId to it.setVideoId })
        }
    }
}
