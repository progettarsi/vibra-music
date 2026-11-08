/*
 * Vibra (2026) — GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.library

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.ktor.client.plugins.ResponseException
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.PlaylistEditRejected
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.utils.completed
import org.json.JSONArray
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.UUID

@Entity(tableName = "playlist_edit", indices = [Index("account", "playlistId")])
data class PlaylistEdit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val account: String,
    val playlistId: String,
    val kind: String,
    val value: String? = null,
    val remoteId: String? = null,
    val videoId: String? = null,
    val entryId: Int? = null,
    val setId: String? = null,
    val beforeId: Int? = null,
    val beforeSetId: String? = null,
    val afterId: Int? = null,
    val afterSetId: String? = null,
    val toEnd: Boolean = false,
    val attempt: String? = null,
    val nextAttemptAt: Long? = null,
    val completedAt: Long? = null,
    val blocked: Boolean = false,
)

internal const val CREATE = "create"
internal const val ADD = "add"
internal const val REMOVE = "remove"
internal const val MOVE = "move"
internal const val RENAME = "rename"
internal const val COVER = "cover"
internal const val DELETE = "delete"
private const val CONFIRMED = "confirmed"
private const val CONFIRMED_ADD_PREFIX = "confirmed:"

internal fun PlaylistEdit.confirmAttempt(): String? {
    val current = attempt ?: return null
    return when (kind) {
        CREATE -> CONFIRMED
        ADD -> CONFIRMED_ADD_PREFIX + current.removePrefix(CONFIRMED_ADD_PREFIX)
        else -> current
    }
}

internal const val RETRY_MILLIS = 10 * 60 * 1_000L

internal data class RemotePlaylistEntry(val videoId: String, val setId: String?)
internal data class RemotePlaylist(val title: String, val entries: List<RemotePlaylistEntry>)

internal interface PlaylistRemote {
    suspend fun findCreated(title: String): List<String>
    suspend fun read(id: String): RemotePlaylist
    suspend fun create(title: String): String
    suspend fun add(id: String, videoId: String): String
    suspend fun remove(id: String, entry: RemotePlaylistEntry)
    suspend fun move(id: String, setId: String, successor: String?)
    suspend fun rename(id: String, title: String)
    suspend fun cover(id: String, image: ByteArray?): String?
    suspend fun delete(id: String)
}

internal object YouTubePlaylistRemote : PlaylistRemote {
    override suspend fun findCreated(title: String): List<String> =
        YouTube.library("FEmusic_liked_playlists").completed().getOrThrow().items
            .filterIsInstance<PlaylistItem>()
            .filter { it.title == title }
            .map { it.id.removePrefix("VL") }
            .distinct()

    override suspend fun read(id: String): RemotePlaylist =
        YouTube.playlist(id).completed().getOrThrow().let { page ->
            RemotePlaylist(page.playlist.title, page.songs.map { RemotePlaylistEntry(it.id, it.setVideoId) })
        }

    override suspend fun create(title: String) =
        YouTube.createPlaylist(title).getOrThrow().removePrefix("VL").also { check(it.isNotBlank()) }

    override suspend fun add(id: String, videoId: String) = YouTube.addToPlaylist(id, videoId).getOrThrow()

    override suspend fun remove(id: String, entry: RemotePlaylistEntry) {
        YouTube.removeFromPlaylist(id, entry.videoId, requireNotNull(entry.setId)).getOrThrow()
    }

    override suspend fun move(id: String, setId: String, successor: String?) {
        YouTube.moveSongPlaylist(id, setId, successor).getOrThrow()
    }

    override suspend fun rename(id: String, title: String) {
        YouTube.renamePlaylist(id, title).getOrThrow()
    }

    override suspend fun cover(id: String, image: ByteArray?): String? =
        if (image != null) YouTube.uploadCustomPlaylistCover(id, image).getOrThrow()
        else YouTube.removeCustomPlaylistCover(id).getOrThrow()

    override suspend fun delete(id: String) {
        YouTube.deletePlaylist(id).getOrThrow()
    }
}

internal class PlaylistOutbox(
    private val database: MusicDatabase,
    private val now: () -> Long,
    private val readCover: (String) -> ByteArray,
    private val removeCover: (String) -> Unit,
) {
    suspend fun apply(
        edit: PlaylistEdit,
        auth: PlaybackAuthState,
        checkAccount: suspend () -> Unit,
        remote: PlaylistRemote,
    ) = YouTube.withLibraryAuth(auth) {
        checkAccount()
        val current = database.playlistEdit(edit.id) ?: return@withLibraryAuth
        if (current.completedAt != null) return@withLibraryAuth
        if (current.kind == CREATE) {
            create(current, checkAccount, remote)
            return@withLibraryAuth
        }

        val id = current.remoteId ?: database.getPlaylistById(current.playlistId)?.playlist?.browseId
            ?: throw PlaylistEditConflict()
        val remoteId = id.removePrefix("VL")
        when (current.kind) {
            ADD -> add(current, remoteId, checkAccount, remote)
            REMOVE -> {
                val entries = remote.read(remoteId).entries
                val entry = entries.resolve(current.setId, current.videoId)
                if (entry != null) {
                    checkAccount()
                    remote.remove(remoteId, entry)
                }
                complete(current)
            }
            MOVE -> {
                val entries = remote.read(remoteId).entries
                val source = entries.resolve(current.setId, current.videoId)
                if (source != null) {
                    val target = entries.moveTarget(current, source)
                    if (target != null && target.index != entries.indexOf(source) + 1) {
                        checkAccount()
                        remote.move(remoteId, requireNotNull(source.setId), target.successor)
                    }
                }
                complete(current)
            }
            RENAME -> {
                val title = requireNotNull(current.value)
                checkAccount()
                remote.rename(remoteId, title)
                complete(current)
            }
            COVER -> {
                val image = current.value?.let(readCover)
                checkAccount()
                val url = remote.cover(remoteId, image)
                database.withTransaction {
                    val latest = playlistEdit(current.id) ?: return@withTransaction
                    val newer = playlistEdits(current.playlistId).any { it.id > current.id && it.kind == COVER }
                    if (!newer) getPlaylistById(current.playlistId)?.playlist?.let { update(it.copy(thumbnailUrl = url)) }
                    updatePlaylistEdit(latest.done(remoteId))
                }
                current.value?.let(removeCover)
            }
            DELETE -> {
                try {
                    remote.delete(remoteId)
                } catch (failure: ResponseException) {
                    if (failure.response.status.value != 404 && failure.response.status.value != 410) throw failure
                }
                complete(current, remoteId)
            }
            else -> error("Unknown playlist edit")
        }
    }

    private suspend fun create(edit: PlaylistEdit, checkAccount: suspend () -> Unit, remote: PlaylistRemote) {
        val marker = requireNotNull(edit.value)
        val found = if (edit.attempt != null) {
            val matches = remote.findCreated(marker)
            if (matches.size > 1) throw PlaylistEditConflict()
            matches.singleOrNull()
        } else null
        val id = found ?: run {
            if (edit.attempt != null && edit.attempt != CONFIRMED) throw UnconfirmedPlaylistEdit()
            checkAccount()
            if (!recordAttempt(edit, marker)) return
            invokeOnce(edit) { remote.create(marker) }
        }

        database.withTransaction {
            val current = playlistEdit(edit.id) ?: return@withTransaction
            val playlist = getPlaylistById(edit.playlistId)?.playlist
            if (playlist != null) update(playlist.copy(browseId = id))
            playlistEdits(edit.playlistId).filter { it.account == edit.account }.forEach { pending ->
                updatePlaylistEdit(pending.copy(remoteId = id))
            }
            updatePlaylistEdit(current.done(id))
        }
    }

    private suspend fun add(edit: PlaylistEdit, id: String, checkAccount: suspend () -> Unit, remote: PlaylistRemote) {
        val videoId = requireNotNull(edit.videoId)
        val entries = remote.read(id).entries.filter { it.videoId == videoId }
        val knownSetIds = entries.map { it.setId ?: throw PlaylistEditConflict() }.toSet()
        val attempt = edit.attempt
        val confirmed = attempt?.startsWith(CONFIRMED_ADD_PREFIX) == true
        val newIds = attempt?.let { knownSetIds - readSetIds(it.removePrefix(CONFIRMED_ADD_PREFIX)) }
        if (newIds != null && newIds.size > 1) throw PlaylistEditConflict()
        val setId = when {
            newIds?.size == 1 -> newIds.single()
            attempt != null && !confirmed -> throw UnconfirmedPlaylistEdit()
            attempt == null && edit.value == null && knownSetIds.size == 1 -> knownSetIds.single()
            else -> {
                if (attempt == null && edit.value == null && knownSetIds.size > 1) throw PlaylistEditConflict()
                checkAccount()
                if (!recordAttempt(edit, JSONArray(knownSetIds.toList()).toString())) return
                invokeOnce(edit) { remote.add(id, videoId) }.also {
                    if (it.isBlank() || it in knownSetIds) throw UnconfirmedPlaylistEdit()
                }
            }
        }
        database.withTransaction {
            val current = playlistEdit(edit.id) ?: return@withTransaction
            val entryId = requireNotNull(edit.entryId)
            bindPlaylistEntry(edit.playlistId, entryId, setId)
            bindPlaylistEditEntry(edit.playlistId, entryId, setId)
            updatePlaylistEdit(current.done(id))
        }
    }

    private suspend fun recordAttempt(edit: PlaylistEdit, attempt: String): Boolean =
        database.withTransaction {
            val current = playlistEdit(edit.id) ?: return@withTransaction false
            if (current.completedAt != null || current.attempt != edit.attempt) return@withTransaction false
            updatePlaylistEdit(current.copy(attempt = attempt))
            true
        }

    private suspend fun <T> invokeOnce(edit: PlaylistEdit, send: suspend () -> T): T =
        try {
            send()
        } catch (failure: Exception) {
            if (failure.definitelyNotSent()) {
                database.withTransaction {
                    playlistEdit(edit.id)?.let { updatePlaylistEdit(it.copy(attempt = null)) }
                }
            }
            throw failure
        }

    private suspend fun complete(edit: PlaylistEdit, remoteId: String? = edit.remoteId) {
        database.withTransaction {
            val current = playlistEdit(edit.id) ?: return@withTransaction
            if (current.kind == RENAME && current.value != edit.value) return@withTransaction
            updatePlaylistEdit(current.done(remoteId))
        }
    }

    private fun PlaylistEdit.done(remoteId: String?) =
        copy(remoteId = remoteId, completedAt = now(), attempt = null, nextAttemptAt = null, blocked = false)
}

internal class PlaylistEditConflict : IllegalStateException()
internal class UnconfirmedPlaylistEdit : IOException()

private const val MARKER_PREFIX = "VIBRA sync "
internal fun playlistCreationMarker() = MARKER_PREFIX + UUID.randomUUID()
internal fun isPlaylistCreationMarker(title: String): Boolean =
    title.startsWith(MARKER_PREFIX) &&
        runCatching { UUID.fromString(title.removePrefix(MARKER_PREFIX)).toString() == title.removePrefix(MARKER_PREFIX) }
            .getOrDefault(false)

private fun Throwable.definitelyNotSent(): Boolean =
    generateSequence(this) { it.cause?.takeUnless { cause -> cause === it } }.any {
        it is UnknownHostException || it is ConnectException || it is PlaylistEditRejected ||
            it is ResponseException && it.response.status.value in setOf(401, 403, 429)
    }

private fun readSetIds(value: String): Set<String> =
    JSONArray(value).let { ids -> (0 until ids.length()).map(ids::getString).toSet() }

private fun List<RemotePlaylistEntry>.resolve(setId: String?, videoId: String?): RemotePlaylistEntry? {
    if (setId != null) return firstOrNull { it.setId == setId }
    val matches = filter { it.videoId == videoId }
    if (matches.size > 1 || matches.any { it.setId == null }) throw PlaylistEditConflict()
    return matches.singleOrNull()
}

private data class MoveTarget(val index: Int, val successor: String?)

private fun List<RemotePlaylistEntry>.moveTarget(edit: PlaylistEdit, source: RemotePlaylistEntry): MoveTarget? {
    if (edit.toEnd) return MoveTarget(size, null)
    val after = indexOfFirst { it != source && it.setId != null && it.setId == edit.afterSetId }
    if (after >= 0) return MoveTarget(after, this[after].setId)
    val before = indexOfFirst { it != source && it.setId != null && it.setId == edit.beforeSetId }
    if (before < 0) return null
    val successor = drop(before + 1).firstOrNull { it != source }
    if (successor != null && successor.setId == null) throw PlaylistEditConflict()
    return MoveTarget(if (successor == null) size else indexOf(successor), successor?.setId)
}

internal fun PlaylistEdit.forEntry(entry: PlaylistSongMap): PlaylistEdit =
    copy(videoId = entry.songId, entryId = entry.id, setId = entry.setVideoId)
