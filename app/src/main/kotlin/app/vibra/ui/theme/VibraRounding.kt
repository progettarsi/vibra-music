/*
 * Vibra (2026) — adaptive corner geometry. GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.datastore.preferences.core.stringPreferencesKey

/** Stored by name, using the app's existing enum preference infrastructure. */
enum class RoundingMode { AUTOMATIC, CLASSIC }

val RoundingModeKey = stringPreferencesKey("vibraRoundingMode")

/** Resolved once at the theme root; children derive concentric radii from actual insets. */
@Immutable
data class VibraRadii(val base: Dp = 32.dp) {
    val card: Dp get() = base
    val row: Dp get() = scaled(24.dp)
    val menu: Dp get() = row
    val field: Dp get() = scaled(20.dp)
    val albumCover: Dp get() = scaled(28.dp)
    val innerArtwork: Dp get() = inset(card, 8.dp)
    val rowThumbnail: Dp get() = inset(row, 8.dp)
    val sheet: Dp get() = card + 20.dp

    // Allocate shared outlines once per style, not on each row/card recomposition.
    internal val cardShape = RoundedCornerShape(card)
    internal val rowShapes = VibraRowShapes(row)
    internal val menuShape = RoundedCornerShape(menu)
    internal val fieldShape = RoundedCornerShape(field)
    internal val albumCoverShape = RoundedCornerShape(albumCover)
    internal val innerArtworkShape = RoundedCornerShape(innerArtwork)
    internal val sheetShape = RoundedCornerShape(topStart = sheet, topEnd = sheet)

    fun scaled(classic: Dp): Dp = classic * (base.value / 32f)
    fun inset(outer: Dp, gap: Dp): Dp = (outer - gap).coerceAtLeast(0.dp)
}

/** Null means unavailable, whereas a reported zero is a valid square display. */
fun resolveVibraRadii(mode: RoundingMode, displayRadiiPx: List<Int?>, density: Float): VibraRadii {
    if (mode == RoundingMode.CLASSIC || !density.isFinite() || density <= 0f) return VibraRadii()
    val values = displayRadiiPx.filterNotNull().filter { it >= 0 }.sorted()
    if (values.isEmpty()) return VibraRadii()
    val middle = values.size / 2
    val median = if (values.size % 2 == 0) {
        (values[middle - 1].toFloat() + values[middle].toFloat()) / 2f
    } else values[middle].toFloat()
    return VibraRadii((median / density).coerceIn(24f, 40f).dp)
}

val LocalVibraRadii = staticCompositionLocalOf { VibraRadii() }
internal val LocalVibraRowShapes = staticCompositionLocalOf<VibraRowShapes?> { null }

@Immutable
internal class VibraRowShapes(radius: Dp) {
    val container = RoundedCornerShape(radius)
    val thumbnail = RoundedCornerShape((radius - 8.dp).coerceAtLeast(0.dp))
}

/** Scale an independent classic radius; resolve before entering draw/layout callbacks. */
@Composable
fun adaptiveRadius(classic: Dp): Dp = LocalVibraRadii.current.scaled(classic)

/** Compatibility factory for independent legacy shapes; never use for already resolved radii. */
@Composable
fun adaptiveRoundedCornerShape(size: Dp): RoundedCornerShape {
    val radius = adaptiveRadius(size)
    return remember(radius) { RoundedCornerShape(radius) }
}

@Composable
fun adaptiveRoundedCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): RoundedCornerShape {
    val radii = LocalVibraRadii.current
    return remember(radii, topStart, topEnd, bottomEnd, bottomStart) {
        RoundedCornerShape(radii.scaled(topStart), radii.scaled(topEnd), radii.scaled(bottomEnd), radii.scaled(bottomStart))
    }
}

/** Percentage corners are geometric stadiums/circles, independent of device style. */
fun adaptiveRoundedCornerShape(percent: Int): RoundedCornerShape = RoundedCornerShape(percent)

/** Resolve the outer style first, then subtract the unscaled physical spacing. */
@Composable
fun concentricRoundedCornerShape(classicOuter: Dp, inset: Dp): RoundedCornerShape {
    val radii = LocalVibraRadii.current
    val radius = radii.inset(radii.scaled(classicOuter), inset)
    return remember(radius) { RoundedCornerShape(radius) }
}

/** Follow a caller-supplied rounded container, including overrides; expressive shapes keep their silhouette. */
fun insetRoundedShape(outer: Shape, inset: Dp): Shape =
    if (outer is CornerBasedShape) RoundedCornerShape(
        InsetCorner(outer.topStart, inset), InsetCorner(outer.topEnd, inset),
        InsetCorner(outer.bottomEnd, inset), InsetCorner(outer.bottomStart, inset),
    ) else outer

private data class InsetCorner(val outer: CornerSize, val inset: Dp) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float = with(density) {
        (outer.toPx(Size(shapeSize.width + 2 * inset.toPx(), shapeSize.height + 2 * inset.toPx()), density) - inset.toPx()).coerceAtLeast(0f)
    }
}

private data class ScaledCorner(val corner: CornerSize, val scale: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float = corner.toPx(shapeSize, density) * scale
}

private fun VibraRadii.scaledShape(shape: CornerBasedShape): CornerBasedShape {
    val scale = base.value / 32f
    return shape.copy(ScaledCorner(shape.topStart, scale), ScaledCorner(shape.topEnd, scale),
        ScaledCorner(shape.bottomEnd, scale), ScaledCorner(shape.bottomStart, scale))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val DefaultMaterialShapes = Shapes()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun VibraRadii.materialShapes() = Shapes(
    extraSmall = RoundedCornerShape(scaled(8.dp)),
    small = RoundedCornerShape(scaled(12.dp)),
    medium = RoundedCornerShape(scaled(16.dp)),
    large = RoundedCornerShape(row),
    extraLarge = RoundedCornerShape(card),
    largeIncreased = scaledShape(DefaultMaterialShapes.largeIncreased),
    extraLargeIncreased = scaledShape(DefaultMaterialShapes.extraLargeIncreased),
    extraExtraLarge = scaledShape(DefaultMaterialShapes.extraExtraLarge),
)
