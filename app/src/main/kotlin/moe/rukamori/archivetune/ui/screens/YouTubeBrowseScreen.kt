/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.vibra.ui.blocks.BlockExpandToggle
import app.vibra.ui.blocks.BlockSize
import app.vibra.ui.blocks.CollectionBlock
import app.vibra.ui.blocks.CoverRowBlock
import app.vibra.ui.blocks.MosaicBlock
import app.vibra.ui.blocks.SongEntry
import app.vibra.ui.blocks.SongsBlock
import app.vibra.ui.blocks.VibraBlock
import app.vibra.ui.blocks.blockColumnsFor
import app.vibra.ui.blocks.blockGrid
import app.vibra.ui.components.ArtworkSkeleton
import app.vibra.ui.components.MoodAtmosphere
import app.vibra.ui.components.TopScrim
import app.vibra.ui.components.VibraGlyphButton
import app.vibra.ui.components.VibraPrimaryButton
import app.vibra.ui.home.toShelfEntry
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSpeed
import app.vibra.ui.navigation.VibraItemActions
import app.vibra.ui.navigation.rememberVibraItemActions
import app.vibra.ui.theme.MoodTone
import app.vibra.ui.theme.VibraDimens
import app.vibra.ui.theme.VibraEmotionalShapes
import app.vibra.ui.theme.VibraShapes
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.viewmodels.YouTubeBrowseViewModel

/*
 * A mood or genre from YouTube Music, in Vibra blocks:
 *   behind everything ... the category's shape in its colours, blurred and drifting
 *   back ................ top-left, consistent with every screen
 *   header .............. the category's name, one button that starts its radio
 *   blocks .............. untitled, like the home: the first section as a mosaic, songs as
 *                         rows, artists as a row of covers, the rest as collections
 */
@Composable
fun YouTubeBrowseScreen(
    navController: NavController,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val actions = rememberVibraItemActions(navController)
    val result by viewModel.result.collectAsStateWithLifecycle()
    val startingRadio by viewModel.startingRadio.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val activeId = mediaMetadata?.id
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    val title = result?.title.orEmpty()
    val polygon = remember(title) { VibraEmotionalShapes.moodPolygon(title) }
    val tone =
        remember(title) {
            VibraEmotionalShapes.moodTone(title)
                ?: viewModel.stripeColor?.let { VibraEmotionalShapes.moodTone(Color(it)) }
        } ?: themeTone()
    val sections = result?.items.orEmpty()
    val blocks = remember(sections, actions, activeId) { browseBlocks(sections, actions, activeId) }
    val hasRadio = remember(sections) { sections.any { section -> section.items.any { it.feedsRadio } } }

    // Nothing to show: back to the home rather than a page that never fills.
    LaunchedEffect(failed) {
        if (failed) {
            Toast.makeText(context, R.string.vibra_browse_failed, Toast.LENGTH_SHORT).show()
            if (!navController.popBackStack(Screens.Home.route, inclusive = false)) navController.navigateUp()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = result != null, enter = fadeIn(Liquid.effects(LiquidSpeed.Slow))) {
            MoodAtmosphere(polygon = polygon, tone = tone)
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = blockColumnsFor(maxWidth - VibraDimens.Gutter * 2)
            LazyColumn(
                state = listState,
                contentPadding =
                    PaddingValues(
                        start = VibraDimens.Gutter,
                        end = VibraDimens.Gutter,
                        top = topPadding + 52.dp,
                        bottom = bottomPadding + 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(VibraDimens.CardGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (result == null) {
                    item(key = "skeleton_title") { ArtworkSkeleton(VibraShapes.Row, Modifier.padding(start = 4.dp, bottom = 14.dp).width(220.dp).height(52.dp)) }
                    item(key = "skeleton_mosaic") { ArtworkSkeleton(VibraShapes.Card, Modifier.fillMaxWidth().height(244.dp)) }
                    item(key = "skeleton_collection") { ArtworkSkeleton(VibraShapes.Card, Modifier.fillMaxWidth().height(258.dp)) }
                } else {
                    item(key = "header") {
                        BrowseHeader(
                            title = title,
                            tone = tone,
                            radioLoading = startingRadio,
                            onStartRadio =
                                if (hasRadio) {
                                    {
                                        viewModel.startRadio(playerConnection) {
                                            Toast.makeText(context, R.string.vibra_for_you_unavailable, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    null
                                },
                        )
                    }
                    blockGrid(blocks = blocks, columns = columns)
                }
            }
        }

        // Clear at rest, so the mood reaches the top; content dissolves under the bar once it moves.
        val scrimDistance = ScrimDistance
        TopScrim(Modifier.graphicsLayer { alpha = scrimAlpha(listState, scrimDistance.toPx()) })
        VibraGlyphButton(
            icon = R.drawable.arrow_back,
            contentDescription = stringResource(R.string.vibra_back),
            onClick = { navController.navigateUp() },
            modifier = Modifier.padding(top = topPadding + 4.dp, start = 8.dp),
        )
    }
}

/** The category's name and its one action, which starts one of its first section's items. */
@Composable
private fun BrowseHeader(
    title: String,
    tone: MoodTone,
    radioLoading: Boolean,
    onStartRadio: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (onStartRadio != null) {
            Spacer(Modifier.height(16.dp))
            VibraPrimaryButton(
                text = stringResource(if (radioLoading) R.string.vibra_radio_loading else R.string.start_radio),
                icon = R.drawable.radio,
                onClick = onStartRadio,
                loading = radioLoading,
                height = RadioButtonHeight,
                containerColor = tone.accent,
                contentColor = tone.onAccent,
            )
        }
    }
}

/** Songs that open in place, like the collections. */
@Composable
private fun BrowseSongsBlock(
    songs: List<SongEntry>,
    size: BlockSize,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SongsBlock(
        songs = songs,
        size = size,
        rows = if (expanded) songs.size else SongsPreview,
        modifier = modifier.animateContentSize(Liquid.spatial()),
        footer =
            if (songs.size > SongsPreview) {
                { BlockExpandToggle(expanded = expanded, count = songs.size, onToggle = { expanded = !expanded }) }
            } else {
                null
            },
    )
}

/** Nothing is left out: what the mosaic has no room for follows it as a collection. */
private fun browseBlocks(
    sections: List<BrowseResult.Item>,
    actions: VibraItemActions,
    activeId: String?,
): List<VibraBlock> =
    buildList {
        var mosaicPlaced = false
        sections.forEachIndexed { index, section ->
            val key = "browse_$index"
            val items = section.items.distinctBy { it.id }
            val entries = items.map { it.toShelfEntry(actions) }
            val songs = items.filterIsInstance<SongItem>()
            when {
                items.isEmpty() -> Unit

                songs.size == items.size -> {
                    val rows = songs.map { it.toSongEntry(actions, activeId) }
                    add(
                        VibraBlock(key = key, relevance = 0.95f, minSize = BlockSize.Medium) { size, modifier ->
                            BrowseSongsBlock(songs = rows, size = size, modifier = modifier)
                        },
                    )
                }

                items.all { it is ArtistItem } ->
                    add(
                        VibraBlock(key = key, relevance = 0.7f, minSize = BlockSize.Medium, maxSize = BlockSize.Medium) { _, modifier ->
                            CoverRowBlock(entries = entries, modifier = modifier)
                        },
                    )

                !mosaicPlaced && items.size >= MosaicMin -> {
                    mosaicPlaced = true
                    val lead = items.first().takeIf { it.canPlay }
                    add(
                        VibraBlock(key = key, relevance = 1f, minSize = BlockSize.Large) { size, modifier ->
                            MosaicBlock(
                                title = null,
                                entries = entries.take(MosaicSize),
                                size = size,
                                onPlayLead = lead?.let { item -> { actions.play(item) } },
                                modifier = modifier,
                            )
                        },
                    )
                    val rest = entries.drop(MosaicSize)
                    if (rest.isNotEmpty()) add(collectionBlock("${key}_rest", rest))
                }

                else -> add(collectionBlock(key, entries))
            }
        }
    }

private fun collectionBlock(
    key: String,
    entries: List<app.vibra.ui.components.ShelfEntry>,
) = VibraBlock(key = key, relevance = 0.7f, minSize = BlockSize.Medium, maxSize = BlockSize.Medium) { _, modifier ->
    CollectionBlock(entries = entries, modifier = modifier)
}

private fun SongItem.toSongEntry(
    actions: VibraItemActions,
    activeId: String?,
) = SongEntry(
    key = id,
    title = title,
    subtitle = artists.joinToString { it.name },
    thumbnailUrl = thumbnail,
    duration = duration?.let { makeTimeString(it * 1000L) },
    onClick = { actions.open(this) },
    onLongClick = { actions.showMenu(this) },
    isActive = id == activeId,
)

/** What the Play button and the mosaic's play can start right away. */
private val YTItem.canPlay: Boolean
    get() =
        when (this) {
            is SongItem, is AlbumItem -> true
            is PlaylistItem -> playEndpoint != null
            is ArtistItem -> radioEndpoint != null
            else -> false
        }

private val YTItem.feedsRadio: Boolean
    get() = this is SongItem || this is AlbumItem || this is PlaylistItem

private fun scrimAlpha(
    list: LazyListState,
    distance: Float,
): Float = if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / distance).coerceIn(0f, 1f)

/** The route of a category, with the colour YouTube gives it. */
internal fun youtubeBrowseRoute(item: MoodAndGenres.Item): String =
    "youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}&color=${item.stripeColor}"

/** A category in no mood family that came without a colour follows the theme. */
@Composable
private fun themeTone(): MoodTone {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        MoodTone(
            bright = scheme.primary,
            middle = scheme.primaryContainer,
            deep = scheme.tertiaryContainer,
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
        )
    }
}

private const val MosaicMin = 3
private const val MosaicSize = 5
private const val SongsPreview = 6
private val RadioButtonHeight = 56.dp
private val ScrimDistance = 96.dp
