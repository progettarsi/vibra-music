/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The liquid gradient of the splash (res/drawable/splash_liquid.xml), live: five soft blobs
 * on springs chasing targets around the wave, one 4 s cycle that loops without a seam.
 * Same simulation and constants as the splash, so the two read as one animation; the
 * colours follow the app's accent (see [palette]).
 *
 * Coordinates are the logo's own (the 1080 icon viewport the wave path is drawn in).
 */
internal object LiquidLogo {
    private class Blob(
        val color: Long,
        val radius: Float,
        val ox: Float,
        val oy: Float,
        val delay: Float,
        val slow: Float,
        val stiffness: Float,
        val damping: Float,
    )

    private val blobs =
        listOf(
            Blob(0xFF4F7BFF, 380f, -250f, 190f, 230f, 1.22f, 46f, 0.34f),
            Blob(0xFF5CC9FF, 290f, -80f, 170f, 170f, 1.16f, 58f, 0.32f),
            Blob(0xFF8B5CF6, 350f, -140f, 70f, 110f, 1.08f, 72f, 0.36f),
            Blob(0xFFFF8AD0, 250f, 150f, -50f, 50f, 1.04f, 92f, 0.34f),
            Blob(0xFFE7BCFF, 280f, 0f, 0f, 0f, 1.0f, 118f, 0.38f),
        )
    private const val LEAD = 4

    private val tail = floatArrayOf(300f, 700f)
    private val peak = floatArrayOf(480f, 330f)
    private val valley = floatArrayOf(640f, 520f)
    private val end = floatArrayOf(860f, 500f)

    private class Beat(val at: Float, val dur: Float, val to: FloatArray, val rot: Float)

    private val beats =
        listOf(
            Beat(0f, 1150f, peak, 35f),
            Beat(900f, 1150f, end, -25f),
            Beat(1850f, 1050f, valley, 60f),
            Beat(2750f, 1250f, tail, 0f),
        )
    private const val PERIOD = 4000f
    private const val HZ = 240
    private const val COUPLE = 26f
    private const val WOBBLE_PERIOD = 4000f
    private val steps = (PERIOD / 1000f * HZ).toInt()

    /** x, y, vx, vy per blob per recorded step: one cycle at steady state. */
    private val baked: FloatArray by lazy { bake() }

    /** Prepare the simulation off the UI thread before the first animated frame. */
    fun prepare() {
        baked
    }

    /** The colours of one liquid: the wave's base gradient and one brush per blob. */
    class Palette internal constructor(
        internal val base: Brush,
        internal val blobs: List<Brush>,
    )

    private val baseColors = longArrayOf(0xFF3F3FC4, 0xFF7D55DC, 0xFFC39AF0)

    /** Hue of the splash palette's centre (its violet blob): the one the accent replaces. */
    private const val PALETTE_HUE = 258f

    /**
     * The splash palette turned to the app's [accent]: every colour keeps its distance in hue
     * from the centre (so the blue, cyan, pink and lilac stay relative to it) and its
     * lightness; a greyish accent makes the whole liquid as muted.
     */
    fun palette(accent: Color): Palette {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(accent.toArgb(), hsv)
        val shift = hsv[0] - PALETTE_HUE
        val mute = (hsv[1] / FULL_SATURATION).coerceIn(0f, 1f)
        fun turn(argb: Long): Color {
            val c = FloatArray(3)
            AndroidColor.colorToHSV(argb.toInt(), c)
            c[0] = ((c[0] + shift) % 360f + 360f) % 360f
            c[1] *= mute
            return Color(AndroidColor.HSVToColor(c))
        }
        val base =
            Brush.linearGradient(
                0f to turn(baseColors[0]),
                0.5f to turn(baseColors[1]),
                1f to turn(baseColors[2]),
                start = Offset(160f, 800f),
                end = Offset(920f, 300f),
            )
        val blobBrushes =
            blobs.map { b ->
                val c = turn(b.color)
                Brush.radialGradient(
                    0f to c.copy(alpha = 0.95f),
                    0.45f to c.copy(alpha = 0.55f),
                    1f to c.copy(alpha = 0f),
                    center = Offset.Zero,
                    radius = b.radius,
                )
            }
        return Palette(base, blobBrushes)
    }

    /** Accent saturation (HSV) from which the liquid keeps its full colour. */
    private const val FULL_SATURATION = 0.25f

    private fun ease(t: Float) = (-(cos(PI * t) - 1) / 2).toFloat()

    private fun rotate(x: Float, y: Float, deg: Float): Pair<Float, Float> {
        val a = deg * PI / 180
        return Pair((x * cos(a) - y * sin(a)).toFloat(), (x * sin(a) + y * cos(a)).toFloat())
    }

    private class Segment(val t0: Float, val dur: Float, val dx: Float, val dy: Float)

    private class Track(val restX: Float, val restY: Float, val segments: List<Segment>)

    private fun tracks(): List<Track> =
        blobs.map { b ->
            var px = tail[0] + b.ox
            var py = tail[1] + b.oy
            val segments =
                beats.map { beat ->
                    val (ox, oy) = rotate(b.ox, b.oy, beat.rot)
                    val tx = beat.to[0] + ox
                    val ty = beat.to[1] + oy
                    Segment(beat.at + b.delay, beat.dur * b.slow, tx - px, ty - py).also {
                        px = tx
                        py = ty
                    }
                }
            Track(tail[0] + b.ox, tail[1] + b.oy, segments)
        }

    /** Where blob [track] is heading at [t] ms (moves of the previous cycle still finishing count too). */
    private fun target(track: Track, t: Float, out: FloatArray) {
        var x = track.restX
        var y = track.restY
        for (s in track.segments) {
            for (tt in floatArrayOf(t, t + PERIOD)) {
                val u = ((tt - s.t0) / s.dur).coerceIn(0f, 1f)
                if (u <= 0f) continue
                val e = ease(u)
                x += s.dx * e
                y += s.dy * e
            }
            x -= s.dx
            y -= s.dy
        }
        out[0] = x
        out[1] = y
    }

    private fun bake(): FloatArray {
        val tracks = tracks()
        val n = blobs.size
        val dt = 1f / HZ
        val warm = 3
        val state = FloatArray(n * 4)
        val tmp = FloatArray(2)
        val goal = FloatArray(n * 2)
        tracks.forEachIndexed { i, tr ->
            target(tr, 0f, tmp)
            state[i * 4] = tmp[0]
            state[i * 4 + 1] = tmp[1]
        }
        val out = FloatArray((steps + 1) * n * 4)
        for (s in 0..steps * (warm + 1)) {
            val ms = s * dt * 1000f
            val t = ms % PERIOD
            if (s >= steps * warm) state.copyInto(out, (s - steps * warm) * n * 4)
            val ph = 2 * PI * t / WOBBLE_PERIOD
            for (i in 0 until n) {
                target(tracks[i], t, tmp)
                goal[i * 2] = tmp[0] + (15 * sin(2 * ph + i * 1.3) + 8 * sin(5 * ph + i * 2.1)).toFloat()
                goal[i * 2 + 1] = tmp[1] + (15 * cos(3 * ph + i * 0.9) + 8 * sin(7 * ph + i * 1.7)).toFloat()
            }
            val lx = state[LEAD * 4]
            val ly = state[LEAD * 4 + 1]
            for (i in 0 until n) {
                val b = blobs[i]
                val w = sqrt(b.stiffness)
                val c = 2 * b.damping * w
                val o = i * 4
                var ax = b.stiffness * (goal[i * 2] - state[o]) - c * state[o + 2]
                var ay = b.stiffness * (goal[i * 2 + 1] - state[o + 1]) - c * state[o + 3]
                if (i != LEAD) {
                    ax += COUPLE * ((lx + goal[i * 2] - goal[LEAD * 2]) - state[o])
                    ay += COUPLE * ((ly + goal[i * 2 + 1] - goal[LEAD * 2 + 1]) - state[o + 1])
                }
                state[o + 2] += ax * dt
                state[o + 3] += ay * dt
            }
            for (i in 0 until n) {
                state[i * 4] += state[i * 4 + 2] * dt
                state[i * 4 + 1] += state[i * 4 + 3] * dt
            }
        }
        return out
    }

    /**
     * Draws the blobs at [timeMs] (any value: the cycle repeats). [intensity] fades them in
     * and out; above 1 (a spring overshooting) they swell.
     */
    private fun DrawScope.drawBlobs(
        palette: Palette,
        timeMs: Float,
        intensity: Float,
    ) {
        if (intensity <= 0f) return
        val data = baked
        val n = blobs.size
        val f = ((timeMs / 1000f * HZ) % steps + steps) % steps
        val i0 = f.toInt().coerceAtMost(steps - 1)
        val u = f - i0
        val alpha = intensity.coerceIn(0f, 1f)
        val swell = 0.75f + 0.25f * intensity
        for (j in 0 until n) {
            val a = (i0 * n + j) * 4
            val b = ((i0 + 1) * n + j) * 4
            val x = data[a] + (data[b] - data[a]) * u
            val y = data[a + 1] + (data[b + 1] - data[a + 1]) * u
            val vx = data[a + 2] + (data[b + 2] - data[a + 2]) * u
            val vy = data[a + 3] + (data[b + 3] - data[a + 3]) * u
            val stretch = (hypot(vx, vy) / 1300f).coerceAtMost(1f)
            val angle = (atan2(vy, vx) * 180 / PI).toFloat()
            translate(x, y) {
                rotate(angle, pivot = Offset.Zero) {
                    scale(swell * (1 + 0.5f * stretch), swell * (1 - 0.2f * stretch), pivot = Offset.Zero) {
                        drawCircle(palette.blobs[j], radius = blobs[j].radius, center = Offset.Zero, alpha = alpha)
                    }
                }
            }
        }
    }

    /** The full liquid fill in [palette], clipped to [wave] (logo coordinates). */
    fun DrawScope.drawLiquidWave(
        palette: Palette,
        wave: Path,
        timeMs: Float,
        intensity: Float,
    ) {
        if (intensity <= 0f) return
        clipPath(wave) {
            drawPath(wave, palette.base, alpha = intensity.coerceIn(0f, 1f))
            drawBlobs(palette, timeMs, intensity)
        }
    }
}
