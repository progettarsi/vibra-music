/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class VibraRoundingTest {
    @Test fun medianUsesAllFourCornersAndConvertsPixelsToDp() {
        assertEquals(35.dp, resolveVibraRadii(RoundingMode.AUTOMATIC, listOf(60, 64, 76, 100), 2f).base)
        assertEquals(38.dp, resolveVibraRadii(RoundingMode.AUTOMATIC, listOf(null, 76, null, null), 2f).base)
    }

    @Test fun squareAndUnavailableAreDifferentInputs() {
        assertEquals(24.dp, resolveVibraRadii(RoundingMode.AUTOMATIC, listOf(0, 0, 0, 0), 3f).base)
        assertEquals(32.dp, resolveVibraRadii(RoundingMode.AUTOMATIC, listOf(null, null, null, null), 3f).base)
        assertEquals(32.dp, resolveVibraRadii(RoundingMode.AUTOMATIC, emptyList(), 3f).base)
    }

    @Test fun boundsClassicAndInvalidInputsAreSafe() {
        assertEquals(24.dp, resolveVibraRadii(RoundingMode.AUTOMATIC, listOf(12), 3f).base)
        assertEquals(40.dp, resolveVibraRadii(RoundingMode.AUTOMATIC, listOf(900), 3f).base)
        assertEquals(32.dp, resolveVibraRadii(RoundingMode.CLASSIC, listOf(900), 3f).base)
        for (density in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(32.dp, resolveVibraRadii(RoundingMode.AUTOMATIC, listOf(100), density).base)
        }
        assertEquals(32.dp, resolveVibraRadii(RoundingMode.AUTOMATIC, listOf(-1), 1f).base)
    }

    @Test fun rolesAndConcentricInsetsRemainCorrectThroughoutTheRange() {
        for (base in 24..40) {
            val radii = VibraRadii(base.dp)
            assertEquals(base * 0.75f, radii.row.value, 0.001f)
            assertEquals(base * 0.625f, radii.field.value, 0.001f)
            assertEquals(base * 0.875f, radii.albumCover.value, 0.001f)
            assertEquals(radii.card, radii.innerArtwork + 8.dp)
            assertEquals(radii.row, radii.rowThumbnail + 8.dp)
            assertEquals(radii.sheet, radii.card + 20.dp)
            assertEquals(0.dp, radii.inset(4.dp, 8.dp))
        }
        assertEquals(16.dp, VibraRadii().rowThumbnail)
    }

    @Test fun inheritedArtworkSubtractsSpacingAfterScalingAndHonoursCustomParents() {
        for (base in listOf(24, 32, 40)) {
            val outer = VibraRadii(base.dp).scaled(26.dp)
            val child = insetRoundedShape(RoundedCornerShape(outer), 12.dp)
            val outline = child.createOutline(Size(72f, 72f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
            assertEquals(outer.value - 12f, outline.roundRect.topLeftCornerRadius.x, 0.001f)
        }
    }

    @Test fun smallChildrenNormalizeRadiiToTheirAvailableSize() {
        val outline = RoundedCornerShape(VibraRadii(40.dp).innerArtwork)
            .createOutline(Size(24f, 24f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
        assertEquals(12f, outline.roundRect.topLeftCornerRadius.x, 0.001f)
    }

    @Test fun windowCornersKeepTheirPhysicalPositions() {
        val shape = WindowCornerRadii(10.dp, 20.dp, 30.dp, 40.dp).shape()
        // AbsoluteRoundedCornerShape keeps these slots physical in both layout directions.
        assertEquals(10f, shape.topStart.toPx(Size(200f, 200f), Density(1f)), 0f)
        assertEquals(20f, shape.topEnd.toPx(Size(200f, 200f), Density(1f)), 0f)
        assertEquals(30f, shape.bottomEnd.toPx(Size(200f, 200f), Density(1f)), 0f)
        assertEquals(40f, shape.bottomStart.toPx(Size(200f, 200f), Density(1f)), 0f)
    }

    @Test fun physicalMorphHasTheSameEndpointsForForegroundAndBackground() {
        val window = WindowCornerRadii(10.dp, 20.dp, 30.dp, 40.dp)
        val pixels = window.pixels(Density(2f))
        for (progress in listOf(0f, 0.5f, 1f)) {
            val foreground = pixels.roundRect(Rect(0f, 0f, 400f, 800f), 56f, progress)
            val background = window.shape(28.dp, progress)
            val size = Size(400f, 800f)
            val density = Density(2f)
            assertEquals(background.topStart.toPx(size, density), foreground.topLeftCornerRadius.x, 0f)
            assertEquals(background.topEnd.toPx(size, density), foreground.topRightCornerRadius.x, 0f)
            assertEquals(background.bottomEnd.toPx(size, density), foreground.bottomRightCornerRadius.x, 0f)
            assertEquals(background.bottomStart.toPx(size, density), foreground.bottomLeftCornerRadius.x, 0f)
        }
        assertEquals(5f, pixels.roundRect(Rect(0f, 0f, 10f, 20f)).bottomLeftCornerRadius.x, 0f)
    }
}
