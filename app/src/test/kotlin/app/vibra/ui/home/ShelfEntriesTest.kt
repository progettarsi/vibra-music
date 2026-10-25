/*
 * Vibra (2026) — GPL-3.0.
 */

package app.vibra.ui.home

import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfEntriesTest {
    @Test
    fun songSubtitle_joinsArtists() {
        val song =
            SongItem(
                id = "id",
                title = "Title",
                artists = listOf(Artist(name = "A", id = null), Artist(name = "B", id = null)),
                thumbnail = "",
            )
        assertEquals("A, B", song.subtitleText())
    }

    @Test
    fun albumSubtitle_addsYear() {
        val album =
            AlbumItem(
                browseId = "b",
                playlistId = "p",
                title = "Album",
                artists = listOf(Artist(name = "Artist", id = null)),
                year = 2024,
                thumbnail = "",
            )
        assertEquals("Artist · 2024", album.subtitleText())
    }
}
