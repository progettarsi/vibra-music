/*
 * Vibra (2026) — Canvas identity matching: a wrong clip is worse than none.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasMatcherTest {
    private fun query(title: String, artists: List<String>, album: String? = null) =
        CanvasQuery("id", title, artists, album)

    private fun candidate(title: String?, artist: String?, album: String? = null) =
        CanvasCandidate("https://cdn.example.test/clip.mp4", null, title, artist, album)

    private fun accepts(q: CanvasQuery, c: CanvasCandidate) = CanvasMatcher.accepts(q, c)

    @Test fun acceptsTheSameTrackWhateverTheFormatting() {
        val q = query("Crazy in Love (feat. JAY-Z)", listOf("Beyoncé"), "Dangerously in Love")
        assertTrue(accepts(q, candidate("CRAZY IN LOVE", "Beyonce & JAY-Z", "Dangerously in Love")))
    }

    @Test fun foldsAccentsPunctuationAndRepeatedSpaces() {
        assertEquals("dont stop me now", CanvasMatcher.normalize("  Don’t   Stop—Me, Now! "))
        assertEquals("beyonce", CanvasMatcher.normalize("Beyoncé"))
        assertTrue(CanvasMatcher.titleMatches("Don't Stop Me Now", "Dont stop me now"))
        assertTrue(CanvasMatcher.titleMatches("Rock & Roll", "Rock and Roll"))
    }

    @Test fun dropsPackagingFromTitles() {
        assertEquals("song", CanvasMatcher.cleanTitle("Song (Official Video)"))
        assertEquals("song", CanvasMatcher.cleanTitle("Song - Official Audio"))
        assertEquals("song", CanvasMatcher.cleanTitle("Song [Lyrics]"))
        assertEquals("song", CanvasMatcher.cleanTitle("Song (Official Music Video) [4K]"))
        assertEquals("song", CanvasMatcher.cleanTitle("Song feat. Someone"))
        assertEquals("song", CanvasMatcher.cleanTitle("Song (ft. Someone Else)"))
        // A song that is itself called like the noise is not touched.
        assertEquals("video games", CanvasMatcher.cleanTitle("Video Games"))
        assertEquals("feat of strength", CanvasMatcher.cleanTitle("Feat of Strength"))
    }

    @Test fun splitsEveryKindOfCreditSeparator() {
        assertEquals(listOf("dua lipa", "elton john"), CanvasMatcher.splitArtists("Dua Lipa feat. Elton John"))
        assertEquals(listOf("dua lipa", "elton john"), CanvasMatcher.splitArtists("Dua Lipa ft Elton John"))
        assertEquals(listOf("dua lipa", "elton john"), CanvasMatcher.splitArtists("Dua Lipa featuring Elton John"))
        assertEquals(listOf("a", "b", "c"), CanvasMatcher.splitArtists("A, B & C"))
        assertEquals(listOf("a", "b"), CanvasMatcher.splitArtists("A x B"))
        assertEquals(listOf("a", "b"), CanvasMatcher.splitArtists("A × B"))
        assertEquals(listOf("jay z"), CanvasMatcher.splitArtists("JAY-Z"))
    }

    @Test fun creditOrderAndExtraFeaturedArtistsDoNotMatter() {
        val q = query("Song", listOf("Jay-Z", "Beyoncé"))
        assertTrue(accepts(q, candidate("Song", "Beyoncé & JAY-Z")))
        // The lead artist alone is part of a longer credit, in either direction.
        assertTrue(accepts(query("Song", listOf("Beyoncé")), candidate("Song", "Beyoncé, Kendrick Lamar")))
        assertTrue(accepts(query("Song", listOf("Beyoncé", "Kendrick Lamar")), candidate("Song", "Beyoncé")))
    }

    @Test fun rejectsAnotherTrackBySameArtist() {
        assertFalse(accepts(query("Anti-Hero", listOf("Taylor Swift")), candidate("Lavender Haze", "Taylor Swift")))
        assertFalse(accepts(query("Anti-Hero", listOf("Taylor Swift")), candidate("Anti-Hero (Remix)", "Taylor Swift")))
    }

    @Test fun rejectsAnotherArtistWithTheSameTitle() {
        assertFalse(accepts(query("Halo", listOf("Beyoncé")), candidate("Halo", "Texas")))
        assertFalse(accepts(query("Get Lucky", listOf("Daft Punk", "Pharrell Williams")), candidate("Get Lucky", "Pharrell Williams")))
        assertFalse(accepts(query("Song", listOf("A")), candidate("Song", null)))
        assertFalse(accepts(query("Song", listOf("A")), candidate(null, "A")))
    }

    @Test fun differentRecordingsAreNeverEquivalent() {
        val artists = listOf("Taylor Swift")
        assertFalse(accepts(query("Love Story", artists), candidate("Love Story (Taylor's Version)", "Taylor Swift")))
        assertFalse(accepts(query("Song", artists), candidate("Song (Live)", "Taylor Swift")))
        assertFalse(accepts(query("Song", artists), candidate("Song (Acoustic)", "Taylor Swift")))
        assertFalse(accepts(query("Song", artists), candidate("Song - Radio Edit", "Taylor Swift")))
        assertFalse(accepts(query("Song (Remastered 2011)", artists), candidate("Song", "Taylor Swift")))
        assertTrue(accepts(query("Song (Remastered 2011)", artists), candidate("Song - 2011 Remaster", "Taylor Swift")))
        assertFalse(accepts(query("Song (Remastered 2011)", artists), candidate("Song - 2009 Live", "Taylor Swift")))
    }

    @Test fun albumsAreCheckedOnlyWhenBothSidesKnowThem() {
        val artists = listOf("Artist")
        assertTrue(accepts(query("Song", artists, null), candidate("Song", "Artist", "Any Album")))
        assertTrue(accepts(query("Song", artists, "Any Album"), candidate("Song", "Artist", null)))
        assertTrue(accepts(query("Song", artists, "Folklore"), candidate("Song", "Artist", "folklore")))
        assertFalse(accepts(query("Song", artists, "Folklore"), candidate("Song", "Artist", "Evermore")))
        // An edition is not the original release.
        assertFalse(accepts(query("Song", artists, "Folklore"), candidate("Song", "Artist", "Folklore (Deluxe Version)")))
        // A single's album is often "<title> - Single".
        assertTrue(accepts(query("Song", artists, "Song"), candidate("Song", "Artist", "Song - Single")))
    }

    @Test fun nonLatinTitlesStayDistinct() {
        assertFalse(CanvasMatcher.titleMatches("夜に駆ける", "紅蓮華"))
        assertTrue(CanvasMatcher.titleMatches("夜に駆ける", "夜に駆ける"))
        assertFalse(CanvasMatcher.titleMatches("!!!", "???"))
    }

    @Test fun queriesWithoutIdentityAreNotSearchable() {
        assertFalse(CanvasMatcher.isIdentifiable(query("  ", listOf("A"))))
        assertFalse(CanvasMatcher.isIdentifiable(query("Song", emptyList())))
        assertFalse(CanvasMatcher.isIdentifiable(query("Song", listOf(" "))))
        assertTrue(CanvasMatcher.isIdentifiable(query("Song", listOf("A"))))
    }

    @Test fun identityIgnoresTheAlbumButNotTheTrack() {
        val withAlbum = CanvasMatcher.identity(query("Song (Official Video)", listOf("A"), "X"))
        val without = CanvasMatcher.identity(query("Song", listOf("A"), null))
        assertEquals(without, withAlbum)
        assertFalse(CanvasMatcher.identity(CanvasQuery("other", "Song", listOf("A"))) == without)
    }

    @Test fun urlsMustBeHttpsPublicAndPlayable() {
        assertTrue(CanvasUrls.isAcceptable("https://cdn.example.test/a/b.mp4"))
        assertTrue(CanvasUrls.isAcceptable("https://cdn.example.test/stream/master.m3u8?token=1"))
        assertFalse(CanvasUrls.isAcceptable("http://cdn.example.test/a.mp4"))
        assertFalse(CanvasUrls.isAcceptable("https://localhost/a.mp4"))
        assertFalse(CanvasUrls.isAcceptable("https://192.168.1.4/a.mp4"))
        assertFalse(CanvasUrls.isAcceptable("https://127.0.0.1/a.mp4"))
        assertFalse(CanvasUrls.isAcceptable("https://[::1]/a.mp4"))
        assertFalse(CanvasUrls.isAcceptable("https://printer.local/a.mp4"))
        assertFalse(CanvasUrls.isAcceptable("https://intranet/a.mp4"))
        assertFalse(CanvasUrls.isAcceptable("https://user:pw@cdn.example.test/a.mp4"))
        assertFalse(CanvasUrls.isAcceptable("https://cdn.example.test/a.exe"))
        assertFalse(CanvasUrls.isAcceptable("https://cdn.example.test/page.html"))
        assertFalse(CanvasUrls.isAcceptable("file:///sdcard/a.mp4"))
        assertFalse(CanvasUrls.isAcceptable("javascript:alert(1)"))
        assertFalse(CanvasUrls.isAcceptable(null))
        assertFalse(CanvasUrls.isAcceptable(""))
    }
}
