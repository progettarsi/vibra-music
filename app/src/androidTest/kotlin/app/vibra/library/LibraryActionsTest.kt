/*
 * Vibra (2026) — GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.library

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.db.InternalDatabase
import moe.rukamori.archivetune.db.LIBRARY_MUTATION_MIGRATION
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.PLAYLIST_OUTBOX_MIGRATION
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.AlbumEntity
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.PodcastEntity
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.UUID

class LibraryActionsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = MutableStateFlow(account("first"))
    private var time = 1_000_000L

    @Test
    fun latestLikeIsVisibleWhileAnOlderRequestIsStillInFlight() = roomTest { db, actions ->
        db.insert(SongEntity(id = "video", title = "Track", duration = 20))
        val refresh = actions.refreshSnapshot()
        val sent = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val uploaded = mutableListOf<Boolean>()
        actions.changeSong("video") { it.toggleLike() }.getOrThrow()
        val drain = async {
            actions.drain { operation, _ ->
                uploaded += operation.desired
                if (operation.desired) {
                    sent.complete(Unit)
                    release.await()
                }
            }
        }

        sent.await()
        assertTrue(db.song("video").first()!!.song.liked)
        actions.changeSong("video") { it.toggleLike() }.getOrThrow()
        assertFalse(db.song("video").first()!!.song.liked)
        assertTrue(actions.protects(refresh, "video", VIDEO))
        release.complete(Unit)
        assertTrue(drain.await())
        assertEquals(listOf(true, false), uploaded)
        assertFalse(db.song("video").first()!!.song.liked)
        assertTrue(actions.protects(refresh, "video", VIDEO))
    }

    @Test
    fun bookmarksAndRemotePodcastRemovalAreVisibleBeforeNetwork() = roomTest { db, actions ->
        val album = AlbumEntity(id = "album", playlistId = "albumPlaylist", title = "Album", songCount = 1, duration = 20)
        val onlineAlbum = AlbumItem(browseId = "remoteAlbum", playlistId = "remoteAlbumPlaylist", title = "Online", artists = null, thumbnail = "")
        val artist = ArtistEntity(id = "UCartist", name = "Artist")
        val playlist = PlaylistItem("playlist", "Playlist", null, null, null, null, null, null)
        db.insert(album)
        db.upsert(PodcastEntity("podcast", "podcastPlaylist", "Podcast", null, null, null, null, LocalDateTime.now()))

        actions.toggleAlbum(album).join()
        actions.toggleAlbum(onlineAlbum).join()
        actions.toggleArtist(artist).join()
        actions.togglePlaylist(playlist, emptyList()).join()
        actions.setPodcastSaved("podcast", false).getOrThrow()
        assertNotNull(db.libraryAlbumEntity("album")!!.bookmarkedAt)
        assertNotNull(db.libraryAlbumEntity("remoteAlbum")!!.bookmarkedAt)
        assertNotNull(db.getArtistById("UCartist")!!.bookmarkedAt)
        assertNotNull(db.playlistEntityByBrowseId("playlist")!!.bookmarkedAt)
        assertFalse(db.getPodcast("podcast")!!.isSaved)
        assertEquals(5, db.pendingLibraryMutations(actions.refreshSnapshot().account!!).size)
        assertEquals(2, db.pendingLibraryMutations("").size)
    }

    @Test
    fun retryAccountSwitchAndPermanentRejectionPreserveLocalChoice() = roomTest { db, actions ->
        db.insert(SongEntity(id = "video", title = "Track", duration = 20))
        actions.changeSong("video") { it.toggleLike() }.getOrThrow()
        assertFalse(actions.drain { _, _ -> throw IOException("offline") })

        var unexpectedSends = 0
        prefs.value = account("second")
        assertTrue(actions.drain { _, _ -> unexpectedSends++ })
        prefs.value = account("first", enabled = false)
        assertTrue(actions.drain { _, _ -> unexpectedSends++ })

        prefs.value = account("first")
        time += RETRY_MILLIS
        assertTrue(actions.drain { _, _ -> throw IllegalArgumentException("permanent rejection") })
        assertTrue(db.song("video").first()!!.song.liked)
        assertTrue(actions.protects(actions.refreshSnapshot(), "video", VIDEO))
        assertTrue(actions.drain { _, _ -> unexpectedSends++ })
        assertEquals(0, unexpectedSends)
    }

    @Test
    fun bulkUpdateIsLocalAndDisablingSyncSupersedesAnOldPendingLike() = roomTest { db, actions ->
        val ids = listOf("firstVideo", "secondVideo")
        ids.forEach { db.insert(SongEntity(id = it, title = it, duration = 20)) }
        val failed = actions.changeSongs(ids + "missing") { it.localToggleLike() }
        assertEquals(setOf("missing"), failed)
        ids.forEach { assertTrue(db.song(it).first()!!.song.liked) }
        prefs.value = account("first", enabled = false)
        actions.changeSong(ids.first()) { it.toggleLike() }.getOrThrow()
        prefs.value = account("first")
        val uploads = mutableListOf<String>()
        assertTrue(actions.drain { operation, _ -> uploads += operation.targetId })
        assertEquals(listOf(ids.last()), uploads)
        assertFalse(db.song(ids.first()).first()!!.song.liked)
    }

    @Test
    fun migrationPreservesLibraryAndPendingActionsSurviveReopen() = runBlocking(Dispatchers.IO) {
        val name = "library-actions-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, InternalDatabase::class.java, name)
            .addMigrations(LIBRARY_MUTATION_MIGRATION, PLAYLIST_OUTBOX_MIGRATION)
            .build()
        try {
            open().useRoom { original ->
                original.dao.insert(SongEntity(id = "video", title = "Track", duration = 20))
                val sqlite = original.openHelper.writableDatabase
                sqlite.execSQL("DROP TABLE playlist_edit")
                sqlite.execSQL("ALTER TABLE library_mutation RENAME TO library_mutation_v40")
                sqlite.execSQL("""
                    CREATE TABLE library_mutation (
                        account TEXT NOT NULL, target TEXT NOT NULL, targetId TEXT NOT NULL,
                        localId TEXT NOT NULL, desired INTEGER NOT NULL, revision TEXT NOT NULL,
                        createdAt INTEGER NOT NULL, completedAt INTEGER, rejected INTEGER NOT NULL,
                        PRIMARY KEY (account, target, targetId)
                    )
                """.trimIndent())
                sqlite.execSQL("DROP TABLE library_mutation_v40")
                sqlite.execSQL("INSERT INTO library_mutation VALUES ('old-account', 'video', 'video', 'video', 1, 'old-revision', 1, NULL, 0)")
                sqlite.version = 39
            }
            open().useRoom { migrated ->
                val db = MusicDatabase(migrated)
                assertEquals("Track", db.getSongById("video")!!.song.title)
                assertEquals("old-revision", db.pendingLibraryMutations("old-account").single().revision)
                val actions = LibraryActions(db, prefs) {}
                actions.changeSong("video") { it.toggleLike() }.getOrThrow()
                actions.createPlaylist("Offline", publish = true).getOrThrow()
            }
            open().useRoom { reopened ->
                val db = MusicDatabase(reopened)
                val actions = LibraryActions(db, prefs) {}
                assertTrue(db.getSongById("video")!!.song.liked)
                val uploaded = mutableListOf<String>()
                val remote = FakePlaylistRemote()
                assertTrue(actions.drain(remote) { operation, _ -> uploaded += operation.targetId })
                assertEquals(listOf("video"), uploaded)
                assertEquals("Offline", remote.read("created-1").title)
            }
        } finally {
            context.deleteDatabase(name)
        }
        Unit
    }

    @Test
    fun lostCreateResponseIsReconciledBeforeAnySecondCreation() = roomTest { db, actions ->
        val remote = FakePlaylistRemote().apply { loseCreateResponse = true }
        val local = actions.createPlaylist("First name", publish = true).getOrThrow()
        actions.renamePlaylist(local, "Latest name").getOrThrow()

        assertFalse(actions.drain(remote))
        assertEquals(1, remote.createCalls)
        assertEquals("Latest name", db.getPlaylistById(local)!!.playlist.name)
        assertEquals(null, db.getPlaylistById(local)!!.playlist.browseId)
        remote.creationVisible = false
        time += RETRY_MILLIS
        assertFalse(actions.drain(remote))
        assertEquals(1, remote.createCalls)

        remote.creationVisible = true
        time += RETRY_MILLIS
        assertTrue(actions.drain(remote))
        assertEquals(1, remote.createCalls)
        assertEquals("Latest name", remote.read("created-1").title)
        assertEquals("created-1", db.getPlaylistById(local)!!.playlist.browseId)
    }

    @Test
    fun explicitCreationRetryChecksRemoteAgainBeforeSending() = roomTest { _, actions ->
        val remote = FakePlaylistRemote().apply { loseCreateResponse = true }
        val local = actions.createPlaylist("Playlist", publish = true).getOrThrow()
        assertFalse(actions.drain(remote))
        actions.retryPlaylist(local, actions.observePlaylistSync(local).first()!!.editId).getOrThrow()
        assertTrue(actions.drain(remote))
        assertEquals(1, remote.createCalls)
    }

    @Test
    fun replayPreservesRemoteAdditionsAndTargetsExactDuplicate() = roomTest { db, actions ->
        val playlist = PlaylistEntity(name = "Playlist", browseId = "playlist")
        db.insert(playlist)
        listOf("same", "anchor", "added").forEach { db.insert(SongEntity(id = it, title = it, duration = 20)) }
        val first = db.insertPlaylistEntry(PlaylistSongMap(playlistId = playlist.id, songId = "same", position = 0, setVideoId = "one")).toInt()
        val second = db.insertPlaylistEntry(PlaylistSongMap(playlistId = playlist.id, songId = "same", position = 1, setVideoId = "two")).toInt()
        val anchor = db.insertPlaylistEntry(PlaylistSongMap(playlistId = playlist.id, songId = "anchor", position = 2, setVideoId = "anchor")).toInt()
        val remote = FakePlaylistRemote().apply {
            seed("playlist", "Playlist", listOf(
                RemotePlaylistEntry("same", "one"), RemotePlaylistEntry("same", "two"),
                RemotePlaylistEntry("anchor", "anchor"), RemotePlaylistEntry("external", "external"),
            ))
            loseAddResponse = true
        }
        val refresh = actions.refreshSnapshot()
        actions.addPlaylistSongs(playlist.id, listOf("added")).getOrThrow()
        val added = db.playlistEntries(playlist.id).last().id
        actions.movePlaylistSong(playlist.id, added, second, anchor, false).getOrThrow()
        actions.removePlaylistSongs(playlist.id, entryIds = setOf(first)).getOrThrow()
        assertEquals(listOf("same", "added", "anchor"), db.playlistEntries(playlist.id).map { it.songId })
        assertTrue(actions.protectsPlaylist(refresh, playlist.id, playlist.browseId))

        assertFalse(actions.drain(remote))
        time += RETRY_MILLIS
        assertTrue(actions.drain(remote))
        assertEquals(1, remote.addCalls)
        assertEquals(listOf("two", "added-1", "anchor", "external"), remote.read("playlist").entries.map { it.setId })
        assertEquals("added-1", db.playlistEntries(playlist.id).first { it.id == added }.setVideoId)
        assertTrue(actions.protectsPlaylist(refresh, playlist.id, playlist.browseId))
    }

    @Test
    fun retryWaitsTenMinutesWhileAnotherPlaylistDrainsAndAccountChangesAreIsolated() = roomTest { db, actions ->
        val first = PlaylistEntity(name = "One", browseId = "one")
        val second = PlaylistEntity(name = "Two", browseId = "two")
        db.insert(first)
        db.insert(second)
        val remote = FakePlaylistRemote().apply {
            seed("one", "One")
            seed("two", "Two")
            unavailable = "one"
        }
        actions.renamePlaylist(first.id, "First").getOrThrow()
        actions.renamePlaylist(second.id, "Second").getOrThrow()
        assertFalse(actions.drain(remote))
        assertEquals("Second", remote.read("two").title)
        remote.unavailable = null
        time += RETRY_MILLIS - 1
        assertFalse(actions.drain(remote))
        assertEquals("One", remote.read("one").title)

        time++
        prefs.value = account("second")
        assertTrue(actions.drain(remote))
        assertTrue(actions.renamePlaylist(first.id, "Wrong account").isFailure)
        prefs.value = account("first", enabled = false)
        assertTrue(actions.drain(remote))
        assertEquals("One", remote.read("one").title)
        prefs.value = account("first")
        assertTrue(actions.drain(remote))
        assertEquals("First", remote.read("one").title)
    }

    @Test
    fun addThenRemoveBeforeReconnectKeepsIdentityAndDeleteDoesNotResurrect() = roomTest { db, actions ->
        val playlist = PlaylistEntity(name = "Playlist", browseId = "playlist")
        db.insert(playlist)
        db.insert(SongEntity(id = "song", title = "Song", duration = 20))
        val remote = FakePlaylistRemote().apply { seed("playlist", "Playlist") }
        actions.addPlaylistSongs(playlist.id, listOf("song")).getOrThrow()
        actions.removePlaylistSongs(playlist.id, songIds = setOf("song")).getOrThrow()
        assertTrue(db.playlistEntries(playlist.id).isEmpty())
        assertTrue(actions.drain(remote))
        assertTrue(remote.read("playlist").entries.isEmpty())

        val snapshot = actions.refreshSnapshot()
        actions.removePlaylists(listOf(playlist.id)).getOrThrow()
        assertTrue(actions.protectsPlaylist(snapshot, "other-id", "playlist"))
        assertTrue(actions.drain(remote))
        assertTrue(actions.protectsPlaylist(snapshot, "other-id", "playlist"))
        assertTrue(runCatching { remote.read("playlist") }.isFailure)
    }

    @Test
    fun queuedCoverSurvivesRepositoryRecreation() = roomTest { db, _ ->
        val directory = File(context.cacheDir, "playlist-cover-test-" + UUID.randomUUID())
        fun actions() = LibraryActions(db, prefs, now = { time }, coverDirectory = directory) {}
        val playlist = PlaylistEntity(name = "Playlist", browseId = "playlist")
        db.insert(playlist)
        val image = byteArrayOf(1, 2, 3, 4)
        actions().changePlaylistCover(playlist.id, image).getOrThrow()
        assertTrue(db.getPlaylistById(playlist.id)!!.playlist.thumbnailUrl!!.startsWith("file:"))
        val remote = FakePlaylistRemote()
        assertTrue(actions().drain(remote))
        assertArrayEquals(image, remote.coverImage)
        assertEquals("https://example.invalid/cover", db.getPlaylistById(playlist.id)!!.playlist.thumbnailUrl)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        directory.delete()
    }

    @Test
    fun workManagerHonorsNetworkConstraint() = runBlocking(Dispatchers.IO) {
        val phase = InstrumentationRegistry.getArguments().getString("networkProbe")
        assumeTrue(phase == "offline" || phase == "online")
        val manager = WorkManager.getInstance(context)
        if (phase == "offline") {
            scheduleLibraryWork(context).await()
            delay(1_000)
            val queued = manager.getWorkInfosForUniqueWork(LIBRARY_WORK_NAME).get()
            assertTrue(queued.any { it.state == WorkInfo.State.ENQUEUED && it.constraints.requiredNetworkType == NetworkType.CONNECTED })
        } else {
            withTimeout(30_000) {
                while (manager.getWorkInfosForUniqueWork(LIBRARY_WORK_NAME).get().none { it.state == WorkInfo.State.SUCCEEDED }) {
                    delay(250)
                }
            }
        }
        Unit
    }

    private fun roomTest(block: suspend kotlinx.coroutines.CoroutineScope.(MusicDatabase, LibraryActions) -> Unit) =
        runBlocking(Dispatchers.IO) {
            withTimeout(10_000) {
                Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).build().useRoom { room ->
                    val db = MusicDatabase(room)
                    block(db, LibraryActions(db, prefs, now = { time }) {})
                }
            }
        }

    private fun account(id: String, enabled: Boolean = true): Preferences = preferencesOf(
        DataSyncIdKey to id,
        InnerTubeCookieKey to "SAPISID=fake-test-cookie",
        YtmSyncKey to enabled,
    )
}

private class FakePlaylistRemote : PlaylistRemote {
    private val playlists = mutableMapOf<String, RemotePlaylist>()
    var createCalls = 0
    var addCalls = 0
    var loseCreateResponse = false
    var loseAddResponse = false
    var creationVisible = true
    var unavailable: String? = null
    var coverImage: ByteArray? = null

    fun seed(id: String, title: String, entries: List<RemotePlaylistEntry> = emptyList()) {
        playlists[id] = RemotePlaylist(title, entries)
    }

    override suspend fun findCreated(title: String): List<String> =
        if (creationVisible) playlists.filterValues { it.title == title }.keys.toList() else emptyList()

    override suspend fun read(id: String): RemotePlaylist {
        if (id == unavailable) throw IOException()
        return playlists.getValue(id)
    }

    override suspend fun create(title: String): String {
        val id = "created-" + ++createCalls
        seed(id, title)
        if (loseCreateResponse) throw IOException()
        return id
    }

    override suspend fun add(id: String, videoId: String): String {
        val setId = "added-" + ++addCalls
        val playlist = read(id)
        playlists[id] = playlist.copy(entries = playlist.entries + RemotePlaylistEntry(videoId, setId))
        if (loseAddResponse) throw IOException()
        return setId
    }

    override suspend fun remove(id: String, entry: RemotePlaylistEntry) {
        val playlist = read(id)
        playlists[id] = playlist.copy(entries = playlist.entries - entry)
    }

    override suspend fun move(id: String, setId: String, successor: String?) {
        val playlist = read(id)
        val entries = playlist.entries.toMutableList()
        val source = entries.removeAt(entries.indexOfFirst { it.setId == setId })
        val index = successor?.let { target -> entries.indexOfFirst { it.setId == target } } ?: entries.size
        entries.add(index, source)
        playlists[id] = playlist.copy(entries = entries)
    }

    override suspend fun rename(id: String, title: String) {
        playlists[id] = read(id).copy(title = title)
    }

    override suspend fun cover(id: String, image: ByteArray?): String? {
        coverImage = image
        return "https://example.invalid/cover"
    }

    override suspend fun delete(id: String) {
        playlists.remove(id)
    }
}

private inline fun <T> InternalDatabase.useRoom(block: (InternalDatabase) -> T): T =
    try {
        block(this)
    } finally {
        close()
    }
