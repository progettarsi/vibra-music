/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.library

import app.vibra.player.cleanChannelName
import app.vibra.player.cleanVideoTitle
import app.vibra.player.songMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongCoverTest {
    @Test
    fun videoTitlesLoseTheirDecorations() {
        assertEquals("Blinding Lights", cleanVideoTitle("The Weeknd - Blinding Lights (Official Video)", listOf("The Weeknd")))
        assertEquals("Levitating", cleanVideoTitle("Levitating [Official Music Video] [4K]"))
        assertEquals("Song - 2011 Remaster", cleanVideoTitle("Song - 2011 Remaster", listOf("Someone")))
        assertEquals("Bad Guy", cleanVideoTitle("Bad Guy - Official Video"))
    }

    @Test
    fun channelNamesBecomeArtists() {
        assertEquals("Adele", cleanChannelName("AdeleVEVO"))
        assertEquals("Muse", cleanChannelName("Muse - Topic"))
    }

    @Test
    fun matchesNeedTitleAndArtist() {
        assertTrue(songMatches("Blinding Lights", listOf("The Weeknd"), "Blinding Lights", listOf("The Weeknd")))
        assertTrue(songMatches("Déjà vu", listOf("Olivia Rodrigo"), "deja vu", listOf("Olivia Rodrigo")))
        assertFalse(songMatches("Blinding Lights", listOf("The Weeknd"), "Blinding Lights", listOf("A Cover Band")))
        assertFalse(songMatches("Blinding Lights", listOf("The Weeknd"), "Save Your Tears", listOf("The Weeknd")))
    }
}
