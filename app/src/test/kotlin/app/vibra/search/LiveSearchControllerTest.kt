/*
 * Vibra (2026) — deterministic live-search concurrency tests.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.search

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.resume

class LiveSearchControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var clock = 0L
    private val waits = mutableListOf<Pair<Long, CancellableContinuation<Unit>>>()
    private val history = MutableStateFlow(listOf("history"))
    private val library = MutableStateFlow(listOf("local"))
    private val calls = mutableListOf<String>()
    private var fetch: suspend (String) -> List<String> = { calls += it; listOf("result:$it") }
    private val controller = LiveSearchController(
        scope, { _: String -> history }, { _: String -> library },
        { q: String -> listOf("suggestion:$q") }, { q: String -> fetch(q) },
        nowMillis = { clock },
        pause = { ms -> suspendCancellableCoroutine { waits += (clock + ms) to it } },
    )
    private fun advance(ms: Long) {
        clock += ms
        val due = waits.filter { it.first <= clock }.toList()
        waits.removeAll(due.toSet())
        due.forEach { if (it.second.isActive) it.second.resume(Unit) }
    }
    @After fun close() { controller.cancel(); scope.cancel() }

    @Test fun `local results and history do not wait for either network delay`() {
        controller.updateQuery("hello")
        assertEquals(listOf("local"), controller.state.value.localItems)
        assertEquals(listOf("history"), controller.state.value.history)
        assertTrue(controller.state.value.isLoading)
        assertTrue(calls.isEmpty())
    }
    @Test fun `suggestions at 150ms and real search at 250ms`() {
        controller.updateQuery("hello")
        advance(149)
        assertTrue(controller.state.value.suggestions.isEmpty())
        advance(1)
        assertEquals(listOf("suggestion:hello"), controller.state.value.suggestions)
        assertTrue(calls.isEmpty())
        advance(100)
        assertEquals(listOf("hello"), calls)
        assertEquals(listOf("result:hello"), controller.state.value.items)
        assertFalse(controller.state.value.isLoading)
    }
    @Test fun `rapid edits cancel the previous debounce immediately`() {
        controller.updateQuery("he"); advance(100)
        controller.updateQuery("hello"); advance(150)
        assertTrue(calls.isEmpty())
        advance(100)
        assertEquals(listOf("hello"), calls)
    }
    @Test fun `late non cooperative response never replaces current results`() {
        val old = CompletableDeferred<List<String>>()
        fetch = { q -> if (q == "old") withContext(NonCancellable) { old.await() } else listOf(q) }
        controller.updateQuery("old"); advance(250)
        controller.updateQuery("new"); advance(250)
        old.complete(listOf("obsolete"))
        assertEquals("new", controller.state.value.query)
        assertEquals(listOf("new"), controller.state.value.items)
    }
    @Test fun `clearing cancels requests and restores history`() {
        controller.updateQuery("hello"); advance(100)
        controller.updateQuery("  "); advance(1000)
        assertEquals("", controller.state.value.query)
        assertTrue(controller.state.value.items.isEmpty())
        assertFalse(controller.state.value.isLoading)
        assertEquals(listOf("history"), controller.state.value.history)
        assertTrue(calls.isEmpty())
    }
    @Test fun `one character only fetches suggestions`() {
        controller.updateQuery("a"); advance(1000)
        assertTrue(calls.isEmpty())
        assertEquals(listOf("suggestion:a"), controller.state.value.suggestions)
    }
    @Test fun `network error leaves local results and retry bypasses debounce`() {
        fetch = { throw IllegalStateException("offline") }
        controller.updateQuery("hello"); advance(250)
        assertTrue(controller.state.value.hasError)
        assertEquals(listOf("local"), controller.state.value.localItems)
        fetch = { listOf(it) }; controller.retry()
        assertFalse(controller.state.value.hasError)
        assertEquals(listOf("hello"), controller.state.value.items)
    }
    @Test fun `cache expires after sixty seconds and invalidates with context`() {
        controller.updateQuery("first"); advance(250)
        controller.updateQuery("second"); advance(250)
        controller.updateQuery("first")
        assertEquals(listOf("result:first"), controller.state.value.items)
        assertEquals(1, calls.count { it == "first" })
        controller.invalidate(); advance(250)
        assertEquals(2, calls.count { it == "first" })
        controller.updateQuery("second"); advance(60_001)
        controller.updateQuery("first"); advance(250)
        assertEquals(3, calls.count { it == "first" })
    }
    @Test fun `cache contains at most twenty queries`() {
        repeat(21) { controller.updateQuery("query$it"); advance(250) }
        controller.updateQuery("query0"); advance(250)
        assertEquals(2, calls.count { it == "query0" })
    }
    @Test fun `submission cancels pending work immediately`() {
        controller.updateQuery("hello"); controller.cancel(); advance(1000)
        assertTrue(calls.isEmpty())
        controller.updateQuery("hello"); advance(250)
        assertEquals(1, calls.size)
    }
    @Test fun `text normalization does not repeat a request for whitespace changes`() {
        controller.updateQuery("  hello   world "); advance(250)
        controller.updateQuery("hello world"); advance(250)
        assertEquals(listOf("hello world"), calls)
    }
}
