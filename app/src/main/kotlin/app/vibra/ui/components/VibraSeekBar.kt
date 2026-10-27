/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.components

import app.vibra.ui.motion.ambientMotionAllowed
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSpeed
import app.vibra.ui.motion.detectOwnedHorizontalDragGestures
import app.vibra.ui.theme.VibraDimens
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.collectLatest
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.makeTimeString

/**
 * The player's progress bar: a thin M3 Expressive slider (Figma "playing menu MD3").
 *
 * Look: the played part is a soft wave while music plays ([wavy]) and flattens into a
 * line when paused or held; a short vertical thumb marks the position; the rest of the
 * track is a thinner line after a small gap.
 *
 * Interaction model:
 *  - Touch anywhere on the 36 dp tall hit area to grab; the line thickens and the thumb
 *    grows with a spatial spring so it is obvious the bar is "held".
 *  - While scrubbing, a light haptic tick marks every 10 % so position can be felt.
 *  - Release commits the seek once (no seeking storm while dragging).
 *  - TalkBack: exposed as an adjustable progress bar with a time description.
 *
 * @param scrubbing reports the in-progress fraction (null when idle) so callers can
 *        update the time labels live.
 */
@Composable
fun VibraSeekBar(
    fraction: Float,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onScrub: (Float?) -> Unit = {},
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    thickness: Dp = VibraDimens.ProgressTrack,
    wavy: Boolean = false,
    waveMoving: () -> Boolean = { true },
) {
    val haptics = rememberVibraHaptics()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnScrub by rememberUpdatedState(onScrub)

    val shown = if (dragging) dragFraction else fraction
    // Glide between 250 ms position samples so the fill moves continuously.
    val animatedFraction by animateFloatAsState(
        targetValue = shown,
        animationSpec = if (dragging) Liquid.spatial(LiquidSpeed.Fast) else Liquid.effects(LiquidSpeed.Slow),
        label = "seekFraction",
    )
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) thickness * 2 else thickness,
        animationSpec = Liquid.spatial(LiquidSpeed.Fast),
        label = "seekThickness",
    )
    // The wave is ambient motion: flat in battery saver too.
    val animationsDisabled = !ambientMotionAllowed
    val waveOn = wavy && !dragging && !animationsDisabled
    val amplitude by animateFloatAsState(
        targetValue = if (waveOn) 1f else 0f,
        animationSpec = Liquid.spatial(LiquidSpeed.Slow),
        label = "seekWave",
    )
    val phase = remember { Animatable(0f) }
    val moving by rememberUpdatedState(waveMoving)
    LaunchedEffect(waveOn) {
        if (!waveOn) return@LaunchedEffect
        // Held still while nobody sees it (the closed player), and resumed where it stopped.
        snapshotFlow { moving() }.collectLatest { movingNow ->
            while (movingNow) {
                if (phase.value >= 1f) phase.snapTo(0f)
                val remaining = ((1f - phase.value) * WavePeriodMillis).roundToInt().coerceAtLeast(1)
                phase.animateTo(1f, tween(remaining, easing = LinearEasing))
            }
        }
    }
    val seekLabel = stringResource(R.string.vibra_seek)
    val positionText = makeTimeString((shown * durationMs).toLong())

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics {
                    contentDescription = seekLabel
                    stateDescription = positionText
                    progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                    setProgress { target ->
                        latestOnSeek(target.coerceIn(0f, 1f))
                        true
                    }
                }.pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        haptics.press()
                        latestOnSeek((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }.pointerInput(durationMs) {
                    var lastDecile = -1
                    detectOwnedHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            lastDecile = (dragFraction * 10).toInt()
                            haptics.longPress()
                            latestOnScrub(dragFraction)
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            dragFraction = (dragFraction + delta / size.width).coerceIn(0f, 1f)
                            val decile = (dragFraction * 10).toInt()
                            if (decile != lastDecile) {
                                lastDecile = decile
                                haptics.tick()
                            }
                            latestOnScrub(dragFraction)
                        },
                        onDragEnd = {
                            haptics.gestureEnd()
                            latestOnSeek(dragFraction)
                            dragging = false
                            latestOnScrub(null)
                        },
                        onDragCancel = {
                            dragging = false
                            latestOnScrub(null)
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        val wavePath = remember { Path() }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = trackHeight.toPx()
            val cy = size.height / 2f
            val thumbX = size.width * animatedFraction
            val thumbWidth = ThumbWidth.toPx()
            val gap = ThumbGap.toPx()
            // Remaining track: thinner, after a gap from the thumb.
            val restStart = thumbX + thumbWidth / 2f + gap
            if (restStart < size.width) {
                drawLine(
                    color = trackColor,
                    start = Offset(restStart, cy),
                    end = Offset(size.width - stroke * 0.35f, cy),
                    strokeWidth = stroke * 0.7f,
                    cap = StrokeCap.Round,
                )
            }
            // Played part: a line that ripples into a wave while playing.
            val playedEnd = thumbX - thumbWidth / 2f - gap
            if (playedEnd > stroke / 2f) {
                val amp = WaveAmplitude.toPx() * amplitude
                val wavelength = WaveLength.toPx()
                wavePath.reset()
                wavePath.moveTo(stroke / 2f, cy)
                var x = stroke / 2f
                val step = 2.dp.toPx()
                while (x < playedEnd) {
                    x = (x + step).coerceAtMost(playedEnd)
                    // The wave fades in from the start so the line begins flat.
                    val ramp = (x / wavelength).coerceAtMost(1f)
                    val y = cy + amp * ramp * sin((x / wavelength - phase.value) * 2f * PI.toFloat())
                    wavePath.lineTo(x, y)
                }
                drawPath(wavePath, color = fillColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
            // Thumb: a short rounded bar.
            val thumbHeight = (ThumbHeight.toPx() + (stroke - thickness.toPx()) * 2f).coerceAtMost(size.height)
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(thumbX - thumbWidth / 2f, cy - thumbHeight / 2f),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbWidth / 2f),
            )
        }
    }
}

private val ThumbWidth = 4.dp
private val ThumbHeight = 18.dp
private val ThumbGap = 4.dp
private val WaveAmplitude = 3.dp
private val WaveLength = 28.dp
private const val WavePeriodMillis = 1_600
