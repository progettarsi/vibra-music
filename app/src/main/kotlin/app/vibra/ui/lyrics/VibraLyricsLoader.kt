/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.vibra.ui.components.ShapeTracingLoaderState
import app.vibra.ui.components.rememberShapeTracingLoaderState
import app.vibra.ui.components.shapeTracingLoader
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalAnimationsDisabled

/**
 * The lyrics while they load: Vibra's shape loader — the cookie of the play button, turning
 * slowly, with the arc running round its edge — in the middle, where the sung line will be.
 * It only appears when loading takes a moment: lyrics already on the device arrive within a
 * frame or two and must not flash a loader on the way.
 */
@Composable
fun VibraLyricsLoader(
    color: Color,
    modifier: Modifier = Modifier,
) {
    var due by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LoaderDelayMillis)
        due = true
    }
    val loader = rememberShapeTracingLoaderState(active = due)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loader != null) LoaderCookie(loader = loader, color = color)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoaderCookie(
    loader: ShapeTracingLoaderState,
    color: Color,
) {
    val cookie = MaterialShapes.Cookie12Sided.toShape()
    val turning = !LocalAnimationsDisabled.current
    val angle by rememberInfiniteTransition(label = "lyricsLoader").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(TurnMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "lyricsLoaderAngle",
    )
    Box(
        modifier =
            Modifier
                .size(LoaderSize)
                .graphicsLayer {
                    val shown = loader.visibility()
                    alpha = shown
                    val scale = lerp(EnterScale, 1f, shown)
                    scaleX = scale
                    scaleY = scale
                    if (turning) rotationZ = angle
                }.clip(cookie)
                .background(color.copy(alpha = FillAlpha))
                .shapeTracingLoader(state = loader, shape = cookie, color = color, strokeWidth = 3.dp),
    )
}

private const val LoaderDelayMillis = 250L
private const val TurnMillis = 6_000
private const val EnterScale = 0.8f
private const val FillAlpha = 0.12f
private val LoaderSize = 48.dp
