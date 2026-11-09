/*
 * Vibra (2026) — rejected token, network cache and concurrent reset regressions.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package moe.rukamori.archivetune.playback.stream

import app.vibra.playback.PlaybackStage
import kotlinx.coroutines.*
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiNetworkConfiguration
import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class EngineStreamSyncTest {
    private fun stream(url: String, account: String = "account", binding: GvsPoTokenBinding = GvsPoTokenBinding.VIDEO) =
        ResolvedAudioStream(url, emptyMap(), 140, "audio/mp4", "mp4a", 128000, 44100, 1000,
            Long.MAX_VALUE, account, StreamSource.YOUTUBEI, gvsPoTokenBinding = binding)

    @Test fun rejectionAlternatesBindingPerAccountAndDuplicateFailuresDoNotToggleBack() {
        val preferences = GvsPoTokenPreferences()
        assertEquals(GvsPoTokenBinding.VIDEO, preferences.preferred("a"))
        preferences.reject("a", GvsPoTokenBinding.VIDEO)
        preferences.reject("a", GvsPoTokenBinding.VIDEO)
        assertEquals(GvsPoTokenBinding.SESSION, preferences.preferred("a"))
        assertEquals(GvsPoTokenBinding.VIDEO, preferences.preferred("b"))
        preferences.reject("a", GvsPoTokenBinding.SESSION)
        assertEquals(GvsPoTokenBinding.VIDEO, preferences.preferred("a"))
    }

    @Test fun rejectedUrlEvictsOnlySameAccountAndBinding() {
        val url = "https://r.googlevideo.com/videoplayback?expire=4102444800"
        val rejected = stream(url)
        val cache = mutableMapOf(1 to rejected, 2 to rejected.copy(url = "$url&other=1"),
            3 to stream(url, "other"), 4 to stream("$url&session=1", binding = GvsPoTokenBinding.SESSION))
        val reports = mutableListOf<Pair<String, GvsPoTokenBinding>>()
        assertTrue(reportRejectedCachedStream(url, cache) { a, b -> reports += a to b })
        assertEquals(listOf("account" to GvsPoTokenBinding.VIDEO), reports)
        assertEquals(setOf(3, 4), cache.keys)
    }

    @Test fun expiredAndNearExpiredUrlsAndNonNativeSourcesAreIgnored() {
        val urls = listOf("https://r.googlevideo.com/v?expire=1",
            "https://r.googlevideo.com/v?expire=${System.currentTimeMillis() / 1000 + 30}")
        for (url in urls) {
            val cache = mutableMapOf(1 to stream(url))
            assertFalse(reportRejectedCachedStream(url, cache) { _, _ -> fail("Expired token reported") })
            assertEquals(1, cache.size)
        }
        val public = stream("https://public.example/audio").copy(source = StreamSource.PIPED)
        assertFalse(reportRejectedCachedStream(public.url, mutableMapOf(1 to public)) { _, _ -> fail() })
        assertFalse(reportRejectedCachedStream("unknown", mutableMapOf(1 to public)) { _, _ -> fail() })
    }

    @Test fun cacheKeyChangesWhenNetworkChanges() {
        val network = YoutubeiNetworkConfiguration(null, null, null, Dns.SYSTEM, networkIdentity = "wifi")
        val key = ResolveAudioStreamUseCase.CacheKey("song", "AUTO", false, StreamPurpose.PLAYBACK,
            "a", null, false, false, 0L, PlaybackStage.DIRECT, false, network)
        assertEquals(key, key.copy(network = network.copy()))
        assertNotEquals(key, key.copy(network = network.copy(networkIdentity = "mobile")))
    }

    @Test fun pendingResetRunsOnceAcrossParallelResolutions() = runBlocking {
        val reset = PlaybackSessionReset()
        val count = AtomicInteger()
        reset.requestReset()
        val results = (1..32).map { async(Dispatchers.Default) {
            reset.resetIfRequired { count.incrementAndGet(); delay(15) }
        } }.awaitAll()
        assertEquals(1, count.get())
        assertEquals(1, results.count { it })
        reset.requestReset()
        assertTrue(reset.resetIfRequired { count.incrementAndGet() })
        assertEquals(2, count.get())
    }

    @Test fun failedResetRemainsPending() = runBlocking {
        val reset = PlaybackSessionReset()
        reset.requestReset()
        try { reset.resetIfRequired { throw IOException() }; fail() } catch (_: IOException) { }
        assertTrue(reset.resetIfRequired { })
        assertFalse(reset.resetIfRequired { fail() })
    }

    @Test fun resetRequestedDuringResetIsNotLost() = runBlocking {
        val reset = PlaybackSessionReset()
        reset.requestReset()
        assertTrue(reset.resetIfRequired { reset.requestReset() })
        assertTrue(reset.resetIfRequired { })
        assertFalse(reset.resetIfRequired { fail() })
    }
}
