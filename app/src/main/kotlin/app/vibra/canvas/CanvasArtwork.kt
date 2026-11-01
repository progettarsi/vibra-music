/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 *
 * Canvas / motion artwork: the model is modelled on BitChord's CanvasArtwork
 * (github.com/kushagrasinghx/BitChord, GPL-3.0), rewritten for Vibra.
 */

package app.vibra.canvas

/** The catalogue a clip came from. Informational: nothing in the UI depends on it. */
enum class CanvasSource { APPLE, TIDAL, COMMUNITY }

/** What is playing, reduced to what a lookup needs. Built from MediaMetadata (see toCanvasQuery). */
data class CanvasQuery(
    val mediaId: String,
    val title: String,
    val artists: List<String>,
    val album: String? = null,
)

/** A provider's raw answer. It is verified against the [CanvasQuery] before it becomes a [CanvasArtwork]. */
data class CanvasCandidate(
    val url: String,
    val fallbackUrl: String? = null,
    val title: String?,
    val artist: String?,
    val album: String?,
    /** The same clip framed 3:4, when the catalogue has one: the phone player runs it under the whole screen. */
    val portraitUrl: String? = null,
)

/**
 * A short looping clip that stands in for the cover of the track [mediaId].
 *
 * [mediaId] is part of the value on purpose: two tracks of one album share a cover URL but not
 * necessarily a clip, so the presentation layer keys on this and never on the cover.
 * [fallbackUrl] is a second rendition tried once if [url] will not play.
 */
data class CanvasArtwork(
    val mediaId: String,
    val url: String,
    val fallbackUrl: String? = null,
    val source: CanvasSource,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    /** The vertical (3:4) rendition. When present the player shows the clip full screen, not inside the cover. */
    val portraitUrl: String? = null,
) {
    /**
     * What to play. Full screen takes the vertical rendition alone: a square one stretched over a
     * phone screen would be cropped to a sliver. The cover takes [url] and its alternate.
     */
    fun playbackUrls(portrait: Boolean): List<String> =
        if (portrait && portraitUrl != null) listOf(portraitUrl) else listOfNotNull(url, fallbackUrl)
}
