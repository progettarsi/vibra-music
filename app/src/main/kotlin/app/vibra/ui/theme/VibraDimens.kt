/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.vibra.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing tokens measured from the Figma frames (402 × 874 pt artboards,
 * which map 1:1 to dp on a typical 400–412 dp wide phone).
 */
object VibraDimens {
    /** Horizontal page gutter: every card starts at x = 20 (layout grid 20/362/20). */
    val Gutter = 20.dp

    /** Vertical gap between home cards (homepageContent auto-layout gap = 10). */
    val CardGap = 10.dp

    /** Inner padding of cards (song rows sit at 18 dp ≈ 4.98 % of 362). */
    val CardPadding = 18.dp

    /** Gap between song rows inside cards and lists (Canzone stack: 67.5 → 77.7). */
    val RowGap = 10.dp

    /** Height of the song row ("Canzone", 67.54). */
    val RowHeight = 68.dp

    /** Artwork thumbnail inside a song row (13.33 % insets of 75 → 50). */
    val RowThumbnail = 50.dp

    /** Floating dock height (dock symbol 55.46). */
    val DockHeight = 56.dp

    /** Gap between dock pills (profile 0–15.3 %, player 17.96 %, search 84.96 % of 362). */
    val DockGap = 9.dp

    /** Space between the dock and the bottom system inset (874 − 853.46 = 20.5). */
    val DockBottomMargin = 20.dp

    /** Small round icon buttons in action rows (49.6) and the player add button (40). */
    val ActionButton = 50.dp

    /** Large primary action height (Play button 66 in Figma, M3 "medium-large"). */
    val PrimaryActionHeight = 64.dp

    /** Cookie play button of the full player (Union 95.16). */
    val PlayerPlayButton = 96.dp

    /** Progress line thickness (thin M3 Expressive slider, "playing menu MD3"). */
    val ProgressTrack = 4.dp
}

/** Corner radii and expressive shapes used by the design. */
object VibraShapes {
    /** Home/profile cards ("Rectangle" bg, radius 32). */
    val Card: Shape = RoundedCornerShape(32.dp)

    /** Nested artwork inside cards (cover cards 22–24). */
    val InnerArtwork: Shape = RoundedCornerShape(24.dp)

    /** Song row container (radius 24). */
    val Row: Shape = RoundedCornerShape(24.dp)

    /** Song row thumbnail (radius 14). */
    val RowThumbnail: Shape = RoundedCornerShape(14.dp)

    /** Album hero cover. */
    val AlbumCover: Shape = RoundedCornerShape(28.dp)

    /** Bottom sheets / list containers ("Rectangle 18" with top corners 32). */
    val SheetTop: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    /** Dock pills are full stadiums. */
    val Pill: Shape = CircleShape

    /** Centred dialogs (same radius as Material's AlertDialog, so both families match). */
    val Dialog: Shape = RoundedCornerShape(28.dp)

    /** Anchored dropdown menus. */
    val Menu: Shape = RoundedCornerShape(24.dp)

    /** Groups of rows inside a sheet (action menus, pickers). */
    val SheetGroup: Shape = RoundedCornerShape(24.dp)

    // ── M3 Expressive shape set (Figma "Shape Set" instances) ──────────────────────
    // MaterialShapes.toShape() is composable (it caches the outline per composition).

    /** Dock / mini-player play button and the full player's play button. */
    val Cookie12: Shape @Composable get() = MaterialShapes.Cookie12Sided.toShape()

    /** "For you" genre covers and favourites artwork. */
    val Cookie9: Shape @Composable get() = MaterialShapes.Cookie9Sided.toShape()

    /** Playlist hero cover (soft pentagon). */
    val Pentagon: Shape @Composable get() = MaterialShapes.Pentagon.toShape()

    /** Profile picture frame (flower). */
    val Flower: Shape @Composable get() = MaterialShapes.Flower.toShape()

    /** Playlist card artwork (soft burst). */
    val SoftBurst: Shape @Composable get() = MaterialShapes.SoftBurst.toShape()

    /** "For you" large cover blob. */
    val Sunny: Shape @Composable get() = MaterialShapes.Sunny.toShape()
}
