/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.spotify

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.*
import moe.rukamori.archivetune.spotify.*
import moe.rukamori.archivetune.network.NetworkMonitor
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.utils.dataStore
import java.time.LocalDateTime

/** The complete import reports partial failures explicitly; no remote account writes. */
data class SpotifySyncState(
    val running: Boolean = false,
    val phase: String = "idle",
    val processed: Int = 0,
    val imported: Int = 0,
    val unmatched: Int = 0,
    val errors: Int = 0,
    val lastCompleted: Long = 0,
)

@Singleton
class SpotifySyncRepository @Inject constructor(
    private val library: SpotifyLibraryRepository,
    private val artistImport: SpotifyOnboardingImport,
    private val database: MusicDatabase,
    private val network: NetworkMonitor,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(SpotifySyncState())
    val state: StateFlow<SpotifySyncState> = mutable.asStateFlow()
    @Volatile private var job: Job? = null
    private var started = false
    private var lastAttempt = 0L
    @Volatile private var generation = 0

    fun start() {
        if (started) return
        started = true
        scope.launch {
            context.dataStore.data.map { it[SpotifySpDcKey] }.distinctUntilChanged().collect {
                generation++
                job?.cancelAndJoin()
                job = null
                lastAttempt = 0L
                mutable.value = SpotifySyncState()
                if (!it.isNullOrBlank()) sync()
            }
        }
        scope.launch {
            network.isOnline.collect { if (it) sync(automatic = true) }
        }
        scope.launch {
            while (isActive) {
                if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) sync(automatic = true)
                delay(60_000)
            }
        }
    }

    @Synchronized
    fun sync(automatic: Boolean = false) {
        if (job?.isActive == true) return
        val epoch = generation
        job = scope.launch {
            if (!mutex.tryLock()) return@launch
            try {
                if (context.dataStore.data.first()[SpotifySpDcKey].isNullOrBlank()) return@launch
                if (!network.isOnline.first()) return@launch
                if (automatic && mutable.value.lastCompleted > 0 && System.currentTimeMillis() - mutable.value.lastCompleted < SIX_HOURS) return@launch
                if (automatic && System.currentTimeMillis() - lastAttempt < 15 * 60 * 1000L) return@launch
                lastAttempt = System.currentTimeMillis()
                check(library.restoreSession().isAuthenticated)
                val account = Spotify.me().getOrThrow().id
                val completedKey = longPreferencesKey("spotify_sync_completed_" + account)
                val completed = context.dataStore.data.first()[completedKey] ?: 0L
                mutable.update { it.copy(lastCompleted = completed) }
                if (automatic && System.currentTimeMillis() - completed < SIX_HOURS) return@launch
                lastAttempt = System.currentTimeMillis()
                mutable.value = SpotifySyncState(running = true, phase = "artists", lastCompleted = completed)
                suspend fun guard() { currentCoroutineContext().ensureActive(); check(epoch == generation) }
                suspend fun attempt(block: suspend () -> Unit) {
                    try { guard(); block() } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { mutable.update { it.copy(errors = it.errors + 1) } }
                }
                attempt {
                    val totalArtists = library.followedArtists().size
                    val artists = artistImport.importFollowedArtists().getOrThrow()
                    mutable.update { it.copy(unmatched = it.unmatched + (totalArtists - artists.size).coerceAtLeast(0)) }
                    for (artist in artists) {
                        guard()
                        database.withTransaction {
                            val old = database.artist(artist.browseId).first()?.artist
                            if (old == null) insert(ArtistEntity(id = artist.browseId, name = artist.name, thumbnailUrl = artist.thumbnailUrl, bookmarkedAt = LocalDateTime.now()))
                            else if (old.bookmarkedAt == null) update(old.copy(bookmarkedAt = LocalDateTime.now()))
                            saveSpotifyImport(SpotifyImportMap(account, "artist", artist.sourceId, artist.browseId))
                        }
                        mutable.update { it.copy(processed = it.processed + 1, imported = it.imported + 1) }
                    }
                }
                val gate = Semaphore(4)
                suspend fun resolve(tracks: List<moe.rukamori.archivetune.spotify.models.SpotifyTrack>) = coroutineScope {
                    tracks.map { track -> async { gate.withPermit {
                        guard()
                        val mapped = database.spotifyImport(account, "song", track.id)
                        val cached = mapped?.let { database.song(it.localId).first() }
                        val metadata = if (cached != null) null else SpotifyPlaybackResolver.resolveToMetadata(track)
                        val id = cached?.id ?: metadata?.id
                        if (id != null) database.withTransaction {
                            guard()
                            if (metadata != null) insert(metadata)
                            saveSpotifyImport(SpotifyImportMap(account, "song", track.id, id))
                        }
                        mutable.update { it.copy(processed = it.processed + 1, unmatched = it.unmatched + if (id == null) 1 else 0) }
                        id
                    } } }.awaitAll().filterNotNull()
                }
                mutable.update { it.copy(phase = "songs") }
                attempt {
                    for (id in resolve(library.likedSongs()).distinct()) {
                        guard()
                        database.withTransaction {
                            val song = database.song(id).first()?.song ?: return@withTransaction
                            update(song.copy(liked = true, likedDate = song.likedDate ?: LocalDateTime.now(), inLibrary = song.inLibrary ?: LocalDateTime.now()))
                            saveSpotifyImport(SpotifyImportMap(account, "liked", id, id))
                        }
                        mutable.update { it.copy(imported = it.imported + 1) }
                    }
                }
                mutable.update { it.copy(phase = "playlists") }
                attempt {
                    val playlists = library.refreshPlaylists()
                    check(library.errorMessage.value == null)
                    for (source in playlists) attempt {
                        val ids = resolve(library.playlistTracks(source.id)).distinct()
                        guard()
                        val mapping = database.spotifyImport(account, "playlist", source.id)
                        var existing = mapping?.let { database.playlist(it.localId).first() }
                        if (existing == null && ids.isNotEmpty()) {
                            val matches = database.playlistsByNameAsc().first().filter { it.playlist.name == source.name }
                                .filter { database.playlistSongs(it.id).first().map { row -> row.song.id } == ids }
                            existing = matches.singleOrNull()
                        }
                        val entity = existing?.playlist ?: PlaylistEntity(name = source.name, isLocal = true, bookmarkedAt = LocalDateTime.now())
                        val extras = existing?.let { database.playlistSongs(it.id).first().map { row -> row.song.id }.filterNot { it in ids } }.orEmpty()
                        database.withTransaction {
                            guard()
                            if (existing == null) insert(entity) else update(entity.copy(name = source.name))
                            clearPlaylist(entity.id)
                            mergeImportedPlaylist(ids, extras).forEachIndexed { index, id -> insert(PlaylistSongMap(playlistId = entity.id, songId = id, position = index)) }
                            saveSpotifyImport(SpotifyImportMap(account, "playlist", source.id, entity.id))
                        }
                        mutable.update { it.copy(imported = it.imported + 1) }
                    }
                }
                guard()
                val now = System.currentTimeMillis()
                if (mutable.value.errors == 0 && mutable.value.unmatched == 0) context.dataStore.edit { it[completedKey] = now }
                mutable.update { it.copy(running = false, phase = if (it.errors + it.unmatched == 0) "complete" else "partial", lastCompleted = if (it.errors + it.unmatched == 0) now else completed) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(running = false, phase = "error", errors = it.errors + 1) } }
            finally { mutable.update { it.copy(running = false) }; mutex.unlock() }
        }
    }
    companion object { private const val SIX_HOURS = 6 * 60 * 60 * 1000L }
}

/** Additive ordering: Spotify order first, retained local content follows once. */
internal fun mergeImportedPlaylist(remote: List<String>, local: List<String>): List<String> = (remote + local).distinct()
