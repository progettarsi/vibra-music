/*
 * Vibra (2026) - GPL-3.0 License.
 * Derived from ArchiveTune c Rukamori (github.com/rukamori).
 */
package moe.rukamori.archivetune.home

import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.HomePage

typealias HomeSection = HomePage.Section

/** Pure exposure policy: fetched items are reserved for this session; UI confirms actual exposure. */
class HomeFeedExposure(private val cooldownMs: Long = 14L * 24 * 60 * 60 * 1_000) {
    private val session = mutableSetOf<String>()
    private val lastShownAt = mutableMapOf<String, Long>()

    @Synchronized
    fun restore(history: Map<String, Long>) { history.forEach { (key, time) -> lastShownAt[key] = maxOf(lastShownAt[key] ?: Long.MIN_VALUE, time) } }

    @Synchronized
    fun reserve(keys: Collection<String>) { session.addAll(keys) }

    @Synchronized
    fun accept(keys: Collection<String>, now: Long): Set<String> = buildSet {
        keys.forEach { key ->
            val shown = lastShownAt[key]
            if (key !in session && (shown == null || (now >= shown && now - shown >= cooldownMs))) {
                add(key)
                session.add(key)
            }
        }
    }

    @Synchronized
    fun markShown(keys: Collection<String>, now: Long) {
        keys.forEach { lastShownAt[it] = maxOf(lastShownAt[it] ?: Long.MIN_VALUE, now) }
        session.addAll(keys)
    }

    companion object {
        /** Type matters: artist, song and playlist IDs are independent namespaces. */
        fun key(item: YTItem): String {
            val type = when (item) {
                is moe.rukamori.archivetune.innertube.models.SongItem -> "song"
                is moe.rukamori.archivetune.innertube.models.AlbumItem -> "album"
                is moe.rukamori.archivetune.innertube.models.ArtistItem -> "artist"
                is moe.rukamori.archivetune.innertube.models.PlaylistItem -> "playlist"
                is moe.rukamori.archivetune.innertube.models.PodcastItem -> "podcast"
                is moe.rukamori.archivetune.innertube.models.EpisodeItem -> "episode"
                else -> "item"
            }
            return "$type:${item.id}"
        }
    }
}
