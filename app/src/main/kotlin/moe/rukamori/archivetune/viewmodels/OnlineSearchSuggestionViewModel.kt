/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import app.vibra.search.LiveSearchController
import app.vibra.search.observeLocalSearchPreview
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SearchHistory
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject

@HiltViewModel
class OnlineSearchSuggestionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
    private val filterAiContent: FilterAiContentUseCase,
) : ViewModel() {
    private val search = LiveSearchController<SearchHistory, LocalItem, YTItem>(
        scope = viewModelScope,
        history = { query -> if (query.isEmpty()) database.searchHistory() else database.searchHistory(query).map { it.take(3) } },
        localItems = { observeLocalSearchPreview(database, it) },
        suggestions = { YouTube.searchSuggestions(it).getOrThrow().queries },
        results = { query ->
            val result = YouTube.searchSummary(query).getOrThrow()
            val prefs = context.dataStore.data.first()
            val policy = loadAiContentFilterPolicy()
            filterAiContent(
                result.filterExplicit(prefs[HideExplicitKey] ?: false).filterVideo(prefs[HideVideoKey] ?: false)
                    .summaries.flatMap { it.items }.distinctBy { it::class to it.id }, policy,
            )
        },
    )
    val viewState = search.state

    init {
        // These values remain local; a change invalidates cached results and current requests.
        viewModelScope.launch {
            context.dataStore.data.map { prefs ->
                prefs.asMap().filterKeys { key -> key in setOf(InnerTubeCookieKey, DataSyncIdKey, HideExplicitKey, HideVideoKey) ||
                    key.name.contains("aiContent", ignoreCase = true) || key.name.contains("language", ignoreCase = true) }
            }.distinctUntilChanged().drop(1).collect { search.invalidate() }
        }
    }
    fun updateQuery(query: String) = search.updateQuery(query)
    fun retry() = search.retry()
    fun cancel() = search.cancel()
    fun deleteHistory(history: SearchHistory) { database.query { delete(history) } }
}
