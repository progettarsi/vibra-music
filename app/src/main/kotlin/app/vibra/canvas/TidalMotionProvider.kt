/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 *
 * The track → album → `videoCover` route follows BitChord's TidalCanvas.kt
 * (github.com/kushagrasinghx/BitChord, GPL-3.0). Unofficial: see CanvasConfig.
 */

package app.vibra.canvas

import java.io.IOException
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Tidal's square "video cover". A track search returns each track's album; the album carries a
 * `videoCover` id that expands to a fixed URL on Tidal's CDN. The track and album are verified
 * with [CanvasMatcher] before the id is trusted, and an id that is not a UUID is not addressed.
 * [embedToken] is Tidal's public embed token; it is supplied by [CanvasConfig], never stored here.
 */
class TidalMotionProvider(
    private val http: CanvasHttp,
    private val embedToken: String,
    private val countryCode: String = defaultCountry(),
) : CanvasProvider {
    override val source = CanvasSource.TIDAL

    override suspend fun find(query: CanvasQuery): CanvasCandidate? {
        val artist = query.artists.first()
        val q = listOfNotNull(query.album?.takeIf { it.isNotBlank() }, artist, query.title).joinToString(" ")
        val url =
            SEARCH.toHttpUrl().newBuilder()
                .addQueryParameter("query", q)
                .addQueryParameter("limit", "10")
                .addQueryParameter("types", "TRACKS")
                .addQueryParameter("countryCode", countryCode)
                .build().toString()
        val response = http.get(url, mapOf("X-Tidal-Token" to embedToken, "User-Agent" to CanvasConfig.USER_AGENT))
        if (response.code == 404) return null
        if (!response.isSuccessful) throw IOException("Catalogue answered ${response.code}")
        return parse(response.body.orEmpty(), query)
    }

    companion object {
        private const val SEARCH = "https://api.tidal.com/v1/search"
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
        private val UUID = Regex("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$")

        fun defaultCountry(): String =
            Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "US"

        /**
         * The first track that really is [query], on an album that really has a cover. The right
         * track is often not the first result, so a hit without a cover does not end the search.
         */
        internal fun parse(body: String, query: CanvasQuery): CanvasCandidate? {
            val items = runCatching { JSON.parseToJsonElement(body).jsonObject["tracks"]?.jsonObject?.get("items")?.jsonArray }
                .getOrNull() ?: return null
            for (item in items) {
                val track = item as? JsonObject ?: continue
                val title = track["title"]?.jsonPrimitive?.contentOrNull ?: continue
                val artists = runCatching {
                    track["artists"]?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                }.getOrNull().orEmpty()
                val album = track["album"] as? JsonObject
                val albumTitle = album?.get("title")?.jsonPrimitive?.contentOrNull
                val identity = CanvasCandidate("", null, title, artists.joinToString(", "), albumTitle)
                if (artists.isEmpty() || !CanvasMatcher.accepts(query, identity)) continue
                val cover = album?.get("videoCover")?.jsonPrimitive?.contentOrNull
                val url = cover?.let(::coverUrl) ?: continue
                return identity.copy(url = url)
            }
            return null
        }

        /** A cover id is a UUID whose five groups spell its path on the CDN; anything else is unknown. */
        internal fun coverUrl(id: String): String? {
            if (!UUID.matches(id)) return null
            return "https://resources.tidal.com/videos/${id.lowercase(Locale.ROOT).split("-").joinToString("/")}/1280x1280.mp4"
        }
    }
}
