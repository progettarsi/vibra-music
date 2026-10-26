/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.vibra.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.vibra.ui.blocks.BlockSize
import app.vibra.ui.blocks.CoverBlock
import app.vibra.ui.blocks.ForYouBlock
import app.vibra.ui.blocks.MoodEntry
import app.vibra.ui.blocks.MosaicBlock
import app.vibra.ui.blocks.PlaylistBlock
import app.vibra.ui.blocks.SongEntry
import app.vibra.ui.blocks.SongsBlock
import app.vibra.ui.blocks.SpotlightBlock
import app.vibra.ui.blocks.VibraBlock
import app.vibra.ui.blocks.blockColumnsFor
import app.vibra.ui.blocks.blockGrid
import app.vibra.ui.components.ArtworkSkeleton
import app.vibra.ui.components.RefreshLogoState
import app.vibra.ui.components.VibraLogoMark
import app.vibra.ui.components.VibraMessageCard
import app.vibra.ui.components.VibraPullToRefresh
import app.vibra.ui.navigation.VibraItemActions
import app.vibra.ui.navigation.rememberVibraItemActions
import app.vibra.ui.shell.VibraRoutes
import app.vibra.ui.theme.VibraDimens
import app.vibra.ui.theme.VibraEmotionalShapes
import app.vibra.ui.theme.VibraShapes
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomeScreenState
import moe.rukamori.archivetune.home.HomeUiState
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.viewmodels.HomeViewModel

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  HOME (Figma "main-homepage", 1:2) — built from blocks
 * ─────────────────────────────────────────────────────────────────────────────────────
 *   wave logo, then blocks sized by relevance (see blocks/VibraBlocks.kt):
 *     For You (moods + blob) ......... always first, large
 *     Quick picks (songsCard) ........ large when personalised, medium otherwise
 *     Keep listening (coverCard) ..... small, pairs with your playlist
 *     Your playlist (playlistCard) .... small/medium
 *     Shelves (feed + "similar to") .. a fixed rhythm of sizes all the way down
 *                                      (FeedRhythm): a spotlight, a pair of small
 *                                      tiles, a medium and a large mosaic…; shelves
 *                                      that are mostly songs become song lists
 *   No long horizontal shelves: each section shows a few covers and its title opens the
 *   full section (engine browse screen) when YouTube provides one. A shelf named after
 *   its seed (an artist or song it is "similar to") shows no title: the name would
 *   describe someone else's music (see contentTitle).
 *
 *  Data: HomeViewModel (the engine's home pipeline). Nothing is mocked; blocks without
 *  data are simply not emitted, and the grid re-packs the rest.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

@OptIn(FlowPreview::class)
@Composable
fun VibraHomeScreen(
    navController: NavController,
    viewModel: HomeViewModel,
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val actions = rememberVibraItemActions(navController)
    val listState = rememberLazyListState()
    val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val success = state as? HomeScreenState.Success
    val feedSections by rememberUpdatedState(success?.uiState?.homePage?.sections.orEmpty())

    // Endless home: tell the feed which recommendations were really on
    // screen — rows the user lingered on, not prefetched pages — so it does not offer
    // them again for a while.
    LaunchedEffect(listState) {
        val reported = mutableSetOf<String>()
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key.toString() } }
            .debounce(SeenAfterMillis)
            .collect { visibleKeys ->
                val seen =
                    feedSections
                        .filter { section ->
                            val key = section.blockKey()
                            visibleKeys.any { it.contains(key) } && reported.add(key)
                        }.flatMap { it.items.take(SeenItemsPerSection) }
                if (seen.isNotEmpty()) viewModel.markHomeItemsShown(seen)
            }
    }

    VibraPullToRefresh(
        isRefreshing = success?.uiState?.isRefreshing == true,
        onRefresh = { viewModel.onAction(HomeAction.Refresh) },
    ) { travel, logo ->
        // The logo heads the feed and scrolls away with it, but a pull lowers the feed only:
        // the logo stays where it is and shows the refresh.
        VibraLogo(state = logo, listState = listState, top = topPadding + 16.dp)
        BoxWithConstraints(modifier = travel.fillMaxSize()) {
            val columns = blockColumnsFor(maxWidth - VibraDimens.Gutter * 2)
            val blocks =
                success?.let {
                    homeBlocks(
                        ui = it.uiState,
                        actions = actions,
                        navController = navController,
                    )
                }.orEmpty()
            LazyColumn(
                state = listState,
                contentPadding =
                    PaddingValues(
                        start = VibraDimens.Gutter,
                        end = VibraDimens.Gutter,
                        top = topPadding + 16.dp,
                        bottom = bottomPadding + 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(VibraDimens.CardGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Room for the logo, which is drawn outside the list (see above).
                item(key = LogoKey) { Spacer(Modifier.fillMaxWidth().height(LogoRowHeight)) }

                when (val current = state) {
                    HomeScreenState.Loading -> homeSkeleton()
                    HomeScreenState.Empty ->
                        item(key = "empty") {
                            VibraMessageCard(
                                title = stringResource(R.string.vibra_home_empty_title),
                                body = stringResource(R.string.vibra_home_empty_body),
                                action = {
                                    FilledTonalButton(onClick = { navController.navigate(VibraRoutes.Profile) }) {
                                        Text(stringResource(R.string.vibra_open_profile))
                                    }
                                },
                            )
                        }

                    is HomeScreenState.Error ->
                        item(key = "error") {
                            VibraMessageCard(
                                title = stringResource(R.string.vibra_home_error_title),
                                body = stringResource(current.messageResId),
                                action = {
                                    FilledTonalButton(onClick = { viewModel.onAction(HomeAction.Refresh) }) {
                                        Text(stringResource(R.string.retry))
                                    }
                                },
                            )
                        }

                    is HomeScreenState.Success -> {
                        val ui = current.uiState
                        blockGrid(
                            blocks = blocks,
                            columns = columns,
                            onReachEnd = { viewModel.onAction(HomeAction.LoadMore(ui.homePage?.continuation)) },
                        )
                        if (ui.isLoadingMore) {
                            item(key = "loading_more") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Figma 63:354 — the Vibra wave, centred above the first block; it is also the refresh
 * indicator. It sits on the list's first row ([LogoKey]) and scrolls with it,
 * but is not part of the list, so pulling the feed down leaves it in place.
 *
 * @param top where the list's content starts (its top padding).
 */
@Composable
private fun VibraLogo(
    state: RefreshLogoState,
    listState: LazyListState,
    top: Dp,
) {
    val appName = stringResource(R.string.app_name)
    VibraLogoMark(
        state = state,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = top + LogoTopGap)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .graphicsLayer {
                    // Read in the layer: follows the scroll without recomposing.
                    val info = listState.layoutInfo
                    val row = info.visibleItemsInfo.firstOrNull { it.key == LogoKey }
                    if (row == null) {
                        alpha = 0f
                    } else {
                        alpha = 1f
                        translationY = row.offset - info.viewportStartOffset - top.toPx()
                    }
                }.semantics { contentDescription = appName },
    )
}

private const val LogoKey = "logo"

/** Space above the logo, inside its row. */
private val LogoTopGap = 8.dp

/** The logo's row: gap above, the wave, gap below. */
private val LogoRowHeight = LogoTopGap + 35.dp + 12.dp

/** Turns the home pipeline's data into relevance-weighted blocks. */
@Composable
private fun homeBlocks(
    ui: HomeUiState,
    actions: VibraItemActions,
    navController: NavController,
): List<VibraBlock> {
    val blocks = mutableListOf<VibraBlock>()
    // Local quick picks are personalised from listening history; YouTube's otherwise.
    val localPicks = ui.quickPicks
    val remotePicks = ui.remoteQuickPicks?.items.orEmpty().filterIsInstance<SongItem>()
    val hasPicks = localPicks.isNotEmpty() || remotePicks.isNotEmpty()

    // ── For You ────────────────────────────────────────────────────────────────────
    // The cookies beside For You are dedicated mixes: real "Mix" playlists
    // the feed or the account offers (My Mix, Discover Mix, Supermix…), started with one
    // tap. YouTube's home chips only re-filter the feed, so they are not dressed up as
    // playlists; with no mix available the For You blob takes the whole card.
    val moods =
        (ui.homePage?.sections.orEmpty().flatMap { it.items } + ui.accountPlaylists)
            .filterIsInstance<PlaylistItem>()
            .filter { it.title.contains(MixMarker, ignoreCase = true) }
            .distinctBy { it.id }
            .take(2)
            .map { mix -> MoodEntry(label = mix.title, selected = false, onClick = { actions.play(mix) }) }
    val forYouTitle = stringResource(R.string.vibra_for_you)
    val forYouSubtitle = stringResource(if (hasPicks) R.string.vibra_for_you_subtitle else R.string.vibra_for_you_explore)
    blocks +=
        VibraBlock(key = "for_you", relevance = 1f, minSize = BlockSize.Large) { _, modifier ->
            ForYouBlock(
                artworkUrl = localPicks.firstOrNull()?.song?.thumbnailUrl ?: remotePicks.firstOrNull()?.thumbnail,
                title = forYouTitle,
                subtitle = forYouSubtitle,
                moods = moods,
                onClick = {
                    when {
                        localPicks.isNotEmpty() -> actions.playSongs(localPicks, 0)
                        remotePicks.isNotEmpty() -> actions.play(remotePicks.first())
                        // Nothing to recommend yet (new user / offline): explore moods.
                        else -> navController.navigate(Screens.MoodAndGenres.route)
                    }
                },
                modifier = modifier,
            )
        }

    // ── Quick picks ────────────────────────────────────────────────────────────────
    if (hasPicks) {
        val songs =
            if (localPicks.isNotEmpty()) {
                localPicks.mapIndexed { index, song ->
                    SongEntry(
                        key = song.id,
                        title = song.song.title,
                        subtitle = song.artists.joinToString { it.name },
                        thumbnailUrl = song.song.thumbnailUrl,
                        duration = song.song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
                        onClick = { actions.playSongs(localPicks, index) },
                        onLongClick = { actions.showMenu(song) },
                    )
                }
            } else {
                remotePicks.map { item ->
                    SongEntry(
                        key = item.id,
                        title = item.title,
                        subtitle = item.artists.joinToString { it.name },
                        thumbnailUrl = item.thumbnail,
                        duration = item.duration?.let { makeTimeString(it * 1000L) },
                        onClick = { actions.open(item) },
                        onLongClick = { actions.showMenu(item) },
                    )
                }
            }
        blocks +=
            VibraBlock(
                key = "quick_picks",
                relevance = if (localPicks.isNotEmpty()) 0.85f else 0.7f,
                minSize = BlockSize.Medium,
            ) { size, modifier -> SongsBlock(songs = songs, size = size, modifier = modifier) }
    }

    // ── Keep listening + your playlist ─────────────────────────────────────────────
    val keepListening = ui.keepListening.firstOrNull { it !is Playlist }
    if (keepListening != null) {
        val entry = keepListening.toShelfEntry(actions)
        val label = stringResource(R.string.vibra_keep_listening)
        blocks +=
            VibraBlock(key = "keep_listening", relevance = 0.5f, maxSize = BlockSize.Small) { _, modifier ->
                CoverBlock(entry = entry, onPlay = { actions.play(keepListening) }, label = label, modifier = modifier)
            }
    }
    val localPlaylist = (ui.speedDialItems + ui.keepListening).filterIsInstance<Playlist>().firstOrNull()
    val accountPlaylist = ui.accountPlaylists.firstOrNull()
    if (localPlaylist != null || accountPlaylist != null) {
        val byYou = stringResource(R.string.vibra_by_you)
        blocks +=
            VibraBlock(key = "your_playlist", relevance = 0.5f, maxSize = BlockSize.Medium) { size, modifier ->
                PlaylistBlock(
                    title = localPlaylist?.title ?: accountPlaylist?.title.orEmpty(),
                    subtitle = byYou,
                    thumbnailUrl = localPlaylist?.coverUrl ?: accountPlaylist?.thumbnail,
                    size = size,
                    onOpen = { localPlaylist?.let(actions::open) ?: accountPlaylist?.let(actions::open) },
                    onPlay = { localPlaylist?.let(actions::play) ?: accountPlaylist?.let(actions::play) },
                    onMore = { localPlaylist?.let(actions::showMenu) ?: accountPlaylist?.let(actions::showMenu) },
                    modifier = modifier,
                )
            }
    }

    // ── Shelves: recommendations from your library, then the feed ──────────────────
    // "Similar to" shelves are named after the artist or song they start from, not after
    // what they hold, so they go untitled.
    val shelves =
        ui.similarRecommendations.map { recommendation ->
            FeedShelf(key = "similar_${recommendation.title.id}", title = null, items = recommendation.items, browseId = null)
        } +
            ui.homePage
                ?.sections
                .orEmpty()
                .filter { it.items.isNotEmpty() && !VibraEmotionalShapes.isExcludedCategory(it.title) }
                .map { section ->
                    FeedShelf(
                        key = section.blockKey(),
                        title = section.contentTitle(),
                        items = section.items,
                        browseId = section.endpoint?.browseId,
                    )
                }
    var step = 0
    shelves.forEach { shelf ->
        val onTitleClick = shelf.browseId?.let { id -> { navController.navigate("browse/$id") } }
        val songs = shelf.items.filterIsInstance<SongItem>()
        if (songs.size >= SongListMin && songs.size * 10 >= shelf.items.size * 6) {
            // Mostly songs: a list reads better than a wall of covers.
            val rows =
                songs.map { item ->
                    SongEntry(
                        key = item.id,
                        title = item.title,
                        subtitle = item.artists.joinToString { it.name },
                        thumbnailUrl = item.thumbnail,
                        duration = item.duration?.let { makeTimeString(it * 1000L) },
                        onClick = { actions.open(item) },
                        onLongClick = { actions.showMenu(item) },
                    )
                }
            blocks +=
                VibraBlock(key = shelf.key, relevance = 0.7f, minSize = BlockSize.Medium, maxSize = BlockSize.Medium) { size, modifier ->
                    SongsBlock(songs = rows, size = size, title = shelf.title, onTitleClick = onTitleClick, modifier = modifier)
                }
            return@forEach
        }
        val entries = shelf.items.map { it.toShelfEntry(actions) }
        val shape = FeedRhythm[step++ % FeedRhythm.size]
        blocks +=
            if (shape == FeedShape.Spotlight) {
                VibraBlock(key = shelf.key, relevance = 1f, minSize = BlockSize.Large) { _, modifier ->
                    SpotlightBlock(entries = entries, title = shelf.title, onTitleClick = onTitleClick, modifier = modifier)
                }
            } else {
                // A small tile left without a partner is promoted to the medium mosaic by the grid.
                VibraBlock(key = shelf.key, relevance = shape.relevance, minSize = shape.size, maxSize = maxOf(shape.size, BlockSize.Medium)) { size, modifier ->
                    MosaicBlock(title = shelf.title, entries = entries, size = size, onTitleClick = onTitleClick, modifier = modifier)
                }
            }
    }

    // ── Where the feed runs out: favourites from long ago ──────────────────────────
    // The endless feed never repeats recent recommendations; past its end, songs the
    // user loved but has not played for a long time keep the page going.
    val forgotten = ui.forgottenFavorites
    if (forgotten.isNotEmpty()) {
        val title = stringResource(R.string.forgotten_favorites)
        val songs =
            forgotten.mapIndexed { index, song ->
                SongEntry(
                    key = song.id,
                    title = song.song.title,
                    subtitle = song.artists.joinToString { it.name },
                    thumbnailUrl = song.song.thumbnailUrl,
                    duration = song.song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
                    onClick = { actions.playSongs(forgotten, index, title) },
                    onLongClick = { actions.showMenu(song) },
                )
            }
        blocks +=
            VibraBlock(key = "forgotten_favorites", relevance = 0.6f, minSize = BlockSize.Medium) { size, modifier ->
                SongsBlock(songs = songs, size = size, title = title, modifier = modifier)
            }
    }
    return blocks
}

/** Calm skeleton (no flashing) matching the real block geometry. */
private fun LazyListScope.homeSkeleton() {
    item(key = "skeleton_for_you") { ArtworkSkeleton(VibraShapes.Card, Modifier.fillMaxWidth().height(244.dp)) }
    item(key = "skeleton_songs") { ArtworkSkeleton(VibraShapes.Card, Modifier.fillMaxWidth().height(258.dp)) }
    item(key = "skeleton_row") { ArtworkSkeleton(VibraShapes.Card, Modifier.fillMaxWidth().height(180.dp)) }
}

/** Stable block key of a feed section (also used to tell which sections were seen). */
private fun HomePage.Section.blockKey(): String = "section_${title}_${items.firstOrNull()?.id.orEmpty()}"

/**
 * The section's title when it describes its content, else null. YouTube heads its "similar
 * to" shelves with a strapline ("Similar to") over the seed's name and picture: the title
 * alone is then an artist or song whose music the shelf does not hold.
 */
private fun HomePage.Section.contentTitle(): String? = title.takeIf { label.isNullOrBlank() && thumbnail == null }

/** A shelf of the feed, whichever source it comes from. */
private class FeedShelf(
    val key: String,
    val title: String?,
    val items: List<YTItem>,
    val browseId: String?,
)

/** How a shelf is drawn: one step of [FeedRhythm]. */
private enum class FeedShape(
    val size: BlockSize,
    val relevance: Float,
) {
    Spotlight(BlockSize.Large, 1f),
    Large(BlockSize.Large, 0.9f),
    Medium(BlockSize.Medium, 0.7f),
    Small(BlockSize.Small, 0.4f),
}

/**
 * The sizes shelves take, in turn, all the way down the endless feed: big moments
 * between pairs of small tiles, so the page never settles into a run of equal blocks.
 */
private val FeedRhythm =
    listOf(
        FeedShape.Spotlight,
        FeedShape.Small,
        FeedShape.Small,
        FeedShape.Medium,
        FeedShape.Large,
        FeedShape.Small,
        FeedShape.Small,
        FeedShape.Medium,
    )

/** Songs a shelf needs (and share of its items, 60 %) to be drawn as a song list. */
private const val SongListMin = 4

/** A feed row counts as seen once it has stayed on screen this long. */
private const val SeenAfterMillis = 800L

/** Items of a seen section that were actually visible (a mosaic shows at most five). */
private const val SeenItemsPerSection = 5

/** Word YouTube Music puts in the titles of its personal mixes. */
private const val MixMarker = "mix"
