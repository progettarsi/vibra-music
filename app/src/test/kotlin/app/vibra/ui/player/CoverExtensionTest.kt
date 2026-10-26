/*
 * Vibra (2026) — GPL-3.0.
 */

package app.vibra.ui.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverExtensionTest {
    private val slot = Rect(0f, 200f, 1000f, 1200f)

    @Test
    fun parts_putTheCoverAtItsSlotAndStretchItsEdgeRows() {
        val parts = extensionParts(100, 100, slot, canvasHeight = 2400f)
        assertEquals(
            listOf(
                IntRect(0, 0, 100, 100) to IntRect(0, 200, 1000, 1200),
                IntRect(0, 0, 100, 6) to IntRect(0, 0, 1000, 200),
                IntRect(0, 94, 100, 100) to IntRect(0, 1200, 1000, 2400),
            ),
            parts,
        )
    }

    @Test
    fun parts_cropAWidePictureToItsCentreSquare() {
        val (source, _) = extensionParts(160, 90, slot, canvasHeight = 2400f).first()
        assertEquals(IntRect(35, 0, 125, 90), source)
    }

    @Test
    fun parts_skipEdgesTheCoverAlreadyReaches() {
        assertEquals(1, extensionParts(100, 100, Rect(0f, 0f, 1000f, 2400f), canvasHeight = 2400f).size)
    }
}
