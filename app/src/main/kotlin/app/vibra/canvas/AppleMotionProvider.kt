/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 *
 * Apple's `editorialVideo` lookup and the web-player token scrape follow BitChord's
 * AppleMusicCanvas.kt (github.com/kushagrasinghx/BitChord, GPL-3.0). Unofficial: see CanvasConfig.
 */

package app.vibra.canvas

import java.io.IOException
import java.util.Base64
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Where the catalogue bearer token comes from; replaced by a fake in tests. */
interface AppleTokenSource {
    suspend fun token(): String?

    /** The catalogue turned this token down. */
    fun reject(token: String)
}

/** The renditions of one clip: the preferred one, an alternate, and the vertical (3:4) one if there is one. */
internal data class AppleMotion(val primary: String, val alternate: String?, val portrait: String? = null)

internal data class AppleSong(
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String?,
    val motion: AppleMotion?,
)

/**
 * Apple Music motion artwork. The catalogue is searched for the song, the hit is verified with
 * [CanvasMatcher] (title, lead artist, album), and only then is its album's `editorialVideo` read.
 * The square rendition is preferred; the others follow as the fallback. Clips are HLS.
 */
class AppleMotionProvider(
    private val http: CanvasHttp,
    private val tokens: AppleTokenSource,
    private val storefront: String = defaultStorefront(),
) : CanvasProvider {
    override val source = CanvasSource.APPLE

    override suspend fun find(query: CanvasQuery): CanvasCandidate? {
        val token = tokens.token() ?: return null
        val artist = query.artists.first()
        val term =
            buildString {
                if (!query.title.contains(artist, ignoreCase = true)) append(artist).append(' ')
                append(query.title)
                query.album?.takeIf { it.isNotBlank() && !query.title.contains(it, ignoreCase = true) }
                    ?.let { append(' ').append(it) }
            }
        val url =
            "$AMP/$storefront/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", term)
                .addQueryParameter("types", "songs")
                .addQueryParameter("limit", "10")
                .addQueryParameter("extend", "editorialVideo")
                .addQueryParameter("include", "albums")
                .build().toString()
        val body = authorized(url, token) ?: return null
        for (song in parseSongs(body)) {
            if (isCompilation(song.album)) continue
            if (!CanvasMatcher.accepts(query, CanvasCandidate("", null, song.title, song.artist, song.album))) continue
            song.motion?.let { return candidate(it, song) }
            val albumId = song.albumId ?: continue
            fetchAlbumMotion(albumId, song.album, token)?.let { return candidate(it, song) }
        }
        return null
    }

    private fun candidate(motion: AppleMotion, song: AppleSong) =
        CanvasCandidate(motion.primary, motion.alternate, song.title, song.artist, song.album, motion.portrait)

    private suspend fun fetchAlbumMotion(albumId: String, expectedAlbum: String, token: String): AppleMotion? {
        val url =
            "$AMP/$storefront/albums/$albumId".toHttpUrl().newBuilder()
                .addQueryParameter("extend", "editorialVideo").build().toString()
        val body = authorized(url, token) ?: return null
        val attributes = runCatching {
            JSON.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("attributes")?.jsonObject
        }.getOrNull() ?: return null
        val name = attributes["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        // The clip belongs to the album: it must be the album the song was matched on.
        if (isCompilation(name) || !CanvasMatcher.albumMatches(expectedAlbum, name)) return null
        return motionOf(attributes["editorialVideo"] as? JsonObject)
    }

    /** 2xx body; null for a clean "not found"; [IOException] for anything that says "could not ask". */
    private suspend fun authorized(url: String, token: String): String? {
        val response = http.get(url, headers(token))
        return when {
            response.isSuccessful -> response.body
            response.code == 404 -> null
            response.code == 401 -> {
                tokens.reject(token)
                throw IOException("Catalogue token rejected")
            }
            else -> throw IOException("Catalogue answered ${response.code}")
        }
    }

    private fun headers(token: String) =
        mapOf(
            "Authorization" to "Bearer $token",
            "Origin" to "https://music.apple.com",
            "Referer" to "https://music.apple.com/",
            "User-Agent" to CanvasConfig.USER_AGENT,
        )

    companion object {
        private const val AMP = "https://amp-api.music.apple.com/v1/catalog"
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
        private val COMPILATION_MARKERS =
            listOf("playlist", "set list", "essentials", "dj mix", "mixed", "apple music", "today's hits", "session")

        fun defaultStorefront(): String =
            Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: "us"

        /** Editorial playlists and radio mixes carry motion artwork of their own, never a track's. */
        internal fun isCompilation(name: String): Boolean {
            val lower = name.lowercase(Locale.ROOT)
            return COMPILATION_MARKERS.any { lower.contains(it) }
        }

        internal fun parseSongs(body: String): List<AppleSong> {
            val data = runCatching {
                JSON.parseToJsonElement(body).jsonObject["results"]?.jsonObject
                    ?.get("songs")?.jsonObject?.get("data")?.jsonArray
            }.getOrNull() ?: return emptyList()
            return data.mapNotNull { element ->
                val song = element as? JsonObject ?: return@mapNotNull null
                val attributes = song["attributes"] as? JsonObject ?: return@mapNotNull null
                AppleSong(
                    title = attributes.string("name") ?: return@mapNotNull null,
                    artist = attributes.string("artistName") ?: return@mapNotNull null,
                    album = attributes.string("albumName").orEmpty(),
                    albumId = albumId(song, attributes),
                    motion = motionOf(attributes["editorialVideo"] as? JsonObject),
                )
            }
        }

        private fun JsonObject.string(key: String) =
            (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

        private fun albumId(song: JsonObject, attributes: JsonObject): String? {
            val related = runCatching {
                song["relationships"]?.jsonObject?.get("albums")?.jsonObject?.get("data")?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            if (related != null) return related.takeUnless { it.startsWith("pl.") }
            // The web URL always ends in the album id: .../album/<slug>/<id>?i=<song id>
            return attributes.string("url")?.substringAfter("/album/", "")?.substringBefore("?")
                ?.substringAfterLast("/")?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
        }

        private fun link(video: JsonObject, key: String): String? =
            (video[key] as? JsonObject)?.let { asset ->
                asset.string("video") ?: asset.string("videoUrl") ?: asset.string("hlsUrl") ?: asset.string("url")
            }?.takeIf { it.isNotBlank() }

        /** Square first (fills the cover without cropping), then the raw and tall renditions. */
        internal fun motionOf(video: JsonObject?): AppleMotion? {
            video ?: return null
            val square = link(video, "motionDetailSquare") ?: link(video, "motionSquareVideo1x1")
            val raw = link(video, "motionDetailRaw")
            val tall = link(video, "motionDetailTall") ?: link(video, "motionTallVideo3x4")
            val primary = square ?: raw ?: tall ?: return null
            return AppleMotion(primary, listOfNotNull(square, raw, tall).firstOrNull { it != primary }, tall)
        }
    }
}

/**
 * The anonymous token Apple's web player hands to every visitor, found the way the browser finds
 * it: load the player page, find its script bundle, pick the player's own JWT out of it. It
 * rotates, so a failed scrape backs off instead of retrying for every track.
 */
class AppleWebTokenSource(
    private val http: CanvasHttp,
    private val clock: () -> Long = System::currentTimeMillis,
) : AppleTokenSource {
    private val mutex = Mutex()
    private var cached: String? = null
    private var expiresAt = 0L
    private var retryAfter = 0L
    private val rejected = HashSet<String>()

    override suspend fun token(): String? = mutex.withLock {
        val now = clock()
        cached?.let { if (now < expiresAt - 60_000) return it }
        if (now < retryAfter) return null
        val headers = mapOf("User-Agent" to CanvasConfig.USER_AGENT)
        val html = runCatching { http.get(WEB_PLAYER, headers).body }.getOrNull()
        for (path in html?.let(::bundlePaths).orEmpty()) {
            val script = runCatching { http.get("https://music.apple.com$path", headers).body }.getOrNull() ?: continue
            val found = tokensIn(script, now).filter { it.first !in rejected }
            val (jwt, exp) = found.firstOrNull { isWebPlayerToken(it.first) } ?: continue
            cached = jwt
            expiresAt = exp
            return jwt
        }
        retryAfter = now + RETRY_MS
        null
    }

    override fun reject(token: String) {
        rejected += token
        if (cached == token) {
            cached = null
            expiresAt = 0L
        }
    }

    companion object {
        private const val WEB_PLAYER = "https://music.apple.com/us/browse"
        private const val RETRY_MS = 30L * 60 * 1000
        private val BUNDLE = Regex("""/assets/index(?:-legacy)?[~-][A-Za-z0-9_-]+\.js""")
        private val JWT = Regex("""ey[A-Za-z0-9_-]+\.ey[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
        private val EXP = Regex("\"exp\"\\s*:\\s*(\\d+)")

        internal fun bundlePaths(html: String): List<String> =
            BUNDLE.findAll(html).map { it.value }.distinct().toList()

        /** JWTs in [script] that have not expired at [now], with their expiry in millis. */
        internal fun tokensIn(script: String, now: Long): List<Pair<String, Long>> =
            JWT.findAll(script).map { it.value }.distinct()
                .mapNotNull { jwt -> expiry(jwt)?.let { jwt to it } }
                .filter { it.second > now }.toList()

        internal fun isWebPlayerToken(jwt: String): Boolean = runCatching {
            val parts = jwt.split(".")
            val header = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8)
            val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            header.contains("WebPlayKid") || payload.contains("AMPWebPlay")
        }.getOrDefault(false)

        private fun expiry(jwt: String): Long? = runCatching {
            val payload = String(Base64.getUrlDecoder().decode(jwt.split(".")[1]), Charsets.UTF_8)
            EXP.find(payload)?.groupValues?.get(1)?.toLong()?.times(1000)
        }.getOrNull()
    }
}
