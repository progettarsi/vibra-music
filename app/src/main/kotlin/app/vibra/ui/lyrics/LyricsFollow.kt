/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.lyrics

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  LYRICS FOLLOW — keeps the sung line in the middle, or lets the reader browse
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  • The active line's centre rests at the centre of the list. The list has half its height
 *    of padding at both ends, so the first and the last line can sit there too: nothing has
 *    to move when the song starts.
 *  • Moving to the next line is a glide: the list itself scrolls on the spatial spring, and
 *    the lines below the new active one lag behind it (a time offset each), so the text is
 *    pulled along line by line and settles with a light bounce. Every glide is a pure function
 *    of time, so a line that comes into view mid-glide takes exactly its place in the
 *    cascade, and glides that overlap simply add up.
 *  • A jump too long to glide (a seek, a return from far away) fades through instead.
 *  • When the reader drags the text, following stops and every line turns sharp; after a
 *    few seconds without touching it the text glides back to the line being sung.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/** The springs of the follow motion, taken from the theme's motion scheme by the composable. */
internal class FollowSpecs(
    /** Next line: spatial spring, may overshoot a little. */
    val rise: FiniteAnimationSpec<Float>,
    /** Return after browsing: the whole text moves as one surface, so it settles without overshoot. */
    val settle: FiniteAnimationSpec<Float>,
    /** Opacity of a fade-through and of the first appearance. */
    val fade: FiniteAnimationSpec<Float>,
    /** Sharp ⇄ soft when browsing starts and ends. */
    val focus: FiniteAnimationSpec<Float>,
    /** False with reduced motion: no cascade. */
    val cascade: Boolean,
)

@Stable
internal class LyricsFollow(
    private val starts: IntArray,
    initialActive: Int,
    private val scope: CoroutineScope,
) {
    /** The line being sung; -1 before the first. */
    var active by mutableIntStateOf(initialActive)
        private set

    /** True while the reader scrolls on their own: no following, no blur. */
    var browsing by mutableStateOf(false)
        private set

    /** A finger is on the lyrics: the countdown back to the sung line waits for it. */
    var touching by mutableStateOf(false)

    /** 0 = following (depth blur on) … 1 = browsing (every line sharp). Read in draw. */
    val focus = Animatable(0f)

    /** Opacity of the whole list: 0 until it is placed, and through a fade-through. */
    val visibility = Animatable(0f)

    lateinit var specs: FollowSpecs

    private val glides = ArrayList<Glide>()
    private var clockNanos by mutableLongStateOf(0L)
    private var timeScale = 1f
    private var ticker: Job? = null
    private var scroller: Job? = null
    private var seekTarget = NoSeek
    private var seekSince = 0L

    /** New playback position (ms, on the lyrics' clock). */
    fun onPosition(position: Int) {
        if (seekTarget != NoSeek) {
            // Until the clock has caught up with a tap-to-seek, the old position would
            // briefly select the old line again.
            val caughtUp = abs(position - seekTarget) < SeekCatchUpMs
            if (!caughtUp && SystemClock.uptimeMillis() - seekSince < SeekTimeoutMs) return
            seekTarget = NoSeek
        }
        active = followLine(starts, active, position)
    }

    /** The reader started dragging the text. */
    fun startBrowsing() {
        if (browsing) return
        browsing = true
        scroller?.cancel()
        scope.launch { focus.animateTo(1f, specs.focus) }
    }

    /** Back to following the sung line (it glides into the middle). */
    fun stopBrowsing() {
        if (!browsing) return
        browsing = false
        scope.launch { focus.animateTo(0f, specs.focus) }
    }

    /** Line [index] was tapped and playback seeks to its start: it becomes the active line now. */
    fun seekedTo(index: Int) {
        seekTarget = starts[index]
        seekSince = SystemClock.uptimeMillis()
        active = lineAt(starts, starts[index])
        stopBrowsing()
    }

    /** Vertical offset (px) of line [index] while glides run; read it in a graphics layer. */
    fun offset(index: Int): Float {
        val now = clockNanos
        if (glides.isEmpty()) return 0f
        var sum = 0f
        for (glide in glides) sum += glide.lag(index, now, timeScale)
        return sum
    }

    /** Places the list and then keeps it on the active line for as long as it runs. */
    suspend fun drive(state: LazyListState) {
        if (!browsing) guarded { centre(state, maxOf(active, 0)) }
        visibility.animateTo(1f, specs.fade)
        var returning = false
        snapshotFlow { browsing to active }.collect { (isBrowsing, line) ->
            if (isBrowsing) {
                returning = true
            } else {
                moveTo(state, maxOf(line, 0), calm = returning)
                returning = false
            }
        }
    }

    private suspend fun moveTo(
        state: LazyListState,
        index: Int,
        calm: Boolean,
    ) {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            fadeTo(state, index)
            return
        }
        val pending = glides.sumOf { if (it.scrolling) (it.delta - it.applied).toDouble() else 0.0 }.toFloat()
        val delta = centreOf(item, info) - info.viewportSize.height / 2f - pending
        if (abs(delta) < 1f) return
        // A return to a line in view always glides (the whole text moves as one); a next line
        // that is suddenly far away (a seek) fades through instead of racing past.
        if (!calm && abs(delta) > info.viewportSize.height * FarJumpShare) {
            fadeTo(state, index)
            return
        }
        glides +=
            Glide(
                delta = delta,
                target = index,
                spec = (if (calm) specs.settle else specs.rise).vectorize(Float.VectorConverter),
                cascade = !calm && specs.cascade,
            )
        if (ticker?.isActive != true) ticker = scope.launch { tick() }
        if (scroller?.isActive != true) scroller = scope.launch { scroll(state) }
    }

    /** Out, re-centre on [index] at once, in: a long jump that would only blur past. */
    private suspend fun fadeTo(
        state: LazyListState,
        index: Int,
    ) {
        scroller?.cancel()
        glides.clear()
        clockNanos = 0L
        visibility.animateTo(0f, specs.fade)
        guarded { centre(state, index) }
        visibility.animateTo(1f, specs.fade)
    }

    private suspend fun centre(
        state: LazyListState,
        index: Int,
    ) {
        state.scrollToItem(index)
        var item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            withFrameNanos { }
            item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        }
        val info = state.layoutInfo
        state.scrollBy(centreOf(item, info) - info.viewportSize.height / 2f)
    }

    /** Advances the shared clock the line offsets are read from, while any glide is alive. */
    private suspend fun tick() {
        timeScale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        while (glides.isNotEmpty()) {
            withFrameNanos { now ->
                glides.forEach { it.begin(now) }
                glides.removeAll { it.settled(now, timeScale) }
                clockNanos = now
            }
        }
    }

    /** Scrolls the list along the glides; the reader's drag takes over at any time. */
    private suspend fun scroll(state: LazyListState) {
        try {
            state.scroll(MutatePriority.Default) {
                while (glides.any { it.scrolling }) {
                    val now = withFrameNanos { it }
                    var step = 0f
                    for (glide in glides) {
                        if (!glide.scrolling) continue
                        glide.begin(now)
                        val travel = glide.travel(glide.elapsed(now, timeScale))
                        step += travel - glide.applied
                        glide.applied = travel
                        if (travel == glide.delta) glide.scrolling = false
                    }
                    if (step != 0f) scrollBy(step)
                }
            }
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
        } finally {
            // Interrupted: the line offsets still ease out on the clock, the scroll stays put.
            glides.forEach { it.scrolling = false }
        }
    }

    /** Runs a list operation that the reader's drag may interrupt, without ending the caller. */
    private suspend inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
        }
    }
}

/** One move of the text by [delta] px towards line [target]. */
private class Glide(
    val delta: Float,
    val target: Int,
    private val spec: VectorizedFiniteAnimationSpec<AnimationVector1D>,
    private val cascade: Boolean,
) {
    private val from = AnimationVector1D(0f)
    private val to = AnimationVector1D(delta)
    private val durationNanos = spec.getDurationNanos(from, to, from)
    private var startNanos = Unset

    /** How much of [delta] has been scrolled. */
    var applied = 0f

    /** False once the scroll part is done or was interrupted. */
    var scrolling = true

    fun begin(now: Long) {
        if (startNanos == Unset) startNanos = now
    }

    /** Animation time since the glide started, honouring the animator duration scale. */
    fun elapsed(
        now: Long,
        scale: Float,
    ): Long =
        when {
            startNanos == Unset -> 0L
            scale <= 0f -> Long.MAX_VALUE / 2
            else -> ((now - startNanos) / scale).toLong()
        }

    /** Distance travelled after [time] ns of animation time; exactly [delta] once settled. */
    fun travel(time: Long): Float =
        when {
            time <= 0L -> 0f
            time >= durationNanos -> delta
            else -> spec.getValueFromNanos(time, from, to, from).value
        }

    /** How far line [index] trails the scroll: the lines below the target start later. */
    fun lag(
        index: Int,
        now: Long,
        scale: Float,
    ): Float {
        if (!cascade || startNanos == Unset) return 0f
        val time = elapsed(now, scale)
        return travel(time) - travel(time - staggerMillis(index, target, forward = delta > 0f) * NanosPerMilli)
    }

    fun settled(
        now: Long,
        scale: Float,
    ): Boolean {
        if (scrolling || startNanos == Unset) return false
        val tail = if (cascade) MaxStaggerMillis * NanosPerMilli else 0L
        return elapsed(now, scale) >= durationNanos + tail
    }

    private companion object {
        const val Unset = Long.MIN_VALUE
    }
}

/** Centre of [item] from the top of the list's viewport, in px. */
private fun centreOf(
    item: LazyListItemInfo,
    info: LazyListLayoutInfo,
): Float = item.offset - info.viewportStartOffset + item.size / 2f

/** A move longer than this share of the viewport fades through instead of gliding. */
private const val FarJumpShare = 0.8f

private const val NanosPerMilli = 1_000_000L
private const val NoSeek = Int.MIN_VALUE
private const val SeekCatchUpMs = 1_000
private const val SeekTimeoutMs = 1_000L
