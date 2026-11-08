/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import app.vibra.library.CHANNEL
import app.vibra.library.LibraryActions
import app.vibra.library.isPlaylistCreationMarker
import app.vibra.library.PLAYLIST
import app.vibra.library.VIDEO
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.SelectedYtmPlaylistsKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.PodcastEntity
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.PODCAST_LIBRARY_BROWSE_ID
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.models.toMediaMetadata
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val SYNC_DATABASE_BATCH_SIZE = 900
private const val LIKED_SONG_ORDER_STEP_NANOS = 1_000_000L
class LibraryLoginRequiredException : IllegalStateException()

class LibrarySyncDisabledException : IllegalStateException()

@Singleton
class SyncUtils
    @Inject
    constructor(
        private val database: MusicDatabase,
        val libraryActions: LibraryActions,
        private val downloadUtil: moe.rukamori.archivetune.playback.DownloadUtil,
        @ApplicationContext private val context: Context,
    ) {
        private val syncScope = CoroutineScope(Dispatchers.IO)
        private val syncEnabled = MutableStateFlow(true)
        private val syncGeneration = AtomicLong(0L)

        private val syncMutex = Mutex()
        private val likedSongsSyncMutex = Mutex()
        private val playlistSyncMutex = Mutex()
        private val podcastSyncMutex = Mutex()
        private val dbWriteSemaphore = Semaphore(2)

        init {
            syncScope.launch {
                context.dataStore.data
                    .map { it[YtmSyncKey] ?: true }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        syncEnabled.value = enabled
                        if (!enabled) {
                            syncGeneration.incrementAndGet()
                        }
                    }
            }
        }

        suspend fun performFullSync(authoritative: Boolean = false, propagateFailures: Boolean = false) =
            withContext(Dispatchers.IO) {
                if (authoritative) {
                    syncGeneration.incrementAndGet()
                    syncMutex.lock()
                } else if (propagateFailures) {
                    syncMutex.lock()
                } else if (!syncMutex.tryLock()) {
                    Timber.d("Sync already in progress, skipping")
                    return@withContext
                }

                try {
                    if (!isLoggedIn()) {
                        if (propagateFailures) throw LibraryLoginRequiredException()
                        Timber.w("Skipping full sync - user not logged in")
                        return@withContext
                    }
                    if (!isYtmSyncEnabled()) {
                        if (propagateFailures) throw LibrarySyncDisabledException()
                        Timber.w("Skipping full sync - sync disabled")
                        return@withContext
                    }

                    supervisorScope {
                        val songResults = listOf(
                            captureSyncFailure { syncLikedSongs(authoritative, propagateFailures = true) },
                            captureSyncFailure { syncLibrarySongs(authoritative, propagateFailures = true) },
                        )
                        val results = songResults + listOf(
                            async { captureSyncFailure { syncLikedAlbums(authoritative, propagateFailures = true) } },
                            async { captureSyncFailure { syncArtistsSubscriptions(authoritative, propagateFailures = true) } },
                            async { captureSyncFailure { syncSavedPlaylists(authoritative, propagateFailures = true) } },
                            async { captureSyncFailure { syncSavedPodcasts(propagateFailures = true) } },
                        ).awaitAll()
                        results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
                        if (!authoritative) syncAutoSyncPlaylists(propagateFailures)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (propagateFailures) throw e
                    Timber.e(e, "Error during full sync")
                } finally {
                    syncMutex.unlock()
                }
            }

        suspend fun requireLibrarySyncEnabled() {
            if (!isLoggedIn()) throw LibraryLoginRequiredException()
            if (!isYtmSyncEnabled()) throw LibrarySyncDisabledException()
        }

        private suspend fun captureSyncFailure(block: suspend () -> Unit): Result<Unit> =
            try {
                block()
                Result.success(Unit)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Library category sync failed")
                Result.failure(e)
            }

        suspend fun cleanupDuplicatePlaylists() =
            withContext(Dispatchers.IO) {
                try {
                    val allPlaylists = database.playlistsByNameAsc().first()
                    val browseIdGroups =
                        allPlaylists
                            .filter { it.playlist.browseId != null }
                            .groupBy { it.playlist.browseId }

                    val refresh = libraryActions.refreshSnapshot()
                    for ((browseId, playlists) in browseIdGroups) {
                        if (playlists.size < 2) continue
                        database.withTransaction {
                            if (playlists.any { libraryActions.protectsPlaylist(refresh, it.id, browseId) }) return@withTransaction
                            val toKeep = playlists.maxByOrNull { it.songCount } ?: return@withTransaction
                            playlists.filter { it.id != toKeep.id }.forEach { duplicate ->
                                getPlaylistById(duplicate.id)?.playlist
                                    ?.takeIf { it.browseId == browseId }
                                    ?.let { delete(it) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.e(e, "Error cleaning up duplicate playlists")
                }
            }

        private fun preferredPlaylistCopy(
            current: Playlist,
            candidate: Playlist,
        ): Playlist {
            val bySongCount = candidate.songCount.compareTo(current.songCount)
            if (bySongCount != 0) {
                return if (bySongCount > 0) candidate else current
            }

            val byLastUpdated = compareNullableDateTimes(candidate.playlist.lastUpdateTime, current.playlist.lastUpdateTime)
            if (byLastUpdated != 0) {
                return if (byLastUpdated > 0) candidate else current
            }

            val byCreatedAt = compareNullableDateTimes(candidate.playlist.createdAt, current.playlist.createdAt)
            if (byCreatedAt != 0) {
                return if (byCreatedAt > 0) candidate else current
            }

            if (candidate.playlist.isEditable != current.playlist.isEditable) {
                return if (candidate.playlist.isEditable) candidate else current
            }

            return if (candidate.id < current.id) candidate else current
        }

        private fun compareNullableDateTimes(
            first: LocalDateTime?,
            second: LocalDateTime?,
        ): Int {
            if (first == second) return 0
            if (first == null) return -1
            if (second == null) return 1
            return first.compareTo(second)
        }

        private fun canonicalPlaylistCopies(playlists: List<Playlist>): List<Playlist> =
            playlists
                .groupBy { it.playlist.browseId ?: it.id }
                .values
                .map { copies ->
                    copies.reduce(::preferredPlaylistCopy)
                }

        private suspend fun isLoggedIn(): Boolean {
            val cookie =
                context.dataStore.data
                    .map { it[InnerTubeCookieKey] }
                    .first()
            return hasYouTubeLoginCookie(cookie)
        }

        private suspend fun isYtmSyncEnabled(): Boolean {
            val enabled =
                context.dataStore.data
                    .map { it[YtmSyncKey] ?: true }
                    .first()
            syncEnabled.value = enabled
            if (!enabled) {
                syncGeneration.incrementAndGet()
            }
            return enabled
        }

        private fun isSyncStillEnabled(gen: Long): Boolean = syncEnabled.value && syncGeneration.get() == gen

        suspend fun syncLikedSongs(authoritative: Boolean = false, propagateFailures: Boolean = false) {
            if (authoritative || propagateFailures) {
                likedSongsSyncMutex.withLock {
                    syncLikedSongsInternal(authoritative = authoritative, propagateFailures = propagateFailures)
                }
                return
            }

            if (!likedSongsSyncMutex.tryLock()) {
                Timber.d("syncLikedSongs: Sync already in progress, skipping")
                return
            }

            try {
                syncLikedSongsInternal(authoritative = false, propagateFailures = propagateFailures)
            } finally {
                likedSongsSyncMutex.unlock()
            }
        }

        private suspend fun syncLikedSongsInternal(authoritative: Boolean, propagateFailures: Boolean) =
            coroutineScope {
                if (!isLoggedIn()) {
                    if (propagateFailures) throw LibraryLoginRequiredException()
                    Timber.w("Skipping syncLikedSongs - user not logged in")
                    return@coroutineScope
                }
                if (!isYtmSyncEnabled()) {
                    if (propagateFailures) throw LibrarySyncDisabledException()
                    Timber.w("Skipping syncLikedSongs - sync disabled")
                    return@coroutineScope
                }
                val refresh = libraryActions.refreshSnapshot()
                val gen = syncGeneration.get()
                YouTube
                    .library("VLLM")
                    .completed()
                    .onSuccess { page ->
                        if (!isSyncStillEnabled(gen) || !libraryActions.isCurrent(refresh)) return@onSuccess
                        val rawRemoteSongs = page.items.filterIsInstance<SongItem>().distinctBy { song -> song.id }
                        if (rawRemoteSongs.isEmpty() && !authoritative) {
                            Timber.w("syncLikedSongs: Remote playlist is empty")
                            return@onSuccess
                        }
                        val remoteSongs = rawRemoteSongs
                        val remoteIds = remoteSongs.map { it.id }.toSet()
                        val existingLikedDates =
                            remoteIds
                                .chunked(SYNC_DATABASE_BATCH_SIZE)
                                .flatMap { songIds -> database.likedSongDates(songIds) }
                                .mapNotNull { song ->
                                    song.likedDate?.let { likedDate -> song.id to likedDate }
                                }.toMap()
                        if (authoritative) {
                            val localLikedSongs = database.likedSongsByNameAsc().first()
                            if (!isSyncStillEnabled(gen)) return@onSuccess
                            val importedSpotifyLikes = database.spotifyImportedIds("liked").toSet()
                            val staleLikedSongs =
                                localLikedSongs
                                    .asSequence()
                                    .map { it.song }
                                    .filterNot { it.isLocal }
                                    .filterNot { it.id in remoteIds }
                                    .filterNot { it.id in importedSpotifyLikes }
                                    .map { it.copy(liked = false, likedDate = null) }
                                    .toList()
                            if (staleLikedSongs.isNotEmpty()) {
                                database.withTransaction {
                                    staleLikedSongs.forEach { staleSong ->
                                        if (libraryActions.isCurrent(refresh) && !libraryActions.protects(refresh, staleSong.id, VIDEO)) {
                                            update(staleSong)
                                        }
                                    }
                                }
                            }
                        }
                        val likedSongTimestamps =
                            reconcileLikedSongTimestamps(
                                songIds = remoteSongs.map { song -> song.id },
                                existingLikedDates = existingLikedDates,
                                fallbackTimestamp = LocalDateTime.now(),
                            )

                        remoteSongs.forEachIndexed { index, song ->
                            val timestamp = likedSongTimestamps[index]
                            launch {
                                if (!isSyncStillEnabled(gen)) return@launch
                                dbWriteSemaphore.withPermit {
                                    if (!isSyncStillEnabled(gen)) return@withPermit
                                    val dbSong = database.song(song.id).firstOrNull()
                                    val mediaMetadata = song.toMediaMetadata()
                                    database.withTransaction {
                                        if (!isSyncStillEnabled(gen)) return@withTransaction
                                        if (!libraryActions.isCurrent(refresh) || libraryActions.protects(refresh, song.id, VIDEO)) return@withTransaction
                                        if (dbSong == null) {
                                            insert(mediaMetadata) { it.copy(liked = true, likedDate = timestamp) }
                                        } else {
                                            update(dbSong, mediaMetadata)
                                            if (!dbSong.song.liked || dbSong.song.likedDate != timestamp) {
                                                getSongByIdBlocking(song.id)?.song?.let { refreshedSong ->
                                                    update(refreshedSong.copy(liked = true, likedDate = timestamp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }.onFailure { e ->
                        if (e is CancellationException || propagateFailures) throw e
                        Timber.e(e, "syncLikedSongs: Failed to sync liked songs")
                    }
            }

        suspend fun syncLibrarySongs(authoritative: Boolean = false, propagateFailures: Boolean = false) =
            coroutineScope {
                if (!isLoggedIn()) {
                    if (propagateFailures) throw LibraryLoginRequiredException()
                    Timber.w("Skipping syncLibrarySongs - user not logged in")
                    return@coroutineScope
                }
                if (!isYtmSyncEnabled()) {
                    if (propagateFailures) throw LibrarySyncDisabledException()
                    Timber.w("Skipping syncLibrarySongs - sync disabled")
                    return@coroutineScope
                }
                val refresh = libraryActions.refreshSnapshot()
                val gen = syncGeneration.get()
                YouTube
                    .library("FEmusic_liked_videos")
                    .completed()
                    .onSuccess { page ->
                        if (!isSyncStillEnabled(gen) || !libraryActions.isCurrent(refresh)) return@onSuccess
                        val rawRemoteSongs = page.items.filterIsInstance<SongItem>().reversed()
                        if (rawRemoteSongs.isEmpty() && !authoritative) {
                            Timber.w("syncLibrarySongs: Remote library is empty")
                            return@onSuccess
                        }
                        val remoteSongs = rawRemoteSongs
                        val remoteIds = remoteSongs.map { it.id }.toSet()
                        val localSongs = database.songsByNameAsc().first()
                        val importedSpotifyLikes = database.spotifyImportedIds("liked").toSet()

                        if (!isSyncStillEnabled(gen)) return@onSuccess
                        val staleLibrarySongs =
                            localSongs
                                .asSequence()
                                .filterNot { it.song.isLocal }
                                .filterNot { it.id in remoteIds }
                                .filterNot { it.id in importedSpotifyLikes }
                                .map { it.song.copy(inLibrary = null) }
                                .toList()
                        if (staleLibrarySongs.isNotEmpty()) {
                            database.withTransaction {
                                staleLibrarySongs.forEach { staleSong ->
                                    if (libraryActions.isCurrent(refresh) && !libraryActions.protects(refresh, staleSong.id, VIDEO)) {
                                        update(staleSong)
                                    }
                                }
                            }
                        }

                        remoteSongs.forEach { song ->
                            launch {
                                if (!isSyncStillEnabled(gen)) return@launch
                                dbWriteSemaphore.withPermit {
                                    if (!isSyncStillEnabled(gen)) return@withPermit
                                    val dbSong = database.song(song.id).firstOrNull()
                                    val mediaMetadata = song.toMediaMetadata()
                                    database.withTransaction {
                                        if (!isSyncStillEnabled(gen)) return@withTransaction
                                        if (!libraryActions.isCurrent(refresh) || libraryActions.protects(refresh, song.id, VIDEO)) return@withTransaction
                                        if (dbSong == null) {
                                            insert(mediaMetadata) { it.toggleLibrary() }
                                        } else {
                                            update(dbSong, mediaMetadata)
                                            if (dbSong.song.inLibrary == null) {
                                                getSongByIdBlocking(song.id)?.song?.let { refreshedSong ->
                                                    update(refreshedSong.toggleLibrary())
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }.onFailure { e ->
                        if (e is CancellationException || propagateFailures) throw e
                        Timber.e(e, "syncLibrarySongs: Failed to sync library songs")
                    }
            }

        suspend fun syncLikedAlbums(authoritative: Boolean = false, propagateFailures: Boolean = false) =
            coroutineScope {
                if (!isLoggedIn()) {
                    if (propagateFailures) throw LibraryLoginRequiredException()
                    Timber.w("Skipping syncLikedAlbums - user not logged in")
                    return@coroutineScope
                }
                if (!isYtmSyncEnabled()) {
                    if (propagateFailures) throw LibrarySyncDisabledException()
                    Timber.w("Skipping syncLikedAlbums - sync disabled")
                    return@coroutineScope
                }
                val refresh = libraryActions.refreshSnapshot()
                val gen = syncGeneration.get()
                YouTube
                    .library("FEmusic_liked_albums")
                    .completed()
                    .onSuccess { page ->
                        if (!isSyncStillEnabled(gen) || !libraryActions.isCurrent(refresh)) return@onSuccess
                        val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
                        if (remoteAlbums.isEmpty() && !authoritative) {
                            Timber.w("syncLikedAlbums: No liked albums found")
                            return@onSuccess
                        }
                        val remoteIds = remoteAlbums.map { it.id }.toSet()
                        val localAlbums = database.albumsLikedByNameAsc().first()

                        if (!isSyncStillEnabled(gen)) return@onSuccess
                        val staleAlbums =
                            localAlbums
                                .asSequence()
                                .filterNot { it.album.isLocal }
                                .filterNot { it.id in remoteIds }
                                .map { it.album.localToggleLike() }
                                .toList()
                        if (staleAlbums.isNotEmpty()) {
                            database.withTransaction {
                                if (!libraryActions.isCurrent(refresh)) return@withTransaction
                                staleAlbums.forEach {
                                    if (!libraryActions.protects(refresh, it.id, PLAYLIST, it.playlistId)) update(it)
                                }
                            }
                        }

                        remoteAlbums.forEach { album ->
                            launch {
                                if (!isSyncStillEnabled(gen)) return@launch
                                dbWriteSemaphore.withPermit {
                                    if (!isSyncStillEnabled(gen)) return@withPermit
                                    val needsMetadata = database.libraryAlbumEntity(album.id) == null
                                    val page = if (needsMetadata) YouTube.album(album.browseId) else Result.success(null)
                                    page.onSuccess { albumPage ->
                                        database.withTransaction {
                                            if (!isSyncStillEnabled(gen) || !libraryActions.isCurrent(refresh) ||
                                                libraryActions.protects(refresh, album.id, PLAYLIST, album.playlistId)
                                            ) return@withTransaction
                                            if (libraryAlbumEntity(album.id) == null && albumPage != null) insert(albumPage)
                                            val current = libraryAlbumEntity(album.id)
                                            if (current != null && current.bookmarkedAt == null) {
                                                update(current.localToggleLike())
                                            }
                                        }
                                    }.onFailure { e ->
                                        if (e is CancellationException || propagateFailures) throw e
                                        Timber.w("syncLikedAlbums: Failed to fetch album ${album.id}", e)
                                    }
                                }
                            }
                        }
                    }.onFailure { e ->
                        if (e is CancellationException || propagateFailures) throw e
                        Timber.e(e, "syncLikedAlbums: Failed to sync liked albums")
                    }
            }

        suspend fun syncArtistsSubscriptions(authoritative: Boolean = false, propagateFailures: Boolean = false) =
            coroutineScope {
                if (!isLoggedIn()) {
                    if (propagateFailures) throw LibraryLoginRequiredException()
                    Timber.w("Skipping syncArtistsSubscriptions - user not logged in")
                    return@coroutineScope
                }
                if (!isYtmSyncEnabled()) {
                    if (propagateFailures) throw LibrarySyncDisabledException()
                    Timber.w("Skipping syncArtistsSubscriptions - sync disabled")
                    return@coroutineScope
                }
                val refresh = libraryActions.refreshSnapshot()
                val gen = syncGeneration.get()
                YouTube
                    .library("FEmusic_library_corpus_artists")
                    .completed()
                    .onSuccess { page ->
                        if (!isSyncStillEnabled(gen) || !libraryActions.isCurrent(refresh)) return@onSuccess
                        val remoteArtists = page.items.filterIsInstance<ArtistItem>().distinctBy { it.id }
                        if (remoteArtists.isEmpty() && !authoritative) {
                            Timber.w("syncArtistsSubscriptions: No artist subscriptions found")
                            return@onSuccess
                        }
                        val now = LocalDateTime.now()
                        val remoteIds = remoteArtists.map { it.id }.toSet()
                        val localArtists = database.artistsBookmarkedByNameAsc().first()

                        if (!isSyncStillEnabled(gen)) return@onSuccess
                        val importedSpotifyArtists = database.spotifyImportedIds("artist").toSet()
                        val staleArtists =
                            localArtists
                                .asSequence()
                                .filterNot { it.artist.isLocal }
                                .filterNot { it.id in remoteIds }
                                .filterNot { it.id in importedSpotifyArtists }
                                .map { it.artist.copy(bookmarkedAt = null, lastUpdateTime = now) }
                                .toList()
                        if (staleArtists.isNotEmpty()) {
                            database.withTransaction {
                                if (!libraryActions.isCurrent(refresh)) return@withTransaction
                                staleArtists.forEach {
                                    if (!libraryActions.protects(refresh, it.id, CHANNEL, it.channelId)) update(it)
                                }
                            }
                        }

                        remoteArtists.forEachIndexed { index, artist ->
                            launch {
                                if (!isSyncStillEnabled(gen)) return@launch
                                dbWriteSemaphore.withPermit {
                                    if (!isSyncStillEnabled(gen)) return@withPermit
                                    val bookmarkedAt = now.minusSeconds(index.toLong())
                                    database.withTransaction {
                                        if (!isSyncStillEnabled(gen)) return@withTransaction
                                        if (!libraryActions.isCurrent(refresh) ||
                                            libraryActions.protects(refresh, artist.id, CHANNEL, artist.channelId)
                                        ) return@withTransaction
                                        val dbArtist = getArtistById(artist.id)
                                        if (dbArtist == null) {
                                            insert(
                                                ArtistEntity(
                                                    id = artist.id,
                                                    name = artist.title,
                                                    thumbnailUrl = artist.thumbnail,
                                                    channelId = artist.channelId,
                                                    bookmarkedAt = bookmarkedAt,
                                                ),
                                            )
                                        } else {
                                            val existing = dbArtist
                                            val syncedThumbnail = artist.thumbnail ?: existing.thumbnailUrl
                                            val syncedChannelId = artist.channelId ?: existing.channelId
                                            val metadataChanged =
                                                existing.name != artist.title ||
                                                    existing.thumbnailUrl != syncedThumbnail ||
                                                    existing.channelId != syncedChannelId
                                            val needsBookmark = existing.bookmarkedAt == null

                                            if (metadataChanged || needsBookmark) {
                                                update(
                                                    existing.copy(
                                                        name = artist.title,
                                                        thumbnailUrl = syncedThumbnail,
                                                        channelId = syncedChannelId,
                                                        lastUpdateTime = now,
                                                        bookmarkedAt = existing.bookmarkedAt ?: bookmarkedAt,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }.onFailure { e ->
                        if (e is CancellationException || propagateFailures) throw e
                        Timber.e(e, "syncArtistsSubscriptions: Failed to sync artist subscriptions")
                    }
            }

        suspend fun syncSavedPlaylists(authoritative: Boolean = false, propagateFailures: Boolean = false) =
            playlistSyncMutex.withLock {
                if (!isLoggedIn()) {
                    if (propagateFailures) throw LibraryLoginRequiredException()
                    Timber.w("Skipping syncSavedPlaylists - user not logged in")
                    return@withLock
                }
                if (!isYtmSyncEnabled()) {
                    if (propagateFailures) throw LibrarySyncDisabledException()
                    Timber.w("Skipping syncSavedPlaylists - sync disabled")
                    return@withLock
                }

                cleanupDuplicatePlaylists()

                val refresh = libraryActions.refreshSnapshot()
                val gen = syncGeneration.get()

                libraryActions.withRefreshAuth(refresh) { YouTube.library("FEmusic_liked_playlists").completed() }
                    .onSuccess { page ->
                        if (!isSyncStillEnabled(gen) || !libraryActions.isCurrent(refresh)) return@onSuccess
                        val remotePlaylists =
                            page.items
                                .filterIsInstance<PlaylistItem>()
                                .filterNot { it.id == "LM" || it.id == "SE" || isPlaylistCreationMarker(it.title) }
                                .reversed()

                        if (remotePlaylists.isEmpty() && !authoritative) {
                            Timber.w("syncSavedPlaylists: No playlists found")
                            return@onSuccess
                        }

                        val selectedCsv = context.dataStore.data.first()[SelectedYtmPlaylistsKey] ?: ""
                        val selectedIds =
                            selectedCsv
                                .split(',')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toSet()

                        val localPlaylists = database.playlistsByNameAsc().first()
                        if (!isSyncStillEnabled(gen)) return@onSuccess

                        val now = LocalDateTime.now()
                        val remoteLikedIds = remotePlaylists.map { it.id }.toSet()

                        val stalePlaylists =
                            localPlaylists
                                .asSequence()
                                .map { it.playlist }
                                .filter { it.browseId != null }
                                .filter { it.browseId !in remoteLikedIds }
                                .map { it.copy(bookmarkedAt = null, lastUpdateTime = now) }
                                .toList()
                        if (stalePlaylists.isNotEmpty()) {
                            database.withTransaction {
                                if (!libraryActions.isCurrent(refresh)) return@withTransaction
                                stalePlaylists.forEach {
                                    val latest = getPlaylistById(it.id)?.playlist ?: return@forEach
                                    if (latest.browseId == it.browseId &&
                                        !libraryActions.protects(refresh, it.id, PLAYLIST, it.browseId) &&
                                        !libraryActions.protectsPlaylist(refresh, it.id, it.browseId)
                                    ) update(latest.copy(bookmarkedAt = null, lastUpdateTime = now))
                                }
                            }
                        }

                        val localPlaylistIdByBrowseId = HashMap<String, String>(remotePlaylists.size)
                        for (playlist in remotePlaylists) {
                            if (!isSyncStillEnabled(gen)) return@onSuccess
                            try {
                                database.withTransaction {
                                    val existingPlaylist = playlistEntityByBrowseId(playlist.id)
                                    val localId = existingPlaylist?.id ?: playlist.id
                                    if (!libraryActions.isCurrent(refresh) ||
                                        libraryActions.protects(refresh, localId, PLAYLIST, playlist.id) ||
                                        libraryActions.protectsPlaylist(refresh, localId, playlist.id)
                                    ) return@withTransaction
                                    if (existingPlaylist == null) {
                                        val playlistEntity =
                                            PlaylistEntity(
                                                name = playlist.title,
                                                browseId = playlist.id,
                                                thumbnailUrl = playlist.thumbnail,
                                                isEditable = playlist.isEditable,
                                                bookmarkedAt = now,
                                                remoteSongCount =
                                                    playlist.songCountText?.let {
                                                        Regex(
                                                            """\d+""",
                                                        ).find(it)?.value?.toIntOrNull()
                                                    },
                                                playEndpointParams = playlist.playEndpoint?.params,
                                                shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                                radioEndpointParams = playlist.radioEndpoint?.params,
                                            )
                                        insert(playlistEntity)
                                        localPlaylistIdByBrowseId[playlist.id] = playlistEntity.id
                                        Timber.d("syncSavedPlaylists: Created new playlist ${playlist.title} (${playlist.id})")
                                    } else {
                                        val likedEntity =
                                            if (existingPlaylist.bookmarkedAt == null) {
                                                existingPlaylist.copy(bookmarkedAt = now, lastUpdateTime = now)
                                            } else {
                                                existingPlaylist
                                            }
                                        update(likedEntity, playlist)
                                        localPlaylistIdByBrowseId[playlist.id] = likedEntity.id
                                        Timber.d("syncSavedPlaylists: Updated existing playlist ${playlist.title} (${playlist.id})")
                                    }
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException || propagateFailures) throw e
                                Timber.e(e, "syncSavedPlaylists: Failed to upsert playlist ${playlist.title}")
                            }
                        }

                        val playlistsToSync =
                            if (selectedIds.isNotEmpty()) remotePlaylists.filter { it.id in selectedIds } else remotePlaylists
                        if (selectedIds.isNotEmpty() && playlistsToSync.isEmpty()) {
                            Timber.w(
                                "syncSavedPlaylists: Selected playlists not found in remote library; skipping song sync (selected=${selectedIds.size}, remote=${remotePlaylists.size})",
                            )
                        }

                        for (playlist in playlistsToSync) {
                            if (!isSyncStillEnabled(gen)) return@onSuccess
                            try {
                                val playlistId =
                                    localPlaylistIdByBrowseId[playlist.id]
                                        ?: database
                                            .playlistByBrowseId(playlist.id)
                                            .firstOrNull()
                                            ?.playlist
                                            ?.id
                                        ?: continue
                                syncPlaylist(
                                    browseId = playlist.id,
                                    playlistId = playlistId,
                                    authoritative = authoritative,
                                    propagateFailures = propagateFailures,
                                )
                            } catch (e: Exception) {
                                if (e is CancellationException || propagateFailures) throw e
                                Timber.e(e, "Failed to sync playlist ${playlist.title}")
                            }
                        }
                    }.onFailure { e ->
                        if (e is CancellationException || propagateFailures) throw e
                        Timber.e(e, "syncSavedPlaylists: Failed to fetch playlists from YouTube")
                    }
            }

        suspend fun syncSavedPodcasts(propagateFailures: Boolean = false) =
            podcastSyncMutex.withLock {
                if (!isLoggedIn()) {
                    if (propagateFailures) throw LibraryLoginRequiredException()
                    return@withLock
                }
                if (!isYtmSyncEnabled()) {
                    if (propagateFailures) throw LibrarySyncDisabledException()
                    return@withLock
                }

                val refresh = libraryActions.refreshSnapshot()
                try {
                    val remotePodcasts =
                        YouTube
                            .library(PODCAST_LIBRARY_BROWSE_ID)
                            .completed()
                            .getOrThrow()
                            .items
                            .filterIsInstance<PodcastItem>()
                            .distinctBy(PodcastItem::browseId)
                    val remoteIds = remotePodcasts.mapTo(HashSet()) { podcast -> podcast.browseId }
                    val now = LocalDateTime.now()

                    database.withTransaction {
                        if (!libraryActions.isCurrent(refresh)) return@withTransaction
                        getAllPodcasts()
                            .asSequence()
                            .filter { podcast -> podcast.remoteSavedAt != null && podcast.browseId !in remoteIds }
                            .forEach { podcast ->
                                if (!libraryActions.protects(refresh, podcast.browseId, PLAYLIST, podcast.playlistId)) {
                                    upsert(podcast.copy(remoteSavedAt = null, lastUpdateTime = now))
                                }
                            }
                        remotePodcasts.forEach { podcast ->
                            if (libraryActions.protects(refresh, podcast.browseId, PLAYLIST, podcast.playlistId)) return@forEach
                            val existing = getPodcast(podcast.browseId)
                            upsert(
                                PodcastEntity(
                                    browseId = podcast.browseId,
                                    playlistId = podcast.playlistId,
                                    title = podcast.title,
                                    authorName = podcast.author?.name,
                                    authorId = podcast.author?.id,
                                    thumbnailUrl = podcast.thumbnail,
                                    localSavedAt = existing?.localSavedAt,
                                    remoteSavedAt = existing?.remoteSavedAt ?: now,
                                    lastUpdateTime = now,
                                ),
                            )
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Timber.e(error, "syncSavedPodcasts: Failed to sync saved podcasts")
                    if (propagateFailures) throw error
                }
            }

        suspend fun syncAutoSyncPlaylists(propagateFailures: Boolean = false) =
            coroutineScope {
                if (!isLoggedIn()) {
                    if (propagateFailures) throw LibraryLoginRequiredException()
                    Timber.w("Skipping syncAutoSyncPlaylists - user not logged in")
                    return@coroutineScope
                }
                if (!isYtmSyncEnabled()) {
                    if (propagateFailures) throw LibrarySyncDisabledException()
                    Timber.w("Skipping syncAutoSyncPlaylists - sync disabled")
                    return@coroutineScope
                }

                cleanupDuplicatePlaylists()

                val gen = syncGeneration.get()
                val autoSyncPlaylists =
                    try {
                        canonicalPlaylistCopies(
                            database
                                .playlistsByNameAsc()
                                .first()
                                .filter { it.playlist.isAutoSync && it.playlist.browseId != null },
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (propagateFailures) throw e
                        Timber.e(e, "syncAutoSyncPlaylists: Failed to fetch auto-sync playlists")
                        return@coroutineScope
                    }

                Timber.d("syncAutoSyncPlaylists: Found ${autoSyncPlaylists.size} playlists to sync")

                autoSyncPlaylists.forEach { playlist ->
                    launch {
                        if (!isSyncStillEnabled(gen)) return@launch
                        try {
                            dbWriteSemaphore.withPermit {
                                if (!isSyncStillEnabled(gen)) return@withPermit
                                val browseId =
                                    playlist.playlist.browseId ?: run {
                                        Timber.w("syncAutoSyncPlaylists: browseId is null for playlist ${playlist.playlist.name}")
                                        return@withPermit
                                    }
                                syncPlaylist(
                                    browseId = browseId,
                                    playlistId = playlist.playlist.id,
                                    propagateFailures = propagateFailures,
                                )
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            if (propagateFailures) throw e
                            Timber.e(e, "Failed to sync playlist ${playlist.playlist.name}")
                        }
                    }
                }
            }

        suspend fun syncPlaylistNow(
            browseId: String,
            playlistId: String,
            propagateFailures: Boolean = false,
            onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
        ) = playlistSyncMutex.withLock {
            syncPlaylist(
                browseId = browseId,
                playlistId = playlistId,
                propagateFailures = propagateFailures,
                onProgress = onProgress,
            )
        }

        private suspend fun syncPlaylist(
            browseId: String,
            playlistId: String,
            authoritative: Boolean = false,
            propagateFailures: Boolean = false,
            onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
        ) = coroutineScope {
            if (!isYtmSyncEnabled()) {
                Timber.w("syncPlaylist: Skipping - sync disabled")
                return@coroutineScope
            }
            val refresh = libraryActions.refreshSnapshot()
            val gen = syncGeneration.get()
            suspend fun canApply(): Boolean =
                isSyncStillEnabled(gen) && libraryActions.isCurrent(refresh) &&
                    !libraryActions.protectsPlaylist(refresh, playlistId, browseId) &&
                    database.getPlaylistById(playlistId)?.playlist?.browseId == browseId
            if (!canApply()) return@coroutineScope
            Timber.d("syncPlaylist: Starting sync for browseId=$browseId, playlistId=$playlistId")

            val page =
                libraryActions.withRefreshAuth(refresh) { YouTube.playlist(browseId).completed() }.getOrElse { e ->
                    Timber.e(e, "syncPlaylist: Failed to fetch playlist from YouTube")
                    if (propagateFailures) {
                        throw e
                    }
                    return@coroutineScope
                }
            if (!canApply()) return@coroutineScope
            val songs =
                page.songs
                    .orEmpty()
                    .map(SongItem::toMediaMetadata)
            Timber.d("syncPlaylist: Fetched ${songs.size} songs from remote")

            if (songs.isEmpty()) {
                if (authoritative) {
                    database.withTransaction {
                        if (!canApply()) return@withTransaction
                        database.clearPlaylist(playlistId)
                    }
                }
                Timber.w("syncPlaylist: Remote playlist is empty, skipping sync")
                return@coroutineScope
            }

            val remoteIds = songs.mapNotNull { it.id }
            if (remoteIds.isEmpty()) {
                Timber.w("syncPlaylist: No valid song IDs found, skipping sync")
                return@coroutineScope
            }

            val downloadSnapshot = downloadUtil.playlistDownloadSnapshot(playlistId)
            val localIds =
                try {
                    database
                        .playlistSongs(playlistId)
                        .first()
                        .sortedBy { it.map.position }
                        .map { it.song.id }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.w("syncPlaylist: Failed to fetch local songs", e)
                    emptyList()
                }

            if (remoteIds == localIds) {
                database.withTransaction {
                    if (!canApply()) return@withTransaction
                    val entries = playlistEntries(playlistId)
                    if (entries.map { it.songId } != remoteIds) return@withTransaction
                    entries.zip(songs).forEach { (entry, song) ->
                        val setId = song.setVideoId
                        if (setId != null && setId != entry.setVideoId) update(entry.copy(setVideoId = setId))
                    }
                    songs.forEach { song ->
                        val existingSong = getSongByIdBlocking(song.id)
                        if (existingSong == null) {
                            insert(song)
                        } else {
                            update(existingSong, song)
                        }
                    }
                }
                Timber.d("syncPlaylist: Local and remote are in sync, no changes needed")
                onProgress(remoteIds.size, remoteIds.size)
                return@coroutineScope
            }

            Timber.d("syncPlaylist: Updating local playlist (remote: ${remoteIds.size}, local: ${localIds.size})")

            try {
                onProgress(0, remoteIds.size)
                val applied = database.withTransaction {
                    if (!canApply()) return@withTransaction false
                    database.clearPlaylist(playlistId)
                    var completedSongs = 0
                    songs.forEachIndexed { idx, song ->
                        val songId = song.id ?: return@forEachIndexed
                        val existingSong = getSongByIdBlocking(songId)
                        if (existingSong == null) {
                            database.insert(song)
                        } else {
                            database.update(existingSong, song)
                        }
                        database.insert(
                            PlaylistSongMap(
                                songId = songId,
                                playlistId = playlistId,
                                position = idx,
                                setVideoId = song.setVideoId,
                            ),
                        )
                        completedSongs += 1
                        onProgress(completedSongs, remoteIds.size)
                    }
                    check(libraryActions.isCurrent(refresh))
                    true
                }
                if (applied) downloadUtil.downloadPlaylistAdditions(downloadSnapshot, remoteIds)
                Timber.d("syncPlaylist: Successfully synced playlist")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "syncPlaylist: Error during database transaction")
                if (propagateFailures) {
                    throw e
                }
            }
        }
    }

internal fun reconcileLikedSongTimestamps(
    songIds: List<String>,
    existingLikedDates: Map<String, LocalDateTime>,
    fallbackTimestamp: LocalDateTime,
): List<LocalDateTime> {
    if (songIds.isEmpty()) return emptyList()

    val timestamps = songIds.map { songId -> existingLikedDates[songId] }.toMutableList()
    var index = 0
    while (index < timestamps.size) {
        if (timestamps[index] != null) {
            index += 1
            continue
        }

        val runStart = index
        while (index < timestamps.size && timestamps[index] == null) {
            index += 1
        }
        val runEnd = index
        val previousTimestamp = timestamps.getOrNull(runStart - 1)
        val nextTimestamp = timestamps.getOrNull(runEnd)

        for (runIndex in runStart until runEnd) {
            timestamps[runIndex] =
                if (previousTimestamp != null) {
                    previousTimestamp.minusNanos(
                        (runIndex - runStart + 1).toLong() * LIKED_SONG_ORDER_STEP_NANOS,
                    )
                } else if (nextTimestamp != null) {
                    nextTimestamp.plusNanos(
                        (runEnd - runIndex).toLong() * LIKED_SONG_ORDER_STEP_NANOS,
                    )
                } else {
                    fallbackTimestamp.minusNanos(runIndex.toLong() * LIKED_SONG_ORDER_STEP_NANOS)
                }
        }
    }

    return timestamps.map { timestamp ->
        checkNotNull(timestamp)
    }
}
