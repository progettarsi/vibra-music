/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibra.ui.components.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistSongSortType
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.NewAction

@Composable
fun ArtistMenu(originalArtist: Artist, coroutineScope: CoroutineScope, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val database = LocalDatabase.current
  val libraryActions = LocalSyncUtils.current.libraryActions
  val playerConnection = LocalPlayerConnection.current ?: return
  val artistState =
    database.artist(originalArtist.id).collectAsStateWithLifecycle(initialValue = originalArtist)
  val artist = artistState.value ?: originalArtist

  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
  val dividerModifier = Modifier.padding(start = 56.dp)

  val selectionActions = buildList {
    if (artist.songCount > 0) {
      add(
        NewAction(
          icon = {
            Icon(
              painter = painterResource(R.drawable.play),
              contentDescription = null,
              modifier = Modifier.size(24.dp),
            )
          },
          text = stringResource(R.string.play),
          onClick = {
            coroutineScope.launch {
              val songs =
                withContext(Dispatchers.IO) {
                  database
                    .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                    .first()
                    .map { it.toMediaItem() }
                }
              playerConnection.playQueue(ListQueue(title = artist.artist.name, items = songs))
            }
            onDismiss()
          },
        )
      )

      add(
        NewAction(
          icon = {
            Icon(
              painter = painterResource(R.drawable.shuffle),
              contentDescription = null,
              modifier = Modifier.size(24.dp),
            )
          },
          text = stringResource(R.string.shuffle),
          onClick = {
            coroutineScope.launch {
              val songs =
                withContext(Dispatchers.IO) {
                  database
                    .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                    .first()
                    .map { it.toMediaItem() }
                    .shuffled()
                }
              playerConnection.playQueue(ListQueue(title = artist.artist.name, items = songs))
            }
            onDismiss()
          },
        )
      )
    }

    if (!artist.artist.isLocal && artist.artist.isYouTubeArtist) {
      add(
        NewAction(
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
                putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/channel/${artist.id}")
              }
            context.startActivity(Intent.createChooser(intent, null))
          },
        )
      )
    }
  }
  MediaMenuSheet(
    layout = MediaMenuLayout.Expanded,
    header = {
      MediaMenuHeader(
        artist.artist.name,
        pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
        artist.artist.thumbnailUrl,
        MediaMenuLayout.Compact,
        null,
        artworkShape = androidx.compose.foundation.shape.CircleShape,
      )
    },
    actions = selectionActions,
  ) {
    Column {
      MediaMenuRow(
        headlineContent = {
          Text(
            text =
              if (artist.artist.bookmarkedAt != null) {
                stringResource(
                  if (artist.artist.isLocal) R.string.remove_from_library else R.string.subscribed
                )
              } else {
                stringResource(
                  if (artist.artist.isLocal) R.string.add_to_library else R.string.subscribe
                )
              }
          )
        },
        leadingContent = {
          Icon(
            painter =
              painterResource(
                if (artist.artist.bookmarkedAt != null) {
                  R.drawable.subscribed
                } else {
                  R.drawable.subscribe
                }
              ),
            contentDescription = null,
          )
        },
        modifier =
          Modifier.clickable { libraryActions.toggleArtist(artist.artist) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )
    }
  }
}
