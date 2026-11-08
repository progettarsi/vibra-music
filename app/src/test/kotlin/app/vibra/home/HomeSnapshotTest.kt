/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.home

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.innertube.models.*
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomeSnapshotTest {
    @get:Rule val folder = TemporaryFolder()
    private fun song(id: String = "abcdefghijk") = SongItem(id, "Song", listOf(Artist("Artist", "UCtest")), thumbnail = "https://example.org/cover.jpg", explicit = true)
    private fun page(items: List<YTItem> = listOf(song())) = HomePage(null, listOf(HomePage.Section("Shelf", null, null, BrowseEndpoint("browse"), items)), "NEVER_PERSIST_CONTINUATION")

    @Test fun roundTripKeepsActionsFiltersAndDisplayMetadataWithoutPagination() = runBlocking {
        val file = folder.newFile()
        val disk = HomeSnapshotDisk(file) { 1000 }
        disk.write("account", page(), listOf(song()))
        val restored = disk.read("account")!!
        val item = restored.page().sections.single().items.single() as SongItem
        assertEquals("abcdefghijk", item.endpoint?.videoId)
        assertTrue(item.explicit)
        assertEquals("UCtest", item.artists.single().id)
        assertTrue(listOf(item).filterBlockedArtists(setOf("UCtest")).isEmpty())
        assertNull(restored.page().continuation)
        assertFalse(file.readText().contains("NEVER_PERSIST_CONTINUATION"))
        assertEquals(1, restored.picks().size)
    }

    @Test fun videoClassificationSurvivesRoundTrip() = runBlocking {
        val video = song().copy(endpoint = WatchEndpoint(watchEndpointMusicSupportedConfigs = WatchEndpoint.WatchEndpointMusicSupportedConfigs(
            WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig("MUSIC_VIDEO_TYPE_OMV"))))
        val restored = SnapshotItem.from(video)!!.item()!!
        assertTrue(listOf(restored).filterVideo(true).isEmpty())
        assertTrue(listOf(restored).filterExplicit(true).isEmpty())
    }

    @Test fun playlistAndFeaturedCardActionsSurviveRoundTrip() = runBlocking {
        val file = folder.newFile()
        val disk = HomeSnapshotDisk(file)
        val endpoint = WatchEndpoint(playlistId = "PLfixture", params = "shuffle")
        val playlist = PlaylistItem("PLfixture", "Mix", Artist("Artist", "UCtest"), "10 songs", null, endpoint, endpoint, endpoint)
        val card = HomePage.Section.FeaturedCard("PLfixture", "Mix", "Subtitle", null, BrowseEndpoint("VLPLfixture"), endpoint, endpoint, endpoint, listOf("abcdefghijk"))
        val source = page(listOf(playlist, song())).let { it.copy(sections = listOf(it.sections.single().copy(featuredCards = listOf(card)))) }
        disk.write("A", source, emptyList())
        val section = disk.read("A")!!.page().sections.single()
        assertEquals(endpoint, (section.items.first() as PlaylistItem).shuffleEndpoint)
        assertEquals(card, section.featuredCards.single())
    }

    @Test fun accountChangeInvalidatesAndDeletesPreviousSnapshot() = runBlocking {
        val file = folder.newFile()
        val disk = HomeSnapshotDisk(file) { 1000 }
        disk.write("A", page(), emptyList())
        assertNull(disk.read("B"))
        assertFalse(file.exists())
    }

    @Test fun staleFutureAndUnknownSchemaAreDiscarded() = runBlocking {
        val file = folder.newFile()
        var time = 1000L
        val disk = HomeSnapshotDisk(file) { time }
        disk.write("A", page(), emptyList())
        time += HomeSnapshotDisk.MAX_AGE_MS + 1
        assertNull(disk.read("A"))
        disk.write("A", page(), emptyList())
        time -= 1
        assertNull(disk.read("A"))
        disk.write("A", page(), emptyList())
        val current = file.readText()
        assertTrue(current.contains("\"version\":${HomeSnapshotDisk.SCHEMA_VERSION}"))
        file.writeText(current.replace("\"version\":${HomeSnapshotDisk.SCHEMA_VERSION}", "\"version\":99"))
        assertNull(disk.read("A"))
        assertFalse(file.exists())
        // A file without a version is an older layout, not the current one.
        file.writeText(current.replace("\"version\":${HomeSnapshotDisk.SCHEMA_VERSION},", ""))
        assertNull(disk.read("A"))
        assertFalse(file.exists())
    }

    @Test fun corruptOversizeAndMissingCacheAreHarmless() = runBlocking {
        val file = File(folder.root, "home.json")
        val disk = HomeSnapshotDisk(file)
        assertNull(disk.read("A"))
        file.writeText("{")
        assertNull(disk.read("A"))
        file.writeBytes(ByteArray(HomeSnapshotDisk.MAX_BYTES.toInt() + 1))
        assertNull(disk.read("A"))
        assertFalse(file.exists())
    }

    @Test fun emptyOrOversizeRefreshKeepsLastGoodSnapshot() = runBlocking {
        val file = folder.newFile()
        val disk = HomeSnapshotDisk(file)
        disk.write("A", page(), emptyList())
        val original = file.readBytes()
        disk.write("A", null, emptyList())
        assertArrayEquals(original, file.readBytes())
        disk.write("A", page(listOf(song().copy(title = "x".repeat(HomeSnapshotDisk.MAX_BYTES.toInt())))), emptyList())
        assertArrayEquals(original, file.readBytes())
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test fun contextSeparatesAccountsChannelsAndLocalesWithoutStoringSessionValues() {
        val a = HomeSnapshotDisk.contextKey("cookie-secret", "channel", "IT", "it")
        assertEquals(64, a.length)
        assertNotEquals(a, HomeSnapshotDisk.contextKey("other", "channel", "IT", "it"))
        assertNotEquals(a, HomeSnapshotDisk.contextKey("cookie-secret", "other", "IT", "it"))
        assertNotEquals(a, HomeSnapshotDisk.contextKey("cookie-secret", "channel", "US", "it"))
        assertNotEquals(a, HomeSnapshotDisk.contextKey("cookie-secret", "channel", "IT", "en"))
        assertFalse(a.contains("cookie-secret"))
    }
}
