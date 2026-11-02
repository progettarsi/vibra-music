package app.vibra.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ArtworkAccentTest {
  @Test
  fun colourOnANeutralCoverWinsOverTheBackground() {
    // A red jacket on a white cover with a black frame: the controls take the red.
    val red = Color(0xFFD0203A)
    val swatches =
      listOf(
        CoverSwatch(Color(0xFFF4F4F2), 5200),
        CoverSwatch(Color(0xFF101010), 2100),
        CoverSwatch(red, 700),
        CoverSwatch(Color(0xFF8A8A88), 900),
      )
    assertEquals(red, vividSwatch(swatches))
  }

  @Test
  fun aSpeckOfColourDoesNotColourTheControls() {
    val swatches =
      listOf(
        CoverSwatch(Color(0xFF202020), 6000),
        CoverSwatch(Color(0xFFE0E0E0), 3950),
        CoverSwatch(Color(0xFF00C853), 50),
      )
    assertNull(vividSwatch(swatches))
  }

  @Test
  fun aCoverWithoutColourHasNoAccentOfItsOwn() {
    val swatches = listOf(CoverSwatch(Color(0xFF111111), 4000), CoverSwatch(Color(0xFF777777), 3000), CoverSwatch(Color(0xFFEEEEEE), 3000))
    assertNull(vividSwatch(swatches))
    assertNull(vividSwatch(emptyList()))
  }

  @Test
  fun aBroadColourBeatsASlightlyBrighterSmallOne() {
    val orange = Color(0xFFC8641E)
    val pink = Color(0xFFFF2D8A)
    val swatches = listOf(CoverSwatch(orange, 4000), CoverSwatch(pink, 250), CoverSwatch(Color(0xFF9AA59A), 5750))
    assertEquals(orange, vividSwatch(swatches))
  }
}
