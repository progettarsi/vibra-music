/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.canvas

import androidx.datastore.preferences.core.booleanPreferencesKey

/** The Advanced-settings switch for the whole feature; on by default. Off: no lookup, no request, no video. */
val CanvasEnabledKey = booleanPreferencesKey("vibraCanvasEnabled")

/**
 * Which Canvas sources are switched on. All of them are third-party services that Vibra reaches
 * without an agreement; none is an official, guaranteed API (see docs/CANVAS_QA.md), so any of
 * them may stop answering without notice: that simply means no Canvas. Each is switched off here.
 */
object CanvasConfig {
    /** Reads the anonymous token Apple's web player gives every visitor. */
    const val APPLE_ENABLED = true

    /** Tidal's public embed token (the one its own embeddable player ships); null switches Tidal off. */
    val tidalEmbedToken: String? = "vNVdglQOjFJJGG2U"

    /** A plain JSON index; no credentials involved. */
    const val COMMUNITY_ENABLED = true
    const val COMMUNITY_MANIFEST = "https://vivimusicanvas.mkmdevilmi.workers.dev/canvas.json"

    /** Honest about who is asking; the catalogue hosts decide whether they answer. */
    const val USER_AGENT = "Vibra/1.4 (Android; motion artwork lookup)"

    /** Priority order: Apple, Tidal, community. */
    fun providers(http: CanvasHttp = OkHttpCanvasHttp): List<CanvasProvider> =
        buildList {
            if (APPLE_ENABLED) add(AppleMotionProvider(http, AppleWebTokenSource(http)))
            tidalEmbedToken?.let { add(TidalMotionProvider(http, it)) }
            if (COMMUNITY_ENABLED) add(CommunityMotionProvider(http))
        }
}
