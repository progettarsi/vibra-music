/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import app.vibra.ui.components.VibraBottomSheet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
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
import java.time.LocalDateTime
import java.util.Locale

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.height
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
private fun preferredAddTargetPlaylist(
    current: Playlist,
    candidate: Playlist,
): Playlist {
    val bySongCount = candidate.songCount.compareTo(current.songCount)
    if (bySongCount != 0) {
        return if (bySongCount > 0) candidate else current
    }

    val byLastUpdated = compareNullableDates(candidate.playlist.lastUpdateTime, current.playlist.lastUpdateTime)
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
        .map { candidates ->
            candidates.reduce(::preferredAddTargetPlaylist)
        }.toList()

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
                compareValues(
                    playlistPlayCounts[second.id] ?: 0L,
                    playlistPlayCounts[first.id] ?: 0L,
                ).takeIf { it != 0 }
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

private fun compareNullableDates(
    first: LocalDateTime?,
    second: LocalDateTime?,
): Int {
    if (first == second) return 0
    if (first == null) return -1
    if (second == null) return 1
    return first.compareTo(second)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    isVisible: Boolean,
    allowSyncing: Boolean = true,
    initialTextFieldValue: String? = null,
    onGetSong: suspend () -> List<String>,
    onDismiss: () -> Unit,
    onAddComplete: ((songCount: Int, playlistNames: List<String>) -> Unit)? = null,
) {
    if (!isVisible) return
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val allPlaylists by database.playlistsByUpdatedDateAsc().collectAsStateWithLifecycle(initialValue = emptyList())
    val songMaps by remember(database) {
        database.allPlaylistSongMapsForDownloads().map<List<PlaylistSongMap>, List<PlaylistSongMap>?> { it }
    }.collectAsStateWithLifecycle(initialValue = null)
    val (cookie) = rememberPreference(InnerTubeCookieKey, "")
    var query by rememberSaveable { mutableStateOf("") }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var songIds by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val available = remember(allPlaylists) { playlistsForAddToPlaylist(allPlaylists).filter { it.playlist.isEditable } }
    val visible = remember(available, query) {
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
    suspend fun toggleMembership(playlist: Playlist, remove: Boolean): Int = withContext(Dispatchers.IO) {
        val ids = songIds.orEmpty().toSet()
        val browseId = playlist.playlist.browseId
        check(browseId == null || hasYouTubeLoginCookie(cookie))
        var added = 0
        if (remove) {
            val entries = database.getPlaylistSongs(playlist.id).filter { it.map.songId in ids }
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
                    added++
                }
            }
        }
        added
    }

    VibraBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.add_to_playlist), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showCreate = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) { Text(stringResource(R.string.new_playlist)) }
            if (available.size > 8) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                )
            }
            if (error) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.error_unknown), modifier = Modifier.weight(1f))
                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                }
            }
            if (songIds == null || songMaps == null || busy) {
                CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { playlist ->
                    val ids = songIds.orEmpty()
                    val members = songMaps.orEmpty().filter { it.playlistId == playlist.id }.map { it.songId }.toSet()
                    val checked = ids.isNotEmpty() && ids.all { it in members }
                    PlaylistMembershipRow(
                        playlist = playlist,
                        checked = checked,
                        enabled = !busy && songIds != null && songMaps != null && ids.isNotEmpty(),
                        onClick = {
                            if (!busy && songIds != null && songMaps != null && ids.isNotEmpty()) {
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
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (playlist.thumbnails.isNotEmpty()) {
                                    AsyncImage(
                                        model = playlist.thumbnails.first(), contentDescription = null,
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(painterResource(R.drawable.queue_music), contentDescription = null)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(playlist.playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            AnimatedVisibility(visible = checked, enter = androidx.compose.animation.fadeIn(app.vibra.ui.motion.Liquid.effects()), exit = androidx.compose.animation.fadeOut(app.vibra.ui.motion.Liquid.effects())) {
                                Icon(painterResource(R.drawable.check), contentDescription = stringResource(R.string.playlist_membership_selected))
                            }
                        }

    }
}
