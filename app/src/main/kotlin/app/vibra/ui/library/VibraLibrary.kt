/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.vibra.ui.components.VibraCard
import app.vibra.ui.motion.liquidPress
import moe.rukamori.archivetune.constants.AppBarHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.vibra.ui.blocks.BlockSize
import app.vibra.ui.blocks.EmptyBlock
import app.vibra.ui.blocks.MosaicBlock
import app.vibra.ui.blocks.PlaylistBlock
import app.vibra.ui.blocks.PlaylistsBlock
import app.vibra.ui.blocks.SongEntry
import app.vibra.ui.blocks.SongsBlock
import app.vibra.ui.blocks.VibraBlock
import app.vibra.ui.blocks.blockColumnsFor
import app.vibra.ui.blocks.blockGrid
import app.vibra.ui.components.WithVibraTopBar
import app.vibra.ui.home.coverUrl
import app.vibra.ui.home.toShelfEntry
import app.vibra.ui.navigation.VibraItemActions
import app.vibra.ui.navigation.rememberVibraItemActions
import app.vibra.ui.theme.VibraDimens
import app.vibra.ui.theme.VibraShapes
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.utils.makeTimeString
import kotlin.math.min

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  LIBRARY AS BLOCKS (Library screen + the library part of the profile)
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  The user's own music, ordered by their habits: every block's relevance grows with how
 *  much of that thing they have *and use* (playlists count, plays this week, likes,
 *  listening time), so a heavy playlist user sees big playlist blocks first while someone
 *  who mostly likes songs sees their likes first. An empty library collapses to a single
 *  guiding block instead of a wall of empty frames.
 *  Every block's title opens the full list (engine library list, on the right tab).
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/** Snapshot of the library used to build blocks. */
@Immutable
data class LibraryData(
    val playlists: List<Playlist>,
    val likedSongs: List<Song>,
    val likedCount: Int,
    val onRepeat: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
) {
    val isEmpty: Boolean
        get() = playlists.isEmpty() && likedSongs.isEmpty() && onRepeat.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

/** The page's library (kept by its [VibraLibraryViewModel], see VibraLibraryData.kt). */
@Composable
fun rememberLibraryData(viewModel: VibraLibraryViewModel = hiltViewModel()): LibraryData {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val liked by viewModel.likedSongs.collectAsStateWithLifecycle()
    val likedCount by viewModel.likedCount.collectAsStateWithLifecycle()
    val onRepeat by viewModel.onRepeat.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    return LibraryData(
        // "Asc" queries list oldest/least first; habits read best most-recent/most-used first.
        playlists = playlists.asReversed(),
        likedSongs = liked.asReversed().take(8),
        likedCount = likedCount,
        onRepeat = onRepeat,
        albums = albums.asReversed().take(8),
        artists = artists.asReversed().take(8),
    )
}

/** Opens the engine's full library list on [filter]. */
@Composable
fun rememberOpenLibraryList(navController: NavController): (LibraryFilter) -> Unit =
    remember(navController) { { filter -> navController.navigate("$LibraryListRoute?filter=${filter.name}") } }

/**
 * Library blocks, relevance-weighted by habits.
 *
 * @param compact the profile shows a lighter version (sizes capped at medium).
 */
@Composable
fun libraryBlocks(
    data: LibraryData,
    actions: VibraItemActions,
    navController: NavController,
    openList: (LibraryFilter) -> Unit,
    compact: Boolean,
): List<VibraBlock> {
    val blocks = mutableListOf<VibraBlock>()
    val cap = if (compact) BlockSize.Medium else BlockSize.Large

    if (data.isEmpty) {
        val text = stringResource(R.string.vibra_library_empty)
        blocks +=
            VibraBlock(key = "library_empty", relevance = 0.6f, minSize = BlockSize.Medium, maxSize = BlockSize.Medium) { _, modifier ->
                EmptyBlock(text = text, icon = R.drawable.library_music, shape = VibraShapes.Cookie9, modifier = modifier)
            }
        return blocks
    }

    // ── Playlists ──────────────────────────────────────────────────────────────────
    if (data.playlists.isNotEmpty()) {
        val title = stringResource(R.string.vibra_your_playlists)
        val entries = data.playlists.map { it.toShelfEntry(actions) }
        val top = data.playlists.first()
        val byYou = stringResource(R.string.vibra_by_you)
        blocks +=
            if (data.playlists.size == 1) {
                VibraBlock(key = "playlists", relevance = 0.6f, maxSize = BlockSize.Medium) { size, modifier ->
                    PlaylistBlock(
                        title = top.title,
                        subtitle = byYou,
                        thumbnailUrl = top.coverUrl,
                        size = size,
                        onOpen = { actions.open(top) },
                        onPlay = { actions.play(top) },
                        onMore = { actions.showMenu(top) },
                        modifier = modifier,
                    )
                }
            } else {
                VibraBlock(
                    key = "playlists",
                    relevance = 0.55f + min(data.playlists.size, 8) * 0.04f,
                    maxSize = cap,
                ) { size, modifier ->
                    PlaylistsBlock(
                        title = title,
                        entries = entries,
                        size = size,
                        onSeeAll = { openList(LibraryFilter.PLAYLISTS) },
                        modifier = modifier,
                    )
                }
            }
    }

    // ── On repeat (this week) ──────────────────────────────────────────────────────
    if (data.onRepeat.isNotEmpty()) {
        val title = stringResource(R.string.vibra_on_repeat)
        val songs = data.onRepeat.toSongEntries(actions)
        blocks +=
            VibraBlock(
                key = "on_repeat",
                relevance = 0.5f + min(data.onRepeat.size, 6) * 0.06f,
                minSize = BlockSize.Medium,
                maxSize = cap,
            ) { size, modifier ->
                SongsBlock(songs = songs, size = size, title = title, onTitleClick = { navController.navigate(app.vibra.ui.shell.VibraRoutes.Stats) }, modifier = modifier)
            }
    }

    // ── Liked songs ────────────────────────────────────────────────────────────────
    if (data.likedSongs.isNotEmpty()) {
        val title = stringResource(R.string.vibra_liked_songs_count, data.likedCount)
        val songs = data.likedSongs.toSongEntries(actions)
        blocks +=
            VibraBlock(
                key = "liked",
                relevance = (0.45f + data.likedCount / 120f).coerceAtMost(0.85f),
                maxSize = cap,
            ) { size, modifier ->
                SongsBlock(
                    songs = songs,
                    size = size,
                    title = title,
                    onTitleClick = { navController.navigate("auto_playlist/liked") },
                    modifier = modifier,
                )
            }
    }

    // ── Albums and artists, by listening time ──────────────────────────────────────
    if (data.albums.isNotEmpty()) {
        val title = stringResource(R.string.albums)
        val entries = data.albums.map { it.toShelfEntry(actions) }
        blocks +=
            VibraBlock(key = "albums", relevance = 0.4f + min(data.albums.size, 8) * 0.04f, maxSize = cap) { size, modifier ->
                MosaicBlock(title = title, entries = entries, size = size, onTitleClick = { openList(LibraryFilter.ALBUMS) }, modifier = modifier)
            }
    }
    if (data.artists.isNotEmpty()) {
        val title = stringResource(R.string.artists)
        val entries = data.artists.map { it.toShelfEntry(actions) }
        blocks +=
            VibraBlock(key = "artists", relevance = 0.4f + min(data.artists.size, 8) * 0.035f, maxSize = cap) { size, modifier ->
                MosaicBlock(title = title, entries = entries, size = size, onTitleClick = { openList(LibraryFilter.ARTISTS) }, modifier = modifier)
            }
    }
    return blocks
}

private fun List<Song>.toSongEntries(actions: VibraItemActions): List<SongEntry> =
    mapIndexed { index, song ->
        SongEntry(
            key = song.id,
            title = song.song.title,
            subtitle = song.artists.joinToString { it.name },
            thumbnailUrl = song.song.thumbnailUrl,
            duration = song.song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
            onClick = { actions.playSongs(this, index) },
            onLongClick = { actions.showMenu(song) },
        )
    }

/**
 * The Library screen: the same blocks as the profile, at full size, plus quick access to
 * the lists that have no block of their own (songs, downloads, history, device files).
 */
@Composable
fun VibraLibraryScreen(navController: NavController) {
    val actions = rememberVibraItemActions(navController)
    val data = rememberLibraryData()
    val openList = rememberOpenLibraryList(navController)
    val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val blocks = libraryBlocks(data, actions, navController, openList, compact = false)
    val shortcuts = libraryShortcutBlocks(navController, openList)

    WithVibraTopBar(
        title = stringResource(R.string.filter_library),
        onBack = if (navController.previousBackStackEntry != null) ({ navController.navigateUp() }) else null,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = blockColumnsFor(maxWidth - VibraDimens.Gutter * 2)
            val top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
            LazyColumn(
                contentPadding =
                    PaddingValues(
                        start = VibraDimens.Gutter,
                        end = VibraDimens.Gutter,
                        top = top + AppBarHeight + 8.dp,
                        bottom = bottomPadding + 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(VibraDimens.CardGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                blockGrid(blocks = shortcuts + blocks, columns = columns)
            }
        }
    }
}

/** Small tiles for lists without a block of their own. */
@Composable
fun libraryShortcutBlocks(
    navController: NavController,
    openList: (LibraryFilter) -> Unit,
): List<VibraBlock> {
    val items =
        listOf(
            Triple(R.drawable.music_note, R.string.songs) { openList(LibraryFilter.SONGS) },
            Triple(R.drawable.offline, R.string.vibra_downloaded) { navController.navigate("auto_playlist/downloaded") },
            Triple(R.drawable.history, R.string.history) { navController.navigate("history") },
            Triple(R.drawable.storage, R.string.vibra_device_music) { navController.navigate("local_songs") },
        )
    return listOf(
        VibraBlock(key = "shortcuts", relevance = 0.6f, minSize = BlockSize.Medium, maxSize = BlockSize.Medium) { _, modifier ->
            LibraryShortcutRow(items = items, modifier = modifier)
        },
    )
}

/** Card of four tonal tiles (icon in a soft shape + label), two per line. */
@Composable
private fun LibraryShortcutRow(
    items: List<Triple<Int, Int, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    VibraCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.chunked(2).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    line.forEach { (icon, label, onClick) ->
                        ShortcutTile(icon = icon, label = stringResource(label), onClick = onClick, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .liquidPress(interaction, pressedScale = 0.95f)
                .clip(VibraShapes.InnerArtwork)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(VibraShapes.Cookie9).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** Route of the engine's tabbed library list (songs/albums/artists/playlists). */
const val LibraryListRoute = "library_all"
