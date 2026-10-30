/*
 * Vibra (2026) — public stream identity, format and origin regression tests.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.playback

import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.playback.stream.AudioStreamRequest
import moe.rukamori.archivetune.playback.stream.StreamPurpose
import moe.rukamori.archivetune.playback.stream.StreamSource
import org.junit.Assert.*
import org.junit.Test

class PublicStreamParserTest {
    private val inv = PublicStreamInstance("https://api.example.test", "example.test", StreamSource.INVIDIOUS)
    private val piped = inv.copy(source = StreamSource.PIPED)
    private val request = AudioStreamRequest("dQw4w9WgXcQ", quality = AudioQuality.HIGH,
        networkMetered = false, purpose = StreamPurpose.PLAYBACK, authState = PlaybackAuthState())

    private fun invBody(url: String = "https://relay.example.test/videoplayback", id: String = request.mediaId,
        extra: String = "") = """{"videoId":"$id","lengthSeconds":213,$extra
        "adaptiveFormats":[{"url":"$url","type":"audio/webm; codecs=\"opus\"",
        "itag":"251","clen":"12345","bitrate":"128000"}]}"""

    @Test fun parsesTheExactAudioRepresentationWithoutCredentials() {
        val stream = PublicStreamParser.parse(invBody(), request, inv, 1000L)
        assertEquals(251, stream.formatId)
        assertEquals("opus", stream.codecs)
        assertEquals(12345L, stream.contentLength)
        assertEquals(StreamSource.INVIDIOUS, stream.source)
        assertFalse(stream.mixedVideo)
        assertEquals(setOf("Accept"), stream.requestHeaders.keys)
    }

    @Test(expected = java.io.IOException::class)
    fun rejectsAnInstanceReturningAnotherVideo() {
        PublicStreamParser.parse(invBody(id = "kJQP7kiw5Fk"), request, inv, 0L)
    }

    @Test fun rejectsDirectGoogleUrlsAndHostSuffixTricks() {
        for (url in listOf("https://rr1.googlevideo.com/play", "https://example.test.attacker.test/play",
            "http://relay.example.test/play", "https://user:password@relay.example.test/play",
            "https://127.0.0.1/play", "https://relay.example.test:1234/play")) {
            assertThrows(java.io.IOException::class.java) { PublicStreamParser.parse(invBody(url), request, inv, 0L) }
        }
    }

    @Test fun refusesProtectedAndExpiredStreams() {
        assertThrows(java.io.IOException::class.java) {
            PublicStreamParser.parse(invBody(extra = "\"premium\":true,"), request, inv, 0L)
        }
        assertThrows(java.io.IOException::class.java) {
            PublicStreamParser.parse(invBody(url = "https://relay.example.test/play?expire=10"), request, inv, 10000L)
        }
    }

    @Test fun prefersSeparateAudioOverMixedVideo() {
        val body = """{"audioStreams":[{"mimeType":"audio/mp4","url":"https://relay.example.test/audio","bitrate":128000}],
            "videoStreams":[{"mimeType":"video/mp4","url":"https://relay.example.test/video","videoOnly":false}]}"""
        val stream = PublicStreamParser.parse(body, request, piped, 0L)
        assertEquals("audio/mp4", stream.mimeType)
        assertFalse(stream.mixedVideo)
    }

    @Test fun acceptsOnlyProxiedMixedStreamsAndNeverUnverifiedLbryCopies() {
        val body = """{"videoStreams":[
            {"mimeType":"video/mp4","url":"https://relay.example.test/lbry","videoOnly":false,"quality":"LBRY"},
            {"mimeType":"video/mp4","url":"https://relay.example.test/silent","videoOnly":true},
            {"mimeType":"video/mp4","url":"https://relay.example.test/mixed?itag=18","videoOnly":false,"quality":"360p"}]}"""
        val stream = PublicStreamParser.parse(body, request, piped, 0L)
        assertEquals(18, stream.formatId)
        assertTrue(stream.mixedVideo)
        assertTrue(stream.url.contains("/mixed"))
    }

    @Test fun qualityAndMeteredChoicesRemainConsistent() {
        val body = """{"audioStreams":[
            {"mimeType":"audio/mp4","url":"https://relay.example.test/low","bitrate":48000},
            {"mimeType":"audio/mp4","url":"https://relay.example.test/high","bitrate":128000}]}"""
        assertTrue(PublicStreamParser.parse(body, request.copy(quality = AudioQuality.LOW), piped, 0L).url.endsWith("/low"))
        assertTrue(PublicStreamParser.parse(body, request, piped, 0L).url.endsWith("/high"))
        assertTrue(PublicStreamParser.parse(body, request.copy(quality = AudioQuality.AUTO, networkMetered = true), piped, 0L).url.endsWith("/low"))
    }

    @Test fun unusableAudioStillAllowsTheSmallestVerifiedMixedStream() {
        val body = """{"audioStreams":[
            {"mimeType":"audio/mp4","url":"https://rr1.googlevideo.com/direct","bitrate":128000}],
            "videoStreams":[
            {"mimeType":"video/mp4","url":"https://relay.example.test/large","videoOnly":false,"quality":"720p"},
            {"mimeType":"video/mp4","url":"https://relay.example.test/small","videoOnly":false,"quality":"360p"}]}"""
        val stream = PublicStreamParser.parse(body, request, piped, 0L)
        assertTrue(stream.mixedVideo)
        assertTrue(stream.url.endsWith("/small"))
    }
}
