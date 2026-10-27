/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.player

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import coil3.compose.LocalPlatformContext
import kotlin.math.roundToInt
import moe.rukamori.archivetune.LocalAnimationsDisabled

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  LIVE BACKDROP — the cover's backdrop (see CoverStage) following a music video
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  The cover's content — its image and the video playing over it — is recorded once into a
 *  graphics layer (FlyingCover) and drawn here a second time as CoverExtension draws a
 *  cover: at its place, its top and bottom rows stretched to the screen edges, under a heavy
 *  and a soft blur, the contrast dim and the legibility scrims.
 *
 *  Nothing is read back and nothing is redrawn by the app: the recording refers to the
 *  video's surface, so the render thread brings each new video frame into the backdrop in
 *  the same frame the cover shows it — in step, at the video's frame rate, no ghosting.
 *  The blurred layers are a sixth of the screen's size and drawn stretched back: a 44 dp
 *  blur leaves nothing finer, and blurring a thirty-sixth of the pixels is cheap enough to do
 *  for every video frame. When the cover does not change (a paused video), nothing is redone.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/**
 * The backdrop drawn from [content] (the cover's recorded content) while [video] shows a
 * picture: it lies over the cover's own baked backdrop (CoverExtension), dissolves in with
 * the video (it shares its reveal) and out when the video goes away. [url] is the cover's,
 * whose contrast dim it keeps (a video's frames would make it pulse).
 */
@Composable
fun LiveCoverExtension(
    video: CurrentArtworkVideo,
    content: GraphicsLayer,
    url: String?,
    slot: Rect?,
    visibility: () -> Float,
    modifier: Modifier = Modifier,
    offsetX: () -> Float = { 0f },
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val playback = video.playback
    // The video shown, and the opacity left after it has gone.
    var shown by remember { mutableStateOf<ArtworkVideoPlayback?>(null) }
    val fadeOut = remember { Animatable(0f) }
    val animationsDisabled = LocalAnimationsDisabled.current
    LaunchedEffect(playback) {
        if (playback != null) {
            shown = playback
            return@LaunchedEffect
        }
        val leaving = shown ?: return@LaunchedEffect
        fadeOut.snapTo(leaving.reveal.value)
        shown = null
        if (!animationsDisabled) fadeOut.animateTo(0f, tween(DissolveMillis))
        fadeOut.snapTo(0f)
    }
    val drawn by remember { derivedStateOf { shown != null || fadeOut.value > 0f } }
    if (!drawn || slot == null) return
    val context = LocalPlatformContext.current
    val dim by produceState(0f, url) { value = url?.let { coverContrastDim(context, it) } ?: 0f }
    val heavy = rememberGraphicsLayer()
    val soft = rememberGraphicsLayer()
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = visibility() * (shown?.reveal?.value ?: fadeOut.value)
                translationX = offsetX()
            },
    ) {
        val small = IntSize((size.width * LiveScale).roundToInt().coerceAtLeast(1), (size.height * LiveScale).roundToInt().coerceAtLeast(1))
        val heavyRadius = HeavyBlur.toPx() * LiveScale
        val softRadius = SoftBlur.toPx() * LiveScale
        val full = size
        // Heavy blur everywhere…
        heavy.renderEffect = BlurEffect(heavyRadius, heavyRadius, TileMode.Decal)
        heavy.record(small) { scale(LiveScale, Offset.Zero) { drawContentExtension(content, slot, full.height) } }
        // …and a soft one around the cover's edges, where the sharp cover dissolves.
        soft.renderEffect = BlurEffect(softRadius, softRadius, TileMode.Decal)
        soft.record(small) {
            scale(LiveScale, Offset.Zero) { softEdge(slot, full) { drawContentExtension(content, slot, full.height) } }
        }
        scale(1f / LiveScale, Offset.Zero) {
            drawLayer(heavy)
            drawLayer(soft)
        }
        drawBackdropScrims(slot, dim)
    }
}

/** [content] continuing the cover at [slot], like CoverExtension's image (see extensionParts). */
private fun DrawScope.drawContentExtension(
    content: GraphicsLayer,
    slot: Rect,
    canvasHeight: Float,
) {
    val contentSize = content.size
    if (contentSize.width <= 0 || contentSize.height <= 0) return
    extensionParts(CoverCrop.centered(contentSize.width, contentSize.height), slot, canvasHeight).forEach { (source, target, mirrored) ->
        withTransform({
            // A mirrored band is flipped: its first row lands at the target's bottom edge.
            translate(target.left.toFloat(), if (mirrored) target.bottom.toFloat() else target.top.toFloat())
            val scaleY = target.height.toFloat() / source.height
            scale(target.width.toFloat() / source.width, if (mirrored) -scaleY else scaleY, Offset.Zero)
            translate(-source.left.toFloat(), -source.top.toFloat())
            clipRect(source.left.toFloat(), source.top.toFloat(), source.right.toFloat(), source.bottom.toFloat())
        }) {
            drawLayer(content)
        }
    }
}

/** Resolution of the live backdrop's blurred layers relative to the screen. */
private const val LiveScale = 1f / 6f
