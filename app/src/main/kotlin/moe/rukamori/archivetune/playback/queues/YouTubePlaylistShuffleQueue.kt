/*
 * Vibra (2026) — shuffle the whole online playlist with bounded startup work.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package moe.rukamori.archivetune.playback.queues

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.MediaMetadata
import java.io.IOException
import kotlin.random.Random

/** Loads at most 20 pages in 10 seconds, then starts with the collected songs. */
internal class YouTubePlaylistShuffleQueue(private val playlistId: String) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        val result = loadShuffledPlaylist(
            first = {
                val page = YouTube.playlist(playlistId).getOrThrow()
                ShufflePlaylistPage(page.playlist.title, page.songs,
                    page.songsContinuation?.takeIf { it.isNotBlank() } ?: page.continuation)
            },
            next = { token ->
                val page = YouTube.playlistContinuation(token, playlistId).getOrThrow()
                ShufflePlaylistPage(null, page.songs, page.continuation)
            },
            itemId = { it.setVideoId?.takeIf(String::isNotBlank)?.let { entry -> "entry:$entry" } ?: "video:${it.id}" },
        )
        Queue.Status(result.title, result.items.map { it.toMediaItem() }, 0)
    }

    override fun hasNextPage(): Boolean = false
    override suspend fun nextPage(): List<MediaItem> = throw UnsupportedOperationException()

    companion object {
        fun create(playlistId: String, endpoint: WatchEndpoint): Queue {
            val normalized = playlistId.removePrefix("VL")
            // Radio/mix continuations are generated recommendations, not finite playlists.
            return if (normalized.startsWith("RD")) YouTubeQueue.playlist(endpoint)
                else YouTubePlaylistShuffleQueue(normalized)
        }
    }
}

internal data class ShufflePlaylistPage<T>(val title: String?, val items: List<T>, val continuation: String?)
internal data class ShuffledPlaylist<T>(val title: String?, val items: List<T>, val limited: Boolean)

/** Keeps completed pages when our deadline expires; caller cancellation still propagates. */
internal suspend fun <T> loadShuffledPlaylist(
    first: suspend () -> ShufflePlaylistPage<T>,
    next: suspend (String) -> ShufflePlaylistPage<T>,
    itemId: (T) -> String,
    maxPages: Int = 20,
    timeoutMs: Long = 10_000L,
    random: Random = Random.Default,
): ShuffledPlaylist<T> {
    require(maxPages > 0 && timeoutMs > 0)
    val items = linkedMapOf<String, T>()
    val visited = mutableSetOf<String>()
    var title: String? = null
    var loaded = 0
    var continuation: String? = null
    val completed = withTimeoutOrNull(timeoutMs) {
        var page = first()
        title = page.title
        while (true) {
            page.items.forEach { items.putIfAbsent(itemId(it), it) }
            loaded++
            continuation = page.continuation?.takeIf { it.isNotBlank() }
            val token = continuation ?: break
            if (loaded >= maxPages || !visited.add(token)) break
            // A later page failing still starts the shuffle with the songs collected so far.
            page = try {
                next(token)
            } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                break
            }
        }
        true
    } == true
    if (loaded == 0) throw IOException("Playlist loading timed out")
    return ShuffledPlaylist(title, items.values.shuffled(random), !completed || continuation != null)
}
