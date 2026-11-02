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
  fun timerPanelGrowsUpFromItsButtonAcrossThePlayer() {
    // Portrait: the button ends its row 54 px before the gutter (the add button sits there).
    val result =
      timerPanelBounds(Rect(272f, 700f, 316f, 744f), Size(350f, 400f), Size(390f, 844f), 20f, 24f, 24f)
    assertEquals(Rect(20f, 344f, 370f, 744f), result)
  }

  @Test
  fun timerPanelNearTopStaysInsideSafeArea() {
    val result =
      timerPanelBounds(Rect(10f, 40f, 54f, 84f), Size(350f, 300f), Size(390f, 844f), 20f, 24f, 24f)
    assertEquals(44f, result.top)
    assertEquals(20f, result.left)
    assertTrue(result.right <= 370f)
  }

  @Test
  fun tallTimerPanelIsCappedOnLandscapeViewport() {
    val result =
      timerPanelBounds(Rect(700f, 250f, 744f, 294f), Size(420f, 600f), Size(844f, 390f), 20f, 24f, 24f, 48f)
    assertEquals(302f, result.height)
    assertEquals(44f, result.top)
    assertEquals(844f - 48f - 20f, result.right)
  }

  @Test
  fun timerSurfaceStartsOnTheButtonAndEndsOnThePanelWithoutOvershoot() {
    val button = Rect(272f, 700f, 316f, 744f)
    val panel = Rect(20f, 344f, 370f, 744f)
    assertEquals(button, timerContainerRect(button, panel, 0f))
    assertEquals(panel, timerContainerRect(button, panel, 1f))
    // Past either end (a spring, a back gesture) it stays put: no bounce.
    assertEquals(panel, timerContainerRect(button, panel, 1.2f))
    assertEquals(button, timerContainerRect(button, panel, -0.1f))
    var previous = button
    for (step in 1..20) {
      val rect = timerContainerRect(button, panel, step / 20f)
      assertTrue(rect.left <= previous.left && rect.top <= previous.top && rect.right >= previous.right)
      assertTrue(rect.left >= panel.left && rect.top >= panel.top && rect.right <= panel.right)
      previous = rect
    }
  }

  @Test
  fun timerContentGrowsWithTheSurfaceAndIsNeverCut() {
    val button = Rect(272f, 700f, 316f, 744f)
    val panel = Rect(20f, 344f, 370f, 744f)
    assertEquals(panel, timerContentBounds(panel, panel.size))
    for (step in 0..20) {
      val container = timerContainerRect(button, panel, step / 20f)
      val content = timerContentBounds(container, panel.size)
      // Same proportions as the panel, inside the surface, centred on it.
      assertEquals(panel.width / panel.height, content.width / content.height, 0.001f)
      assertTrue(content.left >= container.left - 0.01f && content.right <= container.right + 0.01f)
      assertTrue(content.top >= container.top - 0.01f && content.bottom <= container.bottom + 0.01f)
      assertEquals(container.center.x, content.center.x, 0.01f)
      assertEquals(container.center.y, content.center.y, 0.01f)
    }
  }

  @Test
  fun timerBlocksAppearNearestFirstAndAreAllShownWhenOpen() {
    assertEquals(0f, timerBlockReveal(0, 0f))
    for (order in 0..4) assertEquals(1f, timerBlockReveal(order, 1f))
    for (order in 1..4) assertTrue(timerBlockReveal(order - 1, 0.4f) > timerBlockReveal(order, 0.4f))
  }
}
