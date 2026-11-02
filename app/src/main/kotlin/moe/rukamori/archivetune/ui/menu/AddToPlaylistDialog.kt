/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibra.ui.components.*
import app.vibra.ui.motion.VibraTextButton as TextButton
import coil3.compose.AsyncImage
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.ui.component.CreatePlaylistDialog
import moe.rukamori.archivetune.utils.rememberPreference

private fun preferredAddTargetPlaylist(current: Playlist, candidate: Playlist): Playlist {
  val bySongCount = candidate.songCount.compareTo(current.songCount)
  if (bySongCount != 0) {
    return if (bySongCount > 0) candidate else current
  }

  val byLastUpdated =
    compareNullableDates(candidate.playlist.lastUpdateTime, current.playlist.lastUpdateTime)
  if (byLastUpdated != 0) {
    return if (byLastUpdated > 0) candidate else current
  }

  val byCreatedAt = compareNullableDates(candidate.playlist.createdAt, current.playlist.createdAt)
  if (byCreatedAt != 0) {
    return if (byCreatedAt > 0) candidate else current
  }

  if (candidate.playlist.isEditable != current.playlist.isEditable) {
    return if (candidate.playlist.isEditable) candidate else current
  }

  return if (candidate.id < current.id) candidate else current
}

internal fun playlistsForAddToPlaylist(playlists: List<Playlist>): List<Playlist> =
  playlists
    .asSequence()
    .filter { it.playlist.isEditable || it.playlist.browseId != null }
    .groupBy { it.playlist.browseId ?: it.id }
    .values
    .map { candidates -> candidates.reduce(::preferredAddTargetPlaylist) }
    .toList()

internal enum class AddToPlaylistSortOption {
  RECENTLY_MODIFIED,
  RECENTLY_CREATED,
  MOST_PLAYED,
}

internal fun visiblePlaylistsForAddToPlaylist(
  playlists: List<Playlist>,
  sortOption: AddToPlaylistSortOption,
  query: String,
  playlistPlayCounts: Map<String, Long> = emptyMap(),
): List<Playlist> {
  val normalizedQuery = query.trim()
  val filteredPlaylists =
    playlistsForAddToPlaylist(playlists).filter { playlist ->
      normalizedQuery.isBlank() ||
        playlist.playlist.name.contains(normalizedQuery, ignoreCase = true)
    }

  return filteredPlaylists.sortedWith { first, second ->
    when (sortOption) {
      AddToPlaylistSortOption.RECENTLY_MODIFIED -> {
        compareNullableDates(
          second.playlist.lastUpdateTime ?: second.playlist.createdAt,
          first.playlist.lastUpdateTime ?: first.playlist.createdAt,
        )
      }

      AddToPlaylistSortOption.RECENTLY_CREATED -> {
        compareNullableDates(second.playlist.createdAt, first.playlist.createdAt)
      }

      AddToPlaylistSortOption.MOST_PLAYED -> {
        compareValues(playlistPlayCounts[second.id] ?: 0L, playlistPlayCounts[first.id] ?: 0L)
          .takeIf { it != 0 }
          ?: compareNullableDates(
            second.playlist.lastUpdateTime ?: second.playlist.createdAt,
            first.playlist.lastUpdateTime ?: first.playlist.createdAt,
          )
      }
    }.takeIf { it != 0 }
      ?: compareValues(
        first.playlist.name.lowercase(Locale.getDefault()),
        second.playlist.name.lowercase(Locale.getDefault()),
      )
  }
}

private fun compareNullableDates(first: LocalDateTime?, second: LocalDateTime?): Int {
  if (first == second) return 0
  if (first == null) return -1
  if (second == null) return 1
  return first.compareTo(second)
}

@OptIn(ExperimentalMaterial3Api::class)
/** Optional presentation context; song membership is still owned by the existing mutation path. */
data class PlaylistPickerContext(val title: String, val subtitle: String, val artworkUrl: String?)

@Composable
fun AddToPlaylistDialog(
  isVisible: Boolean,
  allowSyncing: Boolean = true,
  initialTextFieldValue: String? = null,
  onGetSong: suspend () -> List<String>,
  onDismiss: () -> Unit,
  onAddComplete: ((songCount: Int, playlistNames: List<String>) -> Unit)? = null,
  context: PlaylistPickerContext? = null,
) {
  if (!isVisible) return
  val downloadUtil = moe.rukamori.archivetune.LocalDownloadUtil.current
  val database = LocalDatabase.current
  val scope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current
  val allPlaylists by
    remember(database) { database.playlistsByUpdatedDateAsc() }
      .collectAsStateWithLifecycle(initialValue = emptyList())
  // Index once off the UI thread per database emission. Lazy rows perform only a lookup.
  val songMemberships by
    remember(database) {
        database
          .allPlaylistSongMapsForDownloads()
          .map { maps ->
            maps.groupBy({ it.playlistId }, { it.songId }).mapValues { it.value.toSet() }
          }
          .flowOn(Dispatchers.Default)
      }
      .collectAsStateWithLifecycle(initialValue = null)
  val (cookie) = rememberPreference(InnerTubeCookieKey, "")
  var query by rememberSaveable { mutableStateOf("") }
  var showCreate by rememberSaveable { mutableStateOf(false) }
  var songIds by remember { mutableStateOf<List<String>?>(null) }
  var error by remember { mutableStateOf(false) }
  var retry by remember { mutableStateOf(0) }
  var busy by remember { mutableStateOf(false) }
  val available =
    remember(allPlaylists) {
      playlistsForAddToPlaylist(allPlaylists).filter { it.playlist.isEditable }
    }
  val visible =
    remember(available, query) {
      visiblePlaylistsForAddToPlaylist(available, AddToPlaylistSortOption.RECENTLY_MODIFIED, query)
    }
  LaunchedEffect(retry) {
    error = false
    try {
      songIds = withContext(Dispatchers.IO) { onGetSong().distinct() }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      error = true
    }
  }

  LaunchedEffect(available.size) { if (available.size <= 8) query = "" }

  // Each successful remote mutation is persisted immediately. A later failure never
  // pretends that the entire batch succeeded, and a retry only changes missing entries.
  suspend fun toggleMembership(playlist: Playlist, remove: Boolean): Int =
    withContext(Dispatchers.IO) {
      val ids = songIds.orEmpty().toSet()
      val browseId = playlist.playlist.browseId
      check(browseId == null || hasYouTubeLoginCookie(cookie))
      val downloadSnapshot =
        if (!remove) downloadUtil.playlistDownloadSnapshot(playlist.id) else null
      var added = 0
      if (remove) {
        val entries =
          database
            .getPlaylistSongs(playlist.id)
            .filter { it.map.songId in ids }
            .sortedByDescending { it.map.position }
        entries.forEach { entry ->
          if (browseId != null) removeSongFromRemotePlaylist(browseId, entry.map).getOrThrow()
          database.withTransaction {
            move(playlist.id, entry.map.position, Int.MAX_VALUE)
            delete(entry.map.copy(position = Int.MAX_VALUE))
            update(playlist.playlist.copy(lastUpdateTime = LocalDateTime.now()))
          }
        }
      } else {
        ids.forEach { songId ->
          if (database.checkInPlaylist(playlist.id, songId) == 0) {
            val setVideoId = browseId?.let { YouTube.addToPlaylist(it, songId).getOrThrow() }
            database.withTransaction {
              val current = getPlaylistById(playlist.id) ?: kotlin.error("Playlist removed")
              addSongEntriesToPlaylist(current, listOf(songId to setVideoId))
            }
            downloadSnapshot?.let { downloadUtil.downloadPlaylistAdditions(it, listOf(songId)) }
            added++
          }
        }
      }
      added
    }

  VibraDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    groupContent = false,
    contentPadding = PaddingValues(0.dp),
    properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
  ) {
    // Capped below the opening limit: the sheet opens whole and only the playlists scroll.
    Column(
      Modifier.fillMaxWidth().heightIn(max = app.vibra.ui.components.vibraDialogNaturalMaxHeight()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      MediaMenuHeader(
        context?.title ?: stringResource(R.string.add_to_playlist),
        context?.subtitle.orEmpty(),
        context?.artworkUrl,
        MediaMenuLayout.Compact,
        onPlay = null,
        compactArtwork = 56.dp,
      )
      MenuCard(Modifier.weight(1f, fill = false)) {
        if (available.size > 8) {
          TextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            shape = VibraFieldShape,
            colors = vibraTextFieldColors(),
          )
        }
        if (error)
          Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(stringResource(R.string.error_unknown), modifier = Modifier.weight(1f))
            TextButton(onClick = { retry++ }, enabled = !busy) {
              Text(stringResource(R.string.retry))
            }
          }
        LazyColumn(
          modifier = Modifier.fillMaxWidth(),
          contentPadding = PaddingValues(vertical = 12.dp),
        ) {
          item(key = "create") {
            MediaMenuRow(
              headlineContent = { Text(stringResource(R.string.new_playlist)) },
              leadingSize = 40.dp,
              leadingContent = {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                  Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.add), null, Modifier.size(24.dp))
                  }
                }
              },
              modifier = Modifier.clickable(enabled = !busy) { showCreate = true },
            )
          }
          if (!error && (songIds == null || songMemberships == null))
            item(key = "loading") {
              Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp))
              }
            }
          items(visible, key = { it.id }) { playlist ->
            val ids = songIds.orEmpty()
            val members = songMemberships?.get(playlist.id).orEmpty()
            val checked = ids.isNotEmpty() && ids.all { it in members }
            PlaylistMembershipRow(
              playlist = playlist,
              checked = checked,
              enabled = !busy && songIds != null && songMemberships != null && ids.isNotEmpty(),
              onClick = {
                if (!busy && songIds != null && songMemberships != null && ids.isNotEmpty()) {
                  busy = true
                  error = false
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  scope.launch {
                    try {
                      val added = toggleMembership(playlist, checked)
                      if (added > 0) onAddComplete?.invoke(added, listOf(playlist.playlist.name))
                    } catch (cancelled: CancellationException) {
                      throw cancelled
                    } catch (_: Exception) {
                      error = true
                    } finally {
                      busy = false
                    }
                  }
                }
              },
            )
          }
        }
      }
    }
  }
  if (showCreate) {
    CreatePlaylistDialog(
      onDismiss = { showCreate = false },
      initialTextFieldValue = initialTextFieldValue,
      allowSyncing = allowSyncing,
    )
  }
}

/** Shared picker row for saved-song membership and the import-to-playlist workflow. */
@Composable
internal fun PlaylistMembershipRow(
  playlist: Playlist,
  checked: Boolean,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    color = androidx.compose.ui.graphics.Color.Transparent,
    shape = MaterialTheme.shapes.large,
    modifier =
      Modifier.fillMaxWidth().semantics {
        role = Role.Checkbox
        toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
      },
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        Modifier.size(40.dp)
          .clip(rememberPlaylistCoverShape(playlist.id))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
      ) {
        if (playlist.thumbnails.isNotEmpty()) {
          AsyncImage(
            model = playlist.thumbnails.first(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Icon(painterResource(R.drawable.queue_music), contentDescription = null)
        }
      }
      Column(Modifier.weight(1f)) {
        Text(
          playlist.playlist.name,
          fontSize = 16.sp,
          lineHeight = 20.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount),
          fontSize = 13.sp,
          lineHeight = 17.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Box(
        Modifier.size(26.dp)
          .then(
            if (checked) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
            else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
          ),
        contentAlignment = Alignment.Center,
      ) {
        if (checked)
          Icon(
            painterResource(R.drawable.check),
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
          )
      }
    }
  }
}
