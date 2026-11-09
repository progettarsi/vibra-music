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

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import androidx.media3.exoplayer.offline.Download.STATE_STOPPED
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import app.vibra.ui.components.*
import app.vibra.ui.components.VibraLoadingIndicator
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.ListItem
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads

@SuppressLint("MutableCollectionMutableState")
@Composable
fun AlbumMenu(originalAlbum: Album, navController: NavController, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val database = LocalDatabase.current
  val libraryActions = LocalSyncUtils.current.libraryActions
  val downloadUtil = LocalDownloadUtil.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val scope = rememberCoroutineScope()
  val libraryAlbum by
    remember(database, originalAlbum.id) { database.album(originalAlbum.id) }
      .collectAsStateWithLifecycle(initialValue = originalAlbum)
  val album = libraryAlbum ?: originalAlbum
  var songs by remember { mutableStateOf(emptyList<Song>()) }

  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(album.id) { database.albumSongs(album.id).collect { songs = it } }

  var downloadState by remember { mutableStateOf(STATE_STOPPED) }

  val remoteSongs = remember(songs) { songs.filterNot { it.song.isLocal } }

  LaunchedEffect(remoteSongs, album.album.isLocal) {
    if (album.album.isLocal || remoteSongs.isEmpty()) return@LaunchedEffect
    downloadUtil.downloads.collect { downloads ->
      downloadState =
        if (remoteSongs.all { downloads[it.id]?.state == STATE_COMPLETED }) {
          STATE_COMPLETED
        } else if (
          remoteSongs.all {
            downloads[it.id]?.state == STATE_QUEUED ||
              downloads[it.id]?.state == STATE_DOWNLOADING ||
              downloads[it.id]?.state == STATE_COMPLETED
          }
        ) {
          STATE_DOWNLOADING
        } else {
          STATE_STOPPED
        }
    }
  }

  var refetchIconDegree by remember { mutableFloatStateOf(0f) }

  val rotationAnimation by
    animateFloatAsState(
      targetValue = refetchIconDegree,
      animationSpec = tween(durationMillis = 800),
      label = "",
    )

  // Artist separators for splitting artist names
  val artistSeparators = ",;/&"

  val isLocalAlbum = album.album.isLocal

  // Split artists by configured separators
  data class SplitArtist(
    val name: String,
    val originalArtist: moe.rukamori.archivetune.db.entities.ArtistEntity?,
  )

  val splitArtists =
    remember(album.artists, artistSeparators, isLocalAlbum) {
      if (isLocalAlbum || artistSeparators.isEmpty()) {
        album.artists.map { SplitArtist(it.name, it) }
      } else {
        val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
        album.artists.flatMap { artist ->
          val parts = artist.name.split(separatorRegex).map { it.trim() }.filter { it.isNotEmpty() }
          if (parts.size > 1) {
            parts.mapIndexed { index, name -> SplitArtist(name, if (index == 0) artist else null) }
          } else {
            listOf(SplitArtist(artist.name, artist))
          }
        }
      }
    }

  var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }

  var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }

  var showErrorPlaylistAddDialog by rememberSaveable { mutableStateOf(false) }

  val notAddedList by remember { mutableStateOf(mutableListOf<Song>()) }

  AddToPlaylistDialog(
    context =
      PlaylistPickerContext(
        album.album.title,
        album.artists.joinToString { it.name },
        album.album.thumbnailUrl,
      ),
    isVisible = showChoosePlaylistDialog,
    onGetSong = { songs.map { it.id } },
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

  if (showErrorPlaylistAddDialog) {
    ListDialog(
      onDismiss = {
        showErrorPlaylistAddDialog = false
        onDismiss()
      }
    ) {
      item {
        ListItem(
          title = stringResource(R.string.already_in_playlist),
          thumbnailContent = {
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

      items(notAddedList) { song -> SongListItem(song = song) }
    }
  }

  if (showSelectArtistDialog) {
    ListDialog(onDismiss = { showSelectArtistDialog = false }) {
      items(items = splitArtists.distinctBy { it.name }, key = { it.name }) { splitArtist ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.height(ListItemHeight)
              .clickable {
                splitArtist.originalArtist?.let { artist ->
                  navController.navigateToArtist(artist.id)
                  showSelectArtistDialog = false
                  onDismiss()
                }
              }
              .padding(horizontal = 12.dp),
        ) {
          Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
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
              modifier = Modifier.size(ListThumbnailSize).clip(CircleShape),
            )
          }
          Text(
            text = splitArtist.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
          )
        }
      }
    }
  }

  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
  val dividerModifier = Modifier.padding(start = 56.dp)

  val primaryActions = buildList {
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
        text = stringResource(R.string.play),
        onClick = {
          onDismiss()
          if (songs.isNotEmpty()) {
            playerConnection.playQueue(
              ListQueue(title = album.album.title, items = songs.map(Song::toMediaItem))
            )
          }
        },
      )
    )
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
        text = stringResource(R.string.shuffle),
        onClick = {
          onDismiss()
          if (songs.isNotEmpty()) {
            playerConnection.playQueue(
              ListQueue(title = album.album.title, items = songs.shuffled().map(Song::toMediaItem))
            )
          }
        },
      )
    )
    if (!isLocalAlbum) {
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
          text = stringResource(R.string.share),
          onClick = {
            onDismiss()
            val intent =
              Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(
                  Intent.EXTRA_TEXT,
                  "https://music.youtube.com/playlist?list=${album.album.playlistId}",
                )
              }
            context.startActivity(Intent.createChooser(intent, null))
          },
        )
      )
    }
  }

  val mediaDownloadAction: NewAction? =
    if (!isLocalAlbum && remoteSongs.isNotEmpty())
      when (downloadState) {
        STATE_COMPLETED -> {
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
            onClick = {
              remoteSongs.forEach { song ->
                DownloadService.sendRemoveDownload(
                  context,
                  ExoDownloadService::class.java,
                  song.id,
                  false,
                )
              }
            },
          )
        }

        STATE_QUEUED,
        STATE_DOWNLOADING -> {
          NewAction(
            id = MenuActionId.Download,
            icon = { VibraLoadingIndicator(modifier = Modifier.size(24.dp)) },
            text = stringResource(R.string.downloading),
            onClick = {
              remoteSongs.forEach { song ->
                DownloadService.sendRemoveDownload(
                  context,
                  ExoDownloadService::class.java,
                  song.id,
                  false,
                )
              }
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
              sendAddMissingDownloads(
                context = context,
                songs =
                  remoteSongs.map { song ->
                    HeaderDownloadItem(id = song.id, title = song.song.title)
                  },
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
        text = stringResource(R.string.play_next),
        onClick = {
          onDismiss()
          playerConnection.playNext(songs.lazyMediaItems { metadataOf(it) })
        },
      ),
      NewAction(
        id = MenuActionId.Enqueue,
        icon = {
          Icon(painter = painterResource(R.drawable.queue_music), contentDescription = null)
        },
        text = stringResource(R.string.add_to_queue),
        onClick = {
          onDismiss()
          playerConnection.addToQueue(songs.lazyMediaItems { metadataOf(it) })
        },
      ),
      NewAction(
        id = MenuActionId.Playlist,
        icon = {
          Icon(painter = painterResource(R.drawable.add_box), contentDescription = null)
        },
        text = stringResource(R.string.add_to_playlist),
        onClick = { showChoosePlaylistDialog = true },
      ),
      mediaDownloadAction,
    )
  val mediaQuickActions = mediaQuickActions(primaryActions + mediaExtraActions, MediaMenuKind.Album)
  val mediaLayout = rememberMediaMenuLayout()
  val mediaPlayText = stringResource(R.string.play)
  MediaMenuSheet(
    layout = mediaLayout,
    header = {
      MediaMenuHeader(
        title = album.album.title,
        subtitle =
          menuSubtitle(
            album.artists.joinToString { it.name },
            pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
          ),
        artworkUrl = album.album.thumbnailUrl,
        layout = mediaLayout,
        onPlay = primaryActions.firstOrNull { it.id == MenuActionId.Play }?.onClick,
        artworkShape = app.vibra.ui.theme.VibraShapes.AlbumCover,
        toggle = {
          MediaMenuToggle(
            selected = album.album.bookmarkedAt != null,
            onClick = { libraryActions.toggleAlbum(album.album) },
          ) {
            Icon(
              painter =
                painterResource(
                  if (album.album.bookmarkedAt != null) R.drawable.favorite
                  else R.drawable.favorite_border
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
    if (splitArtists.isNotEmpty()) {
      MediaMenuRow(
        headlineContent = { Text(text = stringResource(R.string.view_artist)) },
        leadingContent = {
          Icon(painter = painterResource(R.drawable.artist), contentDescription = null)
        },
        modifier =
          Modifier.clickable {
            if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
              navController.navigateToArtist(splitArtists[0].originalArtist!!.id)
              onDismiss()
            } else {
              showSelectArtistDialog = true
            }
          },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )
    }
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

    Column {
      if (!isLocalAlbum) {

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
              scope.launch(Dispatchers.IO) {
                YouTube.album(album.id).onSuccess {
                  database.transaction { update(album.album, it, album.artists) }
                }
              }
            },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
      }
    }
  }
}
