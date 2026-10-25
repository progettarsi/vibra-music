/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.player

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import app.vibra.ui.haptics.rememberVibraHaptics
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialog

/**
 * Hosts the engine's "Add to playlist" dialog for [metadata] and returns the lambda that
 * opens it. Used by the dock's ⊕ and the player's add button, so both behave the same
 * (local + synced YouTube Music playlists, duplicate detection, result toast).
 */
@Composable
fun rememberAddToPlaylistLauncher(metadata: MediaMetadata?): () -> Unit {
    val context = LocalContext.current
    // Resources from LocalResources follow configuration changes (locale, density).
    val resources = LocalResources.current
    val database = LocalDatabase.current
    val haptics = rememberVibraHaptics()
    var visible by remember { mutableStateOf(false) }

    if (metadata != null) {
        AddToPlaylistDialog(
            isVisible = visible,
            onGetSong = {
                // The playlist map references the song row, so make sure it exists.
                database.withTransaction { insert(metadata) }
                listOf(metadata.id)
            },
            onDismiss = { visible = false },
            onAddComplete = { _, playlistNames ->
                haptics.confirm()
                val message =
                    if (playlistNames.size == 1) {
                        resources.getString(R.string.added_to_playlist, playlistNames.first())
                    } else {
                        resources.getString(R.string.added_to_n_playlists, playlistNames.size)
                    }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
        )
    }
    return remember(metadata) { { if (metadata != null) visible = true } }
}
