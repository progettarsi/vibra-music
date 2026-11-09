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
// Vibra: independent radii follow the global rounding style.
import app.vibra.ui.theme.adaptiveRoundedCornerShape

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import app.vibra.ui.components.*
import app.vibra.ui.components.VibraLoadingIndicator
import app.vibra.ui.motion.VibraTextButton as TextButton
import app.vibra.ui.motion.vibraControlFeedback
import coil3.compose.AsyncImage
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
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubePlaylistMenu(
  playlist: PlaylistItem,
  songs: List<SongItem> = emptyList(),
  coroutineScope: CoroutineScope,
  onDismiss: () -> Unit,
  selectAction: () -> Unit = {},
  canSelect: Boolean = false,
  snackbarHostState: androidx.compose.material3.SnackbarHostState? = null,
) {
  val context = LocalContext.current
  val database = LocalDatabase.current
  val downloadUtil = LocalDownloadUtil.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val syncUtils = LocalSyncUtils.current
  val dbPlaylist by
    remember(database, playlist.id) { database.playlistByBrowseId(playlist.id) }
      .collectAsState(initial = null)

  var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
  var showImportPlaylistDialog by rememberSaveable { mutableStateOf(false) }
  var showErrorPlaylistAddDialog by rememberSaveable { mutableStateOf(false) }

  val notAddedList by remember { mutableStateOf(mutableListOf<MediaMetadata>()) }

  AddToPlaylistDialog(
    context =
      PlaylistPickerContext(playlist.title, playlist.author?.name.orEmpty(), playlist.thumbnail),
    isVisible = showChoosePlaylistDialog,
    onGetSong = {
      val allSongs =
        songs
          .ifEmpty { YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty() }
          .map { it.toMediaMetadata() }
      database.withTransaction { allSongs.forEach(::insert) }
      allSongs.map { it.id }
    },
    onDismiss = { showChoosePlaylistDialog = false },
    onAddComplete = { songCount, playlistNames ->
      val message =
        when {
          songCount == 1 && playlistNames.size == 1 -> {
            context.getString(R.string.added_to_playlist, playlistNames.first())
          }

          songCount > 1 && playlistNames.size == 1 -> {
            context.getString(R.string.added_n_songs_to_playlist, songCount, playlistNames.first())
          }

          songCount == 1 -> {
            context.getString(R.string.added_to_n_playlists, playlistNames.size)
          }

          else -> {
            context.getString(R.string.added_n_songs_to_n_playlists, songCount, playlistNames.size)
          }
        }
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    },
  )

  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

  var downloadState by remember { mutableStateOf(Download.STATE_STOPPED) }
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
  var showRemoveDownloadDialog by remember { mutableStateOf(false) }
  if (showRemoveDownloadDialog) {
    DefaultDialog(
      onDismiss = { showRemoveDownloadDialog = false },
      content = {
        Text(
          text = stringResource(R.string.remove_download_playlist_confirm, playlist.title),
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

  ImportPlaylistDialog(
    isVisible = showImportPlaylistDialog,
    onGetSong = {
      val allSongs =
        songs
          .ifEmpty { YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty() }
          .map { it.toMediaMetadata() }
      database.withTransaction { allSongs.forEach(::insert) }
      allSongs.map { it.id }
    },
    playlistTitle = playlist.title,
    browseId = playlist.id,
    snackbarHostState = snackbarHostState,
    onDismiss = { showImportPlaylistDialog = false },
  )

  if (showErrorPlaylistAddDialog) {
    ListDialog(
      onDismiss = {
        showErrorPlaylistAddDialog = false
        onDismiss()
      }
    ) {
      item {
        ListItem(
          headlineContent = { Text(text = stringResource(R.string.already_in_playlist)) },
          leadingContent = {
            Image(
              painter = painterResource(R.drawable.close),
              contentDescription = null,
              colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
              modifier = Modifier.size(ListThumbnailSize),
            )
          },
          modifier = Modifier.clickable { showErrorPlaylistAddDialog = false },
        )
      }

      items(notAddedList) { song ->
        ListItem(
          headlineContent = { Text(text = song.title) },
          leadingContent = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ListThumbnailSize)) {
              AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(app.vibra.ui.theme.VibraShapes.RowThumbnail),
              )
            }
          },
          supportingContent = {
            Text(
              text =
                joinByBullet(
                  song.artists.joinToString { it.name },
                  makeTimeString(song.duration * 1000L),
                )
            )
          },
        )
      }
    }
  }

  val dividerModifier = Modifier.padding(start = 56.dp)
  val playText = stringResource(R.string.play)
  val shuffleText = stringResource(R.string.shuffle)
  val startRadioText = stringResource(R.string.start_radio)
  val playNextText = stringResource(R.string.play_next)
  val addToQueueText = stringResource(R.string.add_to_queue)
  val addToPlaylistText = stringResource(R.string.add_to_playlist)
  val importPlaylistText = stringResource(R.string.import_playlist)
  val ytSyncText = stringResource(R.string.yt_sync)
  val shareText = stringResource(R.string.share)
  val selectText = stringResource(R.string.select)

  val primaryActions =
    remember(
      playlist.playEndpoint,
      playlist.shuffleEndpoint,
      playlist.radioEndpoint,
      playText,
      shuffleText,
      startRadioText,
      playerConnection,
      onDismiss,
    ) {
      buildList {
        playlist.playEndpoint?.let { playEndpoint ->
          add(
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
                playerConnection.playQueue(YouTubeQueue.playlist(playEndpoint))
                onDismiss()
              },
            )
          )
        }
        playlist.shuffleEndpoint?.let { shuffleEndpoint ->
          add(
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
                playerConnection.playQueue(moe.rukamori.archivetune.playback.queues.YouTubePlaylistShuffleQueue.create(playlist.id, shuffleEndpoint))
                onDismiss()
              },
            )
          )
        }
        playlist.radioEndpoint?.let { radioEndpoint ->
          add(
            NewAction(
              id = MenuActionId.Radio,
              icon = {
                Icon(
                  painter = painterResource(R.drawable.radio),
                  contentDescription = null,
                  modifier = Modifier.size(24.dp),
                )
              },
              text = startRadioText,
              onClick = {
                playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                onDismiss()
              },
            )
          )
        }
      }
    }

  val mediaDownloadAction: NewAction? =
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
            coroutineScope.launch {
              songs
                .ifEmpty {
                  withContext(Dispatchers.IO) {
                    YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty()
                  }
                }
                .let { playlistSongs ->
                  sendAddMissingDownloads(
                    context = context,
                    songs =
                      playlistSongs.map { song ->
                        HeaderDownloadItem(id = song.id, title = song.title)
                      },
                    downloads = downloadUtil.downloads.value,
                  )
                }
            }
          },
        )
      }
    }
  val mediaExtraActions =
    listOfNotNull(
      NewAction(
        id = MenuActionId.PlayNext,
        icon = {
          Icon(painter = painterResource(R.drawable.playlist_play), contentDescription = null)
        },
        text = playNextText,
        onClick = {
          coroutineScope.launch {
            songs
              .ifEmpty {
                withContext(Dispatchers.IO) {
                  YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty()
                }
              }
              .let { list -> playerConnection.playNext(list.lazyMediaItems { metadataOf(it) }) }
          }
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
          coroutineScope.launch {
            songs
              .ifEmpty {
                withContext(Dispatchers.IO) {
                  YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty()
                }
              }
              .let { list -> playerConnection.addToQueue(list.lazyMediaItems { metadataOf(it) }) }
          }
          onDismiss()
        },
      ),
      NewAction(
        id = MenuActionId.Playlist,
        icon = {
          Icon(painter = painterResource(R.drawable.add_box), contentDescription = null)
        },
        text = addToPlaylistText,
        onClick = { showChoosePlaylistDialog = true },
      ),
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
        title = playlist.title,
        subtitle = playlist.author?.name.orEmpty(),
        artworkUrl = playlist.thumbnail,
        layout = mediaLayout,
        onPlay = primaryActions.firstOrNull { it.id == MenuActionId.Play }?.onClick,
        artworkShape = rememberPlaylistCoverShape(dbPlaylist?.id ?: playlist.id),
        toggle = {
          if (playlist.id != "LM" && !playlist.isEditable) {
            MediaMenuToggle(
              selected = dbPlaylist?.playlist?.bookmarkedAt != null,
              onClick = {
                syncUtils.libraryActions.togglePlaylist(playlist, songs)
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
    MediaMenuActionRows(
      actions = mediaRemainingActions(primaryActions + mediaExtraActions, mediaQuickActions)
    )

    Column {
      if (!playlist.isEditable) {}

      MediaMenuRow(
        headlineContent = { Text(text = importPlaylistText) },
        leadingContent = {
          Icon(painter = painterResource(R.drawable.add), contentDescription = null)
        },
        modifier = Modifier.clickable { showImportPlaylistDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )

      MediaMenuRow(
        headlineContent = { Text(text = ytSyncText) },
        leadingContent = {
          Icon(painter = painterResource(R.drawable.sync), contentDescription = null)
        },
        trailingContent = {
          val checked = dbPlaylist?.playlist?.isAutoSync ?: false
          Switch(
            checked = checked,
            onCheckedChange = { newValue ->
              coroutineScope.launch(Dispatchers.IO) {
                try {
                  val currentDbPlaylist = dbPlaylist
                  if (currentDbPlaylist?.playlist == null) {
                    val playlistPage = YouTube.playlist(playlist.id).completed().getOrNull()
                    val fetchedSongs = playlistPage?.songs.orEmpty()

                    if (fetchedSongs.isEmpty() && newValue) {
                      withContext(Dispatchers.Main) {
                        if (snackbarHostState != null) {
                          snackbarHostState.showSnackbar(context.getString(R.string.import_failed))
                        } else {
                          android.widget.Toast.makeText(
                              context,
                              context.getString(R.string.import_failed),
                              android.widget.Toast.LENGTH_SHORT,
                            )
                            .show()
                        }
                      }
                      return@launch
                    }

                    database.transaction {
                      val playlistEntity =
                        PlaylistEntity(
                          name = playlist.title,
                          browseId = playlist.id,
                          thumbnailUrl = playlist.thumbnail,
                          isEditable = false,
                          isAutoSync = newValue,
                          remoteSongCount =
                            playlist.songCountText?.let {
                              Regex("""\d+""").find(it)?.value?.toIntOrNull()
                            },
                          playEndpointParams = playlist.playEndpoint?.params,
                          shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                          radioEndpointParams = playlist.radioEndpoint?.params,
                        )
                      insert(playlistEntity)
                      fetchedSongs.forEach { song -> insert(song.toMediaMetadata()) }
                      fetchedSongs
                        .mapIndexed { index, song ->
                          PlaylistSongMap(
                            songId = song.id,
                            playlistId = playlistEntity.id,
                            position = index,
                            setVideoId = song.setVideoId,
                          )
                        }
                        .forEach(::insert)
                    }

                    if (newValue) {
                      withContext(Dispatchers.Main) {
                        if (snackbarHostState != null) {
                          snackbarHostState.showSnackbar(
                            context.getString(R.string.playlist_synced)
                          )
                        } else {
                          android.widget.Toast.makeText(
                              context,
                              context.getString(R.string.playlist_synced),
                              android.widget.Toast.LENGTH_SHORT,
                            )
                            .show()
                        }
                      }
                    }
                  } else {
                    val existing = currentDbPlaylist.playlist
                    database.query { update(existing.copy(isAutoSync = newValue)) }

                    if (newValue) {
                      syncUtils.syncAutoSyncPlaylists()
                      withContext(Dispatchers.Main) {
                        if (snackbarHostState != null) {
                          snackbarHostState.showSnackbar(
                            context.getString(R.string.playlist_synced)
                          )
                        } else {
                          android.widget.Toast.makeText(
                              context,
                              context.getString(R.string.playlist_synced),
                              android.widget.Toast.LENGTH_SHORT,
                            )
                            .show()
                        }
                      }
                    }
                  }
                } catch (e: Exception) {
                  e.printStackTrace()
                  withContext(Dispatchers.Main) {
                    val errorMsg =
                      context.getString(R.string.import_failed) +
                        ": ${e.message ?: "Unknown error"}"
                    if (snackbarHostState != null) {
                      snackbarHostState.showSnackbar(errorMsg)
                    } else {
                      android.widget.Toast.makeText(
                          context,
                          errorMsg,
                          android.widget.Toast.LENGTH_SHORT,
                        )
                        .show()
                    }
                  }
                }
              }
            },
            modifier = Modifier.vibraControlFeedback(enabled = true),
          )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )
    }

    Column {
      if ((primaryActions + mediaExtraActions).none { it.id == MenuActionId.Share }) {
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
                  putExtra(Intent.EXTRA_TEXT, playlist.shareLink)
                }
              context.startActivity(Intent.createChooser(intent, null))
              onDismiss()
            },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
      }

      if (canSelect) {

        MediaMenuRow(
          headlineContent = { Text(text = selectText) },
          leadingContent = {
            Icon(painter = painterResource(R.drawable.select_all), contentDescription = null)
          },
          modifier =
            Modifier.clickable {
              onDismiss()
              selectAction()
            },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
      }
    }
  }
}
