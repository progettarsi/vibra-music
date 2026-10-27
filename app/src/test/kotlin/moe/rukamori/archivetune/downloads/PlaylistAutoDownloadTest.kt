/*
 * Vibra (2026) — GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package moe.rukamori.archivetune.downloads

import org.junit.Assert.*
import org.junit.Test

class PlaylistAutoDownloadTest {
    @Test fun requiresNonEmptyFullyCompletedPlaylist() {
        assertFalse(shouldAutoDownloadPlaylist(emptyList(), setOf("a")))
        assertFalse(shouldAutoDownloadPlaylist(listOf("a", "b"), setOf("a")))
        assertTrue(shouldAutoDownloadPlaylist(listOf("a", "a"), setOf("a")))
        assertTrue(shouldAutoDownloadPlaylist(listOf("a", "b"), setOf("a", "b", "c")))
    }
}
