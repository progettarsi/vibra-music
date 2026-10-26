/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalAnimationsDisabled

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  LIVE BACKDROP — the cover's backdrop (see CoverStage) following a music video
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  A few times a second a tiny frame is copied from the video (asynchronously, see
 *  ArtworkVideoPlayback.grab) and turned into the same picture CoverExtension bakes for a
 *  cover — stretched edges, a heavy blur everywhere, a soft one around the cover, the
 *  contrast dim and the legibility scrims — but in software, at a sixth of the screen's
 *  resolution, off the main thread. After a 44 dp blur nothing finer survives, so the small
 *  picture drawn stretched looks the same; each new one dissolves into the previous one,
 *  giving a continuous, slightly lagging glow that costs the UI thread nothing.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/**
 * The backdrop following the current track's video ([frames]) while [running]; it lies over
 * the cover's own backdrop (CoverExtension) and fades in and out with the video.
 */
@Composable
fun LiveCoverExtension(
    frames: ArtworkVideoFrames,
    slot: Rect?,
    size: IntSize,
    running: () -> Boolean,
    visibility: () -> Float,
    modifier: Modifier = Modifier,
    offsetX: () -> Float = { 0f },
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    var from by remember { mutableStateOf<ImageBitmap?>(null) }
    var to by remember { mutableStateOf<ImageBitmap?>(null) }
    val mix = remember { Animatable(1f) }
    val reveal = remember { Animatable(0f) }
    val animationsDisabled = LocalAnimationsDisabled.current
    val density = LocalDensity.current
    val source = frames.source
    val currentRunning by rememberUpdatedState(running)
    LaunchedEffect(source, slot, size) {
        if (source == null || slot == null || size.width <= 0 || size.height <= 0) {
            reveal.animateTo(0f, tween(DissolveMillis))
            from = null
            to = null
            return@LaunchedEffect
        }
        val renderer =
            LiveBackdropRenderer(
                slot = slot,
                size = size,
                heavyRadius = with(density) { HeavyBlur.toPx() },
                softRadius = with(density) { SoftBlur.toPx() },
            )
        val frame =
            Bitmap.createBitmap(
                LiveFrameWidth,
                (LiveFrameWidth / source.stream.aspectRatio).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        while (true) {
            snapshotFlow { currentRunning() }.first { it }
            if (!source.grab(frame)) {
                delay(LiveFrameMillis.toLong())
                continue
            }
            val baked = withContext(Dispatchers.Default) { renderer.render(frame) }.asImageBitmap()
            if (to == null || animationsDisabled) {
                to = baked
                if (reveal.value < 1f) launch { reveal.animateTo(1f, tween(DissolveMillis)) }
                delay(LiveFrameMillis.toLong())
            } else {
                from = to
                to = baked
                mix.snapTo(0f)
                mix.animateTo(1f, tween(LiveFrameMillis, easing = LinearEasing))
                from = null
            }
        }
    }
    if (to == null) return
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = visibility() * reveal.value
                translationX = offsetX()
            },
    ) {
        val full = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt())
        from?.let { drawImage(it, dstSize = full, filterQuality = FilterQuality.Low) }
        to?.let { drawImage(it, dstSize = full, alpha = mix.value, filterQuality = FilterQuality.Low) }
    }
}

/**
 * Draws CoverExtension's picture for a video frame in software at [LiveScale] (see the file
 * header). Radii are the full-size blur radii in pixels; one renderer serves one geometry.
 */
internal class LiveBackdropRenderer(
    private val slot: Rect,
    size: IntSize,
    heavyRadius: Float,
    softRadius: Float,
) {
    private val width = (size.width * LiveScale).roundToInt().coerceAtLeast(1)
    private val height = (size.height * LiveScale).roundToInt().coerceAtLeast(1)
    private val fullHeight = size.height.toFloat()
    private var parts: List<Pair<IntRect, IntRect>> = emptyList()
    private val heavyBox = boxRadius(heavyRadius * LiveScale)
    private val softBox = boxRadius(softRadius * LiveScale)

    // Per row: how much of the soft blur shows (around the cover) and the legibility scrim.
    private val softMask = FloatArray(height) { y -> softMaskAt((y + 0.5f) / LiveScale, slot) }
    private val scrim = FloatArray(height) { y -> scrimAt((y + 0.5f) / LiveScale, slot, size.height.toFloat()) }

    private val extension = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(extension)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val from = android.graphics.Rect()
    private val to = RectF()
    private val pixels = IntArray(width * height)
    private val heavy = Array(4) { FloatArray(width * height) }
    private val soft = Array(4) { FloatArray(width * height) }
    private val scratch = FloatArray(maxOf(width, height))

    /** A new, full picture for [frame] (a centre-cropped video frame, like a cover). */
    fun render(frame: Bitmap): Bitmap {
        if (parts.isEmpty()) parts = extensionParts(frame.width, frame.height, slot, fullHeight)
        drawExtension(frame)
        extension.getPixels(pixels, 0, width, 0, 0, width, height)
        // Premultiplied channels: outside the picture is transparent, so blurs fade at the
        // screen edges exactly like the decal blurs of the baked backdrop.
        for (i in pixels.indices) {
            val argb = pixels[i]
            val a = (argb ushr 24) / 255f
            heavy[0][i] = a
            heavy[1][i] = ((argb shr 16) and 0xFF) / 255f * a
            heavy[2][i] = ((argb shr 8) and 0xFF) / 255f * a
            heavy[3][i] = (argb and 0xFF) / 255f * a
        }
        for (c in 0 until 4) heavy[c].copyInto(soft[c])
        heavy.forEach { blur(it, heavyBox) }
        soft.forEach { blur(it, softBox) }
        val dim = contrastDim(frame.asImageBitmap())
        for (y in 0 until height) {
            val m = softMask[y]
            // Contrast dim, then the scrim, both black over the picture.
            val keep = (1f - dim) * (1f - scrim[y])
            for (x in 0 until width) {
                val i = y * width + x
                val softAlpha = soft[0][i] * m
                val under = 1f - softAlpha
                val a = softAlpha + heavy[0][i] * under
                val outA = 1f - (1f - a) * keep
                if (outA <= 0f) {
                    pixels[i] = 0
                    continue
                }
                val r = (soft[1][i] * m + heavy[1][i] * under) * keep / outA
                val g = (soft[2][i] * m + heavy[2][i] * under) * keep / outA
                val b = (soft[3][i] * m + heavy[3][i] * under) * keep / outA
                pixels[i] = (byteOf(outA) shl 24) or (byteOf(r) shl 16) or (byteOf(g) shl 8) or byteOf(b)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** The frame continuing the cover, like CoverExtension's (see extensionParts). */
    private fun drawExtension(frame: Bitmap) {
        extension.eraseColor(0)
        parts.forEach { (source, target) ->
            from.set(source.left, source.top, source.right, source.bottom)
            to.set(target.left * LiveScale, target.top * LiveScale, target.right * LiveScale, target.bottom * LiveScale)
            canvas.drawBitmap(frame, from, to, paint)
        }
    }

    /** Three box passes per direction ≈ a gaussian; outside the picture counts as empty. */
    private fun blur(
        plane: FloatArray,
        radius: Int,
    ) {
        if (radius < 1) return
        repeat(BoxPasses) {
            for (y in 0 until height) boxLine(plane, y * width, 1, width, radius)
            for (x in 0 until width) boxLine(plane, x, width, height, radius)
        }
    }

    private fun boxLine(
        plane: FloatArray,
        start: Int,
        stride: Int,
        count: Int,
        radius: Int,
    ) {
        val window = 2f * radius + 1f
        var sum = 0f
        for (k in 0..minOf(radius, count - 1)) sum += plane[start + k * stride]
        for (k in 0 until count) {
            scratch[k] = sum / window
            val enter = k + radius + 1
            val leave = k - radius
            if (enter < count) sum += plane[start + enter * stride]
            if (leave >= 0) sum -= plane[start + leave * stride]
        }
        for (k in 0 until count) plane[start + k * stride] = scratch[k]
    }

    internal companion object {
        const val BoxPasses = 3

        /** The box radius whose three passes match a blur effect of [radius] (Skia's sigma). */
        fun boxRadius(radius: Float): Int {
            val sigma = 0.57735f * radius + 0.5f
            val width = kotlin.math.sqrt(4f * sigma * sigma + 1f)
            return ((width - 1f) / 2f).roundToInt()
        }

        fun byteOf(value: Float): Int = (value * 255f + 0.5f).toInt().coerceIn(0, 255)

        /** CoverExtension's soft-blur mask at full-size height [y]. */
        fun softMaskAt(
            y: Float,
            slot: Rect,
        ): Float {
            val reach = slot.height * SoftReach
            return when {
                y < slot.top - reach || y > slot.bottom + reach -> 0f
                y < slot.top -> (y - (slot.top - reach)) / reach
                y > slot.bottom -> 1f - (y - slot.bottom) / reach
                else -> 1f
            }
        }

        /** CoverExtension's legibility scrim at full-size height [y]: status bar and controls. */
        fun scrimAt(
            y: Float,
            slot: Rect,
            height: Float,
        ): Float {
            val clearUntil = slot.bottom - slot.height * 0.25f
            return when {
                y < slot.top -> TopScrim * (1f - y / slot.top.coerceAtLeast(1f))
                y < clearUntil -> 0f
                else -> BottomScrim * ((y - clearUntil) / (height - clearUntil).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
        }
    }
}

/** Resolution of a live backdrop relative to the screen. */
private const val LiveScale = 1f / 6f

/** Width of the video frame a live backdrop is made from. */
private const val LiveFrameWidth = 96

/** One live backdrop dissolves into the next over this long (≈ 6 per second). */
private const val LiveFrameMillis = 160
