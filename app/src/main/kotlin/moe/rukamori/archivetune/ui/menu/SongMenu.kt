/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import app.vibra.ui.components.*
import app.vibra.ui.components.VibraLoadingIndicator
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.shareLocalAudio
import moe.rukamori.archivetune.viewmodels.CachePlaylistViewModel
import timber.log.Timber

@Composable
fun SongMenu(
  originalSong: Song,
  event: Event? = null,
  navController: NavController,
  playlistSong: PlaylistSong? = null,
  playlistBrowseId: String? = null,
  onChangeSource: (() -> Unit)? = null,
  onDismiss: () -> Unit,
  isFromCache: Boolean = false,
) {
  val context = LocalContext.current
  val database = LocalDatabase.current
  val downloadUtil = LocalDownloadUtil.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val songState =
    remember(database, originalSong.id) { database.song(originalSong.id) }
      .collectAsState(initial = originalSong)
  val song = songState.value ?: originalSong
  val download by
    remember(downloadUtil, originalSong.id) { downloadUtil.getDownload(originalSong.id) }
      .collectAsState(initial = null)
  val coroutineScope = rememberCoroutineScope()
  val syncUtils = LocalSyncUtils.current
  var refetchIconDegree by remember { mutableFloatStateOf(0f) }

  val cacheViewModel = hiltViewModel<CachePlaylistViewModel>()

  val rotationAnimation by
    animateFloatAsState(
      targetValue = refetchIconDegree,
      animationSpec = tween(durationMillis = 800),
      label = "",
    )

  // Artist separators for splitting artist names
  val artistSeparators = ",;/&"

  val orderedArtists by
    produceState(initialValue = emptyList<ArtistEntity>(), song) {
      withContext(Dispatchers.IO) {
        val artistMaps = database.songArtistMap(song.id).sortedBy { it.position }
        val sorted =
          artistMaps.mapNotNull { map -> song.artists.firstOrNull { it.id == map.artistId } }
        value = sorted
      }
    }

  // Split artists by configured separators
  data class SplitArtist(val name: String, val originalArtist: ArtistEntity?)

  val splitArtists =
    remember(orderedArtists, artistSeparators) {
      if (artistSeparators.isEmpty()) {
        orderedArtists.map { SplitArtist(it.name, it) }
      } else {
        val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
        orderedArtists.flatMap { artist ->
          val parts = artist.name.split(separatorRegex).map { it.trim() }.filter { it.isNotEmpty() }
          if (parts.size > 1) {
            // If the name contains separators, create split artists
            // The first part keeps the original artist reference for navigation
            parts.mapIndexed { index, name -> SplitArtist(name, if (index == 0) artist else null) }
          } else {
            listOf(SplitArtist(artist.name, artist))
          }
        }
      }
    }

  var showEditDialog by rememberSaveable { mutableStateOf(false) }

  val TextFieldValueSaver: Saver<TextFieldValue, *> =
    Saver(save = { it.text }, restore = { text -> TextFieldValue(text, TextRange(text.length)) })

  var titleField by
    rememberSaveable(stateSaver = TextFieldValueSaver) {
      mutableStateOf(TextFieldValue(song.song.title))
    }

  var artistField by
    rememberSaveable(stateSaver = TextFieldValueSaver) {
      mutableStateOf(TextFieldValue(song.artists.firstOrNull()?.name.orEmpty()))
    }

  if (showEditDialog) {
    TextFieldDialog(
      icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
      title = { Text(text = stringResource(R.string.edit_song)) },
      textFields =
        listOf(
          stringResource(R.string.song_title) to titleField,
          stringResource(R.string.artist_name) to artistField,
        ),
      onTextFieldsChange = { index, newValue ->
        if (index == 0) {
          titleField = newValue
        } else {
          artistField = newValue
        }
      },
      onDoneMultiple = { values ->
        val newTitle = values[0]
        val newArtist = values[1]

        coroutineScope.launch {
          database.query {
            update(song.song.copy(title = newTitle, titleOverride = true))
            val artist = song.artists.firstOrNull()
            if (artist != null) {
              update(artist.copy(name = newArtist))
            }
          }

          showEditDialog = false
          onDismiss()
        }
      },
      onDismiss = { showEditDialog = false },
    )
  }

  var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }

  var showErrorPlaylistAddDialog by rememberSaveable { mutableStateOf(false) }

  AddToPlaylistDialog(
    context =
      PlaylistPickerContext(
        song.song.title,
        song.artists.joinToString { it.name },
        song.song.thumbnailUrl,
      ),
    isVisible = showChoosePlaylistDialog,
    onGetSong = { listOf(song.id) },
    onDismiss = { showChoosePlaylistDialog = false },
    onAddComplete = { songCount, playlistNames ->
      val message =
        when {
          playlistNames.size == 1 ->
            context.getString(R.string.added_to_playlist, playlistNames.first())
          else -> context.getString(R.string.added_to_n_playlists, playlistNames.size)
        }
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    },
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

      items(listOf(song)) { song -> SongListItem(song = song) }
    }
  }

  var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }

  if (showSelectArtistDialog) {
    ListDialog(onDismiss = { showSelectArtistDialog = false }) {
      items(items = splitArtists.distinctBy { it.name }, key = { it.name }) { splitArtist ->
        ListItem(
          headlineContent = {
            Text(text = splitArtist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
          },
          leadingContent = {
            AsyncImage(
              model =
                splitArtist.originalArtist
                  ?.thumbnailUrl
                  ?.resize(
                    width = 200,
                    height = 200,
                    ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                  ),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(40.dp).clip(CircleShape),
            )
          },
          modifier =
            Modifier.fillMaxWidth().clickable {
              splitArtist.originalArtist?.let { artist ->
                navController.navigateToArtist(artist.id)
                showSelectArtistDialog = false
                onDismiss()
              }
            },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
      }
    }
  }

  val bottomSheetPageState = LocalBottomSheetPageState.current
  val isLocalSong = song.song.isLocal

  val startRadioText = stringResource(R.string.start_radio)
  val playNextText = stringResource(R.string.play_next)
  val addToQueueText = stringResource(R.string.add_to_queue)
  val addToPlaylistText = stringResource(R.string.add_to_playlist)
  val shareText = stringResource(R.string.share)
  val editText = stringResource(R.string.edit)

  val primaryActions =
    remember(
      song,
      startRadioText,
      playNextText,
      addToQueueText,
      addToPlaylistText,
      shareText,
      editText,
      isLocalSong,
      onDismiss,
      playerConnection,
    ) {
      buildList {
        if (!isLocalSong) {
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
                onDismiss()
                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
              },
            )
          )
        }
        add(
          NewAction(
            id = MenuActionId.PlayNext,
            icon = {
              Icon(
                painter = painterResource(R.drawable.playlist_play),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
              )
            },
            text = playNextText,
            onClick = {
              onDismiss()
              playerConnection.playNext(song.toMediaItem())
            },
          )
        )
        add(
          NewAction(
            id = MenuActionId.Enqueue,
            icon = {
              Icon(
                painter = painterResource(R.drawable.queue_music),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
              )
            },
            text = addToQueueText,
            onClick = {
              onDismiss()
              playerConnection.addToQueue(song.toMediaItem())
            },
          )
        )
        add(
          NewAction(
            id = MenuActionId.Playlist,
            icon = {
              Icon(
                painter = painterResource(R.drawable.add_box),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
              )
            },
            text = addToPlaylistText,
            onClick = { showChoosePlaylistDialog = true },
          )
        )
        add(
          NewAction(
            id = MenuActionId.Share,
            icon = {
              Icon(
                painter = painterResource(R.drawable.share),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
              )
            },
            text = shareText,
            onClick = {
              onDismiss()
              if (isLocalSong) {
                shareLocalAudio(context, song.id, song.format?.mimeType)
              } else {
                val intent =
                  Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.id}")
                  }
                context.startActivity(Intent.createChooser(intent, null))
              }
            },
          )
        )
        add(
          NewAction(
            id = MenuActionId.Edit,
            icon = {
              Icon(
                painter = painterResource(R.drawable.edit),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
              )
            },
            text = editText,
            onClick = { showEditDialog = true },
          )
        )
      }
    }

  val showMutationSection =
    event != null || playlistSong != null || onChangeSource != null || isFromCache || !isLocalSong

  val mediaDownloadAction: NewAction? =
    if (!isLocalSong)
      when (download?.state) {
        Download.STATE_COMPLETED -> {
          NewAction(
            selected = true,
            id = MenuActionId.Download,
            icon = {
              Icon(
                painter = painterResource(R.drawable.offline),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                contentDescription = null,
              )
            },
            text = stringResource(R.string.remove_download),
            onClick = {
              DownloadService.sendRemoveDownload(
                context,
                ExoDownloadService::class.java,
                song.id,
                false,
              )
            },
          )
        }

        Download.STATE_QUEUED,
        Download.STATE_DOWNLOADING -> {
          NewAction(
            id = MenuActionId.Download,
            icon = { VibraLoadingIndicator(modifier = Modifier.size(24.dp)) },
            text = stringResource(R.string.downloading),
            onClick = {
              DownloadService.sendRemoveDownload(
                context,
                ExoDownloadService::class.java,
                song.id,
                false,
              )
            },
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
              val downloadRequest =
                DownloadRequest.Builder(song.id, song.id.toUri())
                  .setCustomCacheKey(song.id)
                  .setData(song.song.title.toByteArray())
                  .build()
              DownloadService.sendAddDownload(
                context,
                ExoDownloadService::class.java,
                downloadRequest,
                false,
              )
            },
          )
        }
      }
    else null
  val mediaExtraActions = listOfNotNull(mediaDownloadAction)
  val mediaQuickActions = mediaQuickActions(primaryActions + mediaExtraActions, MediaMenuKind.Song)
  val mediaLayout = rememberMediaMenuLayout()
  val mediaPlayText = stringResource(R.string.play)
  MediaMenuSheet(
    layout = mediaLayout,
    header = {
      MediaMenuHeader(
        title = song.song.title,
        subtitle =
          menuSubtitle(
            song.artists.joinToString { it.name },
            song.song.duration
              .takeIf { it > 0 }
              ?.let { moe.rukamori.archivetune.utils.makeTimeString(it * 1000L) },
          ),
        artworkUrl = song.song.thumbnailUrl,
        layout = mediaLayout,
        onPlay = {
          onDismiss()
          playerConnection.playQueue(
            moe.rukamori.archivetune.playback.queues.ListQueue(
              title = song.song.title,
              items = listOf(song.toMediaItem()),
            )
          )
        },
        artworkShape = app.vibra.ui.theme.VibraShapes.AlbumCover,
        toggle = {
          MediaMenuToggle(
            selected = song.song.liked,
            onClick = {
              coroutineScope.launch(Dispatchers.IO) {
                try {
                  syncUtils.libraryActions.changeSong(song.id, song.toMediaMetadata()) { it.toggleLike() }.onFailure { error ->
                    Timber.w(error, "Failed to update liked song ${song.id}")
                    withContext(Dispatchers.Main) {
                      Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                    }
                  }
                } catch (error: CancellationException) {
                  throw error
                } catch (error: Exception) {
                  Timber.e(error, "Failed to prepare liked song ${song.id}")
                  withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                  }
                }
              }
            },
          ) {
            Icon(
              painter =
                painterResource(
                  if (song.song.liked) R.drawable.favorite else R.drawable.favorite_border
                ),
              contentDescription = stringResource(R.string.vibra_toggle_saved),
            )
          }
        },
      )
    },
    actions = mediaQuickActions,
  ) {
    MediaMenuActionRows(
      mediaRemainingActions(primaryActions + mediaExtraActions, mediaQuickActions).filter {
        it.id == MenuActionId.Radio || it.id == MenuActionId.Playlist
      }
    )
    MediaMenuNavigationRow(
      onArtist =
        if (splitArtists.isNotEmpty()) {
          {
            if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
              navController.navigateToArtist(splitArtists[0].originalArtist!!.id)
              onDismiss()
            } else {
              showSelectArtistDialog = true
            }
          }
        } else null,
      onAlbum =
        if (song.song.albumId != null) {
          {
            onDismiss()
            navController.navigateToAlbum(song.song.albumId)
          }
        } else null,
    )
    MediaMenuActionRows(
      mediaRemainingActions(primaryActions + mediaExtraActions, mediaQuickActions).filter {
        it.id == MenuActionId.Share
      }
    )
    MediaMenuActionRows(
      mediaRemainingActions(primaryActions + mediaExtraActions, mediaQuickActions).filter {
        it.id != MenuActionId.Share && it.id != MenuActionId.Radio && it.id != MenuActionId.Playlist
      }
    )

    if (!isLocalSong) {

      MediaMenuRow(
        headlineContent = {
          Text(
            text =
              stringResource(
                if (song.song.inLibrary == null) {
                  R.string.add_to_library
                } else {
                  R.string.remove_from_library
                }
              )
          )
        },
        leadingContent = {
          Icon(
            painter =
              painterResource(
                if (song.song.inLibrary == null) {
                  R.drawable.library_add
                } else {
                  R.drawable.library_add_check
                }
              ),
            contentDescription = null,
          )
        },
        modifier =
          Modifier.clickable {
            onDismiss()
            database.query { update(song.song.toggleLibrary()) }
          },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )
    }

    Column {
      if (!isLocalSong) {
        MediaMenuRow(
          headlineContent = { Text(text = stringResource(R.string.refetch)) },
          leadingContent = {
            Icon(
              painter = painterResource(R.drawable.sync),
              contentDescription = null,
              modifier = Modifier.graphicsLayer { rotationZ = rotationAnimation },
            )
          },
          modifier =
            Modifier.clickable {
              refetchIconDegree -= 360
              coroutineScope.launch(Dispatchers.IO) {
                YouTube.queue(listOf(song.id)).onSuccess {
                  val newSong = it.firstOrNull()
                  if (newSong != null) {
                    database.transaction { update(song, newSong.toMediaMetadata()) }
                  }
                }
              }
            },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
      }

      MediaMenuRow(
        headlineContent = { Text(text = stringResource(R.string.details)) },
        leadingContent = {
          Icon(painter = painterResource(R.drawable.info), contentDescription = null)
        },
        modifier =
          Modifier.clickable {
            onDismiss()
            bottomSheetPageState.show { ShowMediaInfo(song.id) }
          },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )
    }

    if (showMutationSection) {

      val dividerModifier = Modifier.padding(start = 56.dp)
      Column {
        if (event != null) {
          MediaMenuRow(
            headlineContent = {
              Text(
                text = stringResource(R.string.remove_from_history),
                color = MaterialTheme.colorScheme.error,
              )
            },
            leadingContent = {
              Icon(
                painter = painterResource(R.drawable.delete),
                tint = MaterialTheme.colorScheme.error,
                contentDescription = null,
              )
            },
            modifier =
              Modifier.clickable {
                onDismiss()
                database.query { delete(event) }
              },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
        }

        if (onChangeSource != null) {
          MediaMenuRow(
            headlineContent = { Text(text = stringResource(R.string.change_song_source)) },
            supportingContent = { Text(text = stringResource(R.string.change_song_source_desc)) },
            leadingContent = {
              Icon(painter = painterResource(R.drawable.swap_horiz), contentDescription = null)
            },
            modifier =
              Modifier.clickable {
                onChangeSource()
                onDismiss()
              },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
        }

        if (playlistSong != null) {
          MediaMenuRow(
            headlineContent = {
              Text(
                text = stringResource(R.string.remove_from_playlist),
                color = MaterialTheme.colorScheme.error,
              )
            },
            leadingContent = {
              Icon(
                painter = painterResource(R.drawable.delete),
                tint = MaterialTheme.colorScheme.error,
                contentDescription = null,
              )
            },
            modifier =
              Modifier.clickable {
                val map = playlistSong.map
                coroutineScope.launch(Dispatchers.IO) {
                  val result = syncUtils.libraryActions.removePlaylistSongs(map.playlistId, entryIds = setOf(map.id))
                  withContext(Dispatchers.Main) {
                    if (result.isFailure) Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                  }
                  withContext(Dispatchers.Main) { onDismiss() }
                }
              },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
        }

        if (isFromCache) {
          MediaMenuRow(
            headlineContent = {
              Text(
                text = stringResource(R.string.remove_from_cache),
                color = MaterialTheme.colorScheme.error,
              )
            },
            leadingContent = {
              Icon(
                painter = painterResource(R.drawable.delete),
                tint = MaterialTheme.colorScheme.error,
                contentDescription = null,
              )
            },
            modifier =
              Modifier.clickable {
                onDismiss()
                cacheViewModel.removeSongFromCache(song.id)
              },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )
        }
      }
    }
  }
}
