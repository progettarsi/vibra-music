/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.lyrics

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  VIBRA LYRICS LIST — synced lyrics without per-frame effects
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  The third-party karaoke view this replaces re-rendered every visible line into fresh
 *  bitmaps on each frame (about seventy texture uploads per frame in a system trace), which
 *  is what made the lyrics crawl. This list draws plain text and moves it with transforms:
 *
 *   • the active line is the last one that has started; it is bright, the others fade out
 *     with their distance from it (alpha and a slight scale — graphics-layer properties,
 *     animated per line only when the active line changes);
 *   • lines far from the active one are blurred a little (RenderEffect on the line's layer,
 *     animated only when the active line changes);
 *   • word-synced lines fill in word by word, smoothly: only the active line's text is
 *     rebuilt per frame;
 *   • the active line rests at [anchorFraction] of the height; to get there every line rises
 *     on its own spring, the ones below one after another, with a clear bounce (LineMotion).
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/**
 * @param currentPosition playback position in ms on the lyrics' clock (read off-composition).
 * @param anchorFraction where the active line rests, as a share of the list's height.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VibraLyricsList(
    lyrics: SyncedLyrics,
    currentPosition: () -> Int,
    listState: LazyListState,
    textColor: Color,
    textStyle: TextStyle,
    accompanimentStyle: TextStyle,
    showTranslation: Boolean,
    onLineClicked: (ISyncedLine) -> Unit,
    onLinePressed: (ISyncedLine) -> Unit,
    modifier: Modifier = Modifier,
    anchorFraction: Float = 0.38f,
) {
    val lines = lyrics.lines
    // The active line is known before the first frame, so a list opened mid-song never shows
    // its beginning for an instant (and the lines never animate from wrong values).
    var active by remember(lines) { mutableIntStateOf(activeLineIndex(lines, currentPosition())) }
    var placed by remember(lines) { mutableStateOf(false) }
    val shown by animateFloatAsState(if (placed) 1f else 0f, tween(PlaceFadeMillis), label = "lyricsPlaced")
    val motion = remember(lines) { LineMotion() }
    LaunchedEffect(lines) {
        snapshotFlow { activeLineIndex(lines, currentPosition()) }.collect { active = it }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val anchorPx = with(density) { maxHeight.toPx() * anchorFraction }
        // Placement without animation: on opening, on a new track, and whenever the layout
        // changes (a resize must not make the lines bounce).
        LaunchedEffect(lines, anchorPx) {
            listState.scrollToItem(maxOf(active, 0), -anchorPx.toInt())
            placed = true
        }
        LaunchedEffect(active) {
            if (placed) motion.advance(listState, maxOf(active, 0), anchorPx)
        }

        LazyColumn(
            state = listState,
            // The first line starts where the active line rests, so the intro needs no travel.
            contentPadding = PaddingValues(top = maxHeight * anchorFraction, bottom = maxHeight * (1f - anchorFraction)),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown }.edgeFade(),
        ) {
            itemsIndexed(lines, key = { index, line -> "${line.start}:$index" }) { index, line ->
                // Each line has its own vertical offset, so lines can rise one by one.
                val rise = remember { Animatable(0f) }
                DisposableEffect(rise) {
                    motion.offsets[index] = rise
                    onDispose { if (motion.offsets[index] === rise) motion.offsets.remove(index) }
                }
                LyricLine(
                    line = line,
                    riseY = { rise.value },
                    distance = if (active < 0) index + 1 else abs(index - active),
                    isActive = index == active,
                    currentPosition = currentPosition,
                    textColor = textColor,
                    textStyle = textStyle,
                    accompanimentStyle = accompanimentStyle,
                    showTranslation = showTranslation,
                    onClick = { onLineClicked(line) },
                    onLongClick = { onLinePressed(line) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricLine(
    line: ISyncedLine,
    riseY: () -> Float,
    distance: Int,
    isActive: Boolean,
    currentPosition: () -> Int,
    textColor: Color,
    textStyle: TextStyle,
    accompanimentStyle: TextStyle,
    showTranslation: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val endAligned = (line as? KaraokeLine)?.alignment == KaraokeAlignment.End
    val alpha by animateFloatAsState(
        targetValue = lineAlpha(distance),
        animationSpec = tween(LineFadeMillis),
        label = "lyricLineAlpha",
    )
    val blur by animateFloatAsState(
        targetValue = lineBlurDp(distance),
        animationSpec = tween(LineBlurMillis),
        label = "lyricLineBlur",
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else InactiveScale,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "lyricLineScale",
    )
    val align = if (endAligned) TextAlign.End else TextAlign.Start
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = riseY()
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(if (endAligned) 1f else 0f, 0.5f)
                    if (blur > 0.05f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val radius = blur.dp.toPx()
                        renderEffect = BlurEffect(radius, radius, TileMode.Decal)
                    }
                }.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).padding(horizontal = 4.dp),
    ) {
        val syllables = (line as? KaraokeLine)?.syllables
        if (isActive && syllables != null && syllables.size > 1) {
            // Each word fills in smoothly over its own duration. Only this one line is rebuilt
            // per frame (a single text layout), never the whole list.
            val position = currentPosition()
            Text(
                text =
                    buildAnnotatedString {
                        syllables.forEach { syllable ->
                            val span = (syllable.end - syllable.start).coerceAtLeast(1)
                            val fill = ((position - syllable.start).toFloat() / span).coerceIn(0f, 1f)
                            val eased = fill * fill * (3f - 2f * fill)
                            withStyle(SpanStyle(color = textColor.copy(alpha = UnsungAlpha + (1f - UnsungAlpha) * eased))) {
                                append(syllable.content)
                            }
                        }
                    },
                style = textStyle,
                textAlign = align,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(text = line.plainText(), style = textStyle, color = textColor, textAlign = align, modifier = Modifier.fillMaxWidth())
        }
        (line as? KaraokeLine.MainKaraokeLine)?.accompanimentLines?.forEach { backing ->
            Text(
                text = backing.syllables.joinToString("") { it.content },
                style = accompanimentStyle,
                color = textColor.copy(alpha = 0.7f),
                textAlign = align,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        val translation = line.translationText()
        if (showTranslation && !translation.isNullOrBlank()) {
            Text(
                text = translation,
                style = accompanimentStyle,
                color = textColor.copy(alpha = 0.7f),
                textAlign = align,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/** Index of the last line that has started at [position], or -1 before the first. */
private fun activeLineIndex(
    lines: List<ISyncedLine>,
    position: Int,
): Int {
    var low = 0
    var high = lines.lastIndex
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].start <= position) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

/** Blur of a line by its distance from the active one (the lines far away go soft). */
private fun lineBlurDp(distance: Int): Float =
    when (distance) {
        0 -> 0f
        1 -> 1.2f
        2 -> 2.4f
        else -> 3.6f
    }

private fun lineAlpha(distance: Int): Float =
    when (distance) {
        0 -> 1f
        1 -> 0.58f
        2 -> 0.46f
        else -> 0.36f
    }

private fun ISyncedLine.plainText(): String =
    when (this) {
        is KaraokeLine -> syllables.joinToString("") { it.content }
        is SyncedLine -> content
        else -> ""
    }

private fun ISyncedLine.translationText(): String? =
    when (this) {
        is KaraokeLine -> translation
        is SyncedLine -> translation
        else -> null
    }

/** The per-line vertical offsets of the lines currently on screen, by line index. */
@Stable
private class LineMotion {
    val offsets = mutableMapOf<Int, Animatable<Float, AnimationVector1D>>()

    /**
     * Brings line [index] to [anchorPx] from the top the way the old karaoke view did: the list
     * jumps to its new position at once while every line keeps its on-screen place (an offset
     * of the same size, the other way), and then each line rises into its place on its own
     * spring — the lines below the active one one after another, so the text is pulled up
     * line by line, overshoots and settles. A far jump (a seek) just repositions.
     */
    suspend fun advance(
        state: LazyListState,
        index: Int,
        anchorPx: Float,
    ) = coroutineScope {
        val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            state.scrollToItem(index, -anchorPx.toInt())
            return@coroutineScope
        }
        val delta = item.offset - anchorPx
        if (abs(delta) < 1f) return@coroutineScope
        if (abs(delta) > state.layoutInfo.viewportSize.height * FarJumpShare) {
            state.scrollToItem(index, -anchorPx.toInt())
            return@coroutineScope
        }
        val lines = offsets.toMap()
        // Adding to the current offset keeps a line that is still rising from jumping.
        lines.values.forEach { it.snapTo(it.value + delta) }
        state.scrollBy(delta)
        lines.forEach { (line, offset) ->
            launch {
                delay(if (line >= index) minOf(line - index, MaxStagger) * StaggerMillis else 0L)
                offset.animateTo(0f, spring(dampingRatio = RiseDamping, stiffness = RiseStiffness))
            }
        }
    }
}

/** Dissolves the top and bottom of the list into the backdrop. */
private fun Modifier.edgeFade(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush =
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.07f to Color.Black,
                        0.9f to Color.Black,
                        1f to Color.Transparent,
                    ),
                blendMode = BlendMode.DstIn,
            )
        }

/** Each line's rise: a clearly visible overshoot (damping 0.5), the lines below start 45 ms apart. */
private const val RiseDamping = 0.5f
private const val RiseStiffness = 420f
private const val StaggerMillis = 45L
private const val MaxStagger = 6

/** A jump longer than this share of the viewport is not animated. */
private const val FarJumpShare = 0.8f

private const val LineFadeMillis = 220
private const val PlaceFadeMillis = 140
private const val LineBlurMillis = 260
private const val InactiveScale = 0.94f
private const val UnsungAlpha = 0.45f
