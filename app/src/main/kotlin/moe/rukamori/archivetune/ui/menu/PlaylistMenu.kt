/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import app.vibra.playback.lazyMediaItems
import app.vibra.playback.metadataOf

import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import app.vibra.ui.components.*
import app.vibra.ui.components.VibraLoadingIndicator
import app.vibra.ui.motion.VibraTextButton as TextButton
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.isSyncEnabled
import moe.rukamori.archivetune.extensions.isUserLoggedIn
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.AssignTagsDialog
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.EditPlaylistDialog
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import timber.log.Timber

@Immutable
private data class PlaylistSyncProgressUi(val completedSongs: Int, val totalSongs: Int) {
  val percent: Int
    get() =
      if (totalSongs <= 0) {
        0
      } else {
        (completedSongs.coerceIn(0, totalSongs).toFloat() / totalSongs.toFloat() * 100f)
          .roundToInt()
          .coerceIn(0, 100)
      }
}

@Composable
public fun PlaylistMenu(
  playlist: Playlist,
  coroutineScope: CoroutineScope,
  onDismiss: () -> Unit,
  autoPlaylist: Boolean? = false,
  downloadPlaylist: Boolean? = false,
  songList: List<Song>? = emptyList(),
  onChangeCover: (() -> Unit)? = null,
  onRemoveCover: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val database = LocalDatabase.current
  val downloadUtil = LocalDownloadUtil.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val syncUtils = LocalSyncUtils.current
  val dbPlaylist by
    remember(database, playlist.id) { database.playlist(playlist.id) }
      .collectAsState(initial = playlist)

  var songs by remember { mutableStateOf(emptyList<Song>()) }

  LaunchedEffect(Unit) {
    if (autoPlaylist == false) {
      database.playlistSongs(playlist.id).collect { songs = it.map(PlaylistSong::song) }
    } else {
      if (songList != null) {
        songs = songList
      }
    }
  }

  var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }
  var syncProgress by remember { mutableStateOf<PlaylistSyncProgressUi?>(null) }

  val editable: Boolean = playlist.playlist.isEditable == true

  fun syncPlaylistToYouTube() {
    coroutineScope.launch(Dispatchers.IO) {
      var lastProgressPercent = -1
      var lastProgressCompleted = -1

      fun updateProgress(completedSongs: Int, totalSongs: Int) {
        val nextProgressPercent =
          if (totalSongs <= 0) {
            -1
          } else {
            (completedSongs.coerceIn(0, totalSongs).toFloat() / totalSongs.toFloat() * 100f)
              .roundToInt()
              .coerceIn(0, 100)
          }
        val shouldUpdate =
          totalSongs <= 0 ||
            completedSongs == totalSongs ||
            nextProgressPercent != lastProgressPercent ||
            completedSongs - lastProgressCompleted >= 25

        if (!shouldUpdate) return

        lastProgressPercent = nextProgressPercent
        lastProgressCompleted = completedSongs

        coroutineScope.launch(Dispatchers.Main) {
          syncProgress =
            PlaylistSyncProgressUi(completedSongs = completedSongs, totalSongs = totalSongs)
        }
      }

      try {
        if (!context.isSyncEnabled()) {
          withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(
                  if (context.isUserLoggedIn()) {
                    R.string.sync_disabled
                  } else {
                    R.string.not_logged_in_youtube
                  }
                ),
                Toast.LENGTH_SHORT,
              )
              .show()
          }
          return@launch
        }

        val browseId = database.getPlaylistById(playlist.id)?.playlist?.browseId
        val hasPendingEdits = syncUtils.libraryActions.observePlaylistSync(playlist.id).first() != null
        if (browseId == null) {
          syncUtils.libraryActions.publishPlaylist(playlist.id).getOrThrow()
        } else if (!hasPendingEdits) {
          updateProgress(completedSongs = 0, totalSongs = 0)
          syncUtils.syncPlaylistNow(
            browseId = browseId,
            playlistId = playlist.id,
            propagateFailures = true,
          ) { completedSongs, totalSongs ->
            updateProgress(completedSongs = completedSongs, totalSongs = totalSongs)
          }
        }

        withContext(Dispatchers.Main) {
          syncProgress = null
          Toast.makeText(context, context.getString(if (browseId == null || hasPendingEdits) R.string.vibra_playlist_sync_pending else R.string.playlist_synced), Toast.LENGTH_SHORT)
            .show()
          onDismiss()
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (e: Exception) {
        Timber.e(e, "Failed to sync playlist ${playlist.playlist.name}")
        withContext(Dispatchers.Main) {
          syncProgress = null
          Toast.makeText(
              context,
              context.getString(R.string.playlist_sync_failed, e.syncErrorDetail(context)),
              Toast.LENGTH_SHORT,
            )
            .show()
        }
      }
    }
  }

  LaunchedEffect(songs) {
    if (songs.isEmpty()) return@LaunchedEffect
    downloadUtil.downloads.collect { downloads ->
      downloadState =
        if (songs.all { downloads[it.id]?.state == Download.STATE_COMPLETED }) {
          Download.STATE_COMPLETED
        } else if (
          songs.all {
            downloads[it.id]?.state == Download.STATE_QUEUED ||
              downloads[it.id]?.state == Download.STATE_DOWNLOADING ||
              downloads[it.id]?.state == Download.STATE_COMPLETED
          }
        ) {
          Download.STATE_DOWNLOADING
        } else {
          Download.STATE_STOPPED
        }
    }
  }

  var showEditDialog by remember { mutableStateOf(false) }

  if (showEditDialog) {
    EditPlaylistDialog(
      onChangeCover = onChangeCover,
      onRemoveCover = onRemoveCover,
      playlistId = playlist.id,
      initialName = playlist.playlist.name,
      onDismiss = { showEditDialog = false },
      onSave = { name ->
        coroutineScope.launch {
          syncUtils.libraryActions.renamePlaylist(playlist.id, name).fold(
            onSuccess = { onDismiss() },
            onFailure = { Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show() },
          )
        }
      },
    )
  }

  var showRemoveDownloadDialog by remember { mutableStateOf(false) }

  if (showRemoveDownloadDialog) {
    DefaultDialog(
      onDismiss = { showRemoveDownloadDialog = false },
      content = {
        Text(
          text = stringResource(R.string.remove_download_playlist_confirm, playlist.playlist.name),
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(horizontal = 18.dp),
        )
      },
      buttons = {
        TextButton(
          onClick = { showRemoveDownloadDialog = false },
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(text = stringResource(android.R.string.cancel))
        }

        TextButton(
          onClick = {
            showRemoveDownloadDialog = false
            songs.forEach { song ->
              DownloadService.sendRemoveDownload(
                context,
                ExoDownloadService::class.java,
                song.id,
                false,
              )
            }
          },
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(text = stringResource(android.R.string.ok))
        }
      },
    )
  }

  var showDeletePlaylistDialog by remember { mutableStateOf(false) }

  var showAssignTagsDialog by remember { mutableStateOf(false) }

  if (showAssignTagsDialog) {
    AssignTagsDialog(
      playlistId = playlist.id,
      onDismiss = {
        showAssignTagsDialog = false
        onDismiss()
      },
    )
  }

  if (showDeletePlaylistDialog) {
    DefaultDialog(
      onDismiss = { showDeletePlaylistDialog = false },
      content = {
        Text(
          text = stringResource(R.string.delete_playlist_confirm, playlist.playlist.name),
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(horizontal = 18.dp),
        )
      },
      buttons = {
        TextButton(
          onClick = { showDeletePlaylistDialog = false },
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(text = stringResource(android.R.string.cancel))
        }

        TextButton(
          onClick = {
            showDeletePlaylistDialog = false
            coroutineScope.launch {
              syncUtils.libraryActions.removePlaylists(listOf(playlist.id)).fold(
                onSuccess = { onDismiss() },
                onFailure = { Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show() },
              )
            }
          },
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(text = stringResource(android.R.string.ok))
        }
      },
    )
  }

  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
  val dividerModifier = Modifier.padding(start = 56.dp)
  val startRadioText = stringResource(R.string.start_radio)
  val playText = stringResource(R.string.play)
  val shuffleText = stringResource(R.string.shuffle)
  val playNextText = stringResource(R.string.play_next)
  val addToQueueText = stringResource(R.string.add_to_queue)
  val shareText = stringResource(R.string.share)

  val primaryActions =
    remember(
      songs,
      playText,
      shuffleText,
      shareText,
      playlist.playlist.name,
      dbPlaylist?.playlist?.browseId,
      onDismiss,
      playerConnection,
    ) {
      listOf(
        NewAction(
          id = MenuActionId.Play,
          icon = {
            Icon(
              painter = painterResource(R.drawable.play),
              contentDescription = null,
              modifier = Modifier.size(24.dp),
            )
          },
          text = playText,
          onClick = {
            onDismiss()
            if (songs.isNotEmpty()) {
              playerConnection.playQueue(
                ListQueue(title = playlist.playlist.name, items = songs.map(Song::toMediaItem))
              )
            }
          },
        ),
        NewAction(
          id = MenuActionId.Shuffle,
          icon = {
            Icon(
              painter = painterResource(R.drawable.shuffle),
              contentDescription = null,
              modifier = Modifier.size(24.dp),
            )
          },
          text = shuffleText,
          onClick = {
            onDismiss()
            if (songs.isNotEmpty()) {
              playerConnection.playQueue(
                ListQueue(
                  title = playlist.playlist.name,
                  items = songs.shuffled().map(Song::toMediaItem),
                )
              )
            }
          },
        ),
      )
    }

  val mediaDownloadAction: NewAction? =
    if (downloadPlaylist != true)
      when (downloadState) {
        Download.STATE_COMPLETED -> {
          NewAction(
            selected = true,
            id = MenuActionId.Download,
            icon = {
              Icon(
                painter = painterResource(R.drawable.offline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            },
            text = stringResource(R.string.remove_download),
            onClick = { showRemoveDownloadDialog = true },
          )
        }

        Download.STATE_QUEUED,
        Download.STATE_DOWNLOADING -> {
          NewAction(
            id = MenuActionId.Download,
            icon = { VibraLoadingIndicator(modifier = Modifier.size(24.dp)) },
            text = stringResource(R.string.downloading),
            onClick = { showRemoveDownloadDialog = true },
          )
        }

        else -> {
          NewAction(
            id = MenuActionId.Download,
            icon = {
              Icon(painter = painterResource(R.drawable.download), contentDescription = null)
            },
            text = stringResource(R.string.action_download),
            onClick = {
              sendAddMissingDownloads(
                context = context,
                songs =
                  songs.map { song -> HeaderDownloadItem(id = song.id, title = song.song.title) },
                downloads = downloadUtil.downloads.value,
              )
            },
          )
        }
      }
    else null
  val mediaExtraActions =
    listOfNotNull(
      NewAction(
        id = MenuActionId.PlayNext,
        icon = {
          Icon(painter = painterResource(R.drawable.playlist_play), contentDescription = null)
        },
        text = playNextText,
        onClick = {
          coroutineScope.launch { playerConnection.playNext(songs.lazyMediaItems { metadataOf(it) }) }
          onDismiss()
        },
      ),
      NewAction(
        id = MenuActionId.Enqueue,
        icon = {
          Icon(painter = painterResource(R.drawable.queue_music), contentDescription = null)
        },
        text = addToQueueText,
        onClick = {
          onDismiss()
          playerConnection.addToQueue(songs.lazyMediaItems { metadataOf(it) })
        },
      ),
      playlist.playlist.browseId?.let { browseId ->
        NewAction(
          id = MenuActionId.Radio,
          icon = { Icon(painterResource(R.drawable.radio), null) },
          text = startRadioText,
          onClick = {
            coroutineScope.launch(Dispatchers.IO) {
              YouTube.playlist(browseId).getOrNull()?.playlist?.radioEndpoint?.let { endpoint ->
                withContext(Dispatchers.Main) { playerConnection.playQueue(YouTubeQueue(endpoint)) }
              }
            }
            onDismiss()
          },
        )
      },
      mediaDownloadAction,
    )
  val mediaQuickActions =
    mediaQuickActions(primaryActions + mediaExtraActions, MediaMenuKind.Playlist)
  val mediaLayout = rememberMediaMenuLayout()
  val mediaPlayText = stringResource(R.string.play)
  MediaMenuSheet(
    layout = mediaLayout,
    header = {
      MediaMenuHeader(
        title = playlist.playlist.name,
        subtitle = pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount),
        artworkUrl = playlist.thumbnails.firstOrNull(),
        layout = mediaLayout,
        onPlay = primaryActions.firstOrNull { it.id == MenuActionId.Play }?.onClick,
        artworkShape = rememberPlaylistCoverShape(playlist.id),
        toggle = {
          if (playlist.playlist.isEditable != true) {
            MediaMenuToggle(
              selected = dbPlaylist?.playlist?.bookmarkedAt != null,
              onClick = {
                dbPlaylist?.playlist?.let { syncUtils.libraryActions.togglePlaylist(it) }
              },
            ) {
              Icon(
                painter =
                  painterResource(
                    if (dbPlaylist?.playlist?.bookmarkedAt != null) {
                      R.drawable.favorite
                    } else {
                      R.drawable.favorite_border
                    }
                  ),
                contentDescription = stringResource(R.string.vibra_toggle_saved),
              )
            }
          }
        },
      )
    },
    actions = mediaQuickActions,
  ) {
    // Vibra: pinned playlists lead the library (see app.vibra.library.PinnedPlaylists).
    if (autoPlaylist != true) {
      val (pinnedRaw, onPinnedRawChange) =
        moe.rukamori.archivetune.utils.rememberPreference(app.vibra.library.PinnedPlaylistsKey, "")
      val pinned = app.vibra.library.decodePinnedPlaylists(pinnedRaw)
      MediaMenuRow(
        headlineContent = {
          Text(
            text =
              stringResource(
                if (playlist.id in pinned) R.string.vibra_unpin_playlist else R.string.vibra_pin_playlist
              )
          )
        },
        leadingContent = {
          Icon(painter = painterResource(R.drawable.push_pin), contentDescription = null)
        },
        modifier =
          Modifier.clickable {
            onPinnedRawChange(
              app.vibra.library.encodePinnedPlaylists(
                app.vibra.library.togglePinnedPlaylist(pinned, playlist.id)
              )
            )
            onDismiss()
          },
      )
    }
    if (editable && autoPlaylist != true) {
      MediaMenuRow(
        headlineContent = { Text(text = stringResource(R.string.edit)) },
        leadingContent = {
          Icon(painter = painterResource(R.drawable.edit), contentDescription = null)
        },
        modifier = Modifier.clickable { showEditDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )
    }
    if (autoPlaylist != true) {
      MediaMenuRow(
        headlineContent = { Text(text = stringResource(R.string.sync_playlist)) },
        leadingContent = {
          Icon(painter = painterResource(R.drawable.sync), contentDescription = null)
        },
        modifier =
          Modifier.clickable {
            if (syncProgress == null) {
              syncPlaylistToYouTube()
            }
          },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )
    }
    playlist.playlist.shareLink?.let { shareLink ->
      MediaMenuRow(
        headlineContent = { Text(text = shareText) },
        leadingContent = {
          Icon(painter = painterResource(R.drawable.share), contentDescription = null)
        },
        modifier =
          Modifier.clickable {
            val intent =
              Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareLink)
              }
            context.startActivity(Intent.createChooser(intent, null))
            onDismiss()
          },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )
    }
    MediaMenuActionRows(
      mediaRemainingActions(primaryActions + mediaExtraActions, mediaQuickActions).filter {
        it.id != MenuActionId.Share
      }
    )

    Column {
      if (autoPlaylist != true && downloadPlaylist != true) {

        MediaMenuRow(
          headlineContent = { Text(text = stringResource(R.string.manage_tags)) },
          leadingContent = {
            Icon(painter = painterResource(R.drawable.style), contentDescription = null)
          },
          modifier = Modifier.clickable { showAssignTagsDialog = true },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
      }
    }

    if (autoPlaylist != true) {

      MediaMenuRow(
        headlineContent = {
          Text(text = stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
        },
        leadingContent = {
          Icon(
            painter = painterResource(R.drawable.delete),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
        },
        modifier = Modifier.clickable { showDeletePlaylistDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )
    }
  }

  syncProgress?.let { progress ->
    LoadingScreen(
      isVisible = true,
      value = progress.percent,
      title = stringResource(R.string.sync_playlist),
      stepText =
        if (progress.totalSongs > 0) {
          stringResource(
            R.string.playlist_sync_progress_step,
            progress.completedSongs,
            progress.totalSongs,
          )
        } else {
          stringResource(R.string.please_wait)
        },
      indeterminate = progress.totalSongs <= 0,
    )
  }
}

private fun Throwable.syncErrorDetail(context: android.content.Context): String =
  localizedMessage?.takeIf(String::isNotBlank)
    ?: javaClass.simpleName.takeIf(String::isNotBlank)
    ?: context.getString(R.string.error_unknown)
