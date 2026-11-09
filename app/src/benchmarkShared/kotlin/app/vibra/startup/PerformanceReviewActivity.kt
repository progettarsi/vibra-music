/* Isolated benchmark harness, 2026. GPL-3.0. Never included in a production APK. */
package app.vibra.startup

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import app.vibra.performance.completedMembership
import app.vibra.playback.*
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.*
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.PersistPlayerState
import moe.rukamori.archivetune.models.PersistQueue
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.utils.dataStore

@AndroidEntryPoint
class PerformanceReviewActivity : ComponentActivity() {
    @Inject lateinit var database: MusicDatabase
    private var bound = false
    private val binding =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as MusicService.MusicBinder).service
                lifecycleScope.launch {
                    val action = intent.getStringExtra("action") ?: "queue"
                    try {
                        withTimeout(30_000) { service.queueRestoreCompleted.first { it } }
                        if (action == "state") {
                            logState(service)
                        } else {
                            val count =
                                intent.getIntExtra(
                                    "queue",
                                    if (action == "verify_queue") 10000 else 30,
                                )
                            val songs =
                                withContext(Dispatchers.IO) {
                                    database.allSongs().first().take(count)
                                }
                            val start = SystemClock.elapsedRealtime()
                            val previousQueueId =
                                (service.player as ProgressivePlayer).queue.content.queueId
                            service.playQueue(
                                ListQueue(
                                    "Performance fixture",
                                    songs.lazyMediaItems { metadataOf(it) },
                                ),
                                false,
                            )
                            withTimeout(15_000) {
                                while (
                                    (service.player as ProgressivePlayer).queue.content.queueId ==
                                        previousQueueId ||
                                        service.player.mediaItemCount != songs.size ||
                                        (service.player as ProgressivePlayer).isPreparingSource
                                ) delay(20)
                            }
                            (service.player as ProgressivePlayer).awaitBackendUpdate()
                            Log.i(
                                "PerfReview",
                                "queue_prepare elapsedMs=${SystemClock.elapsedRealtime() - start} count=${service.player.mediaItemCount}",
                            )
                            if (action == "verify_queue") verifyQueue(service)
                            delay(1500)
                            logState(service)
                            Log.i(
                                "PerfReview",
                                "queue_ready count=${service.player.mediaItemCount}",
                            )
                        }
                        Log.i("PerfReview", "done action=$action")
                    } catch (error: Exception) {
                        Log.e("PerfReview", "failed type=${error.javaClass.simpleName}", error)
                    }
                    finish()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Performance review fixture" })
        lifecycleScope.launch {
            try {
                val action = intent.getStringExtra("action") ?: "seed"
                if (action in setOf("queue", "verify_queue", "state")) {
                    bound =
                        bindService(
                            Intent(this@PerformanceReviewActivity, MusicService::class.java),
                            binding,
                            BIND_AUTO_CREATE,
                        )
                    return@launch
                }
                if (action == "verify_data") {
                    verifyData()
                } else if (action == "filtered_restore") {
                    prepareFilteredRestore()
                } else if (action == "legacy") {
                    writeLegacy()
                } else if (action == "probe") {
                    probe()
                } else if (action == "cache") {
                    cacheHome()
                } else if (action == "sort") {
                    dataStore.edit {
                        it[SongSortTypeKey] = intent.getStringExtra("sort") ?: "ARTIST"
                        it[SongFilterKey] = "LIBRARY"
                    }
                    Log.i("PerfReview", "sort_ready")
                } else {
                    seed(intent.getIntExtra("count", 100))
                }
                Log.i("PerfReview", "done action=$action")
            } catch (e: Exception) {
                Log.e("PerfReview", "failed type=${e.javaClass.simpleName}", e)
            }
            finish()
        }
    }

    private fun logState(service: MusicService) {
        val player = service.player as ProgressivePlayer
        val count = service.localPlayer.mediaItemCount
        check(count <= 100) { "Physical queue exceeded 100: $count" }
        Log.i(
            "PerfReview",
            "queue_state logical=${player.mediaItemCount} physical=$count index=${player.currentMediaItemIndex} uid=${player.queue.currentUid} shuffle=${player.shuffleModeEnabled} repeat=${player.repeatMode} position=${player.currentPosition} ready=${player.playWhenReady}",
        )
    }

    private suspend fun verifyQueue(service: MusicService) {
        val player = service.player as ProgressivePlayer
        check(player.mediaItemCount == 10000)
        val initial = player.metadataSnapshot
        val retained = initial.lazyMediaItems { it }
        player.setMediaItems(initial.take(1000).lazyMediaItems { it })
        player.addMediaItem(initial[0].toMediaItem())
        player.setMediaItems(retained)
        withTimeout(5000) {
            while (player.isPreparingSource || player.mediaItemCount != 10000) delay(10)
        }
        player.awaitBackendUpdate()
        player.setMediaItems(initial.take(500).lazyMediaItems { it })
        player.setMediaItems(retained, 777, 0L)
        withTimeout(5000) { while (player.isPreparingSource) delay(10) }
        player.awaitBackendUpdate()
        check(player.mediaItemCount == 10000 && player.currentMediaItemIndex == 777) {
            "Retained source: count=${player.mediaItemCount} index=${player.currentMediaItemIndex} preparing=${player.isPreparingSource} physical=${player.sessionPlayer.currentMediaItemIndex}"
        }
        Log.i("PerfReview", "queue_stage source-cancellation")
        val all = player.queue.content.order.toSet()
        check(player.sessionPlayer.mediaItemCount <= 100)
        player.seekTo(9999, 1000)
        check(player.currentMediaItemIndex == 9999)
        player.sessionPlayer.seekToPreviousMediaItem()
        check(player.currentMediaItemIndex == 9998)
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.seekTo(9999, 1000)
        player.sessionPlayer.seekToNextMediaItem()
        check(player.currentMediaItemIndex == 0)
        player.sessionPlayer.seekToPreviousMediaItem()
        check(player.currentMediaItemIndex == 9999)
        val lastUid = player.queue.currentUid
        player.shuffleModeEnabled = true
        check(player.queue.content.order.toSet() == all && player.queue.currentUid == lastUid)
        val physical = player.sessionPlayer
        physical.setMediaItems(
            List(physical.mediaItemCount) { physical.getMediaItemAt(it) },
            physical.currentMediaItemIndex,
            1000,
        )
        check(player.mediaItemCount == 10000) {
            "Controller resumption replaced the logical source"
        }
        Log.i("PerfReview", "queue_stage global-transport")
        player.shuffleModeEnabled = false
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.seekTo(5000, 1000)
        for (i in 0..120) {
            player.sessionPlayer.seekToNextMediaItem()
            if (i % 10 == 0) delay(25)
            check(service.localPlayer.mediaItemCount <= 100)
        }
        check(player.currentMediaItemIndex == 5121)
        Log.i("PerfReview", "queue_stage window-traversal")
        val token =
            androidx.media3.session.SessionToken(
                this,
                ComponentName(this, MusicService::class.java),
            )
        val controller =
            androidx.media3.session.MediaController.Builder(this, token).buildAsync().await()
        try {
            check(controller.mediaItemCount <= 100)
            val sourceId = player.queue.content.queueId
            controller.setMediaItem(
                androidx.media3.common.MediaItem.Builder().setMediaId(MusicService.LIKED).build()
            )
            controller.prepare()
            withTimeout(10000) {
                while (player.queue.content.queueId == sourceId || player.isPreparingSource) delay(
                    10
                )
            }
            player.awaitBackendUpdate()
            check(player.mediaItemCount == 10000 && controller.mediaItemCount <= 100)
            val before = player.currentMediaItemIndex
            controller.seekToNextMediaItem()
            withTimeout(5000) { while (player.currentMediaItemIndex != before + 1) delay(10) }
            controller.seekToPreviousMediaItem()
            withTimeout(5000) { while (player.currentMediaItemIndex != before) delay(10) }
        } finally {
            controller.release()
        }
        Log.i("PerfReview", "queue_stage media-controller")
        val duplicate = player.queue.rows[1].metadata
        player.insertUser(listOf(duplicate, duplicate).lazyMediaItems { it }, true)
        withTimeout(5000) { while (player.mediaItemCount != 10002) delay(10) }
        check(player.queue.userCount() == 2)
        val first = player.queue.rows[player.queue.index + 1]
        val second = player.queue.rows[player.queue.index + 2]
        check(first.uid != second.uid && first.metadata.id == second.metadata.id)
        player.sessionPlayer.seekToNextMediaItem()
        check(player.queue.currentUid == first.uid)
        player.moveMediaItem(player.queue.indexOf(second.uid), 8000)
        val removal = service.removeQueueEntries(listOf(second))!!
        check(player.mediaItemCount == 10001)
        check(service.restoreQueueItems(removal))
        check(player.queue.indexOf(second.uid) == 8000)
        val selectedBeforeClear = player.queue.currentUid
        val removedAll = service.removeQueueEntries(player.queue.rows)!!
        check(player.mediaItemCount == 0)
        check(service.restoreQueueItems(removedAll))
        check(player.mediaItemCount == 10002 && player.queue.currentUid == selectedBeforeClear)
        val persistedEmpty = service.removeQueueEntries(player.queue.rows)!!
        player.awaitBackendUpdate()
        withTimeout(5000) {
            while (
                !withContext(Dispatchers.IO) { QueuePersistenceDisk(filesDir).isCleared() }
            ) delay(20)
        }
        check(service.restoreQueueItems(persistedEmpty))
        player.awaitBackendUpdate()
        check(QueuePersistenceWriter.get(filesDir).flush())
        check(player.mediaItemCount == 10002 && player.queue.currentUid == selectedBeforeClear)
        Log.i("PerfReview", "queue_stage duplicates-drag-undo")
        player.replaceMediaItem(
            player.queue.indexOf(first.uid),
            first.metadata.copy(title = "Updated fixture").toMediaItem(),
        )
        withTimeout(5000) {
            while (player.currentMediaItem?.mediaMetadata?.title != "Updated fixture") delay(10)
        }
        check(player.queue.currentUid == first.uid)
        player.shuffleModeEnabled = true
        player.seekTo(7777, 2345)
        val observedPlaying = MutableStateFlow<Boolean?>(null)
        val observedReady = MutableStateFlow(false)
        val playbackListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    observedPlaying.value = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) observedReady.value = true
                }
            }
        player.addListener(playbackListener)
        player.stop()
        player.awaitBackendUpdate()
        check(player.currentMediaItemIndex == 7777 && player.currentPosition == 2345L)
        player.prepare()
        player.play()
        withTimeout(5000) {
            while (
                !player.isPlaying || observedPlaying.value != true || !observedReady.value
            ) delay(10)
        }
        player.pause()
        withTimeout(5000) { while (player.isPlaying || observedPlaying.value != false) delay(10) }
        player.removeListener(playbackListener)
        player.seekTo(7777, 2345)
        player.awaitBackendUpdate()
        withTimeout(5000) { while (player.currentPosition != 2345L) delay(10) }
        Log.i("PerfReview", "queue_stage playback-callbacks-playing-ready-pause")
        val content =
            QueueContentV2(
                content = player.queue.content,
                continuation = PersistQueue("Performance fixture", emptyList(), 0, 0),
            )
        QueuePersistenceWriter.get(filesDir)
            .submit(
                content,
                player.queue.currentUid,
                PersistPlayerState(
                    false,
                    player.repeatMode,
                    true,
                    1f,
                    2345,
                    7777,
                    player.playbackState,
                ),
            )
        check(QueuePersistenceWriter.get(filesDir).flush())
        logState(service)
        Log.i(
            "PerfReview",
            "queue_checks_passed cases=far-seek,session-next-previous,repeat-wrap,global-shuffle,controller-resumption,121-window-steps,manual-duplicates,drag,remove-undo,replace,checkpoint",
        )
    }

    private suspend fun writeLegacy() =
        withContext(Dispatchers.IO) {
            val metadata = database.allSongs().first().map { metadataOf(it) }
            java.io.ObjectOutputStream(File(filesDir, "persistent_queue.data").outputStream()).use {
                it.writeObject(
                    PersistQueue(
                        "Legacy fixture",
                        metadata,
                        7777.coerceAtMost(metadata.lastIndex),
                        2345,
                    )
                )
            }
            java.io
                .ObjectOutputStream(File(filesDir, "persistent_player_state.data").outputStream())
                .use {
                    it.writeObject(
                        PersistPlayerState(
                            false,
                            0,
                            false,
                            1f,
                            2345,
                            7777.coerceAtMost(metadata.lastIndex),
                            3,
                        )
                    )
                }
            Log.i("PerfReview", "legacy_ready count=${metadata.size}")
        }

    private suspend fun prepareFilteredRestore() =
        withContext(Dispatchers.IO) {
            val metadata = database.allSongs().first().map { metadataOf(it) }.toMutableList()
            check(metadata.size == 10000)
            database.insert(
                ArtistEntity(
                    "fixture_blocked",
                    "Blocked fixture",
                    isLocal = true,
                    blockedAt = LocalDateTime.now(),
                )
            )
            metadata[4998] =
                metadata[4998].copy(
                    artists =
                        listOf(
                            moe.rukamori.archivetune.models.MediaMetadata.Artist(
                                "fixture_blocked",
                                "Blocked fixture",
                            )
                        )
                )
            metadata[4999] = metadata[4999].copy(explicit = true)
            metadata[5000] = metadata[5000].copy(isMusicVideo = true)
            val queue = LogicalQueue().apply { reset(metadata, 5000) }
            val expected = queue.rows[5001].uid
            dataStore.edit {
                it[HideExplicitKey] = true
                it[HideVideoKey] = true
            }
            QueuePersistenceDisk(filesDir)
                .write(
                    QueueContentV2(
                        content = queue.content,
                        continuation = PersistQueue("Filter fixture", emptyList(), 0, 0),
                    ),
                    queue.currentUid,
                    PersistPlayerState(false, 0, false, 1f, 2345, 5000, 3),
                )
            Log.i("PerfReview", "filter_expected logical=9997 index=4998 uid=$expected position=0")
        }

    private suspend fun verifyData() =
        withContext(Dispatchers.Default) {
            val expected =
                database.likedSongsByCreateDateAsc().first().takeLast(8).asReversed().map { it.id }
            check(database.likedSongsPreview().first().map { it.id } == expected)
            check(database.albumsPreview().first().size == 8)
            check(database.artistsPreview().first().size == 8)
            val request =
                androidx.media3.exoplayer.offline.DownloadRequest.Builder(
                        "fixture",
                        android.net.Uri.parse("https://fixture.invalid/audio"),
                    )
                    .build()
            fun download(state: Int, bytes: Long) =
                androidx.media3.exoplayer.offline.Download(
                    request,
                    state,
                    1L,
                    10L,
                    1000L,
                    0,
                    0,
                    androidx.media3.exoplayer.offline.DownloadProgress().apply {
                        bytesDownloaded = bytes
                    },
                )
            val states =
                flow {
                        repeat(100) {
                            emit(
                                mapOf(
                                    "fixture" to
                                        download(
                                            androidx.media3.exoplayer.offline.Download
                                                .STATE_DOWNLOADING,
                                            it.toLong(),
                                        )
                                )
                            )
                        }
                        emit(
                            mapOf(
                                "fixture" to
                                    download(
                                        androidx.media3.exoplayer.offline.Download.STATE_COMPLETED,
                                        1000,
                                    )
                            )
                        )
                        repeat(10) {
                            emit(
                                mapOf(
                                    "fixture" to
                                        download(
                                            androidx.media3.exoplayer.offline.Download
                                                .STATE_COMPLETED,
                                            1000,
                                        )
                                )
                            )
                        }
                        emit(emptyMap())
                    }
                    .completedMembership()
                    .toList()
            check(
                states.size == 3 &&
                    states[0].isEmpty() &&
                    states[1].keys == setOf("fixture") &&
                    states[2].isEmpty()
            )
            val cover = File(filesDir, "palette-cache-fixture.png")
            fun writeCover(color: Int, revision: Long) {
                val bitmap =
                    android.graphics.Bitmap.createBitmap(
                        32,
                        32,
                        android.graphics.Bitmap.Config.ARGB_8888,
                    )
                bitmap.eraseColor(color)
                cover.outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                check(cover.setLastModified(revision))
            }
            val url = "file://${cover.absolutePath}"
            val stamp = System.currentTimeMillis()
            writeCover(android.graphics.Color.RED, stamp)
            val palettes = coroutineScope {
                List(20) {
                        async {
                            app.vibra.performance.ArtworkPalettes.load(
                                this@PerformanceReviewActivity,
                                url,
                            )
                        }
                    }
                    .awaitAll()
            }
            val first = checkNotNull(palettes.first())
            check(palettes.all { it === first })
            writeCover(android.graphics.Color.GREEN, stamp + 2000)
            val revised =
                checkNotNull(
                    app.vibra.performance.ArtworkPalettes.load(this@PerformanceReviewActivity, url)
                )
            check(revised !== first && revised.dominantSwatch?.rgb != first.dominantSwatch?.rgb)
            app.vibra.performance.ArtworkPalettes.trim()
            check(
                app.vibra.performance.ArtworkPalettes.load(this@PerformanceReviewActivity, url) !==
                    revised
            )
            Log.i(
                "PerfReview",
                "data_checks_passed previews=8,8,8 progressEvents=112 membershipEvents=${states.size} palettes=deduplicated,revision-invalidated,trimmed",
            )
        }

    private suspend fun seed(count: Int) =
        withContext(Dispatchers.IO) {
            dataStore.edit {
                it[OnboardingCompletedKey] = true
                it[SongFilterKey] = "LIBRARY"
                it[SongSortTypeKey] = "CREATE_DATE"
                it[HideExplicitKey] = false
                it[HideVideoKey] = false
                it[QuickPicksKey] = "LAST_LISTEN"
                it[YtmSyncKey] = false
                it[ContentCountryKey] = "US"
                it[ContentLanguageKey] = "en"
            }
            val now = LocalDateTime.now()
            val tone = File(filesDir, "startup-tone.wav")
            check(tone.exists()) { "Run StartupBenchmarkSetupActivity with bootstrap first" }
            val covers =
                (0..7).map { index ->
                    val file = File(filesDir, "perf-cover-$index.png")
                    val bitmap =
                        android.graphics.Bitmap.createBitmap(
                            1080,
                            1080,
                            android.graphics.Bitmap.Config.ARGB_8888,
                        )
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(
                        android.graphics.Color.HSVToColor(floatArrayOf(index * 45f, .7f, .6f))
                    )
                    val paint =
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 300f
                        }
                    canvas.drawText("${index + 1}", 400f, 650f, paint)
                    file.outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                    "file://${file.absolutePath}"
                }
            database.withTransaction {
                openHelper.writableDatabase.execSQL("DELETE FROM song")
                openHelper.writableDatabase.execSQL("DELETE FROM artist")
                openHelper.writableDatabase.execSQL("DELETE FROM album")
                repeat((count + 19) / 20) { i ->
                    insert(
                        ArtistEntity(
                            "perf_artist_$i",
                            "Artista ${(i * 7919) % count}",
                            isLocal = true,
                        )
                    )
                }
                repeat((count + 9) / 10) { i ->
                    insert(
                        AlbumEntity(
                            "perf_album_$i",
                            "perf_list_$i",
                            "Album $i",
                            songCount = 10,
                            duration = 600,
                            thumbnailUrl = covers[i % 8],
                            isLocal = true,
                        )
                    )
                    insert(AlbumArtistMap("perf_album_$i", "perf_artist_${i / 2}", 0))
                }
                repeat(count) { i ->
                    val id = "file://${tone.absolutePath}?track=$i"
                    insert(
                        SongEntity(
                            id,
                            "Brano ${(i * 7919) % count}",
                            duration = 60,
                            thumbnailUrl = covers[i % 8],
                            albumId = "perf_album_${i / 10}",
                            albumName = "Album ${i / 10}",
                            liked = true,
                            likedDate = now.minusSeconds(i.toLong()),
                            inLibrary = now.minusSeconds(i.toLong()),
                            isLocal = true,
                        )
                    )
                    insert(SongArtistMap(id, "perf_artist_${i / 20}", 0))
                }
            }
            cacheHome()
            Log.i("PerfReview", "seed_ready count=$count")
        }

    private suspend fun cacheHome() =
        withContext(Dispatchers.IO) {
            val songs = database.allSongs().first().take(60)
            val items =
                songs.map { song ->
                    moe.rukamori.archivetune.innertube.models.SongItem(
                        id = song.id,
                        title = song.title,
                        artists =
                            song.artists.map {
                                moe.rukamori.archivetune.innertube.models.Artist(it.name, it.id)
                            },
                        duration = 60,
                        thumbnail = song.thumbnailUrl.orEmpty(),
                    )
                }
            val page =
                moe.rukamori.archivetune.innertube.pages.HomePage(
                    null,
                    (0..11).map { section ->
                        moe.rukamori.archivetune.innertube.pages.HomePage.Section(
                            "Fixture ${section + 1}",
                            null,
                            null,
                            null,
                            items.drop((section % 4) * 10).take(15),
                        )
                    },
                )
            app.vibra.home
                .HomeSnapshotDisk(File(cacheDir, "home-v1.json"))
                .write(
                    app.vibra.home.HomeSnapshotDisk.contextKey(null, null, "US", "en"),
                    page,
                    items.take(20),
                )
            Log.i("PerfReview", "cache_ready")
        }

    private suspend fun probe() {
        val handler = Handler(Looper.getMainLooper())
        for (sort in listOf(SongSortType.CREATE_DATE, SongSortType.NAME, SongSortType.ARTIST)) {
            for (background in listOf(false, true)) {
                repeat(5) { iteration ->
                    var last = SystemClock.uptimeMillis()
                    var maxGap = 0L
                    val tick =
                        object : Runnable {
                            override fun run() {
                                val now = SystemClock.uptimeMillis()
                                maxGap = maxOf(maxGap, now - last)
                                last = now
                                handler.postDelayed(this, 16)
                            }
                        }
                    handler.post(tick)
                    delay(50)
                    val start = SystemClock.elapsedRealtime()
                    val traceName = "PerfReview.$sort.$background"
                    Trace.beginAsyncSection(traceName, iteration)
                    val flow = database.songs(sort, false)
                    val result =
                        (if (background) flow.flowOn(Dispatchers.Default) else flow).first()
                    val duration = SystemClock.elapsedRealtime() - start
                    delay(50)
                    handler.removeCallbacks(tick)
                    Trace.endAsyncSection(traceName, iteration)
                    Log.i(
                        "PerfReview",
                        "probe sort=$sort background=$background iteration=$iteration count=${result.size} durationMs=$duration maxMainGapMs=$maxGap",
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (bound) unbindService(binding)
        super.onDestroy()
    }
}
