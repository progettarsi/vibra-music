/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.menu.isPitchSemitoneAligned
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class MenuPresentationTest {
  @Test
  fun lateInitialContentStillOpensAHiddenSheet() {
    assertEquals(
      SheetValue.PartiallyExpanded,
      sheetOpeningTarget(SheetValue.Hidden, SheetValue.Hidden, true),
    )
    assertEquals(
      SheetValue.Expanded,
      sheetOpeningTarget(SheetValue.Hidden, SheetValue.Hidden, false),
    )
  }

  @Test
  fun openingDoesNotRestartAnExistingSpring() {
    assertNull(sheetOpeningTarget(SheetValue.Hidden, SheetValue.PartiallyExpanded, true))
    assertNull(sheetOpeningTarget(SheetValue.PartiallyExpanded, SheetValue.Expanded, false))
  }

  @Test
  fun openingPolicyNeverReversesAnOngoingDismissal() {
    assertNull(sheetOpeningTarget(SheetValue.PartiallyExpanded, SheetValue.Hidden, true))
    assertNull(sheetOpeningTarget(SheetValue.Expanded, SheetValue.Hidden, false))
  }

  @Test
  fun pitchBelowTheSemitoneRangeKeepsItsMultiplierPresentation() {
    assertFalse(isPitchSemitoneAligned(0.25f))
    assertTrue(isPitchSemitoneAligned(0.5f))
    assertTrue(isPitchSemitoneAligned(1f))
    assertTrue(isPitchSemitoneAligned(2f))
  }

  @Test
  fun shortSheetsWrapWhileContentAtTheOpeningLimitStartsHalfway() {
    assertFalse(sheetOpeningLimitReached(0, 1000))
    assertFalse(sheetOpeningLimitReached(400, 1000))
    assertFalse(sheetOpeningLimitReached(550, 1000))
    assertTrue(sheetOpeningLimitReached(600, 1000))
    assertTrue(sheetOpeningLimitReached(900, 1000))
    assertTrue(sheetOpeningLimitReached(300, 500))
  }

  @Test
  fun nativePresetTerminatorsNeverReachAccessibleText() {
    assertEquals("Normal", menuDisplayText("Normal\u0000\u0000"))
    assertEquals("Voci", menuDisplayText("Voci"))
  }

  @Test
  fun missingMetadataNeverLeavesASeparator() {
    assertEquals("12 brani", menuSubtitle("", "12 brani"))
    assertEquals("Artista · 3:00", menuSubtitle("Artista", null, "3:00"))
    assertEquals("", menuSubtitle(null, " "))
  }

  private fun action(id: MenuActionId, label: String) =
    NewAction(id = id, icon = {}, text = label, onClick = {})

  @Test
  fun quickOrderUsesIdentityRatherThanTranslatedLabels() {
    val actions =
      listOf(
        action(MenuActionId.Share, "Playlist"),
        action(MenuActionId.Download, "Scaricato"),
        action(MenuActionId.Enqueue, "Ajouter"),
        action(MenuActionId.Playlist, "Salva"),
        action(MenuActionId.PlayNext, "Next"),
      )
    val quick = mediaQuickActions(actions, MediaMenuKind.Song)
    assertEquals(quickActionIds(MediaMenuKind.Song), quick.map { it.id })
    assertEquals(listOf(MenuActionId.Share), mediaRemainingActions(actions, quick).map { it.id })
  }

  @Test
  fun duplicatePrimaryActionsAreRemovedWithoutDroppingDistinctTools() {
    val actions =
      listOf(
        action(MenuActionId.Play, "Play"),
        action(MenuActionId.Share, "Share"),
        action(MenuActionId.Share, "Condividi"),
        action(MenuActionId.Other, "Tags"),
        action(MenuActionId.Other, "Details"),
      )
    assertEquals(
      listOf("Share", "Tags", "Details"),
      mediaRemainingActions(actions, emptyList()).map { it.text },
    )
  }

  @Test
  fun missingDownloadDoesNotPromoteAnUnrelatedActionIntoQuickSlots() {
    val actions = listOf(action(MenuActionId.PlayNext, "After"), action(MenuActionId.Edit, "Edit"))
    assertEquals(
      listOf(MenuActionId.PlayNext),
      mediaQuickActions(actions, MediaMenuKind.Song).map { it.id },
    )
  }

  @Test
  fun popupStackHasOneActiveSiblingAndRestoresItsParent() {
    val parent = Any()
    val first = Any()
    val second = Any()
    val stack = mutableListOf(parent to 1, first to 2, second to 2)
    assertSame(second, activePopupToken(stack))
    stack.removeAt(2)
    assertSame(first, activePopupToken(stack))
    stack.removeAt(1)
    assertSame(parent, activePopupToken(stack))
    stack.clear()
    assertNull(activePopupToken(stack))
  }

  @Test
  fun deeperPopupWinsOverLaterShallowRegistration() {
    val child = Any()
    assertSame(child, activePopupToken(listOf(Any() to 1, child to 3, Any() to 2)))
  }

  @Test
  fun timerFitsAboveItsRealButton() {
    val result =
      timerPopupBounds(
        Rect(290f, 700f, 340f, 744f),
        Size(320f, 400f),
        Size(390f, 844f),
        20f,
        24f,
        24f,
      )
    assertEquals(20f, result.left)
    assertEquals(290f, result.top)
    assertTrue(result.bottom < 700f)
  }

  @Test
  fun timerNearTopFallsBelowAnchorAndStaysInsideSafeArea() {
    val result =
      timerPopupBounds(Rect(10f, 40f, 54f, 84f), Size(350f, 300f), Size(390f, 844f), 20f, 24f, 24f)
    assertEquals(20f, result.left)
    assertEquals(94f, result.top)
    assertTrue(result.right <= 370f)
  }

  @Test
  fun tallTimerIsConstrainedOnLandscapeViewport() {
    val result =
      timerPopupBounds(
        Rect(700f, 250f, 744f, 294f),
        Size(350f, 600f),
        Size(844f, 390f),
        20f,
        24f,
        24f,
      )
    assertEquals(302f, result.height)
    assertTrue(result.top >= 44f)
    assertTrue(result.bottom <= 346f)
  }
}
