/*
 * Vibra (2026) — cancellable live search with independent local and remote channels.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.search

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Every published value is tagged with the query that produced it. */
data class LiveSearchState<H, L, I>(
    val query: String = "",
    val history: List<H> = emptyList(),
    val localItems: List<L> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<I> = emptyList(),
    val isLoading: Boolean = false,
    val suggestionsLoading: Boolean = false,
    val hasError: Boolean = false,
    val suggestionsError: Boolean = false,
)

/** Pure orchestration; sources are injected so races and delays can be tested without Android. */
class LiveSearchController<H, L, I>(
    private val scope: CoroutineScope,
    private val history: (String) -> Flow<List<H>>,
    private val localItems: (String) -> Flow<List<L>>,
    private val suggestions: suspend (String) -> List<String>,
    private val results: suspend (String) -> List<I>,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutableState = MutableStateFlow(LiveSearchState<H, L, I>())
    val state: StateFlow<LiveSearchState<H, L, I>> = mutableState.asStateFlow()
    private var job: Job? = null
    private var revision = 0L
    private var currentQuery: String? = null
    private data class Cached<I>(val at: Long, val items: List<I>)
    private val cache = object : LinkedHashMap<String, Cached<I>>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached<I>>) = size > 20
    }

    fun updateQuery(raw: String, immediate: Boolean = false, force: Boolean = false) {
        val query = raw.trim().replace(Regex("\\s+"), " ")
        if (!force && currentQuery == query) return
        currentQuery = query
        val request = ++revision
        job?.cancel()
        val cached = cache[query]?.takeIf { nowMillis() - it.at < 60_000 }
        mutableState.value = LiveSearchState(query = query, items = cached?.items.orEmpty(),
            isLoading = query.length >= 2 && cached == null, suggestionsLoading = query.isNotEmpty())
        fun publish(transform: (LiveSearchState<H, L, I>) -> LiveSearchState<H, L, I>) {
            mutableState.update { if (request == revision) transform(it) else it }
        }
        job = scope.launch {
            supervisorScope {
                launch { history(query).collect { values -> publish { it.copy(history = values) } } }
                launch { localItems(query).collect { values -> publish { it.copy(localItems = values) } } }
                if (query.isNotEmpty()) launch {
                    try {
                        if (!immediate) pause(150)
                        val values = suggestions(query)
                        ensureActive()
                        publish { it.copy(suggestions = values, suggestionsLoading = false) }
                    } catch (c: CancellationException) { throw c }
                    catch (_: Exception) { publish { it.copy(suggestionsLoading = false, suggestionsError = true) } }
                }
                if (query.length >= 2 && cached == null) launch {
                    try {
                        if (!immediate) pause(250)
                        val values = results(query)
                        ensureActive()
                        if (request == revision) cache[query] = Cached(nowMillis(), values)
                        publish { it.copy(items = values, isLoading = false) }
                    } catch (c: CancellationException) { throw c }
                    catch (_: Exception) { publish { it.copy(isLoading = false, hasError = true) } }
                }
            }
        }
    }

    fun retry() {
        currentQuery?.let { cache.remove(it); updateQuery(it, immediate = true, force = true) }
    }

    fun invalidate() {
        cache.clear()
        currentQuery?.let { updateQuery(it, force = true) }
    }

    /** Submission and overlay dismissal cancel work without clearing the editable field. */
    fun cancel() { ++revision; job?.cancel(); job = null; currentQuery = null }
}
