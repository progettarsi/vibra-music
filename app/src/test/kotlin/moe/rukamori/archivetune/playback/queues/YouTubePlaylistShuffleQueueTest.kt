/*
 * Vibra (2026) — complete playlist shuffle and bounded loading regressions.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package moe.rukamori.archivetune.playback.queues

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class YouTubePlaylistShuffleQueueTest {
    @Test fun allPagesAreCollectedBeforeShufflingWithDuplicateSongsRemoved() = runBlocking {
        val tokens = mutableListOf<String>()
        val result = loadShuffledPlaylist(
            first = { ShufflePlaylistPage("Playlist", listOf("a", "b"), "p2") },
            next = { token ->
                tokens += token
                if (token == "p2") ShufflePlaylistPage(null, listOf("b", "c"), "p3")
                else ShufflePlaylistPage(null, listOf("d", "e"), null)
            }, itemId = { it }, random = Random(17))
        assertEquals(listOf("p2", "p3"), tokens)
        assertEquals("Playlist", result.title)
        assertEquals(listOf("a", "b", "c", "d", "e").shuffled(Random(17)), result.items)
        assertFalse(result.limited)
    }

    @Test fun pageLimitStartsWithCollectedSongs() = runBlocking {
        var requests = 0
        val result = loadShuffledPlaylist(
            first = { ShufflePlaylistPage("Long", listOf("a"), "p2") },
            next = { requests++; ShufflePlaylistPage(null, listOf("b"), "p3") },
            itemId = { it }, maxPages = 2)
        assertEquals(1, requests)
        assertEquals(setOf("a", "b"), result.items.toSet())
        assertTrue(result.limited)
    }

    @Test fun timeLimitKeepsCompletedPages() = runBlocking {
        val result = loadShuffledPlaylist(
            first = { ShufflePlaylistPage("Slow", listOf("a"), "p2") },
            next = { delay(1000); ShufflePlaylistPage(null, listOf("b"), null) },
            itemId = { it }, timeoutMs = 50)
        assertEquals(listOf("a"), result.items)
        assertTrue(result.limited)
    }

    @Test fun repeatedContinuationCannotLoop() = runBlocking {
        var requests = 0
        val result = loadShuffledPlaylist(
            first = { ShufflePlaylistPage("Loop", listOf("a"), "same") },
            next = { requests++; ShufflePlaylistPage(null, listOf("b"), "same") }, itemId = { it })
        assertEquals(1, requests)
        assertEquals(setOf("a", "b"), result.items.toSet())
        assertTrue(result.limited)
    }

    @Test fun parentCancellationIsNotConvertedIntoPartialSuccess() = runBlocking {
        try {
            withTimeout(30) {
                loadShuffledPlaylist(
                    first = { ShufflePlaylistPage("Cancelled", listOf("a"), "next") },
                    next = { delay(1000); ShufflePlaylistPage(null, listOf("b"), null) }, itemId = { it })
            }
            fail("Parent cancellation swallowed")
        } catch (_: TimeoutCancellationException) { }
    }
    @Test fun distinctEntriesForSameVideoArePreservedAndBlankContinuationEndsLoading() = runBlocking {
        data class Entry(val video: String, val entry: String)
        val entries = listOf(Entry("same-video", "entry-1"), Entry("same-video", "entry-2"))
        val result = loadShuffledPlaylist(
            first = { ShufflePlaylistPage("Repeated song", entries, " ") },
            next = { error("Blank continuation requested") }, itemId = { it.entry })
        assertEquals(entries.toSet(), result.items.toSet())
        assertFalse(result.limited)
    }

    @Test fun failedLaterPageStartsWithCollectedSongs() = runBlocking {
        val result = loadShuffledPlaylist(
            first = { ShufflePlaylistPage("Flaky", listOf("a", "b"), "p2") },
            next = { throw java.io.IOException("offline") }, itemId = { it })
        assertEquals(setOf("a", "b"), result.items.toSet())
        assertTrue(result.limited)
    }

    @Test fun failedFirstPageStillFails() {
        assertThrows(java.io.IOException::class.java) {
            runBlocking {
                loadShuffledPlaylist<String>(
                    first = { throw java.io.IOException("offline") },
                    next = { error("unused") }, itemId = { it })
            }
        }
    }
}
