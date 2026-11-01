/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 *
 * The community index and its shape follow BitChord's CommunityCanvas.kt
 * (github.com/kushagrasinghx/BitChord, GPL-3.0).
 */

package app.vibra.canvas

import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class CommunityEntry(
    val song: String,
    val artist: String,
    val album: String,
    val url: String,
) {
    val titleKey: String = CanvasMatcher.cleanTitle(song)
    val artists: List<String> = CanvasMatcher.splitArtists(artist)
}

/**
 * A community-curated `song + artist → looping video` index published as one JSON file. The file
 * is untrusted: it is fetched once per [ttlMs], every entry is validated (schema, lengths, HTTPS
 * public host, playable container), and a chosen clip must still answer before it is offered.
 * Matching is exact on title, lead artist and — when both sides know it — album.
 */
class CommunityMotionProvider(
    private val http: CanvasHttp,
    private val manifestUrl: String = CanvasConfig.COMMUNITY_MANIFEST,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = 30L * 60 * 1000,
    private val failureBackoffMs: Long = 2L * 60 * 1000,
) : CanvasProvider {
    override val source = CanvasSource.COMMUNITY

    private val mutex = Mutex()
    private var entries: List<CommunityEntry> = emptyList()
    private var fetchedAt = 0L
    private var failedAt = -1L

    override suspend fun find(query: CanvasQuery): CanvasCandidate? {
        val index = index()
        val title = CanvasMatcher.cleanTitle(query.title)
        val wanted = CanvasMatcher.artistSet(query.artists)
        var probed = 0
        for (entry in index) {
            if (entry.titleKey != title || !CanvasMatcher.artistsMatch(wanted, entry.artists)) continue
            if (!CanvasMatcher.albumMatches(query.album, entry.album)) continue
            if (probed++ >= MAX_PROBES) break
            if (!http.reachable(entry.url)) continue
            return CanvasCandidate(entry.url, null, entry.song, entry.artist, entry.album.takeIf { it.isNotBlank() })
        }
        return null
    }

    /** The index, downloaded at most once per [ttlMs]; a failed refresh keeps serving the old one. */
    private suspend fun index(): List<CommunityEntry> = mutex.withLock {
        val now = clock()
        if (entries.isNotEmpty() && now - fetchedAt < ttlMs) return entries
        if (failedAt >= 0 && now - failedAt < failureBackoffMs) {
            if (entries.isNotEmpty()) return entries
            throw IOException("Community index unavailable")
        }
        val parsed = try {
            val response = http.get(manifestUrl, mapOf("User-Agent" to CanvasConfig.USER_AGENT))
            if (!response.isSuccessful) throw IOException("Community index answered ${response.code}")
            parse(response.body.orEmpty())
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failedAt = now
            if (entries.isNotEmpty()) return entries
            throw failure as? IOException ?: IOException("Community index unusable")
        }
        if (parsed.isEmpty()) {
            failedAt = now
            return entries
        }
        entries = parsed
        fetchedAt = now
        entries
    }

    companion object {
        private const val MAX_ENTRIES = 50_000
        private const val MAX_FIELD = 300
        private const val MAX_PROBES = 3
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Valid entries of [body]; anything with the wrong shape is skipped, never trusted. */
        internal fun parse(body: String): List<CommunityEntry> {
            val items = runCatching { JSON.parseToJsonElement(body).jsonObject["items"]?.jsonArray }
                .getOrNull() ?: return emptyList()
            val result = ArrayList<CommunityEntry>(minOf(items.size, MAX_ENTRIES))
            for (item in items) {
                if (result.size >= MAX_ENTRIES) break
                val obj = item as? JsonObject ?: continue
                val song = obj.text("song") ?: continue
                val artist = obj.text("artist") ?: continue
                val url = obj.text("url") ?: continue
                if (!CanvasUrls.isAcceptable(url)) continue
                result += CommunityEntry(song, artist, obj.text("album").orEmpty(), url)
            }
            return result
        }

        private fun JsonObject.text(key: String): String? =
            runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
                ?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_FIELD }
    }
}
