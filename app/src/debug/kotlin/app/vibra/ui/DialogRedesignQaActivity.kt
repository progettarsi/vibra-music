/*
 * Vibra (2026) — debug-only production menu and playback-control verification.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.ui

import android.content.*
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import app.vibra.ui.components.*
import app.vibra.ui.motion.*
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.*
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.*
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.*
import moe.rukamori.archivetune.ui.component.*
import moe.rukamori.archivetune.ui.menu.*
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import org.json.JSONObject

/** Uses fixed local fixtures only. It neither signs in nor exercises remote mutation. */
@AndroidEntryPoint
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DialogRedesignQaActivity : ComponentActivity() {
  @Inject lateinit var database: MusicDatabase
  @Inject lateinit var downloads: DownloadUtil
  @Inject lateinit var sync: SyncUtils
  private var connection by mutableStateOf<PlayerConnection?>(null)
  private var ready by mutableStateOf(false)
  private val binding =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        connection =
          PlayerConnection(
            this@DialogRedesignQaActivity,
            binder as MusicService.MusicBinder,
            database,
            lifecycleScope,
          )
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        connection = null
      }
    }
  private val cover: String
    get() = "android.resource://$packageName/${R.drawable.album}"

  private val artist = ArtistEntity("qa_dialog_artist", "Artista", isLocal = true)
  private val album: Album
    get() =
      Album(
        AlbumEntity(
          id = "qa_dialog_album",
          playlistId = "qa_dialog_album_list",
          title = "Album",
          songCount = 12,
          duration = 2160,
          thumbnailUrl = cover,
          isLocal = true,
        ),
        listOf(artist),
      )

  private val song: Song
    get() =
      Song(
        SongEntity(
          id = "qa_dialog_song",
          title = "Canzone 1",
          duration = 180,
          thumbnailUrl = cover,
          albumId = album.id,
          albumName = "Album",
          isLocal = true,
          liked = true,
          inLibrary = LocalDateTime.now(),
        ),
        listOf(artist),
        album.album,
      )

  private val playlist: Playlist
    get() =
      Playlist(
        PlaylistEntity(
          id = "qa_dialog_playlist",
          name = "luna",
          browseId = "qa_dialog_remote",
          thumbnailUrl = cover,
        ),
        1,
        listOf(cover),
      )

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val mode = intent.getStringExtra("mode") ?: "song"
    lifecycleScope.launch {
      if (intent.hasExtra("compact"))
        dataStore.edit { it[CompactMediaMenusKey] = intent.getBooleanExtra("compact", false) }
      withContext(Dispatchers.IO) {
        database.withTransaction {
          insert(song.toMediaMetadata())
          insert(album.album)
          if (database.playlist(playlist.id).first() == null) insert(playlist.playlist)
          repeat(4) { index ->
            val id = "qa_fidelity_playlist_$index"
            if (database.getPlaylistById(id) == null)
              insert(
                PlaylistEntity(
                  id = id,
                  name = if (index == 0) "luna" else "Playlist ${index + 1}",
                  thumbnailUrl = cover,
                  isEditable = true,
                  bookmarkedAt = LocalDateTime.now(),
                )
              )
          }
        }
      }
      ready = true
    }
    bindService(Intent(this, MusicService::class.java), binding, BIND_AUTO_CREATE)
    setContent {
      val localized =
        android.content.res.Configuration(resources.configuration).apply {
          setLocale(java.util.Locale.forLanguageTag(intent.getStringExtra("locale") ?: "it"))
        }
      val localizedResources = createConfigurationContext(localized).resources
      CompositionLocalProvider(
        androidx.compose.ui.platform.LocalResources provides localizedResources,
        androidx.compose.ui.platform.LocalConfiguration provides localized,
      ) {
        ArchiveTuneTheme(darkTheme = !intent.getBooleanExtra("light", false)) {
          if (mode == "play") PlaybackControls() else if (ready && connection != null) Menus(mode)
        }
      }
    }
  }

  @Composable
  private fun Menus(mode: String) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val menu = remember { MenuState() }
    val page = remember { BottomSheetPageState() }
    var shown by remember { mutableStateOf(true) }
    val dismiss = { shown = false }
    CompositionLocalProvider(
      LocalDatabase provides database,
      LocalDownloadUtil provides downloads,
      LocalPlayerConnection provides connection,
      LocalSyncUtils provides sync,
      LocalMenuState provides menu,
      LocalBottomSheetPageState provides page,
    ) {
      Box(
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .statusBarsPadding()
          .padding(20.dp)
      ) {
        app.vibra.ui.motion.VibraButton(onClick = { shown = true }) { Text("Apri") }
      }
      var attempts by remember { mutableIntStateOf(0) }
      if (shown)
        when (mode) {
          "addLoading",
          "addError" ->
            AddToPlaylistDialog(
              true,
              onGetSong = {
                if (mode == "addLoading") delay(8000)
                if (mode == "addError" && attempts++ == 0) error("QA load failure")
                listOf(song.id)
              },
              onDismiss = dismiss,
              context = PlaylistPickerContext("Canzone 1", "Artista · 3:00", cover),
            )
          "create" -> CreatePlaylistDialog(dismiss)
          "edit" -> EditPlaylistDialog("luna", dismiss, {}, playlistId = playlist.id)
          "confirm" ->
            DefaultDialog(
              onDismiss = dismiss,
              title = { Text("Eliminare «luna»?") },
              buttons = {
                VibraDialogButton(dismiss) { Text("Annulla") }
                VibraDialogButton(dismiss, primary = true, destructive = true) { Text("Elimina") }
              },
            ) {
              Text("La playlist verrà rimossa dalla libreria.")
            }
          "timer" ->
            Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.BottomEnd) {
              app.vibra.ui.player.SleepTimerButton(connection!!.service.sleepTimer)
            }
          "add" ->
            AddToPlaylistDialog(
              true,
              onGetSong = { listOf(song.id) },
              onDismiss = dismiss,
              context = PlaylistPickerContext("Canzone 1", "Artista · 3:00", cover),
            )
          "tags" -> TagsManagementDialog(onDismiss = dismiss)
          "picker" ->
            ListDialog(onDismiss = dismiss) {
              items(35) { index -> Text("Playlist $index", Modifier.padding(16.dp)) }
            }
          "basic" ->
            VibraBasicAlertDialog(onDismissRequest = dismiss) {
              Text("Basic alert", Modifier.padding(24.dp))
            }
          "siblings" -> {
            var sibling by remember { mutableStateOf(false) }
            var value by remember { mutableStateOf("Stato conservato") }
            DefaultDialog(onDismiss = dismiss, title = { Text("First sibling") }) {
              OutlinedTextField(value, { value = it })
              VibraDialogButton({ sibling = true }) { Text("Open sibling") }
            }
            if (sibling)
              DefaultDialog(
                onDismiss = { sibling = false },
                title = { Text("Second sibling") },
                buttons = { VibraDialogButton({ sibling = false }) { Text("Close sibling") } },
              ) {
                Text("Same depth")
              }
          }
          "nested" -> {
            var child by remember { mutableStateOf(false) }
            DefaultDialog(onDismiss = dismiss, title = { Text("Parent") }) {
              VibraDialogButton(onClick = { child = true }) { Text("Open child") }
              if (child)
                DefaultDialog(
                  onDismiss = { child = false },
                  title = { Text("Child") },
                  buttons = {
                    VibraDialogButton(onClick = { child = false }) { Text("Back to parent") }
                  },
                ) {
                  Text("Nested content")
                }
            }
          }
          "tempo" -> TempoPitchDialog(dismiss)
          "equalizer" -> {
            val capabilities by connection!!.service.eqCapabilities.collectAsState()
            LaunchedEffect(capabilities) {
              File(filesDir, "dialog-eq-capabilities.json")
                .writeText(org.json.JSONArray(capabilities?.systemPresets.orEmpty()).toString())
            }
            EqualizerDialog(dismiss, {})
          }
          else ->
            VibraBottomSheet(onDismissRequest = dismiss) {
              Column(Modifier.padding(horizontal = 20.dp)) {
                when (mode) {
                  "song" -> SongMenu(song, navController = nav, onDismiss = dismiss)
                  "lyrics" -> {
                    var offset by remember { mutableIntStateOf(0) }
                    LyricsMenu(
                      lyricsProvider = { LyricsEntity(song.id, "Testo di prova") },
                      mediaMetadataProvider = { song.toMediaMetadata() },
                      lyricsSyncOffset = offset,
                      onLyricsSyncOffsetChange = { offset = it },
                      onDismiss = dismiss,
                      lyricsRenderViewModel =
                        androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
                    )
                  }
                  "album" -> AlbumMenu(album, nav, dismiss)
                  "playlist" -> PlaylistMenu(playlist, scope, dismiss)
                  "artist" ->
                    ArtistMenu(
                      moe.rukamori.archivetune.db.entities.Artist(artist, 1, 180),
                      scope,
                      dismiss,
                    )
                  "selection" -> SelectionSongMenu(listOf(song), dismiss, {})
                  "playerMenu" ->
                    PlayerMenu(
                      song.toMediaMetadata(),
                      nav,
                      onCollapsePlayer = {},
                      onShowDetailsDialog = {},
                      onDismiss = dismiss,
                    )
                  "youtubeSong" ->
                    YouTubeSongMenu(
                      moe.rukamori.archivetune.innertube.models.SongItem(
                        "qa_dialog_song",
                        "Canzone 1",
                        listOf(
                          moe.rukamori.archivetune.innertube.models.Artist("Artista", artist.id)
                        ),
                        thumbnail = cover,
                      ),
                      nav,
                      dismiss,
                    )
                  "youtubeAlbum" ->
                    YouTubeAlbumMenu(
                      moe.rukamori.archivetune.innertube.models.AlbumItem(
                        album.id,
                        "qa_dialog_album_list",
                        title = "Album",
                        artists =
                          listOf(
                            moe.rukamori.archivetune.innertube.models.Artist("Artista", artist.id)
                          ),
                        thumbnail = cover,
                      ),
                      nav,
                      dismiss,
                    )
                  "youtubePlaylist" ->
                    YouTubePlaylistMenu(
                      moe.rukamori.archivetune.innertube.models.PlaylistItem(
                        "qa_dialog_remote",
                        "luna",
                        null,
                        "1",
                        cover,
                        moe.rukamori.archivetune.innertube.models.WatchEndpoint(
                          playlistId = "qa_dialog_remote"
                        ),
                        null,
                        null,
                      ),
                      coroutineScope = scope,
                      onDismiss = dismiss,
                    )
                }
              }
            }
        }
      BottomSheetMenu(state = menu)
      BottomSheetPage(state = page)
    }
  }

  @Composable
  private fun PlaybackControls() {
    var playing by remember { mutableStateOf(false) }
    var clicks by remember { mutableIntStateOf(0) }
    val spin = rememberPlaybackSpin(playing)
    LaunchedEffect(playing, clicks) {
      while (true) {
        File(filesDir, "dialog-redesign-play.json")
          .writeText(
            JSONObject()
              .put("playing", playing)
              .put("clicks", clicks)
              .put("degrees", spin.degrees)
              .toString()
          )
        delay(40)
      }
    }
    CompositionLocalProvider(LocalPlaybackSpin provides spin) {
      Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(72.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text("Miniplayer", color = MaterialTheme.colorScheme.onBackground)
        VibraPlayButton(
          playing,
          false,
          {
            playing = !playing
            clicks++
          },
          size = 56.dp,
        )
        Text("Player", color = MaterialTheme.colorScheme.onBackground)
        VibraPlayButton(
          playing,
          false,
          {
            playing = !playing
            clicks++
          },
          size = 96.dp,
        )
      }
    }
  }

  override fun onDestroy() {
    unbindService(binding)
    connection?.dispose()
    super.onDestroy()
  }
}
