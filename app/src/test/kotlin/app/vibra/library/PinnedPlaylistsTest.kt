/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.library

import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedPlaylistsTest {
    @Test
    fun pinnedComeFirstInTheirOwnOrder() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("c", "a", "b", "d"), list.pinnedFirst(listOf("c", "a")) { it })
        assertEquals(list, list.pinnedFirst(emptyList()) { it })
    }

    @Test
    fun togglingPinsOnTopAndUnpins() {
        assertEquals(listOf("x", "a"), togglePinnedPlaylist(listOf("a"), "x"))
        assertEquals(listOf("a"), togglePinnedPlaylist(listOf("x", "a"), "x"))
    }

    @Test
    fun encodingRoundTripsAndDropsBlanks() {
        assertEquals(listOf("a", "VLPL1"), decodePinnedPlaylists(encodePinnedPlaylists(listOf("a", "VLPL1", "a"))))
        assertEquals(emptyList<String>(), decodePinnedPlaylists(""))
    }

    @Test
    fun draggingKeepsPinnedPlaylistsThatAreNotShown() {
        assertEquals(listOf("b", "a", "hidden"), reorderedPinned(listOf("a", "b", "hidden"), listOf("b", "x", "a")))
    }
}
