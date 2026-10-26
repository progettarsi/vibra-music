/*
 * Vibra (2026) — GPL-3.0.
 */

package app.vibra.ui.player

import androidx.compose.ui.geometry.Rect
import app.vibra.ui.player.LiveBackdropRenderer.Companion.boxRadius
import app.vibra.ui.player.LiveBackdropRenderer.Companion.scrimAt
import app.vibra.ui.player.LiveBackdropRenderer.Companion.softMaskAt
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveBackdropTest {
    private val slot = Rect(0f, 200f, 1000f, 1200f)

    @Test
    fun softMask_coversTheCoverAndFadesAroundIt() {
        assertEquals(1f, softMaskAt(700f, slot), 0f)
        assertEquals(0.5f, softMaskAt(200f - 150f, slot), 1e-6f)
        assertEquals(0.5f, softMaskAt(1200f + 150f, slot), 1e-6f)
        assertEquals(0f, softMaskAt(1600f, slot), 0f)
    }

    @Test
    fun scrim_darkensOnlyWhereTextSits() {
        assertEquals(TopScrim, scrimAt(0f, slot, 2400f), 1e-6f)
        assertEquals(0f, scrimAt(700f, slot, 2400f), 0f)
        assertEquals(BottomScrim, scrimAt(2400f, slot, 2400f), 1e-6f)
    }

    @Test
    fun boxRadius_growsWithTheBlur() {
        assertEquals(0, boxRadius(0f))
        assertEquals(12, boxRadius(20f))
    }
}
