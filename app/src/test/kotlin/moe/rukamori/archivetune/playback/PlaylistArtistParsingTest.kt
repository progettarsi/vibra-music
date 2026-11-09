/*
 * Vibra (2026) — album links must never replace playlist song artists.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package moe.rukamori.archivetune.playback

import moe.rukamori.archivetune.innertube.models.*
import moe.rukamori.archivetune.innertube.pages.PlaylistPage
import org.junit.Assert.*
import org.junit.Test

class PlaylistArtistParsingTest {
    private fun renderer(metadata: List<Run>) = MusicResponsiveListItemRenderer(
        badges = null, fixedColumns = null,
        flexColumns = listOf(listOf(Run("Song")), metadata).map {
            MusicResponsiveListItemRenderer.FlexColumn(
                MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(Runs(it)))
        },
        thumbnail = ThumbnailRenderer(ThumbnailRenderer.MusicThumbnailRenderer(
            Thumbnails(listOf(Thumbnail("https://example.com/cover.jpg", 120, 120))), null, null)),
        menu = null, playlistItemData = MusicResponsiveListItemRenderer.PlaylistItemData(null, "song"),
        overlay = null, navigationEndpoint = null)

    @Test fun albumBeforeArtistIsParsedAsAlbumAndArtistSeparately() {
        val item = PlaylistPage.fromMusicResponsiveListItemRenderer(renderer(listOf(
            Run("Album", NavigationEndpoint(browseEndpoint = BrowseEndpoint("MPREb_album"))),
            Run(" • "), Run("Artist", NavigationEndpoint(browseEndpoint = BrowseEndpoint("UC_artist"))),
            Run(" • "), Run("3:15"))))!!
        assertEquals(listOf(Artist("Artist", "UC_artist")), item.artists)
        assertEquals(Album("Album", "MPREb_album"), item.album)
        assertEquals(195, item.duration)
    }

    @Test fun albumOnlyMetadataDoesNotBecomeAnArtist() {
        val item = PlaylistPage.fromMusicResponsiveListItemRenderer(renderer(listOf(
            Run("Album", NavigationEndpoint(browseEndpoint = BrowseEndpoint("MPREb_album"))))))!!
        assertTrue(item.artists.isEmpty())
        assertEquals("MPREb_album", item.album?.id)
    }

    @Test fun plainArtistNameIsKeptAsFallback() {
        val item = PlaylistPage.fromMusicResponsiveListItemRenderer(renderer(listOf(Run("Unlinked Artist"))))!!
        assertEquals(listOf(Artist("Unlinked Artist", null)), item.artists)
        assertNull(item.album)
    }
}
