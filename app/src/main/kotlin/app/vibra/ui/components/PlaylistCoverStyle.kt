/*
 * Vibra (2026) — persistent playlist cover presentation.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import app.vibra.ui.theme.VibraShapes
import moe.rukamori.archivetune.R
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.dataStore

/** Shape metadata stays in app settings; media files and remote playlists are unchanged. */
enum class PlaylistCoverStyle {
  Pentagon,
  Rounded,
  Circle,
}

fun playlistCoverStyleKey(id: String) = stringPreferencesKey("playlistCoverStyle:$id")

fun parsePlaylistCoverStyle(value: String) =
  PlaylistCoverStyle.entries.firstOrNull { it.name == value } ?: PlaylistCoverStyle.Pentagon

@Composable
fun PlaylistCoverStyle.shape(): Shape =
  when (this) {
    PlaylistCoverStyle.Pentagon -> VibraShapes.Pentagon
    PlaylistCoverStyle.Rounded -> VibraShapes.AlbumCover
    PlaylistCoverStyle.Circle -> CircleShape
  }

private const val PlaylistCoverStylePrefix = "playlistCoverStyle:"

/**
 * One collector for every cover in the app. Library grids, home cards and picker rows read this
 * map instead of each starting its own DataStore collection (and re-mapping every preference
 * write on the main thread, once per visible row).
 */
private object PlaylistCoverStyles {
  val byPlaylist = mutableStateMapOf<String, String>()
  @Volatile private var started = false

  fun start(context: Context) {
    if (started) return
    synchronized(this) {
      if (started) return
      started = true
    }
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
      context.applicationContext.dataStore.data
        .map { preferences ->
          preferences.asMap().entries.mapNotNull { (key, value) ->
            if (key.name.startsWith(PlaylistCoverStylePrefix) && value is String)
              key.name.removePrefix(PlaylistCoverStylePrefix) to value
            else null
          }.toMap()
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .collect { latest ->
          byPlaylist.keys.retainAll(latest.keys)
          byPlaylist.putAll(latest)
        }
    }
  }
}

@Composable
fun rememberPlaylistCoverShape(id: String): Shape {
  val context = LocalContext.current
  SideEffect { PlaylistCoverStyles.start(context) }
  // Until the shared map's first emission, the in-memory preference cache avoids a default flash.
  val style =
    PlaylistCoverStyles.byPlaylist[id]
      ?: PreferenceStore.get(playlistCoverStyleKey(id))
      ?: PlaylistCoverStyle.Pentagon.name
  return parsePlaylistCoverStyle(style).shape()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistCoverStylePicker(
  value: PlaylistCoverStyle,
  onChange: (PlaylistCoverStyle) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(stringResource(R.string.vibra_cover_shape), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      PlaylistCoverStyle.entries.forEach { style ->
        val label =
          stringResource(
            when (style) {
              PlaylistCoverStyle.Pentagon -> R.string.vibra_shape_pentagon
              PlaylistCoverStyle.Rounded -> R.string.vibra_shape_round
              PlaylistCoverStyle.Circle -> R.string.vibra_shape_circle
            }
          )
        Surface(
          onClick = { onChange(style) },
          shape = androidx.compose.foundation.shape.CircleShape,
          color =
            if (value == style) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
          modifier =
            Modifier.size(44.dp).semantics {
              contentDescription = label
              selected = value == style
              role = Role.RadioButton
            },
        ) {
          Box(contentAlignment = Alignment.Center) {
            Box(
              Modifier.size(24.dp)
                .clip(
                  if (style == PlaylistCoverStyle.Rounded)
                    androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                  else style.shape()
                )
                .background(MaterialTheme.colorScheme.primary)
            )
          }
        }
      }
    }
  }
}
