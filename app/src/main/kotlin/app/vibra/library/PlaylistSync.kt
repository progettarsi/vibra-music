/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.library

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.playback.DownloadUtil

object PlaylistSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun toggle(
        context: Context,
        libraryActions: LibraryActions,
        downloadUtil: DownloadUtil,
        playlist: Playlist,
        songIds: List<String>,
        remove: Boolean,
        onApplied: (added: Int) -> Unit,
        onFailed: () -> Unit,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            val result = if (remove) {
                libraryActions.removePlaylistSongs(playlist.id, songIds = songIds.toSet()).map { emptyList() }
            } else {
                val snapshot = downloadUtil.playlistDownloadSnapshot(playlist.id)
                libraryActions.addPlaylistSongs(playlist.id, songIds).onSuccess {
                    if (it.isNotEmpty()) downloadUtil.downloadPlaylistAdditions(snapshot, it)
                }
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { onApplied(it.size) },
                    onFailure = {
                        onFailed()
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.vibra_playlist_sync_failed, playlist.playlist.name),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
    }
}
