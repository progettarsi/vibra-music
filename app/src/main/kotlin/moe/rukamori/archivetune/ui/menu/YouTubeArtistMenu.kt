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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vibra.ui.components.*
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.NewAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeArtistMenu(artist: ArtistItem, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val database = LocalDatabase.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val libraryArtist by database.artist(artist.id).collectAsState(initial = null)
  val coroutineScope = rememberCoroutineScope()

  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
  val dividerModifier = Modifier.padding(start = 56.dp)
  val selectionActions = buildList {
    artist.radioEndpoint?.let { watchEndpoint ->
      add(
        NewAction(
          icon = {
            Icon(
              painter = painterResource(R.drawable.radio),
              contentDescription = null,
              modifier = Modifier.size(24.dp),
            )
          },
          text = stringResource(R.string.start_radio),
          onClick = {
            playerConnection.playQueue(YouTubeQueue(watchEndpoint))
            onDismiss()
          },
        )
      )
    }

    artist.shuffleEndpoint?.let { watchEndpoint ->
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
            playerConnection.playQueue(YouTubeQueue(watchEndpoint))
            onDismiss()
          },
        )
      )
    }

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
          val intent =
            Intent().apply {
              action = Intent.ACTION_SEND
              type = "text/plain"
              putExtra(Intent.EXTRA_TEXT, artist.shareLink)
            }
          context.startActivity(Intent.createChooser(intent, null))
          onDismiss()
        },
      )
    )
  }
  MediaMenuSheet(
    layout = MediaMenuLayout.Expanded,
    header = {
      MediaMenuHeader(
        artist.title,
        "",
        artist.thumbnail,
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
              if (libraryArtist?.artist?.bookmarkedAt != null) {
                stringResource(R.string.subscribed)
              } else {
                stringResource(R.string.subscribe)
              }
          )
        },
        leadingContent = {
          Icon(
            painter =
              painterResource(
                if (libraryArtist?.artist?.bookmarkedAt != null) {
                  R.drawable.subscribed
                } else {
                  R.drawable.subscribe
                }
              ),
            contentDescription = null,
          )
        },
        modifier =
          Modifier.clickable {
            database.query {
              val libraryArtist = libraryArtist
              if (libraryArtist != null) {
                update(libraryArtist.artist.toggleLike())
              } else {
                insert(
                  ArtistEntity(
                      id = artist.id,
                      name = artist.title,
                      channelId = artist.channelId,
                      thumbnailUrl = artist.thumbnail,
                    )
                    .toggleLike()
                )
              }
            }
          },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )
    }
  }
}
