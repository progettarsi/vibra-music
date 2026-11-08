/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_PODCAST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage
import androidx.annotation.StringRes
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.ItemsPage
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultArgument
import moe.rukamori.archivetune.ui.screens.search.decodeOnlineSearchQuery
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

private const val SUMMARY_LOAD = "summary"

enum class OnlineSearchSort {
    DEFAULT,
    VIEWS,
}

@HiltViewModel
class OnlineSearchViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        savedStateHandle: SavedStateHandle,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
    ) : ViewModel() {
        val query =
            decodeOnlineSearchQuery(
                savedStateHandle.get<String>(OnlineSearchResultArgument).orEmpty(),
            )
        val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
        var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
        val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

        // Vibra: what failed to load, with the message to show for it. A key absent from
        // here and from the pages above is still loading.
        private val failedLoads = mutableStateMapOf<String, Int>()

        private val allModeFilters =
            listOf(
                FILTER_SONG,
                FILTER_VIDEO,
                FILTER_ALBUM,
                FILTER_ARTIST,
                FILTER_PODCAST,
                FILTER_COMMUNITY_PLAYLIST,
                FILTER_FEATURED_PLAYLIST,
            )
        private var isSummaryLoading = false
        private val loadingFilters = mutableSetOf<String>()

        init {
            viewModelScope.launch {
                filter.collect { selectedFilter -> load(selectedFilter) }
            }
        }

        private suspend fun load(selectedFilter: YouTube.SearchFilter?) {
            if (selectedFilter == null) {
                viewModelScope.launch {
                    loadSummaryIfNeeded()
                }
                allModeFilters.forEach { allModeFilter ->
                    viewModelScope.launch {
                        loadFilterIfNeeded(allModeFilter)
                    }
                }
            } else {
                loadFilterIfNeeded(selectedFilter)
            }
        }

        /** The message for a selected filter that failed to load, or null. */
        @StringRes
        fun failureOf(selectedFilter: YouTube.SearchFilter): Int? = failedLoads[selectedFilter.value]

        /** The message when the "all" view has settled with a failure and nothing to show, or null. */
        @StringRes
        fun allModeFailure(): Int? {
            val summarySettled = summaryPage != null || failedLoads.containsKey(SUMMARY_LOAD)
            val filtersSettled = allModeFilters.all { viewStateMap.containsKey(it.value) || failedLoads.containsKey(it.value) }
            if (!summarySettled || !filtersSettled) return null
            // Filters that all answered empty are a real "no results", whatever the summary did.
            return allModeFilters.firstNotNullOfOrNull { failedLoads[it.value] }
        }

        fun retry() {
            failedLoads.clear()
            viewModelScope.launch { load(filter.value) }
        }

        private fun recordFailure(
            key: String,
            error: Throwable,
        ) {
            if (error is CancellationException) return
            reportException(error)
            failedLoads[key] = if (error is IOException) R.string.error_no_internet else R.string.error_unknown
        }

        private suspend fun loadSummaryIfNeeded() {
            if (summaryPage != null || isSummaryLoading) return

            isSummaryLoading = true
            failedLoads.remove(SUMMARY_LOAD)
            try {
                YouTube
                    .searchSummary(query)
                    .onSuccess {
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()
                        val contentFilteredPage =
                            it
                                .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                .filterVideo(context.dataStore.get(HideVideoKey, false))
                        summaryPage =
                            contentFilteredPage.copy(
                                summaries =
                                    contentFilteredPage.summaries.mapNotNull { summary ->
                                        summary
                                            .copy(items = filterAiContent(summary.items, aiContentFilterPolicy))
                                            .takeIf { filteredSummary -> filteredSummary.items.isNotEmpty() }
                                    },
                            )
                    }.onFailure {
                        recordFailure(SUMMARY_LOAD, it)
                    }
            } finally {
                isSummaryLoading = false
            }
        }

        private suspend fun loadFilterIfNeeded(filter: YouTube.SearchFilter) {
            val filterKey = filter.value
            if (viewStateMap.containsKey(filterKey) || !loadingFilters.add(filterKey)) return

            failedLoads.remove(filterKey)
            try {
                YouTube
                    .search(query, filter)
                    .onSuccess { result ->
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()
                        viewStateMap[filterKey] =
                            ItemsPage(
                                filterAiContent(
                                    result.items
                                        .distinctBy { it.id }
                                        .filterExplicit(
                                            context.dataStore.get(
                                                HideExplicitKey,
                                                false,
                                            ),
                                        ).filterVideo(context.dataStore.get(HideVideoKey, false)),
                                    aiContentFilterPolicy,
                                ),
                                result.continuation,
                            )
                    }.onFailure {
                        recordFailure(filterKey, it)
                    }
            } finally {
                loadingFilters.remove(filterKey)
            }
        }

        fun loadMore() {
            val filter = filter.value?.value
            viewModelScope.launch {
                if (filter == null) return@launch
                val viewState = viewStateMap[filter] ?: return@launch
                val continuation = viewState.continuation
                if (continuation != null) {
                    val searchResult =
                        YouTube.searchContinuation(continuation).getOrNull() ?: return@launch
                    val aiContentFilterPolicy = loadAiContentFilterPolicy()
                    val continuedItems =
                        filterAiContent(
                            searchResult.items
                                .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                .filterVideo(context.dataStore.get(HideVideoKey, false)),
                            aiContentFilterPolicy,
                        )
                    viewStateMap[filter] =
                        ItemsPage(
                            (viewState.items + continuedItems).distinctBy { it.id },
                            searchResult.continuation,
                        )
                }
            }
        }

        fun sortedItems(
            items: List<YTItem>,
            sort: OnlineSearchSort = OnlineSearchSort.DEFAULT,
        ): List<YTItem> =
            when (sort) {
                OnlineSearchSort.DEFAULT -> {
                    items
                }

                OnlineSearchSort.VIEWS -> {
                    items
                        .withIndex()
                        .sortedWith(
                            compareByDescending<IndexedValue<YTItem>> {
                                when (val item = it.value) {
                                    is SongItem -> item.viewCount
                                    is PlaylistItem -> item.viewCount
                                    else -> null
                                } ?: Long.MIN_VALUE
                            }.thenBy { it.index },
                        ).map { it.value }
                }
            }
    }
