/*
 * Vibra (2026) — GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.library

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.room.Entity
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.AlbumEntity
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.toPlaybackAuthState
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "library_mutation", primaryKeys = ["account", "target", "targetId"])
data class LibraryMutation(
    val account: String,
    val target: String,
    val targetId: String,
    val localId: String,
    val desired: Boolean,
    val revision: String,
    val createdAt: Long,
    val completedAt: Long? = null,
    val rejected: Boolean = false,
    val nextAttemptAt: Long? = null,
)

private const val LOCAL_CONTENTS = ""
private const val CONTENTS = "contents"
private const val ALBUM_CONTENTS = "album_contents"
internal const val VIDEO = "video"
internal const val PLAYLIST = "playlist"
internal const val CHANNEL = "channel"
private const val PROPAGATION_MILLIS = 5 * 60 * 1_000L
private const val COMPLETED_RETENTION_MILLIS = 24 * 60 * 60 * 1_000L

internal class LibraryRefresh(val account: String?, val startedAt: Long, val auth: PlaybackAuthState)

@Singleton
class LibraryActions internal constructor(
    private val database: MusicDatabase,
    private val preferences: Flow<Preferences>,
    private val now: () -> Long = System::currentTimeMillis,
    private val coverDirectory: File? = null,
    private val scheduleRetry: (Long) -> Unit = {},
    private val enqueueWork: () -> Unit,
) {
    @Inject
    constructor(@ApplicationContext context: Context, database: MusicDatabase) : this(
        database,
        context.dataStore.data,
        coverDirectory = File(context.filesDir, "playlist-sync-covers"),
        scheduleRetry = { scheduleLibraryWakeup(context, it) },
        enqueueWork = { scheduleLibraryWork(context) },
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainMutex = Mutex()
    private val playlistOutbox = PlaylistOutbox(
        database, now, { coverFile(it).takeIf(File::isFile)?.readBytes() ?: throw PlaylistEditConflict() }, { coverFile(it).delete() },
    )
    private val workRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (ignored in workRequests) {
                delay(100)
                workRequests.tryReceive()
                localResult { enqueueWork() }
                    .onFailure { Timber.w("Could not schedule library sync (%s)", it.javaClass.simpleName) }
            }
        }
    }

    fun start(context: Context? = null) {
        context?.let(::scheduleLibraryRecovery)
        scope.launch {
            preferences
                .map { it.syncAccount() }
                .distinctUntilChanged()
                .collect { account ->
                    if (database.pendingLibraryMutations(LOCAL_CONTENTS).isNotEmpty() ||
                        account != null && (database.pendingLibraryMutations(account).isNotEmpty() ||
                            database.pendingPlaylistEdits(account).any { !it.blocked })
                    ) schedule()
                }
        }
    }

    fun toggleAlbum(album: AlbumEntity, insertIfMissing: Boolean = false): Job = submit {
        mutate {
            val stored = libraryAlbumEntity(album.id)
            val current = stored ?: album.takeIf { insertIfMissing } ?: return@mutate
            val next = current.localToggleLike()
            if (stored == null) insert(next) else update(next)
            if (!current.isLocal) {
                enqueue(PLAYLIST, current.playlistId, current.id, next.bookmarkedAt != null)
                if (current.songCount == 0 && next.bookmarkedAt != null) {
                    enqueue(ALBUM_CONTENTS, current.id, current.id, true, account = LOCAL_CONTENTS)
                }
            }
        }
    }

    fun toggleAlbum(album: AlbumItem): Job = toggleAlbum(
        AlbumEntity(
            id = album.id,
            playlistId = album.playlistId,
            title = album.title,
            year = album.year,
            thumbnailUrl = album.thumbnail,
            songCount = 0,
            duration = 0,
            explicit = album.explicit,
        ),
        insertIfMissing = true,
    )

    fun toggleArtist(artist: ArtistEntity): Job = submit {
        mutate {
            val stored = getArtistById(artist.id)
            val current = stored ?: artist
            val next = current.localToggleLike()
            if (stored == null) insert(next) else update(next)
            if (!current.isLocal) enqueue(CHANNEL, current.channelId ?: current.id, current.id, next.bookmarkedAt != null)
        }
    }

    suspend fun removePlaylists(ids: List<String>): Result<Unit> = localResult {
        mutate {
            ids.distinct().forEach { id ->
                val current = getPlaylistById(id)?.playlist ?: return@forEach
                if (!current.isEditable) {
                    if (!current.isLocal && current.bookmarkedAt != null) {
                        enqueue(PLAYLIST, current.browseId, current.id, false)
                    }
                } else {
                    val edit = playlistEditFor(current, DELETE)
                    if (edit != null) {
                        val pending = playlistEdits(id)
                        val creation = pending.firstOrNull { it.kind == CREATE }
                        pending.filter { it.kind != CREATE }.forEach { deletePlaylistEdit(it.id) }
                        if (creation != null && creation.attempt == null) {
                            deletePlaylistEdit(creation.id)
                        } else {
                            enqueueEdit(edit)
                        }
                    }
                }
                delete(current)
            }
        }
    }

    suspend fun createPlaylist(name: String, publish: Boolean): Result<String> = localResult {
        mutate {
            val entity = PlaylistEntity(name = name, bookmarkedAt = LocalDateTime.now())
            insert(entity)
            if (publish) queuePublication(entity)
            entity.id
        }
    }

    suspend fun publishPlaylist(id: String): Result<Unit> = localResult {
        mutate {
            val current = requireNotNull(getPlaylistById(id)).playlist
            if (current.browseId == null && playlistEdits(id).none { it.kind == CREATE }) queuePublication(current)
        }
    }

    suspend fun renamePlaylist(id: String, name: String): Result<Unit> = localResult {
        mutate {
            val playlist = requireNotNull(getPlaylistById(id)).playlist
            check(playlist.isEditable)
            if (playlist.name != name) {
                playlistEditFor(playlist, RENAME)?.let {
                    val pendingRename = playlistEdits(id).firstOrNull { old -> old.kind == RENAME }
                    if (pendingRename == null) {
                        enqueueEdit(it.copy(value = name))
                    } else {
                        updatePlaylistEdit(pendingRename.copy(value = name, nextAttemptAt = null, blocked = false))
                        queued = true
                    }
                }
                update(playlist.copy(name = name, lastUpdateTime = LocalDateTime.now()))
            }
        }
    }

    suspend fun addPlaylistSongs(
        id: String,
        songIds: List<String>,
        metadata: List<MediaMetadata> = emptyList(),
        allowDuplicates: Boolean = false,
    ): Result<List<String>> = localResult {
        mutate {
            val playlist = requireNotNull(getPlaylistById(id)).playlist
            check(playlist.isEditable)
            val edit = playlistEditFor(playlist, ADD)
            val present = playlistEntries(id).mapTo(mutableSetOf()) { it.songId }
            val songs = if (allowDuplicates) songIds else songIds.filter { present.add(it) }
            val metadataById = metadata.associateBy { it.id }
            var position = (maxPlaylistSongPosition(id) ?: -1) + 1
            songs.forEach { songId ->
                if (getSongById(songId) == null) insert(requireNotNull(metadataById[songId]))
                if (edit != null) check(getSongById(songId)?.song?.isLocal == false)
                val map = PlaylistSongMap(playlistId = id, songId = songId, position = position++)
                val entryId = insertPlaylistEntry(map).toInt()
                edit?.let { enqueueEdit(it.forEntry(map.copy(id = entryId)).copy(value = "duplicate".takeIf { allowDuplicates })) }
            }
            if (songs.isNotEmpty()) update(playlist.copy(lastUpdateTime = LocalDateTime.now()))
            songs
        }
    }

    suspend fun removePlaylistSongs(
        id: String,
        songIds: Set<String> = emptySet(),
        entryIds: Set<Int> = emptySet(),
    ): Result<Unit> = localResult {
        mutate {
            val playlist = requireNotNull(getPlaylistById(id)).playlist
            check(playlist.isEditable)
            val edit = playlistEditFor(playlist, REMOVE)
            val entries = playlistEntries(id)
            val (removed, kept) = entries.partition { it.id in entryIds || it.songId in songIds }
            removed.forEach { entry ->
                edit?.let { enqueueEdit(it.forEntry(entry)) }
                delete(entry)
            }
            kept.forEachIndexed { index, entry -> if (entry.position != index) update(entry.copy(position = index)) }
            if (removed.isNotEmpty()) update(playlist.copy(lastUpdateTime = LocalDateTime.now()))
        }
    }

    suspend fun movePlaylistSong(id: String, entryId: Int, beforeId: Int?, afterId: Int?, toEnd: Boolean): Result<Unit> =
        localResult {
            mutate {
                val playlist = requireNotNull(getPlaylistById(id)).playlist
                check(playlist.isEditable)
                val entries = playlistEntries(id).toMutableList()
                val entry = entries.firstOrNull { it.id == entryId } ?: return@mutate
                entries.remove(entry)
                val before = entries.firstOrNull { it.id == beforeId }
                val after = entries.firstOrNull { it.id == afterId }
                val index = when {
                    toEnd -> entries.size
                    after != null -> entries.indexOf(after)
                    before != null -> entries.indexOf(before) + 1
                    else -> return@mutate
                }
                entries.add(index, entry)
                entries.forEachIndexed { position, item -> if (item.position != position) update(item.copy(position = position)) }
                playlistEditFor(playlist, MOVE)?.let {
                    enqueueEdit(it.forEntry(entry).copy(
                        beforeId = before?.id, beforeSetId = before?.setVideoId,
                        afterId = after?.id, afterSetId = after?.setVideoId, toEnd = toEnd,
                    ))
                }
                update(playlist.copy(lastUpdateTime = LocalDateTime.now()))
            }
        }

    suspend fun isPublishedOrPending(id: String): Boolean =
        database.getPlaylistById(id)?.playlist?.browseId != null || database.playlistEdits(id).any { it.kind == CREATE }

    suspend fun changePlaylistCover(id: String, image: ByteArray?): Result<Unit> = localResult {
        val name = image?.let { UUID.randomUUID().toString() }
        val file = name?.let(::coverFile)
        try {
            if (image != null) {
                checkNotNull(file).parentFile?.mkdirs()
                file.writeBytes(image)
            }
            mutate {
                val playlist = requireNotNull(getPlaylistById(id)).playlist
                check(playlist.isEditable)
                val edit = requireNotNull(playlistEditFor(playlist, COVER))
                enqueueEdit(edit.copy(value = name))
                update(playlist.copy(thumbnailUrl = file?.let { Uri.fromFile(it).toString() }, lastUpdateTime = LocalDateTime.now()))
            }
        } catch (error: Exception) {
            // A cancelled transaction may already have committed. Keep its file available for the worker.
            if (error !is CancellationException) file?.delete()
            throw error
        }
    }

    fun observePlaylistSync(id: String): Flow<PlaylistSyncStatus?> =
        database.observePlaylistEdits(id).map { pending ->
            val edit = pending.firstOrNull() ?: return@map null
            val uncertain = edit.attempt != null && (edit.nextAttemptAt != null || edit.blocked)
            val state = when {
                uncertain && edit.kind == CREATE -> PlaylistSyncState.ConfirmCreation
                uncertain && edit.kind == ADD && !edit.blocked -> PlaylistSyncState.ConfirmAddition
                edit.blocked -> PlaylistSyncState.NeedsAttention
                else -> PlaylistSyncState.Pending
            }
            PlaylistSyncStatus(state, edit.id)
        }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLibrarySync(): Flow<LibrarySyncState?> =
        preferences.map { it.accountIdentity() }.distinctUntilChanged().flatMapLatest { account ->
            if (account == null) flowOf(null)
            else database.observeLibraryOutboxState(account).map { status ->
                when (status) {
                    1 -> LibrarySyncState.Pending
                    2 -> LibrarySyncState.NeedsAttention
                    3 -> LibrarySyncState.RemovalNeedsAttention
                    else -> null
                }
            }
        }.distinctUntilChanged()

    suspend fun retryRemovedPlaylists(): Result<Unit> = localResult {
        drainMutex.withLock {
            val account = requireNotNull(preferences.first().syncAccount())
            database.retryRemovedPlaylistEdits(account)
            schedule()
        }
    }

    suspend fun retryPlaylist(id: String, confirmedEditId: Long? = null): Result<Unit> = localResult {
        drainMutex.withLock {
            mutate {
                val pending = playlistEdits(id).firstOrNull() ?: return@mutate
                check(pending.account == account && syncEnabled)
                val attempt = if (pending.id == confirmedEditId) pending.confirmAttempt() else pending.attempt
                updatePlaylistEdit(pending.copy(attempt = attempt, nextAttemptAt = null, blocked = false))
                queued = true
            }
        }
    }

    internal suspend fun protectsPlaylist(
        refresh: LibraryRefresh, id: String, remoteId: String? = null,
    ): Boolean = database.protectsPlaylistEdit(id, remoteId?.removePrefix("VL"), protectionSince(refresh))

    private fun coverFile(name: String): File {
        check(UUID.fromString(name).toString() == name)
        return File(checkNotNull(coverDirectory), name)
    }

    fun togglePlaylist(playlist: PlaylistEntity): Job = submit {
        mutate {
            val current = getPlaylistById(playlist.id)?.playlist ?: return@mutate
            savePlaylistToggle(current)
        }
    }

    fun togglePlaylist(playlist: PlaylistItem, songs: List<SongItem>): Job = submit {
        val created = mutate {
            val stored = playlistEntityByBrowseId(playlist.id)
            if (stored != null) {
                if (!protectsPlaylistEdit(stored.id, playlist.id.removePrefix("VL"), now() - PROPAGATION_MILLIS)) {
                    update(stored, playlist)
                }
                val current = playlistEntityByBrowseId(playlist.id) ?: stored
                savePlaylistToggle(current)
                null
            } else {
                val created = PlaylistEntity(
                    name = playlist.title,
                    browseId = playlist.id,
                    thumbnailUrl = playlist.thumbnail,
                    isEditable = playlist.isEditable,
                    remoteSongCount = playlist.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                    playEndpointParams = playlist.playEndpoint?.params,
                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                    radioEndpointParams = playlist.radioEndpoint?.params,
                ).localToggleLike()
                insert(created)
                enqueue(PLAYLIST, playlist.id, created.id, true)
                if (songs.isEmpty()) enqueue(CONTENTS, playlist.id, created.id, true, account = LOCAL_CONTENTS)
                created
            }
        }
        if (created != null && songs.isNotEmpty()) {
            database.withTransaction {
                val current = playlistEntityByBrowseId(playlist.id)
                if (current?.id == created.id && current.bookmarkedAt == created.bookmarkedAt) {
                    Mutation(null).seedPlaylist(created.id, songs)
                }
            }
        }
    }

    private suspend fun Mutation.savePlaylistToggle(current: PlaylistEntity) {
        val next = current.localToggleLike()
        database.update(next)
        if (!current.isLocal) {
            enqueue(PLAYLIST, current.browseId, current.id, next.bookmarkedAt != null)
            val pending = current.browseId?.let { database.libraryMutation(LOCAL_CONTENTS, CONTENTS, it) }
            if (pending != null && pending.completedAt == null) {
                enqueue(CONTENTS, current.browseId, current.id, next.bookmarkedAt != null, account = LOCAL_CONTENTS)
            }
        }
    }

    suspend fun changeSong(
        id: String,
        metadata: MediaMetadata? = null,
        transform: (SongEntity) -> SongEntity,
    ): Result<SongEntity> = localResult {
        mutate {
            val current = getSongById(id)?.song ?: run {
                insert(requireNotNull(metadata))
                checkNotNull(getSongById(id)?.song)
            }
            val next = transform(current)
            if (current != next) {
                update(next)
                if (!current.isLocal) enqueue(VIDEO, id, id, next.liked)
            }
            next
        }
    }

    suspend fun changeSongs(
        ids: Collection<String>,
        transform: (SongEntity) -> SongEntity,
    ): Set<String> = coroutineScope {
        val failedSongIds = linkedSetOf<String>()
        ids.distinct().chunked(8).forEach { batch ->
            batch.map { id ->
                async { id to changeSong(id, transform = transform) }
            }.awaitAll()
                .filter { (_, result) -> result.isFailure }
                .forEach { (id, _) -> failedSongIds += id }
        }
        failedSongIds
    }

    suspend fun setPodcastSaved(id: String, save: Boolean): Result<Unit> = localResult {
        mutate {
            val current = requireNotNull(getPodcast(id))
            val now = LocalDateTime.now()
            upsert(current.copy(
                localSavedAt = if (save) current.localSavedAt ?: now else null,
                remoteSavedAt = if (save) current.remoteSavedAt else null,
                lastUpdateTime = now,
            ))
            enqueue(PLAYLIST, current.playlistId, id, save)
        }
    }

    internal suspend fun refreshSnapshot(): LibraryRefresh {
        val prefs = preferences.first()
        return LibraryRefresh(prefs.syncAccount(), now(), prefs.toPlaybackAuthState())
    }

    internal suspend fun <T> withRefreshAuth(refresh: LibraryRefresh, fetch: suspend () -> T): T =
        YouTube.withLibraryAuth(refresh.auth, fetch)

    internal suspend fun isCurrent(refresh: LibraryRefresh): Boolean =
        refresh.account != null && refresh.account == preferences.first().syncAccount()

    internal suspend fun protects(refresh: LibraryRefresh, localId: String, target: String, targetId: String? = null): Boolean {
        val account = refresh.account ?: return false
        return database.hasProtectedLibraryMutation(account, localId, target, targetId, protectionSince(refresh))
    }

    private fun protectionSince(refresh: LibraryRefresh): Long =
        minOf(refresh.startedAt, now() - PROPAGATION_MILLIS)

    private fun submit(action: suspend () -> Unit): Job = scope.launch {
        localResult(action).onFailure { Timber.w("Local library action failed (%s)", it.javaClass.simpleName) }
    }

    private inner class Mutation(
        val account: String?,
        val syncEnabled: Boolean = true,
    ) : moe.rukamori.archivetune.db.DatabaseDao by database {
        var queued = false

        suspend fun playlistEditFor(playlist: PlaylistEntity, kind: String): PlaylistEdit? {
            val pending = playlistEdits(playlist.id).firstOrNull()
            val remoteId = playlist.browseId?.removePrefix("VL")
            if (remoteId == null && pending?.kind != CREATE) return null
            val owner = requireNotNull(pending?.account ?: playlistEditAccount(playlist.id) ?: account)
            check(account == null || account == owner)
            return PlaylistEdit(account = owner, playlistId = playlist.id, kind = kind, remoteId = remoteId)
        }

        suspend fun enqueueEdit(edit: PlaylistEdit) {
            enqueuePlaylistEdit(edit)
            queued = true
        }

        suspend fun queuePublication(playlist: PlaylistEntity) {
            check(syncEnabled && playlist.isEditable)
            val edit = PlaylistEdit(account = requireNotNull(account), playlistId = playlist.id, kind = CREATE)
            enqueueEdit(edit.copy(value = playlistCreationMarker()))
            enqueueEdit(edit.copy(kind = RENAME, value = playlist.name))
            playlistEntries(playlist.id).forEach { entry ->
                check(getSongById(entry.songId)?.song?.isLocal == false)
                enqueueEdit(edit.copy(kind = ADD, value = "duplicate").forEntry(entry))
            }
        }

        suspend fun enqueue(target: String, targetId: String?, localId: String, desired: Boolean, account: String? = this.account) {
            if (account == null || targetId.isNullOrBlank()) return
            val localOnly = account != LOCAL_CONTENTS && !syncEnabled
            if (localOnly) {
                val previous = libraryMutation(account, target, targetId)
                if (previous == null || previous.completedAt != null) return
            }
            saveLibraryMutation(
                LibraryMutation(
                    account = account,
                    target = target,
                    targetId = targetId,
                    localId = localId,
                    desired = desired,
                    revision = UUID.randomUUID().toString(),
                    createdAt = now(),
                    rejected = localOnly,
                ),
            )
            queued = queued || !localOnly
        }

        fun seedPlaylist(id: String, songs: List<SongItem>) {
            if (playlistSongIdsForAutoDownload(id).isNotEmpty()) return
            songs.forEachIndexed { index, song ->
                insert(song.toMediaMetadata())
                insert(PlaylistSongMap(playlistId = id, songId = song.id, position = index, setVideoId = song.setVideoId))
            }
        }
    }

    private suspend fun <T> mutate(action: suspend Mutation.() -> T): T {
        val prefs = preferences.first()
        val mutation = Mutation(prefs.accountIdentity(), syncEnabled = prefs[YtmSyncKey] != false)
        return database.withTransaction { mutation.action() }.also {
            if (mutation.queued) schedule()
        }
    }

    private fun schedule() {
        workRequests.trySend(Unit)
    }

    internal suspend fun drain(
        remote: PlaylistRemote = YouTubePlaylistRemote,
        sendMutation: suspend (LibraryMutation, PlaybackAuthState) -> Unit = ::send,
    ): Boolean = drainMutex.withLock {
        while (true) {
            val account = preferences.first().syncAccount()
            val pending = database.pendingLibraryMutations(LOCAL_CONTENTS) +
                (account?.let { database.pendingLibraryMutations(it) } ?: emptyList())
            val playlistHeads = account?.let { database.pendingPlaylistEdits(it) }.orEmpty()
                .distinctBy { it.playlistId }.filterNot { it.blocked }
            val dueLikes = pending.filter { (it.nextAttemptAt ?: 0) <= now() }
            val duePlaylists = playlistHeads.filter { (it.nextAttemptAt ?: 0) <= now() }
            if (dueLikes.isEmpty() && duePlaylists.isEmpty()) {
                val earliest = (pending.mapNotNull { it.nextAttemptAt } + playlistHeads.mapNotNull { it.nextAttemptAt }).minOrNull()
                earliest?.let(scheduleRetry)
                database.deleteCompletedLibraryMutations(now() - COMPLETED_RETENTION_MILLIS)
                database.prunePlaylistEdits(now() - COMPLETED_RETENTION_MILLIS)
                val coverFiles = coverDirectory?.listFiles().orEmpty()
                    .filter { it.lastModified() < now() - COMPLETED_RETENTION_MILLIS }
                if (coverFiles.isNotEmpty()) {
                    val active = database.activeCoverFiles().toSet()
                    coverFiles.filterNot { it.name in active }.forEach { it.delete() }
                }
                return@withLock earliest == null
            }
            for (mutation in dueLikes) {
                val prefs = preferences.first()
                if (mutation.account != LOCAL_CONTENTS && mutation.account != prefs.syncAccount()) continue
                val current = database.libraryMutation(mutation.account, mutation.target, mutation.targetId)
                if (current?.revision != mutation.revision) continue
                val failure = localResult {
                    val auth = prefs.toPlaybackAuthState()
                    if (mutation.account == LOCAL_CONTENTS) sendMutation(mutation, auth)
                    else YouTube.withLibraryAuth(auth) { sendMutation(mutation, auth) }
                }.exceptionOrNull()
                database.withTransaction {
                    val latest = libraryMutation(mutation.account, mutation.target, mutation.targetId)
                    if (latest?.revision == mutation.revision) {
                        saveLibraryMutation(when {
                            failure == null -> latest.copy(completedAt = now(), nextAttemptAt = null)
                            failure.isRetryable() -> latest.copy(nextAttemptAt = now() + RETRY_MILLIS)
                            else -> latest.copy(rejected = true)
                        })
                    }
                }
                if (failure != null) Timber.w("Library sync failed (%s)", failure.javaClass.simpleName)
            }
            for (edit in duePlaylists) {
                val prefs = preferences.first()
                if (edit.account != prefs.syncAccount()) continue
                val failure = localResult {
                    playlistOutbox.apply(edit, prefs.toPlaybackAuthState(), {
                        if (preferences.first().syncAccount() != edit.account) throw SessionChanged()
                    }, remote)
                }.exceptionOrNull() ?: continue
                database.withTransaction {
                    val current = playlistEdit(edit.id) ?: return@withTransaction
                    if (current.kind == RENAME && current.value != edit.value) return@withTransaction
                    val retry = failure.isRetryable() ||
                        current.attempt != null && failure !is PlaylistEditConflict
                    updatePlaylistEdit(current.copy(nextAttemptAt = (now() + RETRY_MILLIS).takeIf { retry }, blocked = !retry))
                }
                Timber.w("Playlist sync failed (%s)", failure.javaClass.simpleName)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        true
    }

    private suspend fun send(mutation: LibraryMutation, auth: PlaybackAuthState) {
        val id = mutation.targetId
        when (mutation.target) {
            VIDEO -> YouTube.likeVideo(id, mutation.desired, auth).getOrThrow()
            PLAYLIST -> YouTube.likePlaylist(id, mutation.desired, auth).getOrThrow()
            CHANNEL -> {
                val channel = id.takeIf { it.startsWith("UC") } ?: YouTube.artist(id, auth).getOrThrow().artist.channelId.orEmpty()
                check(channel.isNotBlank())
                if (preferences.first().syncAccount() != mutation.account) throw SessionChanged()
                YouTube.subscribeChannel(channel, mutation.desired, auth).getOrThrow()
            }
            ALBUM_CONTENTS -> {
                val current = database.libraryAlbumEntity(id)
                if (current?.bookmarkedAt != null && current.songCount == 0) {
                    val page = YouTube.album(id).getOrThrow()
                    database.withTransaction {
                        val latest = libraryAlbumEntity(id)
                        if (latest != null && latest.bookmarkedAt == current.bookmarkedAt && latest.songCount == 0) {
                            update(latest, page)
                        }
                    }
                }
            }
            CONTENTS -> if (mutation.desired) {
                val songs = YouTube.playlist(id).completed().getOrThrow().songs
                database.withTransaction {
                    val current = libraryMutation(mutation.account, mutation.target, id)
                    val playlist = playlistEntityByBrowseId(id)
                    if (current?.revision == mutation.revision && playlist?.id == mutation.localId && playlist.bookmarkedAt != null &&
                        !protectsPlaylistEdit(playlist.id, id, now() - PROPAGATION_MILLIS)
                    ) {
                        Mutation(null).seedPlaylist(playlist.id, songs)
                    }
                }
            }
        }
    }
}

enum class LibrarySyncState { Pending, NeedsAttention, RemovalNeedsAttention }

enum class PlaylistSyncState { Pending, NeedsAttention, ConfirmCreation, ConfirmAddition }

data class PlaylistSyncStatus(val state: PlaylistSyncState, val editId: Long)

private class SessionChanged : IOException()

internal fun librarySyncShouldRetry(status: Int): Boolean = status == 401 || status == 403 || status == 408 || status == 429 || status in 500..599

private fun Throwable.isRetryable(): Boolean =
    when (this) {
        is ResponseException -> librarySyncShouldRetry(response.status.value)
        is IOException, is HttpRequestTimeoutException -> true
        else -> cause?.takeUnless { it === this }?.isRetryable() ?: false
    }

internal const val LIBRARY_WORK_NAME = "vibra-library-mutations"
private const val LIBRARY_WAKEUP = "vibra-library-wakeup"
private const val LIBRARY_RECOVERY = "vibra-library-recovery"

private fun libraryNetwork() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

private fun scheduleLibraryWakeup(context: Context, at: Long) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        LIBRARY_WAKEUP,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<LibraryWakeupWorker>()
            .setConstraints(libraryNetwork())
            .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build(),
    )
}

private fun scheduleLibraryRecovery(context: Context) {
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        LIBRARY_RECOVERY,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<LibraryWakeupWorker>(15, TimeUnit.MINUTES)
            .setConstraints(libraryNetwork())
            .build(),
    )
}

class LibraryWakeupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        scheduleLibraryWork(applicationContext).await()
        return Result.success()
    }
}

internal fun scheduleLibraryWork(context: Context) =
    WorkManager.getInstance(context).enqueueUniqueWork(
        LIBRARY_WORK_NAME,
        ExistingWorkPolicy.APPEND_OR_REPLACE,
        OneTimeWorkRequestBuilder<LibraryMutationWorker>()
            .setConstraints(libraryNetwork())
            .build(),
    )

private fun Preferences.syncAccount(): String? =
    if (this[YtmSyncKey] == false) null else accountIdentity()

private fun Preferences.accountIdentity(): String? {
    val auth = toPlaybackAuthState()
    if (!auth.hasLoginCookie) return null
    val identity = auth.dataSyncId?.let { "account:$it" } ?: "session:${auth.cookie}"
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).toHexString()
}

private suspend fun <T> localResult(action: suspend () -> T): Result<T> =
    try {
        Result.success(action())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

class LibraryMutationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val actions = EntryPointAccessors.fromApplication(applicationContext, LibraryMutationEntryPoint::class.java).libraryActions()
        actions.drain()
        return Result.success()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LibraryMutationEntryPoint {
    fun libraryActions(): LibraryActions
}
