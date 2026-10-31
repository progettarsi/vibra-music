package moe.rukamori.archivetune.playback
import moe.rukamori.archivetune.playback.stream.*
import app.vibra.playback.downloadRepresentationSignature
import org.junit.Assert.*
import org.junit.Test

class DownloadRepresentationTest {
    private val stream = ResolvedAudioStream("https://example.test/a?expire=1", emptyMap(), 251,
        "audio/webm", "opus", 128000, 48000, 123456L, 123L, "account-a", StreamSource.YOUTUBEI)
    @Test fun renewedSignedUrlDoesNotInvalidateTheSameRepresentation() {
        assertEquals(downloadRepresentationSignature(stream), downloadRepresentationSignature(stream.copy(url = "https://example.test/b?expire=2", expiresAtMs = 456L)))
    }
    @Test fun providerCodecOrAccountChangeCannotSpliceEvenWithUnknownLength() {
        for (other in listOf(stream.copy(source = StreamSource.PIPED), stream.copy(codecs = "aac"), stream.copy(authFingerprint = "account-b"))) {
            assertNotEquals(downloadRepresentationSignature(stream), downloadRepresentationSignature(other))
        }
    }
    @Test fun renewedUrlWithUnknownFormatOrLengthRequiresRestart() {
        for (unverified in listOf(stream.copy(formatId = 0), stream.copy(contentLength = 0))) {
            assertNotEquals(downloadRepresentationSignature(unverified), downloadRepresentationSignature(unverified.copy(url = "https://example.test/new")))
        }
    }

}
