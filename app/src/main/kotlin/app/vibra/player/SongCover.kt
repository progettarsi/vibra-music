/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.player

import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  A MUSIC VIDEO'S OWN SONG COVER
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  A music video's picture is a frame of the video (often 16:9, sometimes with bars). Its
 *  song usually exists on YouTube Music as a track with the album's square cover: that cover
 *  is what the mini-player and the player show for the video. Found by searching the songs
 *  for the video's cleaned title and checking the artist; no match keeps the video's picture.
 *  One search per video, remembered for the session.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */
object SongCovers {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lookups = ConcurrentHashMap<String, Deferred<String?>>()

    /** The cover already found for [mediaId] (null if none, or not looked up yet). */
    fun cached(mediaId: String): String? = lookups[mediaId]?.takeIf { it.isCompleted && !it.isCancelled }?.getCompleted()

    /** The square song cover for the music video [metadata], or null. */
    suspend fun coverFor(metadata: MediaMetadata): String? {
        if (!metadata.isMusicVideo) return null
        return lookups.getOrPut(metadata.id) { scope.async { search(metadata) } }.await()
    }

    private suspend fun search(metadata: MediaMetadata): String? {
        val artists = metadata.artists.map { cleanChannelName(it.name) }.filter { it.isNotBlank() }
        val title = cleanVideoTitle(metadata.title, artists)
        if (title.isBlank()) return null
        val query = listOfNotNull(title, artists.firstOrNull()).joinToString(" ")
        val songs =
            YouTube
                .search(query, YouTube.SearchFilter.FILTER_SONG)
                .getOrNull()
                ?.items
                ?.filterIsInstance<SongItem>()
                .orEmpty()
        val match = songs.firstOrNull { songMatches(title, artists, it.title, it.artists.map { artist -> artist.name }) } ?: return null
        return match.thumbnail.resize(width = 1080, height = 1080, ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal)
    }
}

private val VideoDecoration =
    Regex(
        """\s*[\(\[][^)\]]*\b(official|video|audio|lyrics?|visuali[sz]er|mv|m/v|hd|4k|live|clip|performance)\b[^)\]]*[\)\]]""",
        RegexOption.IGNORE_CASE,
    )
private val TrailingDecoration = Regex("""\s*[-|–]\s*(official\s+)?(music\s+)?(video|audio|lyrics?\s*video|visuali[sz]er|mv)\s*$""", RegexOption.IGNORE_CASE)

/**
 * "Artist - Song (Official Music Video) [4K]" → "Song". The "Artist - " prefix goes only when it
 * names one of [artists] (a song may well have a dash of its own, "Song - 2011 Remaster").
 */
fun cleanVideoTitle(
    title: String,
    artists: List<String> = emptyList(),
): String {
    var clean = VideoDecoration.replace(title, "")
    clean = TrailingDecoration.replace(clean, "")
    val dash = Regex("""\s[-–]\s""").find(clean)
    if (dash != null) {
        val prefix = normalized(clean.substring(0, dash.range.first))
        val namesArtist = artists.map(::normalized).any { it.isNotEmpty() && (prefix.contains(it) || it.contains(prefix)) }
        if (prefix.isNotEmpty() && namesArtist) clean = clean.substring(dash.range.last + 1)
    }
    return clean.replace(Regex("""\s+"""), " ").trim().trim('"', '\'', '“', '”')
}

/** "ArtistVEVO" / "Artist - Topic" / "Artist Official" → "Artist". */
fun cleanChannelName(name: String): String =
    name
        .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""VEVO$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+Official$""", RegexOption.IGNORE_CASE), "")
        .trim()

/** Same song: equal titles once normalised (or one starting with the other) and at least one shared artist. */
fun songMatches(
    videoTitle: String,
    videoArtists: List<String>,
    songTitle: String,
    songArtists: List<String>,
): Boolean {
    val a = normalized(videoTitle)
    val b = normalized(cleanVideoTitle(songTitle))
    if (a.isEmpty() || b.isEmpty()) return false
    val titleMatches = a == b || a.startsWith(b) || b.startsWith(a)
    val artistMatches =
        videoArtists.isEmpty() ||
            videoArtists.map(::normalized).any { video ->
                songArtists.map(::normalized).any { song -> song.isNotEmpty() && (video.contains(song) || song.contains(video)) }
            }
    return titleMatches && artistMatches
}

private fun normalized(text: String): String =
    Normalizer
        .normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "")
        .replace(Regex("""[^\p{L}\p{N}]+"""), "")
