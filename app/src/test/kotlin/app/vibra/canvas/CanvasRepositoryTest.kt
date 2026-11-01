/*
 * Vibra (2026) — Canvas repository: ordering, fallback, caching, deduplication, cancellation.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.canvas

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasRepositoryTest {
    private class Fake(
        override val source: CanvasSource,
        private val answer: suspend (CanvasQuery) -> CanvasCandidate?,
    ) : CanvasProvider {
        val calls = AtomicInteger()

        override suspend fun find(query: CanvasQuery): CanvasCandidate? {
            calls.incrementAndGet()
            return answer(query)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var now = 1_000_000L

    @After fun tearDown() = scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()

    private fun query(id: String = "a", title: String = "Song", album: String? = null) =
        CanvasQuery(id, title, listOf("Artist"), album)

    private fun clip(url: String = "https://cdn.example.test/a.mp4", title: String = "Song", album: String? = null) =
        CanvasCandidate(url, null, title, "Artist", album)

    private fun fake(source: CanvasSource = CanvasSource.COMMUNITY, answer: suspend (CanvasQuery) -> CanvasCandidate?) =
        Fake(source, answer)

    private fun repo(
        vararg providers: CanvasProvider,
        providerTimeoutMs: Long = 2_000,
        totalTimeoutMs: Long = 10_000,
        maxConcurrentLookups: Int = 2,
        maxEntries: Int = 96,
    ) = CanvasRepository(
        providers.toList(), scope, { now }, providerTimeoutMs, totalTimeoutMs, maxConcurrentLookups, maxEntries,
    )

    @Test fun theFirstValidSourceInPriorityOrderWins() = runBlocking {
        val apple = fake(CanvasSource.APPLE) { clip("https://cdn.example.test/apple.m3u8") }
        val community = fake { clip("https://cdn.example.test/community.mp4") }
        val result = repo(apple, community).canvasFor(query())
        assertEquals(CanvasSource.APPLE, result?.source)
        assertEquals("https://cdn.example.test/apple.m3u8", result?.url)
        assertEquals("a", result?.mediaId)
        assertEquals(0, community.calls.get())
    }

    @Test fun fallsBackToTheNextSourceOnAMiss() = runBlocking {
        val apple = fake(CanvasSource.APPLE) { null }
        val tidal = fake(CanvasSource.TIDAL) { clip("https://cdn.example.test/tidal.mp4") }
        val result = repo(apple, tidal).canvasFor(query())
        assertEquals(CanvasSource.TIDAL, result?.source)
        assertEquals(1, apple.calls.get())
    }

    @Test fun aWrongAnswerIsSkippedNotTrusted() = runBlocking {
        val sloppy = fake(CanvasSource.APPLE) { clip(title = "Another Song") }
        val right = fake { clip("https://cdn.example.test/right.mp4") }
        assertEquals(CanvasSource.COMMUNITY, repo(sloppy, right).canvasFor(query())?.source)
    }

    @Test fun unsafeUrlsAreNeverOffered() = runBlocking {
        assertNull(repo(fake { clip("http://cdn.example.test/a.mp4") }).canvasFor(query("1")))
        assertNull(repo(fake { clip("https://localhost/a.mp4") }).canvasFor(query("2")))
        val result = repo(fake { clip().copy(fallbackUrl = "https://10.0.0.5/b.mp4") }).canvasFor(query("3"))
        assertNotNull(result)
        assertNull(result?.fallbackUrl)
    }

    @Test fun theVerticalRenditionIsKeptOnlyIfItIsSafe() = runBlocking {
        val good = repo(fake { clip().copy(portraitUrl = "https://cdn.example.test/tall.m3u8") }).canvasFor(query("1"))
        assertEquals("https://cdn.example.test/tall.m3u8", good?.portraitUrl)
        val bad = repo(fake { clip().copy(portraitUrl = "http://cdn.example.test/tall.m3u8") }).canvasFor(query("2"))
        assertNull(bad?.portraitUrl)
        assertNotNull(bad)
    }

    @Test fun anErroringSourceDoesNotStopTheOthers() = runBlocking {
        val broken = fake(CanvasSource.APPLE) { throw IOException("boom") }
        val crashing = fake(CanvasSource.TIDAL) { throw IllegalStateException("bug") }
        val working = fake { clip() }
        assertNotNull(repo(broken, crashing, working).canvasFor(query()))
    }

    @Test fun aSlowSourceTimesOutAndTheNextIsAsked() = runBlocking {
        val hanging = fake(CanvasSource.APPLE) { delay(60_000); clip() }
        val working = fake { clip() }
        val result = withTimeout(5_000) { repo(hanging, working, providerTimeoutMs = 50).canvasFor(query()) }
        assertEquals(CanvasSource.COMMUNITY, result?.source)
    }

    @Test fun theWholeSearchHasAnUpperBound() = runBlocking {
        val hanging = fake { delay(60_000); clip() }
        val second = fake(CanvasSource.TIDAL) { clip() }
        val result = withTimeout(5_000) {
            repo(hanging, second, providerTimeoutMs = 400, totalTimeoutMs = 100).canvasFor(query())
        }
        assertNull(result)
        assertEquals(0, second.calls.get())
    }

    @Test fun networkFailuresAreRememberedOnlyBriefly() = runBlocking {
        val broken = fake { throw IOException("offline") }
        val repository = repo(broken)
        assertNull(repository.canvasFor(query()))
        assertNull(repository.canvasFor(query()))
        assertEquals(1, broken.calls.get())
        now += 3 * 60 * 1000
        assertNull(repository.canvasFor(query()))
        assertEquals(2, broken.calls.get())
    }

    @Test fun hitsAreCachedAndReadableWithoutTheNetwork() = runBlocking {
        val provider = fake { clip() }
        val repository = repo(provider)
        assertNull(repository.cached(query()))
        val first = repository.canvasFor(query())
        assertEquals(first, repository.cached(query())?.artwork)
        assertEquals(first, repository.canvasFor(query()))
        assertEquals(1, provider.calls.get())
        now += 5 * 60 * 60 * 1000L
        assertNotNull(repository.cached(query())?.artwork)
        now += 2 * 60 * 60 * 1000L
        assertNull(repository.cached(query()))
        repository.canvasFor(query())
        assertEquals(2, provider.calls.get())
    }

    @Test fun missesAreCachedForTwentyMinutes() = runBlocking {
        val provider = fake { null }
        val repository = repo(provider)
        assertNull(repository.canvasFor(query()))
        assertNull(repository.cached(query())?.artwork)
        assertNotNull(repository.cached(query()))
        repository.canvasFor(query())
        assertEquals(1, provider.calls.get())
        now += 19 * 60 * 1000
        repository.canvasFor(query())
        assertEquals(1, provider.calls.get())
        now += 2 * 60 * 1000
        repository.canvasFor(query())
        assertEquals(2, provider.calls.get())
    }

    @Test fun aMissWithoutTheAlbumIsProvisional() = runBlocking {
        val provider = fake { q -> if (q.album == null) null else clip(album = q.album) }
        val repository = repo(provider)
        assertNull(repository.canvasFor(query()))
        // The album arrives a moment later: that can change the answer, so ask again.
        assertNull(repository.cached(query(album = "Album")))
        assertNotNull(repository.canvasFor(query(album = "Album")))
        assertEquals(2, provider.calls.get())
        // Without the album the earlier answer stands.
        assertNotNull(repository.canvasFor(query()))
        assertEquals(2, provider.calls.get())
    }

    @Test fun aMissReachedWithTheAlbumIsFinal() = runBlocking {
        val provider = fake { null }
        val repository = repo(provider)
        repository.canvasFor(query(album = "Album"))
        repository.canvasFor(query(album = "Album"))
        repository.canvasFor(query())
        assertEquals(1, provider.calls.get())
    }

    @Test fun aLateAlbumThatContradictsAHitDropsIt() = runBlocking {
        val provider = fake { clip(album = "Other Album") }
        val repository = repo(provider)
        assertNotNull(repository.canvasFor(query()))
        assertNull(repository.cached(query(album = "Real Album")))
        assertNull(repository.canvasFor(query(album = "Real Album")))
        assertEquals(2, provider.calls.get())
    }

    @Test fun concurrentLookupsOfOneTrackAreOneLookup() = runBlocking {
        val provider = fake { delay(150); clip() }
        val repository = repo(provider)
        val results = (1..6).map { async(Dispatchers.Default) { repository.canvasFor(query()) } }.awaitAll()
        assertTrue(results.all { it != null && it == results.first() })
        assertEquals(1, provider.calls.get())
    }

    @Test fun anObsoleteLookupIsCancelledWhenNobodyWaitsForIt() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val provider = fake {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        val repository = repo(provider)
        val job = launch(Dispatchers.Default) { repository.canvasFor(query("old")) }
        withTimeout(2_000) { started.await() }
        job.cancelAndJoin()
        withTimeout(2_000) { cancelled.await() }
    }

    @Test fun aSharedLookupSurvivesOneOfItsWaitersLeaving() = runBlocking {
        val provider = fake { delay(200); clip() }
        val repository = repo(provider)
        val leaving = launch(Dispatchers.Default) { repository.canvasFor(query()) }
        val staying = async(Dispatchers.Default) { repository.canvasFor(query()) }
        delay(50)
        leaving.cancelAndJoin()
        assertNotNull(withTimeout(2_000) { staying.await() })
        assertEquals(1, provider.calls.get())
    }

    @Test fun aSlowOldTrackDoesNotHoldBackTheCurrentOne() = runBlocking {
        val provider = fake { q -> if (q.mediaId == "old") awaitCancellation() else clip() }
        val repository = repo(provider)
        val old = launch(Dispatchers.Default) { repository.canvasFor(query("old")) }
        delay(50)
        val current = withTimeout(2_000) { repository.canvasFor(query("new")) }
        assertEquals("new", current?.mediaId)
        old.cancelAndJoin()
    }

    @Test fun twoTracksOfOneAlbumKeepTheirOwnClips() = runBlocking {
        val provider = fake { q -> clip("https://cdn.example.test/${q.mediaId}.mp4", title = q.title) }
        val repository = repo(provider)
        val a = repository.canvasFor(query("a", "Song"))
        val b = repository.canvasFor(query("b", "Song 2"))
        assertEquals("a", a?.mediaId)
        assertEquals("b", b?.mediaId)
        assertEquals(2, provider.calls.get())
    }

    @Test fun theCacheIsBounded() = runBlocking {
        val provider = fake { q -> clip(title = q.title) }
        val repository = repo(provider, maxEntries = 2)
        repository.canvasFor(query("1", "One"))
        repository.canvasFor(query("2", "Two"))
        repository.canvasFor(query("3", "Three"))
        assertNull(repository.cached(query("1", "One")))
        assertNotNull(repository.cached(query("3", "Three")))
    }

    @Test fun unidentifiableTracksNeverReachTheNetwork() = runBlocking {
        val provider = fake { clip() }
        val repository = repo(provider)
        assertNull(repository.canvasFor(CanvasQuery("x", " ", listOf("Artist"))))
        assertNull(repository.canvasFor(CanvasQuery("x", "Song", emptyList())))
        assertEquals(0, provider.calls.get())
    }
}
