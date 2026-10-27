/*
 * Vibra (2026) — GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.innertube.models.ArtistItem
import org.junit.Assert.*
import org.junit.Test

class SpotifyOnboardingImportTest {
    private fun artist(id: String, name: String) = ArtistItem(id, name, null, shuffleEndpoint = null, radioEndpoint = null)
    @Test fun exactNameWinsAndMissesAreSkipped() {
        val similar = artist("cover", "Muse covers")
        val insensitive = artist("case", "MUSE")
        val exact = artist("exact", "Muse")
        assertEquals(exact, bestExactArtist("Muse", listOf(similar, insensitive, exact)))
        assertNull(bestExactArtist("Radiohead", listOf(similar, exact)))
        assertEquals(exact, bestExactArtist("  MUSE  ", listOf(exact)))
        assertEquals("muse live", normalizedArtistName("  Muse   Live "))
    }
}
