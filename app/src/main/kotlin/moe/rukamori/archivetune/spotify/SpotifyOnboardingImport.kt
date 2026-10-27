/*
 * Vibra (2026) — GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package moe.rukamori.archivetune.spotify

import android.content.Context
import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.ui.screens.settings.SpotifyLoginSheet
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import java.text.Normalizer
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** A YouTube Music artist to show and pick; holds no authentication data. */
data class ImportedArtist(val browseId: String, val name: String, val thumbnailUrl: String?)

internal fun normalizedArtistName(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFKC).trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

internal fun bestExactArtist(name: String, candidates: List<ArtistItem>): ArtistItem? =
    candidates.firstOrNull { it.title == name }
        ?: candidates.firstOrNull { normalizedArtistName(it.title) == normalizedArtistName(name) }

/** Shows the existing login; the caller keeps its visibility and the artist selection. */
@Composable
fun SpotifyOnboardingLogin(
    onDismiss: () -> Unit,
    onConnected: () -> Unit,
    viewModel: SpotifyAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val latestConnected by rememberUpdatedState(onConnected)
    LaunchedEffect(state.isAuthenticated) { if (state.isAuthenticated) latestConnected() }
    if (!state.isAuthenticated) SpotifyLoginSheet(onDismiss = onDismiss, onCookiesCaptured = { spDc, spKey ->
        viewModel.connectWithCookies(spDc, spKey)
    })
}

/** Imports data only. Nothing here plays from or changes Spotify. */
@Singleton
class SpotifyOnboardingImport @Inject constructor(
    private val spotifyLibrary: SpotifyLibraryRepository,
    private val database: MusicDatabase,
    @ApplicationContext private val context: Context,
) {
    /** One search per distinct name; artists without an exact match are left out. */
    suspend fun importFollowedArtists(): Result<List<ImportedArtist>> = withContext(Dispatchers.IO) {
        try {
            val artists = spotifyLibrary.followedArtists().distinctBy { normalizedArtistName(it.name) }
            val imported = artists.filter { it.name.isNotBlank() }.mapNotNull { artist ->
                val results = YouTube.search(artist.name, YouTube.SearchFilter.FILTER_ARTIST).getOrThrow()
                bestExactArtist(artist.name, results.items.filterIsInstance<ArtistItem>())?.let {
                    ImportedArtist(it.id, it.title, it.thumbnail)
                }
            }.distinctBy { it.browseId }
            Result.success(imported)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private val importScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playlistsImported = AtomicBoolean(false)

    /**
     * Copies every Spotify playlist into a local playlist, each track matched on YouTube Music
     * (tracks without a match are left out). Runs on its own scope, so it carries on after the
     * onboarding closes; once per process.
     */
    fun importPlaylistsInBackground() {
        if (!playlistsImported.compareAndSet(false, true)) return
        importScope.launch {
            try {
                spotifyLibrary.refreshPlaylists().forEach { playlist ->
                    try {
                        importPlaylist(playlist)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        reportException(error)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportException(error)
            }
        }
    }

    private suspend fun importPlaylist(source: moe.rukamori.archivetune.spotify.models.SpotifyPlaylist) {
        val tracks = spotifyLibrary.playlistTracks(source.id)
        val gate = Semaphore(PlaylistMatchParallelism)
        val songs =
            coroutineScope {
                tracks.map { track -> async { gate.withPermit { SpotifyPlaybackResolver.resolveToMetadata(track) } } }.awaitAll()
            }.filterNotNull().distinctBy { it.id }
        if (songs.isEmpty()) return
        val entity =
            PlaylistEntity(
                name = source.name,
                thumbnailUrl = source.images.firstOrNull()?.url?.takeIf { it.isNotBlank() },
                bookmarkedAt = LocalDateTime.now(),
            )
        database.withTransaction {
            songs.forEach { insert(it) }
            insert(entity)
        }
        val playlist = database.playlist(entity.id).first() ?: return
        database.addSongToPlaylist(playlist, songs.map { it.id })
    }

    /** Idempotent bookmark, like Follow; when signed in it also subscribes on YouTube Music. */
    suspend fun followArtists(ids: List<String>) = withContext(Dispatchers.IO) {
        val loggedIn = hasYouTubeLoginCookie(context.dataStore.data.first()[InnerTubeCookieKey].orEmpty())
        ids.distinct().forEach { id ->
            val existing = database.artist(id).first()?.artist
            val remote = if (existing == null || (loggedIn && existing.channelId == null)) YouTube.artist(id).getOrThrow().artist else null
            val artist = existing ?: ArtistEntity(id = id, name = requireNotNull(remote).title,
                thumbnailUrl = remote.thumbnail, channelId = remote.channelId)
            database.withTransaction {
                val stored = database.artist(id).first()?.artist
                val current = stored ?: artist
                if (current.bookmarkedAt == null) {
                    val followed = current.copy(bookmarkedAt = LocalDateTime.now())
                    if (stored == null) insert(followed) else update(followed)
                }
            }
            if (loggedIn) {
                val channelId = artist.channelId ?: remote?.channelId ?: YouTube.getChannelId(id)
                YouTube.subscribeChannel(channelId, true).getOrThrow()
            }
        }
    }
}

/** Track searches running at once while a playlist is matched on YouTube Music. */
private const val PlaylistMatchParallelism = 4
