/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 *
 * The normalisation idea comes from BitChord's CanvasArtwork.kt (GPL-3.0); this matcher is
 * deliberately stricter: a wrong clip is worse than none.
 */

package app.vibra.canvas

import java.text.Normalizer
import java.util.Locale

/**
 * Decides whether a provider's answer really is the track that is playing.
 *
 * Packaging that no catalogue shares ("Official Video", "(Lyrics)", "feat. X") is removed from
 * titles; everything that names a different recording (remix, live, remaster, acoustic,
 * "Taylor's Version", deluxe...) is kept, so those only match themselves.
 */
object CanvasMatcher {
    private val MARKS = Regex("\\p{M}+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}\\s]")
    private val SPACES = Regex("\\s+")
    private val BRACKETS = Regex("[(\\[]([^)\\]]*)[)\\]]")
    private val TRAILING_SEGMENT_SEPARATOR = Regex("\\s[-–—|]\\s")
    private val FEATURING = Regex("(?<=\\S)\\s+(?:feat|ft|featuring)\\b.*$")
    private val REMASTER = Regex("\\bremaster(?:ed)?\\b")
    private val YEAR = Regex("\\b(?:19|20)\\d{2}\\b")
    private val SINGLE_OR_EP = Regex("\\s[-–—]\\s(?:single|ep)\\s*$", RegexOption.IGNORE_CASE)
    private val ARTIST_SEPARATORS = Regex(
        "\\s*[,;&]\\s*|\\s+[x×]\\s+|\\s*\\b(?:feat|ft|featuring|with)\\b\\.?\\s*",
        RegexOption.IGNORE_CASE,
    )

    /** Words that make a bracket or a trailing segment pure packaging. */
    private val NOISE_WORDS = setOf(
        "official", "music", "video", "audio", "lyrics", "lyric", "visualizer", "visualiser",
        "hd", "hq", "4k", "1080p", "explicit", "clean", "full", "song", "mv", "clip", "with",
    )

    /** Case, accents and punctuation folded away; `&` read as "and"; apostrophes dropped. */
    fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace("'", "")
            .replace("’", "")
            .replace(NOT_WORD, " ")
            .replace(SPACES, " ")
            .trim()

    private fun isNoise(normalized: String): Boolean {
        val words = normalized.split(' ').filter { it.isNotBlank() }
        return words.isNotEmpty() && words.all { it in NOISE_WORDS }
    }

    /** The title as catalogues share it: packaging and featured credits gone, versions kept. */
    fun cleanTitle(raw: String): String {
        var text = BRACKETS.replace(raw) { match ->
            val inner = normalize(match.groupValues[1])
            when {
                inner.isBlank() -> " "
                isNoise(inner) -> " "
                inner.startsWith("feat ") || inner.startsWith("ft ") || inner.startsWith("featuring ") -> " "
                else -> " $inner "
            }
        }
        val segments = text.split(TRAILING_SEGMENT_SEPARATOR)
        if (segments.size > 1) {
            text = segments.filterIndexed { index, segment -> index == 0 || !isNoise(normalize(segment)) }
                .joinToString(" ")
        }
        var normalized = normalize(text).replace(FEATURING, "").trim()
        if (REMASTER.containsMatchIn(normalized)) {
            // "Remastered 2011" and "2011 Remaster" are one edition.
            normalized = normalized.replace(REMASTER, " remaster ").replace(YEAR, " ")
                .replace(SPACES, " ").trim()
        }
        return normalized
    }

    /** One credit string into its normalised artists, in credit order. */
    fun splitArtists(raw: String): List<String> =
        raw.replace('(', ' ').replace(')', ' ').replace('[', ' ').replace(']', ' ')
            .split(ARTIST_SEPARATORS)
            .map(::normalize)
            .filter { it.isNotBlank() }
            .distinct()

    fun artistSet(credits: List<String>): List<String> =
        credits.flatMap(::splitArtists).distinct()

    fun titleMatches(want: String, got: String): Boolean {
        val a = cleanTitle(want)
        return a.isNotBlank() && a == cleanTitle(got)
    }

    /**
     * Both lead artists must be present on both sides, and the shorter credit list must sit
     * inside the longer: "Beyoncé" is part of "Beyoncé & JAY-Z", "Pharrell" is not "Daft Punk".
     */
    fun artistsMatch(want: List<String>, got: List<String>): Boolean {
        if (want.isEmpty() || got.isEmpty()) return false
        if (want.first() !in got || got.first() !in want) return false
        val (small, big) = if (want.size <= got.size) want to got else got to want
        return small.all { it in big }
    }

    private fun albumKey(raw: String): String = normalize(SINGLE_OR_EP.replace(raw, ""))

    /** Unknown on either side is not a mismatch; two known albums must be the same edition. */
    fun albumMatches(want: String?, got: String?): Boolean {
        val a = want?.let(::albumKey).orEmpty()
        val b = got?.let(::albumKey).orEmpty()
        return a.isBlank() || b.isBlank() || a == b
    }

    /** Whether [query] can be searched at all. */
    fun isIdentifiable(query: CanvasQuery): Boolean =
        cleanTitle(query.title).isNotBlank() && artistSet(query.artists).isNotEmpty()

    fun accepts(query: CanvasQuery, candidate: CanvasCandidate): Boolean {
        val title = candidate.title ?: return false
        val artist = candidate.artist ?: return false
        return titleMatches(query.title, title) &&
            artistsMatch(artistSet(query.artists), splitArtists(artist)) &&
            albumMatches(query.album, candidate.album)
    }

    /** The identity of the track for caching: the id plus what was searched, never the album. */
    fun identity(query: CanvasQuery): String =
        "${query.mediaId}|${cleanTitle(query.title)}|${artistSet(query.artists).firstOrNull().orEmpty()}"
}
