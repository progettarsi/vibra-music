/* Vibra (2026) — GPL-3.0. */
package app.vibra.performance

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SharedResultCacheTest {
    @Test
    fun concurrentReadersShareOneComputationAndCancellingOneKeepsTheOther() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val cache = SharedResultCache<String, String>(64, 2, owner)
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val first =
                async(start = CoroutineStart.UNDISPATCHED) {
                    cache.get("cover") {
                        calls++
                        release.await()
                        "red"
                    }
                }
            val second =
                async(start = CoroutineStart.UNDISPATCHED) {
                    cache.get("cover") { error("Duplicate extraction") }
                }
            first.cancelAndJoin()
            release.complete(Unit)
            assertEquals("red", second.await())
            assertEquals("red", cache.get("cover") { error("Cache missed") })
            assertEquals(1, calls)
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun lastReaderCancellationStopsWorkAndTheNextRequestCanRetry() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val cache = SharedResultCache<String, String>(64, 2, owner)
            val cancelled = CompletableDeferred<Unit>()
            val first =
                async(start = CoroutineStart.UNDISPATCHED) {
                    cache.get("cover") {
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled.complete(Unit)
                        }
                    }
                }
            first.cancelAndJoin()
            withTimeout(1000) { cancelled.await() }
            assertEquals("retry", cache.get("cover") { "retry" })
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun onlyTwoExtractionsRunEvenForDifferentCovers() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val cache = SharedResultCache<Int, Int>(64, 2, owner)
            val release = CompletableDeferred<Unit>()
            val active = AtomicInteger()
            val maximum = AtomicInteger()
            val jobs =
                (1..20).map { key ->
                    async(start = CoroutineStart.UNDISPATCHED) {
                        cache.get(key) {
                            val count = active.incrementAndGet()
                            maximum.updateAndGet { maxOf(it, count) }
                            release.await()
                            active.decrementAndGet()
                            key
                        }
                    }
                }
            assertEquals(2, active.get())
            release.complete(Unit)
            assertEquals((1..20).toList(), jobs.awaitAll())
            assertEquals(2, maximum.get())
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun leastRecentlyUsedResultsAreEvictedAndFailuresAreNotCached() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val cache = SharedResultCache<Int, Int>(2, 2, owner)
            cache.get(1) { 1 }
            cache.get(2) { 2 }
            cache.get(1) { error("Cached result missing") }
            cache.get(3) { 3 }
            var recomputed = false
            assertEquals(
                2,
                cache.get(2) {
                    recomputed = true
                    2
                },
            )
            assertTrue(recomputed)
            assertNull(cache.get(9) { null })
            assertEquals(9, cache.get(9) { 9 })
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun trimmingDoesNotRepopulateWithAnOlderInFlightResult() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val cache = SharedResultCache<String, String>(64, 2, owner)
            val release = CompletableDeferred<Unit>()
            val old =
                async(start = CoroutineStart.UNDISPATCHED) {
                    cache.get("cover") {
                        release.await()
                        "old"
                    }
                }
            cache.clear()
            release.complete(Unit)
            assertEquals("old", old.await())
            assertEquals("fresh", cache.get("cover") { "fresh" })
        } finally {
            owner.cancel()
        }
    }
}
