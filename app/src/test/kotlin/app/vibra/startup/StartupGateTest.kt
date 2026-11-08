/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.startup

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StartupGateTest {
    @Test fun optionalWorkWaitsForContentAndRunsSequentially() = runBlocking {
        val gate = StartupGate()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<Int>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) { gate.run { order += 1; release.await(); order += 2 } }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { gate.run { order += 3 } }
        gate.firstFrame()
        yield()
        assertTrue(order.isEmpty())
        gate.homeVisible()
        yield()
        assertEquals(listOf(1), order)
        release.complete(Unit)
        joinAll(first, second)
        assertEquals(listOf(1, 2, 3), order)
    }

    @Test fun missingActivityOrContentCannotStarveServiceWork() = runBlocking {
        var ran = false
        withTimeout(1000) { StartupGate(timeoutMs = 10).run { ran = true } }
        assertTrue(ran)
    }

    @Test fun cancelledMaintenanceDoesNotRunAfterTheGateOpens() = runBlocking {
        val gate = StartupGate()
        var ran = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) { gate.run { ran = true } }
        job.cancelAndJoin()
        gate.firstFrame(); gate.homeVisible()
        yield()
        assertFalse(ran)
    }
}
