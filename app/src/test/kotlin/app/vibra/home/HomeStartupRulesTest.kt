/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HomeStartupRulesTest {
    private data class Picks(val id: String)

    @Test fun cachedValueIsNotFreshButAnyReplacementIs() {
        val placeholder = CachedPlaceholder<Picks>()
        val cached = placeholder.mark(Picks("a"))
        assertNull(placeholder.fresh(cached))
        assertNull(placeholder.fresh(null))
        // Equal content from the network is still fresh data.
        val fromNetwork = Picks("a")
        assertSame(fromNetwork, placeholder.fresh(fromNetwork))
    }

    @Test fun nothingIsCachedUntilMarked() {
        val value = Picks("a")
        assertSame(value, CachedPlaceholder<Picks>().fresh(value))
    }

    @Test fun onlyARealPageStopsTheReloadWhenOnline() {
        assertTrue(needsFreshFeed(hasPage = false, showingCache = false))
        assertTrue(needsFreshFeed(hasPage = true, showingCache = true))
        assertFalse(needsFreshFeed(hasPage = true, showingCache = false))
    }

    @Test fun failedOptionalStepIsReportedAndWorkContinues() = runBlocking {
        val failures = mutableListOf<Exception>()
        var nextStepRan = false
        optionalStep(failures::add) { throw IllegalStateException("broken cache") }
        nextStepRan = true
        assertEquals(1, failures.size)
        assertTrue(nextStepRan)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNeverSwallowed() = runBlocking {
        optionalStep({ fail("cancellation is not a failure") }) { throw CancellationException("gone") }
    }
}
