package moe.rukamori.archivetune.playback
import app.vibra.playback.DownloadRecoveryPlan
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class DownloadRecoveryTest {
    @Test fun failedPartialSourceIsDiscardedBeforeFallbackCompletes() {
        val bytes = ByteArrayOutputStream()
        var attempt = 0
        var resets = 0
        runRecoveringDownload(
            attempt = {
                if (attempt++ == 0) { bytes.write("partial-A".toByteArray()); throw IOException("403") }
                bytes.write("complete-B".toByteArray())
            },
            reset = { resets++; bytes.reset() }, recover = { true }, isCancelled = { false },
        )
        assertEquals("complete-B", bytes.toString("UTF-8"))
        assertEquals(1, resets)
        assertEquals(2, attempt)
    }
    @Test fun exhaustedSourcesNeverLoopForever() {
        val plan = DownloadRecoveryPlan(false)
        var attempts = 0
        var resets = 0
        assertThrows(IOException::class.java) {
            runRecoveringDownload({ attempts++; throw IOException() }, { resets++ }, { plan.advance() }, { false })
        }
        assertEquals(4, attempts)
        assertEquals(3, resets)
    }
    @Test fun cancellingAnActiveAttemptDoesNotClearOrTryAnotherSource() {
        var cancelled = false
        var resets = 0
        var recoveries = 0
        assertThrows(InterruptedException::class.java) {
            runRecoveringDownload({ cancelled = true; throw IOException() }, { resets++ }, { recoveries++; true }, { cancelled })
        }
        assertEquals(0, resets)
        assertEquals(0, recoveries)
    }
    @Test fun persistedRepresentationRestartsOnceWithoutAdvancingProvider() {
        var attempts = 0
        var resets = 0
        runRecoveringDownload({ if (attempts++ == 0) throw DownloadContentChangedException() }, { resets++ }, { fail("Must not advance"); false }, { false })
        assertEquals(2, attempts)
        assertEquals(1, resets)
    }
    @Test fun aRepeatedRepresentationMismatchIsBounded() {
        var attempts = 0
        assertThrows(IOException::class.java) {
            runRecoveringDownload({ attempts++; throw DownloadContentChangedException() }, {}, { false }, { false })
        }
        assertEquals(2, attempts)
    }
}
