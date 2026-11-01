/*
 * Vibra (2026) — Canvas providers against synthetic fixtures (no network, no real catalogue data).
 * The fixtures follow the response shapes the BitChord reference implementation reads; whether the
 * live services still answer that way is NOT covered here (see docs/CANVAS_QA.md).
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.canvas

import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class FakeHttp(
    private val routes: (String) -> CanvasResponse?,
    private val live: Set<String> = emptySet(),
) : CanvasHttp {
    val requests = mutableListOf<String>()
    val headers = mutableListOf<Map<String, String>>()

    override suspend fun get(url: String, headers: Map<String, String>): CanvasResponse {
        requests += url
        this.headers += headers
        return routes(url) ?: CanvasResponse(404, null)
    }

    override suspend fun reachable(url: String) = url in live
}

private fun ok(body: String) = CanvasResponse(200, body)

private val QUERY = CanvasQuery("id", "Anti-Hero", listOf("Taylor Swift"), "Midnights")

class AppleMotionProviderTest {
    private class Tokens(private val value: String? = "token") : AppleTokenSource {
        val rejected = mutableListOf<String>()
        override suspend fun token() = value
        override fun reject(token: String) { rejected += token }
    }

    private fun song(name: String = "Anti-Hero", album: String = "Midnights", inline: String = "") =
        """{"id":"1","attributes":{"name":"$name","artistName":"Taylor Swift","albumName":"$album",
        "url":"https://music.apple.com/us/album/x/1650000000?i=1650000001"$inline},
        "relationships":{"albums":{"data":[{"id":"1650000000"}]}}}"""

    private fun search(vararg songs: String) = """{"results":{"songs":{"data":[${songs.joinToString(",")}]}}}"""

    private val albumBody = """{"data":[{"id":"1650000000","attributes":{"name":"Midnights",
        "artistName":"Taylor Swift","editorialVideo":{
        "motionDetailSquare":{"video":"https://mvod.example.test/square/master.m3u8"},
        "motionDetailTall":{"video":"https://mvod.example.test/tall/master.m3u8"}}}}]}"""

    private fun http(searchBody: String, album: String = albumBody) = FakeHttp({ url ->
        when {
            "/search" in url -> ok(searchBody)
            "/albums/1650000000" in url -> ok(album)
            else -> null
        }
    })

    @Test fun readsTheAlbumsMotionArtworkSquareFirst() = runBlocking {
        val http = http(search(song()))
        val found = AppleMotionProvider(http, Tokens(), "us").find(QUERY)
        assertEquals("https://mvod.example.test/square/master.m3u8", found?.url)
        assertEquals("https://mvod.example.test/tall/master.m3u8", found?.fallbackUrl)
        assertEquals("https://mvod.example.test/tall/master.m3u8", found?.portraitUrl)
        assertEquals("Anti-Hero", found?.title)
        assertEquals("Midnights", found?.album)
        assertTrue(http.headers.all { it["Authorization"] == "Bearer token" })
    }

    @Test fun usesInlineMotionArtworkWithoutTheAlbumRoundTrip() = runBlocking {
        val inline = ""","editorialVideo":{"motionDetailRaw":{"video":"https://mvod.example.test/raw/master.m3u8"}}"""
        val http = http(search(song(inline = inline)))
        val found = AppleMotionProvider(http, Tokens(), "us").find(QUERY)
        assertEquals("https://mvod.example.test/raw/master.m3u8", found?.url)
        assertEquals(1, http.requests.size)
    }

    @Test fun anotherSongOfTheArtistIsNotAMatch() = runBlocking {
        val http = http(search(song(name = "Lavender Haze"), song(name = "Anti-Hero (Remix)")))
        assertNull(AppleMotionProvider(http, Tokens(), "us").find(QUERY))
        assertEquals(1, http.requests.size)
    }

    @Test fun compilationsAndPlaylistsAreSkipped() = runBlocking {
        val http = http(search(song(album = "Today's Hits Playlist")))
        assertNull(AppleMotionProvider(http, Tokens(), "us").find(QUERY.copy(album = null)))
    }

    @Test fun aClipFromAnotherAlbumEditionIsRefused() = runBlocking {
        val deluxe = albumBody.replace("\"name\":\"Midnights\"", "\"name\":\"Midnights (3am Edition)\"")
        assertNull(AppleMotionProvider(http(search(song()), deluxe), Tokens(), "us").find(QUERY))
    }

    @Test fun aRejectedTokenIsReportedAndIsAFailureNotAMiss() = runBlocking {
        val tokens = Tokens()
        val http = FakeHttp({ CanvasResponse(401, null) })
        try {
            AppleMotionProvider(http, tokens, "us").find(QUERY)
            fail("expected a failure")
        } catch (_: IOException) {
        }
        assertEquals(listOf("token"), tokens.rejected)
    }

    @Test fun serverErrorsAreFailuresAndMissingTokensAreMisses() = runBlocking {
        try {
            AppleMotionProvider(FakeHttp({ CanvasResponse(503, null) }), Tokens(), "us").find(QUERY)
            fail("expected a failure")
        } catch (_: IOException) {
        }
        val http = FakeHttp({ ok("{}") })
        assertNull(AppleMotionProvider(http, Tokens(null), "us").find(QUERY))
        assertTrue(http.requests.isEmpty())
    }

    @Test fun garbageIsAMissNotACrash() = runBlocking {
        assertNull(AppleMotionProvider(FakeHttp({ ok("<html>nope</html>") }), Tokens(), "us").find(QUERY))
        assertNull(AppleMotionProvider(FakeHttp({ ok("""{"results":{"songs":{"data":[1,"x",{}]}}}""") }), Tokens(), "us").find(QUERY))
    }

    @Test fun motionVariantsFollowTheSquareFirstOrder() {
        val video = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"motionTallVideo3x4":{"video":"https://a.example.test/t.m3u8"},
               "motionSquareVideo1x1":{"video":"https://a.example.test/s.m3u8"}}""",
        ) as kotlinx.serialization.json.JsonObject
        val motion = AppleMotionProvider.motionOf(video)
        assertEquals("https://a.example.test/s.m3u8", motion?.primary)
        assertEquals("https://a.example.test/t.m3u8", motion?.alternate)
        assertEquals("https://a.example.test/t.m3u8", motion?.portrait)
        assertNull(AppleMotionProvider.motionOf(null))
    }
}

class AppleWebTokenSourceTest {
    private fun jwt(kid: String, exp: Long): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"ES256","kid":"$kid"}""".toByteArray())
        val payload = enc.encodeToString("""{"iss":"AMPWebPlay","exp":$exp}""".toByteArray())
        return "$header.$payload.c2ln"
    }

    @Test fun findsBundlesAndOnlyUnexpiredTokens() {
        val html = """<script src="/assets/index-legacy~abc123.js"></script><script src="/assets/index-xyz.js"></script>"""
        assertEquals(listOf("/assets/index-legacy~abc123.js", "/assets/index-xyz.js"), AppleWebTokenSource.bundlePaths(html))
        val live = jwt("WebPlayKid", 2_000)
        val dead = jwt("WebPlayKid", 500)
        val found = AppleWebTokenSource.tokensIn("var a='$dead';var b='$live';", now = 1_000_000L)
        assertEquals(listOf(live to 2_000_000L), found)
        assertTrue(AppleWebTokenSource.isWebPlayerToken(live))
        assertFalse(AppleWebTokenSource.isWebPlayerToken("ey.ey.x"))
    }

    @Test fun cachesTheTokenRetriesAfterRejectionAndBacksOffOnFailure() = runBlocking {
        var now = 1_000_000L
        val token = jwt("WebPlayKid", 5_000_000L)
        var page = """<script src="/assets/index-aaa.js"></script>"""
        val http = FakeHttp({ url ->
            when {
                url.endsWith("/browse") -> ok(page)
                url.endsWith("index-aaa.js") -> ok("x='$token'")
                else -> null
            }
        })
        val source = AppleWebTokenSource(http) { now }
        assertEquals(token, source.token())
        assertEquals(token, source.token())
        assertEquals(2, http.requests.size)
        source.reject(token)
        // The only token in the bundle was refused: nothing left to offer, and it backs off.
        assertNull(source.token())
        val afterFailure = http.requests.size
        assertNull(source.token())
        assertEquals(afterFailure, http.requests.size)
        now += 31 * 60 * 1000
        page = ""
        assertNull(source.token())
        assertTrue(http.requests.size > afterFailure)
    }
}

class TidalMotionProviderTest {
    private val cover = "11111111-2222-3333-4444-555555555555"

    private fun track(title: String, artist: String, album: String, cover: String?): String {
        val videoCover = if (cover == null) "" else ",\"videoCover\":\"$cover\""
        return "{\"title\":\"$title\",\"artists\":[{\"name\":\"$artist\"}],\"album\":{\"title\":\"$album\"$videoCover}}"
    }

    private fun body(vararg tracks: String) = """{"tracks":{"items":[${tracks.joinToString(",")}]},"albums":{"items":[]}}"""

    @Test fun buildsTheCdnUrlFromAWellFormedCoverId() {
        assertEquals(
            "https://resources.tidal.com/videos/11111111/2222/3333/4444/555555555555/1280x1280.mp4",
            TidalMotionProvider.coverUrl(cover),
        )
        assertNull(TidalMotionProvider.coverUrl("not-a-cover"))
        assertNull(TidalMotionProvider.coverUrl("1111-2222-3333-4444-5555"))
        assertNull(TidalMotionProvider.coverUrl("../../etc/passwd-a-b-c"))
    }

    @Test fun takesTheRightTrackEvenWhenItIsNotTheFirstResult() {
        val parsed = TidalMotionProvider.parse(
            body(
                track("Lavender Haze", "Taylor Swift", "Midnights", cover),
                track("Anti-Hero", "Taylor Swift", "Midnights", cover),
            ),
            QUERY,
        )
        assertEquals("Anti-Hero", parsed?.title)
        assertTrue(parsed!!.url.endsWith("/1280x1280.mp4"))
    }

    @Test fun refusesAnotherAlbumOrAMissingOrMalformedCover() {
        assertNull(TidalMotionProvider.parse(body(track("Anti-Hero", "Taylor Swift", "Other Album", cover)), QUERY))
        assertNull(TidalMotionProvider.parse(body(track("Anti-Hero", "Taylor Swift", "Midnights", null)), QUERY))
        assertNull(TidalMotionProvider.parse(body(track("Anti-Hero", "Taylor Swift", "Midnights", "abc")), QUERY))
        assertNull(TidalMotionProvider.parse(body(track("Anti-Hero", "Someone Else", "Midnights", cover)), QUERY))
        assertNull(TidalMotionProvider.parse("not json", QUERY))
    }

    @Test fun sendsTheSuppliedTokenAndTreatsServerErrorsAsFailures() = runBlocking {
        val http = FakeHttp({ ok(body(track("Anti-Hero", "Taylor Swift", "Midnights", cover))) })
        assertNotNull(TidalMotionProvider(http, "supplied-token", "US").find(QUERY))
        assertEquals("supplied-token", http.headers.single()["X-Tidal-Token"])
        try {
            TidalMotionProvider(FakeHttp({ CanvasResponse(500, null) }), "t", "US").find(QUERY)
            fail("expected a failure")
        } catch (_: IOException) {
        }
    }
}

class CommunityMotionProviderTest {
    private val good = "https://cdn.example.test/anti-hero.mp4"
    private fun manifest(vararg extra: String) =
        """{"items":[{"song":"Anti-Hero","artist":"Taylor Swift","album":"Midnights","url":"$good"}
        ${extra.joinToString("") { ",$it" }}]}"""

    @Test fun keepsOnlyWellFormedEntriesWithSafeUrls() {
        val parsed = CommunityMotionProvider.parse(
            manifest(
                """{"song":"A","artist":"B","url":"http://cdn.example.test/a.mp4"}""",
                """{"song":"A","artist":"B","url":"https://127.0.0.1/a.mp4"}""",
                """{"song":"A","url":"https://cdn.example.test/a.mp4"}""",
                """{"song":"${"x".repeat(400)}","artist":"B","url":"https://cdn.example.test/a.mp4"}""",
                """{"song":"A","artist":"B","url":"https://cdn.example.test/a.exe"}""",
                "\"not an object\"",
                """{"song":["A"],"artist":"B","url":"https://cdn.example.test/a.mp4"}""",
            ),
        )
        assertEquals(listOf(good), parsed.map { it.url })
        assertTrue(CommunityMotionProvider.parse("{}").isEmpty())
        assertTrue(CommunityMotionProvider.parse("garbage").isEmpty())
    }

    @Test fun matchesExactlyAndChecksThatTheClipStillAnswers() = runBlocking {
        val http = FakeHttp({ ok(manifest()) }, live = setOf(good))
        val provider = CommunityMotionProvider(http, "https://index.example.test/canvas.json")
        assertEquals(good, provider.find(QUERY)?.url)
        assertNull(provider.find(QUERY.copy(title = "Anti-Hero (Remix)")))
        assertNull(provider.find(QUERY.copy(album = "Elsewhere")))
        assertNull(provider.find(QUERY.copy(artists = listOf("Someone Else"))))
    }

    @Test fun aDeadClipIsNotOffered() = runBlocking {
        val provider = CommunityMotionProvider(FakeHttp({ ok(manifest()) }, live = emptySet()), "https://index.example.test/c.json")
        assertNull(provider.find(QUERY))
    }

    @Test fun theIndexIsDownloadedOncePerThirtyMinutes() = runBlocking {
        var now = 0L
        val http = FakeHttp({ ok(manifest()) }, live = setOf(good))
        val provider = CommunityMotionProvider(http, "https://index.example.test/c.json", { now })
        provider.find(QUERY)
        provider.find(QUERY.copy(title = "Other"))
        now += 29 * 60 * 1000
        provider.find(QUERY)
        assertEquals(1, http.requests.size)
        now += 2 * 60 * 1000
        provider.find(QUERY)
        assertEquals(2, http.requests.size)
    }

    @Test fun aFailedRefreshKeepsServingTheOldIndexAndABrokenOneIsAFailure() = runBlocking {
        var now = 0L
        var healthy = true
        val http = FakeHttp({ if (healthy) ok(manifest()) else CanvasResponse(500, null) }, live = setOf(good))
        val provider = CommunityMotionProvider(http, "https://index.example.test/c.json", { now })
        assertNotNull(provider.find(QUERY))
        healthy = false
        now += 31 * 60 * 1000
        assertNotNull(provider.find(QUERY))

        val broken = CommunityMotionProvider(FakeHttp({ CanvasResponse(500, null) }), "https://index.example.test/c.json")
        try {
            broken.find(QUERY)
            fail("expected a failure")
        } catch (_: IOException) {
        }
    }
}
