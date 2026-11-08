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
    @Test fun onlyUntouchedImportsAreRemoved() {
        val imported = playlistSnapshot(listOf("b", "a"))
        assertTrue(isUntouchedImport(null, listOf("b", "a"), imported))
        assertTrue(isUntouchedImport(null, emptyList(), playlistSnapshot(emptyList())))
        assertFalse(isUntouchedImport(null, listOf("b", "a", "manual"), imported))
        assertFalse(isUntouchedImport("VLPL1", listOf("b", "a"), imported))
    }
    @Test fun anyChangeToTheImportedSongsProtectsThePlaylist() {
        val imported = playlistSnapshot(listOf("a", "b"))
        assertFalse("reordered", isUntouchedImport(null, listOf("b", "a"), imported))
        assertFalse("song removed", isUntouchedImport(null, listOf("a"), imported))
        assertFalse("emptied", isUntouchedImport(null, emptyList(), imported))
    }
    @Test fun aPlaylistWithoutSnapshotIsNeverRemoved() {
        assertFalse(isUntouchedImport(null, listOf("a"), null))
        assertFalse(isUntouchedImport(null, emptyList(), null))
    }
    @Test fun adoptedPlaylistRemainsProtectedAfterAnotherSync() {
        val ids = listOf("a", "b")
        val adopted = nextImportSnapshot(false, false, ids, ids, null)
        val syncedAgain = nextImportSnapshot(false, true, ids, ids, adopted)
        assertFalse(isUntouchedImport(null, ids, syncedAgain))
    }
    @Test fun reimportDoesNotEraseEvidenceOfUserEdits() {
        val ids = listOf("a", "b")
        val imported = nextImportSnapshot(true, false, emptyList(), ids, null)
        listOf(listOf("b", "a"), listOf("a"), ids + "c").forEach { edited ->
            val synced = nextImportSnapshot(false, true, edited, ids, imported)
            val syncedAgain = nextImportSnapshot(false, true, ids, ids, synced)
            assertFalse(isUntouchedImport(null, ids, syncedAgain))
        }
    }
    @Test fun unchangedImportCanFollowSpotifyUpdates() {
        val previous = listOf("a")
        val incoming = listOf("b", "a")
        val synced = nextImportSnapshot(false, true, previous, incoming, playlistSnapshot(previous))
        assertTrue(isUntouchedImport(null, incoming, synced))
    }
    @Test fun aSongLeavingSpotifyIsNotMistakenForAUserEdit() {
        // Also the case of a song that fails to resolve during one sync: the merge keeps it.
        val current = listOf("a", "b")
        val incoming = listOf("a")
        val written = mergeImportedPlaylist(incoming, current)
        val synced = nextImportSnapshot(false, true, current, incoming, playlistSnapshot(current))
        assertTrue(isUntouchedImport(null, written, synced))
        val syncedAgain = nextImportSnapshot(false, true, written, incoming, synced)
        assertTrue(isUntouchedImport(null, written, syncedAgain))
    }
    @Test fun snapshotDoesNotConfuseJoinedIds() {
        assertFalse(playlistSnapshot(listOf("ab", "c")) == playlistSnapshot(listOf("a", "bc")))
    }
}
