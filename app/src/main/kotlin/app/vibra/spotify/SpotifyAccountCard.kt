/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.spotify

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.spotify.*
import moe.rukamori.archivetune.ui.screens.settings.*
import moe.rukamori.archivetune.ui.component.*

@Composable
fun SpotifyAccountCard(viewModel: SpotifyAccountViewModel = hiltViewModel()) {
    val (showPlaylists, setShowPlaylists) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.ShowSpotifyPlaylistsKey, false)
    val account by viewModel.uiState.collectAsStateWithLifecycle()
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    var login by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(account.isAuthenticated) { if (account.isAuthenticated) login = false }
    PreferenceGroup(title = "Spotify") {
        spotifyAccountPreferences(account, showPlaylists, { login = true }, setShowPlaylists, viewModel::syncAll, viewModel::logout)
        if (account.isAuthenticated) item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.vibra_spotify_sync_all)) },
                description = (if (sync.running) stringResource(R.string.vibra_spotify_sync_running) + " · " else "") +
                    stringResource(R.string.vibra_spotify_sync_status, sync.processed, sync.imported, sync.unmatched, sync.errors) +
                    (if (sync.lastCompleted > 0) "\n" + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(sync.lastCompleted)) else ""),
                onClick = viewModel::syncAll,
                isEnabled = !sync.running,
            )
        }
    }
    if (account.errorMessage != null) Text(stringResource(R.string.vibra_spotify_connection_error))
    if (login) SpotifyLoginSheet(onDismiss = { login = false }, onCookiesCaptured = viewModel::connectWithCookies)
}
