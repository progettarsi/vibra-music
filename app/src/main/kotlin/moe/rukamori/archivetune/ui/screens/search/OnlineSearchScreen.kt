/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.search

// Vibra: independent radii follow the global rounding style.
import app.vibra.ui.theme.adaptiveRoundedCornerShape

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.vibra.ui.blocks.BlockSize
import app.vibra.ui.blocks.MoodPalette
import app.vibra.ui.blocks.MosaicBlock
import app.vibra.ui.blocks.SongEntry
import app.vibra.ui.blocks.SongsBlock
import app.vibra.ui.blocks.moodGradient
import app.vibra.ui.components.ShelfEntry
import app.vibra.ui.components.VibraArtwork
import app.vibra.ui.components.VibraCard
import app.vibra.ui.components.VibraLoadingIndicator
import app.vibra.ui.components.VibraPrimaryButton
import app.vibra.ui.home.coverUrl
import app.vibra.ui.home.subtitleText
import app.vibra.ui.motion.VibraFilledTonalButton as FilledTonalButton
import app.vibra.ui.motion.VibraIconButton as IconButton
import app.vibra.ui.motion.VibraTextButton as TextButton
import app.vibra.ui.motion.combinedClickable
import app.vibra.ui.motion.interactiveSurface
import app.vibra.ui.navigation.rememberVibraItemActions
import app.vibra.ui.theme.VibraDimens
import app.vibra.ui.theme.VibraEmotionalShapes
import app.vibra.ui.theme.VibraShapes
import app.vibra.ui.theme.rememberMoodShape
import kotlinx.coroutines.flow.filterIsInstance
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SearchHistory
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.viewmodels.MoodAndGenresViewModel
import moe.rukamori.archivetune.viewmodels.OnlineSearchSuggestionViewModel

/*
 * Vibra: the search overlay that rises from the dock while its search pill is open.
 *
 *   empty field ...... recent searches as chips, then "Browse by mood" (mood cookies)
 *   typing ........... a short block of completions, the best result as its own card, then
 *                      the same blocks the home uses: song rows in a card (SongsBlock) and
 *                      albums as a mosaic (MosaicBlock); everything else in "More results"
 *   nothing found .... a headline and one line of guidance
 *
 * The dock itself (field, ✕, keyboard) lives in the shell and is untouched: this list only
 * leaves [bottomContentPadding] free for it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    pureBlack: Boolean,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
    // Vibra: the dock's search field floats above this list (and the keyboard), so the
    // shell passes the space it occupies; null keeps the original system-bar padding.
    bottomContentPadding: Dp? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    // Vibra: closing the search must not cancel a playlist radio or queue action in flight.
    val actions = rememberVibraItemActions(navController, scope = viewModel.viewModelScope)
    val moodsViewModel: MoodAndGenresViewModel = hiltViewModel()
    val moods by moodsViewModel.moodAndGenres.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) { onDispose { viewModel.cancel() } }
    val receivedState by viewModel.viewState.collectAsStateWithLifecycle()
    val normalizedQuery = remember(query) { query.trim().replace(Regex("\\s+"), " ") }
    val viewState =
        if (receivedState.query == normalizedQuery) {
            receivedState
        } else {
            receivedState.copy(
                query = normalizedQuery, history = emptyList(), localItems = emptyList(), items = emptyList(), suggestions = emptyList(),
                isLoading = normalizedQuery.length >= 2, hasError = false, suggestionsError = false,
            )
        }

    val lazyListState = rememberLazyListState()

    // Vibra: only a scroll by the user hides the keyboard. The offset also moves when results,
    // suggestions or the loading row arrive while typing, which closed the keyboard mid-word.
    LaunchedEffect(lazyListState) {
        lazyListState.interactionSource.interactions
            .filterIsInstance<DragInteraction.Start>()
            .collect { keyboardController?.hide() }
    }

    LaunchedEffect(query) {
        viewModel.updateQuery(query)
    }

    val results = remember(viewState.items) { SearchResultGroups.of(viewState.items.distinctBy { it::class to it.id }) }
    val currentId = mediaMetadata?.id
    val currentAlbumId = mediaMetadata?.album?.id
    // A tap on the song already playing only pauses/resumes it: the search stays open.
    val openItem: (YTItem) -> Unit = { item ->
        actions.open(item)
        if (!(item is SongItem && item.id == currentId)) onDismiss()
    }
    val openLocal: (LocalItem) -> Unit = { item ->
        actions.open(item)
        if (!(item is Song && item.id == currentId)) onDismiss()
    }
    val ytEntry: (YTItem) -> SongEntry = { item ->
        SongEntry(
            key = "yt_${item::class.simpleName}_${item.id}",
            title = item.title,
            subtitle = item.subtitleText().orEmpty(),
            thumbnailUrl = item.thumbnail,
            duration = (item as? SongItem)?.duration?.let { makeTimeString(it * 1000L) },
            onClick = { openItem(item) },
            onLongClick = { actions.showMenu(item, onDismissed = onDismiss) },
            isActive = item is SongItem && item.id == currentId || item is AlbumItem && item.id == currentAlbumId,
            circular = item is ArtistItem,
        )
    }

    val typing = normalizedQuery.isNotEmpty()
    val hasResults = results.best != null
    // While results are on screen only a few completions stay above them, so the best match
    // is still in reach with the keyboard open.
    val completions =
        (viewState.history.map { Completion(it.query, fromHistory = true) } + viewState.suggestions.map { Completion(it, fromHistory = false) })
            .distinctBy { it.text.lowercase() }
            .let { if (hasResults) it.take(MaxCompletionsWithResults) else it }
    val shownMoods =
        remember(moods) {
            moods.orEmpty().filterNot { VibraEmotionalShapes.isExcludedCategory(it.title) }.take(MaxMoods)
        }
    val onCompletion: (String) -> Unit = { text ->
        viewModel.cancel()
        onSearch(text)
        onDismiss()
    }
    val fillField: (String) -> Unit = { text -> onQueryChange(TextFieldValue(text, TextRange(text.length))) }
    val cardModifier = Modifier.fillMaxWidth().padding(horizontal = VibraDimens.Gutter)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding =
                PaddingValues(
                    top = 4.dp,
                    bottom =
                        bottomContentPadding ?: WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom)
                            .asPaddingValues()
                            .calculateBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(VibraDimens.CardGap),
            modifier =
                Modifier
                    .widthIn(max = SearchContentMaxWidth)
                    .fillMaxSize(),
        ) {
            if (!typing) {
                if (viewState.history.isNotEmpty()) {
                    item(key = "recent_header", contentType = "section_header") {
                        SearchSectionHeader(
                            title = stringResource(R.string.vibra_search_recent),
                            modifier = Modifier.animateItem(),
                        ) {
                            TextButton(onClick = viewModel::clearHistory) {
                                Text(
                                    text = stringResource(R.string.vibra_search_clear_recent),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item(key = "recent_chips", contentType = "recent_chips") {
                        RecentSearches(
                            history = viewState.history.take(MaxRecent),
                            onSearch = onCompletion,
                            onFill = fillField,
                            onDelete = viewModel::deleteHistory,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (shownMoods.isNotEmpty()) {
                    item(key = "moods_header", contentType = "section_header") {
                        SearchSectionHeader(
                            title = stringResource(R.string.vibra_search_browse_moods),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "moods", contentType = "moods") {
                        MoodGrid(
                            moods = shownMoods,
                            onOpen = { mood ->
                                navController.navigate(moe.rukamori.archivetune.ui.screens.youtubeBrowseRoute(mood))
                                onDismiss()
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                return@LazyColumn
            }

            if (viewState.hasError || (viewState.suggestionsError && normalizedQuery.length < 2)) {
                item(key = "search_error") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = VibraDimens.Gutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.vibra_search_online_error),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        app.vibra.ui.components.VibraDialogButton(onClick = viewModel::retry) {
                            Text(stringResource(R.string.vibra_search_retry))
                        }
                    }
                }
            }

            if (completions.isNotEmpty()) {
                item(key = "completions", contentType = "completions") {
                    Completions(
                        completions = completions,
                        typed = normalizedQuery,
                        onSearch = onCompletion,
                        onFill = fillField,
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (viewState.isLoading && !hasResults) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        VibraLoadingIndicator(Modifier.size(28.dp))
                    }
                }
            }

            results.best?.let { best ->
                item(key = "best_header", contentType = "section_header") {
                    SearchSectionHeader(
                        title = stringResource(R.string.vibra_search_best_result),
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "best_${best::class.simpleName}_${best.id}", contentType = "best_result") {
                    BestResult(
                        item = best,
                        onOpen = { openItem(best) },
                        onPlay = {
                            actions.play(best)
                            if (!(best is SongItem && best.id == currentId)) onDismiss()
                        },
                        onMenu = { actions.showMenu(best, onDismissed = onDismiss) },
                        modifier = cardModifier.animateItem(),
                    )
                }
            }

            if (viewState.localItems.isNotEmpty()) {
                item(key = "library", contentType = "songs_block") {
                    SongsBlock(
                        title = stringResource(R.string.vibra_search_library),
                        songs =
                            viewState.localItems.map { local ->
                                SongEntry(
                                    key = "local_${local::class.simpleName}_${local.id}",
                                    title = local.title,
                                    subtitle = local.subtitleText().orEmpty(),
                                    thumbnailUrl = local.coverUrl,
                                    duration = (local as? Song)?.song?.duration?.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
                                    onClick = { openLocal(local) },
                                    onLongClick = { actions.showMenu(local, onDismissed = onDismiss) },
                                    isActive = local is Song && local.id == currentId,
                                    circular = local is Artist,
                                )
                            },
                        size = BlockSize.Large,
                        rows = viewState.localItems.size,
                        modifier = cardModifier.animateItem(),
                    )
                }
            }

            if (results.songs.isNotEmpty()) {
                item(key = "songs", contentType = "songs_block") {
                    SongsBlock(
                        songs = results.songs.map(ytEntry),
                        size = BlockSize.Medium,
                        modifier = cardModifier.animateItem(),
                    )
                }
            }

            if (results.albums.isNotEmpty()) {
                item(key = "albums", contentType = "mosaic_block") {
                    MosaicBlock(
                        title = stringResource(R.string.albums),
                        entries =
                            results.albums.map { album ->
                                ShelfEntry(
                                    key = "yt_${album.id}",
                                    title = album.title,
                                    subtitle = album.subtitleText(),
                                    thumbnailUrl = album.thumbnail,
                                    onClick = { openItem(album) },
                                    onLongClick = { actions.showMenu(album, onDismissed = onDismiss) },
                                )
                            },
                        size = BlockSize.Medium,
                        modifier = cardModifier.animateItem(),
                    )
                }
            }

            if (results.others.isNotEmpty()) {
                item(key = "more_results", contentType = "songs_block") {
                    SongsBlock(
                        title = stringResource(R.string.vibra_more_results),
                        songs = results.others.map(ytEntry),
                        size = BlockSize.Large,
                        rows = results.others.size,
                        modifier = cardModifier.animateItem(),
                    )
                }
            }

            val nothingFound =
                normalizedQuery.length >= 2 && !viewState.isLoading && !viewState.hasError &&
                    !hasResults && viewState.localItems.isEmpty()
            if (nothingFound) {
                item(key = "no_results") { NoResults(modifier = Modifier.animateItem()) }
            }
        }
    }
}

/**
 * The online results split the way the screen shows them: the first match on its own, the
 * next songs and albums as home-style blocks, and everything left over (artists, playlists,
 * podcasts, the songs and albums past each block) in "More results".
 */
private data class SearchResultGroups(
    val best: YTItem?,
    val songs: List<SongItem>,
    val albums: List<AlbumItem>,
    val others: List<YTItem>,
) {
    companion object {
        fun of(items: List<YTItem>): SearchResultGroups {
            val rest = items.drop(1)
            val songs = rest.filterIsInstance<SongItem>().take(SongRows)
            val albums = rest.filterIsInstance<AlbumItem>().take(AlbumCovers)
            val shown = (songs + albums).toSet()
            return SearchResultGroups(
                best = items.firstOrNull(),
                songs = songs,
                albums = albums,
                others = rest.filterNot { it in shown },
            )
        }
    }
}

private data class Completion(
    val text: String,
    val fromHistory: Boolean,
)

@Composable
private fun SearchSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(start = VibraDimens.Gutter + 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** Recent searches as compact chips: tap searches again, long press edits, ✕ forgets it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearches(
    history: List<SearchHistory>,
    onSearch: (String) -> Unit,
    onFill: (String) -> Unit,
    onDelete: (SearchHistory) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = VibraDimens.Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        history.forEach { entry ->
            Row(
                modifier =
                    Modifier
                        .height(RecentChipHeight)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .combinedClickable(onClick = { onSearch(entry.query) }, onLongClick = { onFill(entry.query) })
                            .padding(start = 12.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.history),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = entry.query,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = RecentChipMaxTextWidth),
                    )
                }
                IconButton(onClick = { onDelete(entry) }, modifier = Modifier.size(RecentChipHeight)) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.remove_from_history),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Moods and genres from YouTube Music, two per row, each with its own cookie. */
@Composable
private fun MoodGrid(
    moods: List<MoodAndGenres.Item>,
    onOpen: (MoodAndGenres.Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = VibraDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(VibraDimens.CardGap),
    ) {
        moods.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(VibraDimens.CardGap)) {
                pair.forEachIndexed { column, mood ->
                    MoodTile(
                        mood = mood,
                        palette = if ((row + column) % 2 == 0) MoodPalette.Cool else MoodPalette.Warm,
                        onClick = { onOpen(mood) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MoodTile(
    mood: MoodAndGenres.Item,
    palette: MoodPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(MoodTileHeight)
                .interactiveSurface(VibraShapes.Row, MaterialTheme.colorScheme.surfaceContainer, onClick = onClick)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(MoodCookieSize)
                .clip(rememberMoodShape(mood.title))
                .background(moodGradient(palette)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = mood.title,
            // Long single words ("Concentrazione") step down a size instead of being cut.
            style =
                if (mood.title.length > MoodTitleLongChars) MaterialTheme.typography.titleSmall
                else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Completions of what is being typed, as one segmented group of short rows: the typed part
 * stays quiet and the rest of each completion is bold. The arrow copies it into the field.
 */
@Composable
private fun Completions(
    completions: List<Completion>,
    typed: String,
    onSearch: (String) -> Unit,
    onFill: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = VibraDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(CompletionSpacing),
    ) {
        completions.forEachIndexed { index, completion ->
            val shape = segmentedShape(index, completions.size)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = CompletionRowHeight)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = CompletionRowHeight)
                            .interactiveSurface(RectangleShape, onClick = { onSearch(completion.text) })
                            .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(if (completion.fromHistory) R.drawable.history else R.drawable.search),
                        contentDescription = null,
                        tint = quiet,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text =
                            buildAnnotatedString {
                                val prefix = completion.text.startsWith(typed, ignoreCase = true)
                                if (prefix) {
                                    withStyle(SpanStyle(color = quiet)) { append(completion.text.take(typed.length)) }
                                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(completion.text.drop(typed.length)) }
                                } else {
                                    append(completion.text)
                                }
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onFill(completion.text) }) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_top_left),
                        contentDescription = stringResource(R.string.vibra_search_complete, completion.text),
                        tint = quiet,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun segmentedShape(
    index: Int,
    count: Int,
): Shape {
    val outer = SearchGroupOuterCorner
    val inner = SearchGroupInnerCorner
    return when {
        count <= 1 -> adaptiveRoundedCornerShape(outer)
        index == 0 -> adaptiveRoundedCornerShape(topStart = outer, topEnd = outer, bottomEnd = inner, bottomStart = inner)
        index == count - 1 -> adaptiveRoundedCornerShape(topStart = inner, topEnd = inner, bottomEnd = outer, bottomStart = outer)
        else -> adaptiveRoundedCornerShape(inner)
    }
}

/** The first match on its own card: what it is, and the one or two things to do with it. */
@Composable
private fun BestResult(
    item: YTItem,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind =
        stringResource(
            when (item) {
                is ArtistItem -> R.string.vibra_search_kind_artist
                is SongItem -> R.string.vibra_search_kind_song
                is AlbumItem -> R.string.vibra_search_kind_album
                is PlaylistItem -> R.string.vibra_search_kind_playlist
                is PodcastItem -> R.string.vibra_search_kind_podcast
                is EpisodeItem -> R.string.vibra_search_kind_episode
            },
        )
    val openLabel =
        when (item) {
            is ArtistItem -> R.string.vibra_search_open_artist
            is AlbumItem -> R.string.vibra_search_open_album
            is PlaylistItem -> R.string.vibra_search_open_playlist
            is PodcastItem -> R.string.vibra_search_open_podcast
            is SongItem, is EpisodeItem -> null
        }
    val playable =
        when (item) {
            is SongItem, is AlbumItem, is EpisodeItem -> true
            is ArtistItem -> item.radioEndpoint != null
            is PlaylistItem -> item.playEndpoint != null
            is PodcastItem -> false
        }
    VibraCard(modifier = modifier, onClick = onOpen, onLongClick = onMenu) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                VibraArtwork(
                    url = item.thumbnail,
                    shape = if (item is ArtistItem) CircleShape else VibraShapes.RowThumbnail,
                    contentDescription = null,
                    sizePx = 240,
                    modifier = Modifier.size(BestArtworkSize),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(kind, item.subtitleText()?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (playable || openLabel != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playable) {
                        VibraPrimaryButton(
                            text = stringResource(R.string.play),
                            icon = R.drawable.play,
                            onClick = onPlay,
                            height = BestActionHeight,
                        )
                    }
                    if (openLabel != null) {
                        // Same label size as the primary pill beside it, so the pair reads as one row.
                        FilledTonalButton(
                            onClick = onOpen,
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            modifier = Modifier.heightIn(min = BestActionHeight),
                        ) {
                            Text(
                                text = stringResource(openLabel),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoResults(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(VibraShapes.Cookie9).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = stringResource(R.string.vibra_search_no_results),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.vibra_search_no_results_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val SearchContentMaxWidth = 720.dp
private val SearchGroupOuterCorner = 24.dp
private val SearchGroupInnerCorner = 6.dp
private val CompletionRowHeight = 48.dp
private val CompletionSpacing = 2.dp
private val RecentChipHeight = 40.dp
private val RecentChipMaxTextWidth = 220.dp
private val MoodTileHeight = 64.dp
private val MoodCookieSize = 40.dp
private const val MoodTitleLongChars = 11
private val BestArtworkSize = 72.dp
private val BestActionHeight = 48.dp

/** How many completions stay above the results while typing, and how many moods to show. */
private const val MaxCompletionsWithResults = 3
private const val MaxMoods = 8
private const val MaxRecent = 8

/** Songs in the song block and covers in the album mosaic (its Medium layout). */
private const val SongRows = 4
private const val AlbumCovers = 3
