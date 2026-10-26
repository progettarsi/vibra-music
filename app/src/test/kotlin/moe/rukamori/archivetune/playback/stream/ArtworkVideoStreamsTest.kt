/*
 * Vibra (2026) — GPL-3.0.
 */

package moe.rukamori.archivetune.playback.stream

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkVideoStreamsTest {
    private fun stream(
        mediaId: String,
        expiresInMillis: Long = 3_600_000L,
    ) = ArtworkVideoStream(
        mediaId = mediaId,
        url = "https://example.invalid/$mediaId",
        requestHeaders = emptyMap(),
        mimeType = "video/mp4",
        width = 854,
        height = 480,
        expiresAtMs = System.currentTimeMillis() + expiresInMillis,
    )

    @Test
    fun published_streamIsOffered() =
        runBlocking {
            val published = stream("published")
            ArtworkVideoStreams.publish(published)
            assertEquals(published, ArtworkVideoStreams.stream("published").first())
        }

    @Test
    fun expiringStream_isNotOffered() =
        runBlocking {
            ArtworkVideoStreams.publish(stream("expiring", expiresInMillis = 10_000L))
            assertNull(ArtworkVideoStreams.stream("expiring").first())
        }

    @Test
    fun failedStream_isNeverOfferedAgain() =
        runBlocking {
            ArtworkVideoStreams.publish(stream("failing"))
            ArtworkVideoStreams.markFailed("failing")
            ArtworkVideoStreams.publish(stream("failing"))
            assertNull(ArtworkVideoStreams.stream("failing").first())
        }

    @Test
    fun aspectRatio_fallsBackToWidescreen() {
        assertEquals(16f / 9f, stream("unknown").copy(width = 0).aspectRatio, 1e-6f)
        assertEquals(854f / 480f, stream("known").aspectRatio, 1e-6f)
    }
}
