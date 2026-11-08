/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.vibra.ui.theme

import androidx.compose.foundation.shape.CircleShape
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

    /** Gap between song rows inside cards and lists (Canzone stack: 67.5 → 77.7). */
    val RowGap = 10.dp

    /** Height of the song row ("Canzone", 67.54). */
    val RowHeight = 68.dp

    /** Artwork thumbnail with exact 8 dp margins in a 68 dp song row. */
    val RowThumbnail = RowHeight - 16.dp

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
    val Card: Shape @Composable get() = LocalVibraRadii.current.cardShape

    /** Nested artwork inside cards (cover cards 22–24). */
    val InnerArtwork: Shape @Composable get() = LocalVibraRadii.current.innerArtworkShape

    /** Song row container (radius 24). */
    val Row: Shape @Composable get() = LocalVibraRowShapes.current?.container ?: LocalVibraRadii.current.rowShapes.container

    /** Song row thumbnail: the row radius minus its 8 dp inset. */
    val RowThumbnail: Shape @Composable get() = LocalVibraRowShapes.current?.thumbnail ?: LocalVibraRadii.current.rowShapes.thumbnail

    /** Album hero cover. */
    val AlbumCover: Shape @Composable get() = LocalVibraRadii.current.albumCoverShape

    /** Bottom sheets / list containers ("Rectangle 18" with top corners 32). */
    val SheetTop: Shape @Composable get() = LocalVibraRadii.current.sheetShape

    /** Dock pills are full stadiums. */
    val Pill: Shape = CircleShape

    /** Centred dialogs (same radius as Material's AlertDialog, so both families match). */
    val Dialog: Shape @Composable get() = LocalVibraRadii.current.cardShape

    /** Anchored dropdown menus. */
    val Menu: Shape @Composable get() = LocalVibraRadii.current.menuShape

    /** Groups of rows inside a sheet (action menus, pickers). */
    val SheetGroup: Shape @Composable get() = LocalVibraRadii.current.cardShape

    // ── M3 Expressive shape set (Figma "Shape Set" instances) ──────────────────────
    // MaterialShapes.toShape() is composable (it caches the outline per composition).

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
