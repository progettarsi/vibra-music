/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import app.vibra.ui.components.*
import app.vibra.ui.components.VibraLoadingIndicator
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.YouTubeAlbumRadio
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.utils.reportException

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubeAlbumMenu(albumItem: AlbumItem, navController: NavController, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val database = LocalDatabase.current
  val libraryActions = LocalSyncUtils.current.libraryActions
  val downloadUtil = LocalDownloadUtil.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val album by
    remember(database, albumItem.id) { database.albumWithSongs(albumItem.id) }
      .collectAsState(initial = null)
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    database.album(albumItem.id).collect { album ->
      if (album == null) {
        YouTube.album(albumItem.id)
          .onSuccess { albumPage ->
            database.withTransaction {
              val current = libraryAlbumEntity(albumItem.id)
              if (current == null) insert(albumPage)
              else if (current.songCount == 0) update(current, albumPage)
            }
          }
          .onFailure { reportException(it) }
      }
    }
  }

  var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }

  LaunchedEffect(album) {
    val songs = album?.songs?.map { it.id } ?: return@LaunchedEffect
    downloadUtil.downloads.collect { downloads ->
      downloadState =
        if (songs.all { downloads[it]?.state == Download.STATE_COMPLETED }) {
          Download.STATE_COMPLETED
        } else if (
          songs.all {
            downloads[it]?.state == Download.STATE_QUEUED ||
              downloads[it]?.state == Download.STATE_DOWNLOADING ||
              downloads[it]?.state == Download.STATE_COMPLETED
          }
        ) {
          Download.STATE_DOWNLOADING
        } else {
          Download.STATE_STOPPED
        }
    }
  }

  val artistSeparators = ",;/&"

  // Split artists by configured separators
  data class SplitArtist(
    val name: String,
    val originalArtist: moe.rukamori.archivetune.db.entities.ArtistEntity?,
  )

  val splitArtists =
    remember(album?.artists, artistSeparators) {
      val artists = album?.artists ?: emptyList()
      if (artistSeparators.isEmpty()) {
        artists.map { SplitArtist(it.name, it) }
      } else {
        val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
        artists.flatMap { artist ->
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

  var showErrorPlaylistAddDialog by rememberSaveable { mutableStateOf(false) }

  val notAddedList by remember { mutableStateOf(mutableListOf<Song>()) }

  AddToPlaylistDialog(
    context =
      PlaylistPickerContext(
        albumItem.title,
        albumItem.artists.orEmpty().joinToString { it.name },
        albumItem.thumbnail,
      ),
    isVisible = showChoosePlaylistDialog,
    onGetSong = { album?.songs?.map { it.id }.orEmpty() },
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

      items(notAddedList) { song -> SongListItem(song = song) }
    }
  }

  var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }

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
          Box(
            contentAlignment = Alignment.CenterStart,
            modifier =
              Modifier.fillParentMaxWidth().height(ListItemHeight).padding(horizontal = 24.dp),
          ) {
            Text(
              text = splitArtist.name,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }

  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
  val dividerModifier = Modifier.padding(start = 56.dp)

  val primaryActions =
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
        text = stringResource(R.string.play),
        onClick = {
          onDismiss()
          album?.songs?.let { songs ->
            if (songs.isNotEmpty()) {
              playerConnection.playQueue(YouTubeAlbumRadio(albumItem.playlistId))
            }
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
        text = stringResource(R.string.shuffle),
        onClick = {
          onDismiss()
          album?.songs?.let { songs ->
            if (songs.isNotEmpty()) {
              playerConnection.playQueue(YouTubeAlbumRadio(albumItem.playlistId))
            }
          }
        },
      ),
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
              putExtra(Intent.EXTRA_TEXT, albumItem.shareLink)
            }
          context.startActivity(Intent.createChooser(intent, null))
        },
      ),
    )

  val mediaDownloadAction: NewAction? =
    if (album?.songs?.isNotEmpty() == true)
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
            onClick = {
              album?.songs?.forEach { song ->
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

        Download.STATE_QUEUED,
        Download.STATE_DOWNLOADING -> {
          NewAction(
            id = MenuActionId.Download,
            icon = { VibraLoadingIndicator(modifier = Modifier.size(24.dp)) },
            text = stringResource(R.string.downloading),
            onClick = {
              album?.songs?.forEach { song ->
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
              album?.songs?.let { songs ->
                sendAddMissingDownloads(
                  context = context,
                  songs =
                    songs.map { song -> HeaderDownloadItem(id = song.id, title = song.song.title) },
                  downloads = downloadUtil.downloads.value,
                )
              }
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
          album?.songs?.map { it.toMediaItem() }?.let(playerConnection::playNext)
          onDismiss()
        },
      ),
      NewAction(
        id = MenuActionId.Enqueue,
        icon = {
          Icon(painter = painterResource(R.drawable.queue_music), contentDescription = null)
        },
        text = stringResource(R.string.add_to_queue),
        onClick = {
          album?.songs?.map { it.toMediaItem() }?.let(playerConnection::addToQueue)
          onDismiss()
        },
      ),
      NewAction(
        id = MenuActionId.Playlist,
        icon = {
          Icon(painter = painterResource(R.drawable.playlist_add), contentDescription = null)
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
        title = albumItem.title,
        subtitle = albumItem.artists.orEmpty().joinToString { it.name },
        artworkUrl = albumItem.thumbnail,
        layout = mediaLayout,
        onPlay = primaryActions.firstOrNull { it.id == MenuActionId.Play }?.onClick,
        artworkShape = app.vibra.ui.theme.VibraShapes.AlbumCover,
        toggle = {
          MediaMenuToggle(
            selected = album?.album?.bookmarkedAt != null,
            onClick = { libraryActions.toggleAlbum(albumItem) },
          ) {
            Icon(
              painter =
                painterResource(
                  if (album?.album?.bookmarkedAt != null) R.drawable.favorite
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
  }
}
