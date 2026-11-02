/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Measurements from the approved three-card action sheets (PDF pages 1–6). */
object MenuTokens {
  const val NaturalSheetOpeningLimit = 0.60f
  val SheetHandleHeight = 38.dp
  val Gutter = 20.dp
  val CardRadius = 32.dp
  val ExpandedGap = 10.dp
  val CompactGap = 10.dp
  val ExpandedArtwork = 104.dp
  val CompactArtwork = 56.dp
  val ExpandedTitle = 22.sp
  val CompactTitle = 20.sp
  val RowText = 16.sp
  val ExpandedRow = 54.dp
  val CompactRow = 48.dp
  val Icon = 24.dp
}

/** Measurement at the opening limit means more content may be below the initial viewport. */
internal fun sheetOpeningLimitReached(measuredHeightPx: Int, windowHeightPx: Int): Boolean =
  measuredHeightPx > 0 &&
    measuredHeightPx >= windowHeightPx * MenuTokens.NaturalSheetOpeningLimit - 1f

/** Stable identities, independent of translations and current download labels. */
enum class MenuActionId {
  Other,
  Play,
  Shuffle,
  PlayNext,
  Enqueue,
  Playlist,
  Download,
  Radio,
  Share,
  Edit,
  Search,
  Refresh,
  Translate,
  LyricsSync,
}

enum class MediaMenuKind {
  Song,
  Album,
  Playlist,
}

/** Metadata can be missing on local media; never leave an orphan separator. */
fun menuSubtitle(vararg parts: String?): String =
  parts.filterNotNull().filter(String::isNotBlank).joinToString(" · ")

/** Some audio drivers expose NUL-terminated preset names; keep terminators out of accessibility. */
fun menuDisplayText(value: String): String = value.substringBefore('\u0000')

fun quickActionIds(kind: MediaMenuKind): List<MenuActionId> =
  when (kind) {
    MediaMenuKind.Song ->
      listOf(
        MenuActionId.PlayNext,
        MenuActionId.Enqueue,
        MenuActionId.Playlist,
        MenuActionId.Download,
      )
    MediaMenuKind.Album ->
      listOf(
        MenuActionId.Shuffle,
        MenuActionId.PlayNext,
        MenuActionId.Enqueue,
        MenuActionId.Download,
      )
    MediaMenuKind.Playlist ->
      listOf(MenuActionId.Shuffle, MenuActionId.Radio, MenuActionId.Enqueue, MenuActionId.Download)
  }

/** The deepest overlay wins; a later sibling replaces an earlier one at equal depth. */
internal fun activePopupToken(layers: List<Pair<Any, Int>>): Any? =
  layers
    .withIndex()
    .maxWithOrNull(compareBy<IndexedValue<Pair<Any, Int>>> { it.value.second }.thenBy { it.index })
    ?.value
    ?.first
