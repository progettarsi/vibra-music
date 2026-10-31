/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.spotify
import org.junit.Assert.*
import org.junit.Test
class SpotifyMergeTest {
    @Test fun preservesManualAdditionsAndRemoteOrder() {
        assertEquals(listOf("b", "a", "manual", "removed"), mergeImportedPlaylist(listOf("b", "a"), listOf("a", "manual", "removed")))
    }
    @Test fun secondImportIsIdempotent() {
        val first = mergeImportedPlaylist(listOf("a", "b"), listOf("c"))
        assertEquals(first, mergeImportedPlaylist(listOf("a", "b"), first))
    }
    @Test fun emptyRemoteDoesNotDeleteLocalContent() {
        assertEquals(listOf("a"), mergeImportedPlaylist(emptyList(), listOf("a")))
    }
}
