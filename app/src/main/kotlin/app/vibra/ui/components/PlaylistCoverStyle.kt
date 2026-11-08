/*
 * Vibra (2026) — persistent playlist cover presentation.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.vibra.ui.components

// Vibra: independent radii follow the global rounding style.
import app.vibra.ui.theme.adaptiveRoundedCornerShape

import androidx.annotation.StringRes
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
import app.vibra.ui.theme.VibraEmotionalShapes
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
enum class PlaylistCoverStyle(@StringRes val label: Int) {
  Pentagon(R.string.vibra_shape_pentagon),
  Rounded(R.string.vibra_shape_round),
  Circle(R.string.vibra_shape_circle),
  // Saved by name: new shapes go after the originals, an unknown name falls back to Pentagon.
  Flower(R.string.vibra_shape_flower),
  Cookie(R.string.vibra_shape_cookie),
  Sunny(R.string.vibra_shape_sunny),
  Clover(R.string.vibra_shape_clover),
  Puffy(R.string.vibra_shape_puffy),
  Gem(R.string.vibra_shape_gem),
  Heart(R.string.vibra_shape_heart),
  Star(R.string.vibra_shape_star),
  Burst(R.string.vibra_shape_burst),
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
    PlaylistCoverStyle.Flower -> VibraShapes.Flower
    PlaylistCoverStyle.Cookie -> VibraShapes.Cookie9
    PlaylistCoverStyle.Sunny -> VibraShapes.Sunny
    PlaylistCoverStyle.Clover -> MaterialShapes.Clover4Leaf.toShape()
    PlaylistCoverStyle.Puffy -> MaterialShapes.Puffy.toShape()
    PlaylistCoverStyle.Gem -> MaterialShapes.Gem.toShape()
    PlaylistCoverStyle.Heart -> MaterialShapes.Heart.toShape()
    PlaylistCoverStyle.Star -> VibraEmotionalShapes.Star.toShape()
    PlaylistCoverStyle.Burst -> MaterialShapes.SoftBoom.toShape()
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
    // Two even rows of six. Each 44 dp button keeps a 48 dp touch area, so the row spreads them
    // across the card instead of adding fixed gaps (that would wrap at five on a phone).
    FlowRow(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalArrangement = Arrangement.spacedBy(6.dp),
      maxItemsInEachRow = 6,
    ) {
      PlaylistCoverStyle.entries.forEach { style ->
        val label = stringResource(style.label)
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
                    adaptiveRoundedCornerShape(6.dp)
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
