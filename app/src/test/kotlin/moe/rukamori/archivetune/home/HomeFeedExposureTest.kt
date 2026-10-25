/*
 * Vibra (2026) - GPL-3.0 License.
 * Derived from ArchiveTune c Rukamori (github.com/rukamori).
 */
package moe.rukamori.archivetune.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedExposureTest {
    @Test fun repeatedCandidatesAndOverlappingPagesOnlyEmitOncePerSession() {
        val policy = HomeFeedExposure(100)
        assertEquals(setOf("song:a", "song:b"), policy.accept(listOf("song:a", "song:a", "song:b"), 500))
        assertEquals(setOf("song:c"), policy.accept(listOf("song:a", "song:c"), 501))
        assertTrue(policy.accept(listOf("song:a"), 1_000).isEmpty())
    }

    @Test fun onlyOldEnoughItemsCanReturnInANewSession() {
        val policy = HomeFeedExposure(100)
        policy.restore(mapOf("old" to 100, "recent" to 101, "future" to 300))
        assertEquals(setOf("old", "new"), policy.accept(listOf("old", "recent", "future", "new"), 200))
    }

    @Test fun initialPageIsReservedEvenWithoutAnExposureAcknowledgement() {
        val policy = HomeFeedExposure(100)
        policy.reserve(listOf("initial"))
        assertTrue(policy.accept(listOf("initial"), 500).isEmpty())
    }

    @Test fun exposureUpdatesTheCooldownInsteadOfTheFetchTime() {
        val first = HomeFeedExposure(100)
        first.accept(listOf("track"), 100)
        first.markShown(listOf("track"), 200)
        val next = HomeFeedExposure(100)
        next.restore(mapOf("track" to 200))
        assertTrue(next.accept(listOf("track"), 299).isEmpty())
        assertEquals(setOf("track"), next.accept(listOf("track"), 300))
    }
    @Test fun stalePersistedSnapshotsCannotShortenTheCooldown() {
        val policy = HomeFeedExposure(100)
        policy.restore(mapOf("track" to 200))
        policy.restore(mapOf("track" to 100))
        assertTrue(policy.accept(listOf("track"), 250).isEmpty())
        assertEquals(setOf("track"), policy.accept(listOf("track"), 300))
    }
}
