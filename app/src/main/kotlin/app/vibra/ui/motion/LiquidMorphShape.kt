/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * A [Shape] that is a snapshot of a [Morph] between two polygons at [progress], rotated by
 * [rotationDegrees] around its centre. Instances are cheap and immutable, so a new one is
 * created per animation frame and handed to `clip`/`background`.
 */
class LiquidMorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationDegrees: Float = 0f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toPath(progress).asComposePath()
        val bounds = morph.calculateBounds()
        val boundsWidth = (bounds[2] - bounds[0]).coerceAtLeast(1e-3f)
        val boundsHeight = (bounds[3] - bounds[1]).coerceAtLeast(1e-3f)
        val matrix =
            Matrix().apply {
                // Applied in reverse order: normalise → scale to size → rotate about centre.
                translate(size.width / 2f, size.height / 2f)
                rotateZ(rotationDegrees)
                translate(-size.width / 2f, -size.height / 2f)
                scale(size.width / boundsWidth, size.height / boundsHeight)
                translate(-bounds[0], -bounds[1])
            }
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Remembers a shape that morphs to [target] with a spatial spring whenever it changes.
 * Interrupting mid-morph starts from the current outline, so rapid toggles never jump.
 */
@Composable
fun rememberLiquidMorphShape(
    target: RoundedPolygon,
    rotationDegrees: Float = 0f,
    speed: LiquidSpeed = LiquidSpeed.Default,
): Shape {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    val spec = Liquid.spatial<Float>(speed)

    LaunchedEffect(target) {
        if (target !== to) {
            // Re-base the morph on the shape we are leaving so the animation is continuous.
            from = to
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, spec)
        }
    }
    val morph = remember(from, to) { Morph(from, to) }
    return LiquidMorphShape(morph, progress.value, rotationDegrees)
}
