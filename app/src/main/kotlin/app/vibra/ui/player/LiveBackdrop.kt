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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import moe.rukamori.archivetune.LocalAnimationsDisabled

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  LIVE BACKDROP — the cover's backdrop (see CoverStage) following a music video
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  The cover's content — its image and the video playing over it — is recorded once into a
 *  graphics layer (FlyingCover). Come per CoverExtension, il campo continuo dei bordi mantiene
 *  orientamento e blur uniforme. Lo scurimento segue la copertina e la posizione del titolo
 *  (i colori dei frame non vengono analizzati, per evitare variazioni durante il video).
 *
 *  Nothing is read back and nothing is redrawn by the app: the recording refers to the
 *  video's surface, so the render thread brings each new video frame into the backdrop in
 *  the same frame the cover shows it — in step, at the video's frame rate, no ghosting.
 *  Il piano viene sfocato e composto a un sesto della risoluzione, poi disteso una sola volta.
 *  Il blur non contiene dettagli più fini; lavorare su un
 *  trentaseiesimo dei pixel riduce il costo per frame. Con il video in pausa non si aggiorna.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/**
 * The backdrop drawn from [content] (the cover's recorded content) while [video] shows a
 * picture: it lies over the cover's own baked backdrop (CoverExtension). The source already
 * composites the video with its cover: [videoMix] restores the cover's flat edges without
 * multiplying the video's opacity a second time. [url] is the cover's,
 * whose scrims it keeps (a video's frames would make them pulse).
 */
@Composable
internal fun LiveCoverExtension(
    video: CurrentArtworkVideo,
    content: GraphicsLayer,
    url: String?,
    slot: Rect?,
    visibility: () -> Float,
    modifier: Modifier = Modifier,
    offsetX: () -> Float = { 0f },
    titleY: Float? = null,
    videoMix: () -> Float = { 1f },
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
    val crop = rememberCoverCrop(url)
    val pictureLayer = rememberGraphicsLayer()
    val surface = rememberGraphicsLayer()
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = visibility() * (if (shown != null) 1f else fadeOut.value)
                translationX = offsetX()
            },
    ) {
        val canvas = size
        val small = IntSize((canvas.width * LiveScale).roundToInt().coerceAtLeast(1), (canvas.height * LiveScale).roundToInt().coerceAtLeast(1))
        val blur = BackdropBlur.toPx()
        val top = crop?.topScrim ?: MinTopScrim
        val bottom = crop?.bottomScrim ?: MinBottomScrim
        val coverWeight = 1f - videoMix().coerceIn(0f, 1f)
        val plainTop = crop?.plainTop?.let { it.copy(weight = it.weight * coverWeight) }
        val plainBottom = crop?.plainBottom?.let { it.copy(weight = it.weight * coverWeight) }
        surface.record(small) {
            drawBackdrop(pictureLayer, slot, canvas, LiveScale, blur, plainTop, plainBottom, top, bottom, titleY ?: (slot.bottom + slot.height * 0.06f), crop?.topSpread ?: 0.2f, crop?.bottomSpread ?: 0.2f) { rect, source -> drawCoverContent(content, rect, source) }
        }
        scale(1f / LiveScale, Offset.Zero) { drawLayer(surface) }
    }
}

/** The cover's recorded [content] (a square, like the cover) drawn [into] a rectangle. */
internal fun DrawScope.drawCoverContent(
    content: GraphicsLayer,
    into: Rect,
    source: Rect,
    expectedSize: IntSize = content.size,
) {
    val contentSize = expectedSize
    if (contentSize.width <= 0 || contentSize.height <= 0) return
    // Il ritaglio è sul layer già presente in GPU, anche per righe molto sottili.
    clipRect(into.left, into.top, into.right, into.bottom) {
        val sx = into.width / (contentSize.width * source.width)
        val sy = into.height / (contentSize.height * source.height)
        withTransform({
            translate(into.left - source.left * contentSize.width * sx, into.top - source.top * contentSize.height * sy)
            scale(sx, sy, Offset.Zero)
        }) { drawLayer(content) }
    }
}

/** Resolution the live backdrop is made at, relative to the screen. */
private const val LiveScale = 1f / 6f
