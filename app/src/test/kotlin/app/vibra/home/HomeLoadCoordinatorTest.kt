/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.home

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class HomeLoadCoordinatorTest {
    @Test fun overlappingRefreshesShareOneRequestAndLaterRefreshRunsAgain() = runBlocking {
        val loader = HomeLoadCoordinator(this)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val a = async { loader.run("A") { calls++; started.complete(Unit); release.await() } }
        started.await()
        val b = async(start = CoroutineStart.UNDISPATCHED) { loader.run("A") { calls++ } }
        release.complete(Unit)
        awaitAll(a, b)
        assertEquals(1, calls)
        loader.run("A") { calls++ }
        assertEquals(2, calls)
    }

    @Test fun switchingContextCancelsPreviousRequestBeforeItCanPublish() = runBlocking {
        val loader = HomeLoadCoordinator(this)
        val started = CompletableDeferred<Unit>()
        var oldPublished = false
        var newPublished = false
        val old = launch { loader.run("A") { started.complete(Unit); awaitCancellation(); oldPublished = true } }
        started.await()
        loader.run("B") { newPublished = true }
        old.join()
        assertFalse(oldPublished)
        assertTrue(newPublished)
    }

    @Test fun oneCancelledWaiterDoesNotCancelSharedWork() = runBlocking {
        val loader = HomeLoadCoordinator(this)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val a = launch { loader.run("A") { calls++; started.complete(Unit); release.await() } }
        started.await()
        val b = async(start = CoroutineStart.UNDISPATCHED) { loader.run("A") { calls++ } }
        a.cancelAndJoin()
        release.complete(Unit)
        b.await()
        assertEquals(1, calls)
    }
}
