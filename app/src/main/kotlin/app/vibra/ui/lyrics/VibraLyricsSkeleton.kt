/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R

internal const val LyricsSkeletonDelayMillis = 250L
private const val EntryDuration = 240
private const val EntryStagger = 62
private const val SweepDelay = 190
private const val SweepDuration = 690
private const val SkeletonExitDuration = 180
private const val VerseEnterDuration = 260
private const val RevealStagger = 27
private val Ease = CubicBezierEasing(0.2f, 0.65f, 0.3f, 1f)
private val BarWidths = listOf(listOf(.64f, .45f), listOf(.83f), listOf(.92f, .63f), listOf(.76f), listOf(.85f, .51f))
private val IntroDuration = SweepDelay + SweepDuration + BarWidths.lastIndex * EntryStagger

@Stable
internal class LyricsEntrance(private val reducedMotion: State<Boolean>) {
    val intro = Animatable(0f)
    val reveal = Animatable(0f)
    var shown by mutableStateOf(false)
    var rowRanks by mutableStateOf<Map<Int, Int>?>(null)
        private set
    val shouldReveal get() = shown && !reducedMotion.value

    suspend fun showAfterDelay() {
        shown = false
        rowRanks = null
        intro.snapTo(0f)
        reveal.snapTo(0f)
        delay(LyricsSkeletonDelayMillis)
        if (reducedMotion.value) intro.snapTo(IntroDuration.toFloat())
        shown = true
    }

    /** Snapshot the initial viewport once. Subsequent scrolling must not replay the entrance. */
    fun onPlaced(info: LazyListLayoutInfo) {
        if (!shouldReveal || rowRanks != null) return
        rowRanks = lyricsVisibleRanks(
            info.visibleItemsInfo.map { LyricViewportRow(it.index, it.offset, it.size) },
            info.viewportStartOffset,
            info.viewportEndOffset,
        )
    }

    fun rowModifier(index: Int): Modifier = Modifier.graphicsLayer {
        val ranks = rowRanks
        val rank = ranks?.get(index)
        val fraction = when {
            ranks == null -> 0f
            rank == null -> 1f
            else -> Ease.transform(lyricsPhase(reveal.value, rank * RevealStagger, VerseEnterDuration))
        }
        alpha = fraction
        translationY = (1f - fraction) * 4.dp.toPx()
    }
}

@Composable
internal fun rememberLyricsEntrance(
    mediaId: String?,
    active: Boolean,
    loading: Boolean,
    hasLyrics: Boolean,
): LyricsEntrance {
    val motionScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val reduced = LocalAnimationsDisabled.current || motionScale?.scaleFactor == 0f
    val latestReduced = rememberUpdatedState(reduced)
    val entrance = remember(mediaId, active) { LyricsEntrance(latestReduced) }
    LaunchedEffect(entrance, loading) {
        if (active && loading) entrance.showAfterDelay()
    }
    LaunchedEffect(entrance, loading, entrance.shown, reduced) {
        if (loading && entrance.shown) {
            val target = IntroDuration.toFloat()
            if (reduced) {
                entrance.intro.snapTo(target)
            } else {
                entrance.intro.animateTo(target, tween((target - entrance.intro.value).toInt(), easing = LinearEasing))
            }
        }
    }
    val ranks = entrance.rowRanks
    LaunchedEffect(entrance, loading, hasLyrics, entrance.shown, ranks, reduced) {
        if (!loading && entrance.shown) {
            if (!hasLyrics || reduced) {
                entrance.shown = false
            } else if (ranks != null) {
                val duration = maxOf(
                    SkeletonExitDuration + BarWidths.lastIndex * RevealStagger,
                    VerseEnterDuration + (ranks.size - 1).coerceAtLeast(0) * RevealStagger,
                )
                entrance.reveal.animateTo(duration.toFloat(), tween(duration, easing = LinearEasing))
                entrance.shown = false
            }
        }
    }
    return entrance
}

@Composable
internal fun VibraLyricsSkeleton(
    entrance: LyricsEntrance,
    color: Color,
    textSize: TextUnit,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.vibra_lyrics_loading)
    Canvas(
        modifier.fillMaxSize().clearAndSetSemantics {
            if (loading) {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                liveRegion = LiveRegionMode.Polite
            }
        },
    ) {
        val gutter = 8.dp.toPx()
        val width = (size.width - 2 * gutter).coerceAtLeast(0f)
        if (width == 0f || size.height <= 0f) return@Canvas
        // Five groups contain eight bars, three interline gaps and four verse gaps.
        val unit = min(textSize.toPx(), size.height * .85f / 10.4f)
        val height = unit * .46f
        val lineGap = unit * .36f
        val verseGap = unit * 1.41f
        val groupBlockHeight = 8 * height + 3 * lineGap + 4 * verseGap
        val rtl = layoutDirection == LayoutDirection.Rtl
        val elapsed = entrance.intro.value
        val reveal = entrance.reveal.value
        val corner = CornerRadius(height / 2f)
        var y = (size.height - groupBlockHeight) / 2f
        BarWidths.forEachIndexed { index, bars ->
            val delay = index * EntryStagger
            val enter = Ease.transform(lyricsPhase(elapsed, delay, EntryDuration))
            val exit = Ease.transform(lyricsPhase(reveal, index * RevealStagger, SkeletonExitDuration))
            val visibility = enter * (1f - exit)
            val sweep = Ease.transform(lyricsPhase(elapsed, delay + SweepDelay, SweepDuration))
            val dy = (1f - enter) * -5.dp.toPx()
            val barHeight = height * (1f - .28f * exit)
            bars.forEach { fraction ->
                if (visibility > 0f) {
                    val barWidth = width * fraction
                    val x = if (rtl) size.width - gutter - barWidth else gutter
                    val top = Offset(x, y + dy + (height - barHeight) / 2f)
                    val barSize = Size(barWidth, barHeight)
                    val opacity = if (index == 0 || index == BarWidths.lastIndex) .13f else .21f
                    drawRoundRect(color.copy(alpha = color.alpha * opacity * visibility), top, barSize, corner)
                    if (sweep > 0f && sweep < 1f) {
                        val centre = x + barWidth * (1.6f * (if (rtl) 1f - sweep else sweep) - .3f)
                        val spread = barWidth * .28f
                        val brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, color.copy(alpha = color.alpha * .15f * visibility), Color.Transparent),
                            startX = centre - spread,
                            endX = centre + spread,
                        )
                        drawRoundRect(brush, top, barSize, corner)
                    }
                }
                y += height + lineGap
            }
            y += verseGap - lineGap
        }
    }
}

internal data class LyricViewportRow(val index: Int, val offset: Int, val size: Int)

internal fun lyricsVisibleRanks(rows: List<LyricViewportRow>, start: Int, end: Int): Map<Int, Int> =
    rows.asSequence()
        .filter { it.size > 0 && it.offset < end && it.offset + it.size > start }
        .sortedBy { it.offset }
        .mapIndexed { rank, row -> row.index to rank }
        .toMap()

internal fun lyricsPhase(elapsed: Float, delay: Int, duration: Int): Float =
    ((elapsed - delay) / duration).coerceIn(0f, 1f)
