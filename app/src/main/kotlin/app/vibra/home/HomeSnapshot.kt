/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.models.*
import moe.rukamori.archivetune.innertube.pages.HomePage

/** Display metadata only: no auth, stream URLs or pagination tokens are representable. */
@Serializable
internal data class SnapshotArtist(val name: String, val id: String?)

@Serializable
internal data class SnapshotItem(
    val kind: String,
    val id: String,
    val title: String,
    val thumbnail: String?,
    val artists: List<SnapshotArtist> = emptyList(),
    val explicit: Boolean = false,
    val duration: Int? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val playlistId: String? = null,
    val videoType: String? = null,
    val count: String? = null,
    val play: WatchEndpoint? = null,
    val shuffle: WatchEndpoint? = null,
    val radio: WatchEndpoint? = null,
    val channelId: String? = null,
    val year: Int? = null,
    val releaseType: String = "ALBUM",
) {
    fun item(): YTItem? {
        val creators = artists.map { Artist(it.name, it.id) }
        return when (kind) {
            "song" -> SongItem(id, title, creators,
                album = albumId?.let { Album(albumName.orEmpty(), it) }, duration = duration,
                thumbnail = thumbnail.orEmpty(), explicit = explicit,
                endpoint = WatchEndpoint(videoId = id, watchEndpointMusicSupportedConfigs = videoType?.let {
                    WatchEndpoint.WatchEndpointMusicSupportedConfigs(WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig(it))
                }))
            "album" -> AlbumItem(id, playlistId.orEmpty(), title = title, artists = creators,
                thumbnail = thumbnail.orEmpty(), explicit = explicit, year = year, releaseType = runCatching { AlbumReleaseType.valueOf(releaseType) }.getOrDefault(AlbumReleaseType.ALBUM))
            "artist" -> ArtistItem(id, title, thumbnail, channelId = channelId, playEndpoint = play, shuffleEndpoint = shuffle, radioEndpoint = radio)
            "playlist" -> PlaylistItem(id, title, creators.firstOrNull(), count, thumbnail,
                play ?: WatchEndpoint(playlistId = id), shuffle, radio)
            else -> null
        }
    }

    companion object {
        fun from(item: YTItem): SnapshotItem? = when (item) {
            is SongItem -> if (item.isPodcast) null else SnapshotItem("song", item.id, item.title, item.thumbnail,
                item.artists.map { SnapshotArtist(it.name, it.id) }, item.explicit, item.duration,
                item.album?.id, item.album?.name,
                videoType = item.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType)
            is AlbumItem -> SnapshotItem("album", item.id, item.title, item.thumbnail,
                item.artists.orEmpty().map { SnapshotArtist(it.name, it.id) }, item.explicit, playlistId = item.playlistId,
                year = item.year, releaseType = item.releaseType.name)
            is ArtistItem -> SnapshotItem("artist", item.id, item.title, item.thumbnail,
                play = item.playEndpoint, shuffle = item.shuffleEndpoint, radio = item.radioEndpoint, channelId = item.channelId)
            is PlaylistItem -> SnapshotItem("playlist", item.id, item.title, item.thumbnail,
                listOfNotNull(item.author?.let { SnapshotArtist(it.name, it.id) }), count = item.songCountText,
                play = item.playEndpoint, shuffle = item.shuffleEndpoint, radio = item.radioEndpoint)
            else -> null
        }
    }
}

@Serializable
internal data class SnapshotFeatured(
    val id: String,
    val title: String,
    val subtitle: String?,
    val thumbnail: String?,
    val endpoint: BrowseEndpoint?,
    val play: WatchEndpoint?,
    val shuffle: WatchEndpoint?,
    val radio: WatchEndpoint?,
    val itemIds: List<String>,
) {
    fun card() = HomePage.Section.FeaturedCard(id, title, subtitle, thumbnail, endpoint, play, shuffle, radio, itemIds)
}

@Serializable
internal data class SnapshotSection(
    val title: String,
    val items: List<SnapshotItem>,
    val endpoint: BrowseEndpoint? = null,
    val label: String? = null,
    val thumbnail: String? = null,
    val featured: List<SnapshotFeatured> = emptyList(),
) {
    fun section() = HomePage.Section(title, label, thumbnail, endpoint, items.mapNotNull { it.item() }, featuredCards = featured.map { it.card() })
}

@Serializable
internal data class HomeSnapshot(
    val version: Int,
    val context: String,
    val writtenAt: Long,
    val sections: List<SnapshotSection>,
    val quickPicks: List<SnapshotItem>,
) {
    fun page() = HomePage(chips = null, sections = sections.map { it.section() })
    fun picks() = quickPicks.mapNotNull { it.item() }
}

/** A single bounded, atomic snapshot. Switching contexts destroys the previous snapshot. */
internal class HomeSnapshotDisk(private val file: File, private val now: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(context: String): HomeSnapshot? = mutex.withLock {
        try {
            if (!file.isFile) return@withLock null
            if (file.length() > MAX_BYTES) { file.delete(); return@withLock null }
            val snapshot = json.decodeFromString<HomeSnapshot>(file.readText())
            if (snapshot.version != SCHEMA_VERSION || snapshot.context != context || now() - snapshot.writtenAt !in 0..MAX_AGE_MS) {
                file.delete()
                return@withLock null
            }
            snapshot
        } catch (_: java.io.IOException) { null }
          catch (_: IllegalArgumentException) { file.delete(); null }
    }

    suspend fun write(context: String, page: HomePage?, picks: List<YTItem>) = mutex.withLock {
        val sections = page?.sections.orEmpty().take(20).map { section ->
            SnapshotSection(section.title, section.items.take(30).mapNotNull(SnapshotItem::from), section.endpoint, section.label, section.thumbnail,
                section.featuredCards.take(12).map { SnapshotFeatured(it.id, it.title, it.subtitle, it.thumbnail, it.endpoint, it.playEndpoint, it.shuffleEndpoint, it.radioEndpoint, it.itemIds.take(30)) })
        }.filter { it.items.isNotEmpty() || it.featured.isNotEmpty() }
        val snapshot = HomeSnapshot(version = SCHEMA_VERSION, context = context, writtenAt = now(), sections = sections,
            quickPicks = picks.take(20).mapNotNull(SnapshotItem::from))
        if (sections.isEmpty() && snapshot.quickPicks.isEmpty()) return@withLock
        val bytes = json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) return@withLock
        val temp = File(file.path + ".tmp")
        try {
            file.parentFile?.mkdirs()
            FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temp.delete() }
    }

    companion object {
        // Required in the file: a default value would not be written, so old layouts could not be recognised.
        const val SCHEMA_VERSION = 1
        const val MAX_BYTES = 1_048_576L
        const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
        fun contextKey(cookie: String?, channel: String?, country: String, language: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(listOf(cookie.orEmpty(), channel.orEmpty(), country, language).joinToString("\u0000").toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}

@Singleton
class HomeSnapshotRepository @Inject constructor(@ApplicationContext context: Context) {
    // cacheDir is excluded from Android backup and from the app's explicit library backups.
    private val disk = HomeSnapshotDisk(File(context.cacheDir, "home-v1.json"))
    internal suspend fun read(key: String) = withContext(Dispatchers.IO) { disk.read(key) }
    internal suspend fun write(key: String, page: HomePage?, picks: List<YTItem>) = withContext(Dispatchers.IO) {
        try { disk.write(key, page, picks) } catch (_: java.io.IOException) { /* Optional cache never fails a refresh. */ }
    }
}
