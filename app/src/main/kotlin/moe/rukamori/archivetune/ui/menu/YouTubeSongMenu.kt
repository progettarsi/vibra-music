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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import app.vibra.ui.components.*
import app.vibra.ui.components.VibraLoadingIndicator
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.utils.makeTimeString
import timber.log.Timber

@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubeSongMenu(song: SongItem, navController: NavController, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val database = LocalDatabase.current
  val downloadUtil = LocalDownloadUtil.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val librarySong by
    remember(database, song.id) { database.song(song.id) }.collectAsState(initial = null)
  val download by
    remember(downloadUtil, song.id) { downloadUtil.getDownload(song.id) }
      .collectAsState(initial = null)
  val coroutineScope = rememberCoroutineScope()
  val syncUtils = LocalSyncUtils.current
  val artists = remember {
    song.artists.mapNotNull {
      it.id?.let { artistId -> MediaMetadata.Artist(id = artistId, name = it.name) }
    }
  }

  // Artist separators for splitting artist names
  val artistSeparators = ",;/&"

  // Split artists by configured separators
  data class SplitArtist(val name: String, val originalArtist: MediaMetadata.Artist?)

  val splitArtists =
    remember(artists, artistSeparators) {
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

  AddToPlaylistDialog(
    context =
      PlaylistPickerContext(song.title, song.artists.joinToString { it.name }, song.thumbnail),
    isVisible = showChoosePlaylistDialog,
    onGetSong = {
      database.withTransaction { insert(song.toMediaMetadata()) }
      listOf(song.id)
    },
    onDismiss = { showChoosePlaylistDialog = false },
    onAddComplete = { _, playlistNames ->
      val message =
        when {
          playlistNames.size == 1 ->
            context.getString(R.string.added_to_playlist, playlistNames.first())
          else -> context.getString(R.string.added_to_n_playlists, playlistNames.size)
        }
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    },
  )

  var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }

  if (showSelectArtistDialog) {
    ListDialog(onDismiss = { showSelectArtistDialog = false }) {
      items(splitArtists.distinctBy { it.name }) { splitArtist ->
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

  val bottomSheetPageState = LocalBottomSheetPageState.current
  val dividerModifier = Modifier.padding(start = 56.dp)
  val startRadioText = stringResource(R.string.start_radio)
  val playNextText = stringResource(R.string.play_next)
  val addToQueueText = stringResource(R.string.add_to_queue)
  val addToPlaylistText = stringResource(R.string.add_to_playlist)
  val shareText = stringResource(R.string.share)

  val primaryActions =
    remember(
      song,
      startRadioText,
      playNextText,
      addToQueueText,
      addToPlaylistText,
      shareText,
      onDismiss,
      playerConnection,
    ) {
      listOf(
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
        ),
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
        ),
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
        ),
        NewAction(
          id = MenuActionId.Playlist,
          icon = {
            Icon(
              painter = painterResource(R.drawable.playlist_add),
              contentDescription = null,
              modifier = Modifier.size(24.dp),
            )
          },
          text = addToPlaylistText,
          onClick = { showChoosePlaylistDialog = true },
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
          text = shareText,
          onClick = {
            val intent =
              Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, song.shareLink)
              }
            context.startActivity(Intent.createChooser(intent, null))
            onDismiss()
          },
        ),
      )
    }

  val mediaDownloadAction: NewAction? =
    if (true)
      when (download?.state) {
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
              database.transaction { insert(song.toMediaMetadata()) }
              val downloadRequest =
                DownloadRequest.Builder(song.id, song.id.toUri())
                  .setCustomCacheKey(song.id)
                  .setData(song.title.toByteArray())
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
        title = song.title,
        subtitle =
          menuSubtitle(
            song.artists.joinToString { it.name },
            song.duration
              ?.takeIf { it > 0 }
              ?.let { moe.rukamori.archivetune.utils.makeTimeString(it * 1000L) },
          ),
        artworkUrl = song.thumbnail,
        layout = mediaLayout,
        onPlay = {
          onDismiss()
          playerConnection.playQueue(
            moe.rukamori.archivetune.playback.queues.ListQueue(
              title = song.title,
              items = listOf(song.toMediaMetadata().toMediaItem()),
            )
          )
        },
        artworkShape = app.vibra.ui.theme.VibraShapes.AlbumCover,
        toggle = {
          MediaMenuToggle(
            selected = librarySong?.song?.liked == true,
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
                  if (librarySong?.song?.liked == true) {
                    R.drawable.favorite
                  } else {
                    R.drawable.favorite_border
                  }
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
        if (song.album != null) {
          {
            navController.navigateToAlbum(song.album!!.id)
            onDismiss()
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

    MediaMenuRow(
      headlineContent = {
        Text(
          text =
            if (librarySong?.song?.inLibrary != null) {
              stringResource(R.string.remove_from_library)
            } else {
              stringResource(R.string.add_to_library)
            }
        )
      },
      leadingContent = {
        Icon(
          painter =
            painterResource(
              if (librarySong?.song?.inLibrary != null) {
                R.drawable.library_add_check
              } else {
                R.drawable.library_add
              }
            ),
          contentDescription = null,
        )
      },
      modifier =
        Modifier.clickable {
          coroutineScope.launch(Dispatchers.IO) {
            try {
              syncUtils.libraryActions.changeSong(song.id, song.toMediaMetadata()) { currentSong ->
                val shouldAdd = currentSong.inLibrary == null
                val now = LocalDateTime.now()
                currentSong.copy(
                  liked = shouldAdd,
                  likedDate = if (shouldAdd) now else null,
                  inLibrary = if (shouldAdd) now else null,
                )
              }.onFailure { error ->
                Timber.w(error, "Failed to update song library state ${song.id}")
                withContext(Dispatchers.Main) {
                  Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                }
              }
            } catch (error: CancellationException) {
              throw error
            } catch (error: Exception) {
              Timber.e(error, "Failed to prepare song library state ${song.id}")
              withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
              }
            }
          }
        },
      colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )

    if (splitArtists.isNotEmpty() || song.album != null) {

      Column {
        if (splitArtists.isNotEmpty()) {}

        if (splitArtists.isNotEmpty() && song.album != null) {}
      }
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
}
