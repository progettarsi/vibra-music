/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.lyrics

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSpeed
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import moe.rukamori.archivetune.LocalAnimationsDisabled

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  VIBRA LYRICS LIST — synced lyrics, word by word, without per-frame work on the list
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  Plain text moved and shaded by graphics layers (a third-party karaoke view that rendered
 *  every line into fresh bitmaps per frame was what made the lyrics crawl):
 *
 *   • the sung line sits in the middle of the list and the text glides to the next one,
 *     the lines below following one after another (LyricsFollow);
 *   • lines fade and soften with their distance from the sung one; the sung line's words
 *     light up one by one, drawn with a per-word gradient in the draw phase, so the text is
 *     never re-laid out or recomposed while it is sung;
 *   • a line that becomes active keeps its brightness: only its words light up, and when
 *     it is over they fade back down (no flash in between);
 *   • dragging the text lets the reader browse it sharp; three seconds after the last touch
 *     it glides back to the line being sung. Tapping a line plays from there.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/**
 * @param currentPosition playback position in ms on the lyrics' clock (read off-composition).
 * @param onLineClicked a line was tapped; returns true when playback seeks to it, which also
 *   brings the text back to following.
 */
@Composable
fun VibraLyricsList(
    lyrics: SyncedLyrics,
    currentPosition: () -> Int,
    textColor: Color,
    textStyle: TextStyle,
    accompanimentStyle: TextStyle,
    showTranslation: Boolean,
    onLineClicked: (ISyncedLine) -> Boolean,
    onLinePressed: (ISyncedLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = lyrics.lines
    val position = rememberUpdatedState(currentPosition)
    val scope = rememberCoroutineScope()
    // The active line is known before the first frame, so a list opened mid-song never shows
    // its beginning for an instant.
    val follow =
        remember(lines) {
            val starts = IntArray(lines.size) { lines[it].start }
            LyricsFollow(starts = starts, initialActive = lineAt(starts, position.value()), scope = scope)
        }
    val listState = remember(lines) { LazyListState() }
    val specs =
        FollowSpecs(
            rise = Liquid.spatial(),
            settle = Liquid.container(),
            fade = Liquid.effects(),
            focus = Liquid.container(),
            cascade = !LocalAnimationsDisabled.current,
        )
    SideEffect { follow.specs = specs }

    LaunchedEffect(follow) {
        snapshotFlow { position.value() }.collect { follow.onPosition(it) }
    }
    LaunchedEffect(follow, listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) follow.startBrowsing()
        }
    }
    // Back to the sung line once the text has rested, untouched, for a while.
    LaunchedEffect(follow, listState) {
        snapshotFlow { follow.browsing && !follow.touching && !listState.isScrollInProgress }
            .collectLatest { resting ->
                if (resting) {
                    delay(ResyncAfterMillis)
                    follow.stopBrowsing()
                }
            }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(follow) {
                    // Observes fingers without consuming them: scrolling and taps stay the list's.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        follow.touching = true
                        try {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                            } while (event.changes.any { it.pressed })
                        } finally {
                            follow.touching = false
                        }
                    }
                },
    ) {
        // Placement without animation on opening and whenever the height changes.
        LaunchedEffect(follow, constraints.maxHeight) { follow.drive(listState) }
        val half = maxHeight / 2
        LazyColumn(
            state = listState,
            // Half the height at both ends: the first and the last line can rest in the middle.
            contentPadding = PaddingValues(top = half, bottom = half),
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = follow.visibility.value },
        ) {
            itemsIndexed(lines, key = { index, line -> "${line.start}:$index" }) { index, line ->
                LyricLine(
                    line = line,
                    index = index,
                    distance = lineDistance(index, follow.active),
                    follow = follow,
                    position = position.value,
                    textColor = textColor,
                    textStyle = textStyle,
                    accompanimentStyle = accompanimentStyle,
                    showTranslation = showTranslation,
                    onClick = { if (onLineClicked(line)) follow.seekedTo(index) },
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
    index: Int,
    distance: Int,
    follow: LyricsFollow,
    position: () -> Int,
    textColor: Color,
    textStyle: TextStyle,
    accompanimentStyle: TextStyle,
    showTranslation: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val active = distance == 0
    val lit =
        animateFloatAsState(
            targetValue = if (active) 1f else 0f,
            // Sung words fade back down more slowly than a line lights up.
            animationSpec = if (active) Liquid.effects() else Liquid.effects(LiquidSpeed.Slow),
            label = "lyricLit",
        )
    val dim = animateFloatAsState(lineDim(distance), Liquid.effects(), label = "lyricDim")
    val blur = animateFloatAsState(lineBlurDp(distance), Liquid.effects(), label = "lyricBlur")
    val scale = animateFloatAsState(if (active) 1f else InactiveScale, Liquid.spatial(), label = "lyricScale")
    // What is not lit: the depth dimming, evened out while the reader browses.
    val base = { lerp(dim.value, BrowseDim, follow.focus.value) }
    val glow = { lerp(base(), 1f, lit.value) }
    val softness = remember { LineSoftness() }

    val endAligned = (line as? KaraokeLine)?.alignment == KaraokeAlignment.End
    val align = if (endAligned) TextAlign.End else TextAlign.Start
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                // Two layers: the offset changes every frame of a glide, the look only when
                // the line's distance or the browsing focus changes.
                .graphicsLayer { translationY = follow.offset(index) }
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    transformOrigin = TransformOrigin(if (endAligned) 1f else 0f, 0.5f)
                    renderEffect = softness.effect((blur.value * (1f - follow.focus.value)).dp.toPx())
                }.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    // The gap between lines is part of each line, so there is nowhere to tap in vain.
                ).padding(horizontal = 4.dp, vertical = LineGap / 2),
    ) {
        val syllables = (line as? KaraokeLine)?.syllables
        if (!syllables.isNullOrEmpty()) {
            SungText(
                syllables = syllables,
                style = textStyle,
                color = textColor,
                align = align,
                base = base,
                lit = { lit.value },
                position = position,
            )
        } else {
            Text(
                text = line.plainText(),
                style = textStyle,
                color = textColor,
                textAlign = align,
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = glow() },
            )
        }
        val backing = (line as? KaraokeLine.MainKaraokeLine)?.accompanimentLines.orEmpty()
        val translation = line.translationText()?.takeIf { showTranslation && it.isNotBlank() }
        if (backing.isNotEmpty() || translation != null) {
            Column(modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = SecondaryAlpha * glow() }) {
                backing.forEach { accompaniment ->
                    Text(
                        text = accompaniment.syllables.joinToString("") { it.content },
                        style = accompanimentStyle,
                        color = textColor,
                        textAlign = align,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
                if (translation != null) {
                    Text(
                        text = translation,
                        style = accompanimentStyle,
                        color = textColor,
                        textAlign = align,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * A word-synced line. Laid out once; in the draw phase each visual row is painted with a
 * horizontal gradient that holds every word at its own brightness — from [base] up to full
 * as the word is sung (times [lit], which fades the line back down when it is over). The
 * position is only read while the line is lit, so the other lines never redraw for it.
 */
@Composable
private fun SungText(
    syllables: List<KaraokeSyllable>,
    style: TextStyle,
    color: Color,
    align: TextAlign,
    base: () -> Float,
    lit: () -> Float,
    position: () -> Int,
) {
    val words = remember(syllables) { SungWords(syllables) }
    Text(
        text = words.text,
        style = style,
        color = color,
        textAlign = align,
        onTextLayout = words::onLayout,
        modifier =
            Modifier.fillMaxWidth().drawWithContent {
                val layout = words.layout
                if (layout == null) drawContent() else words.draw(this, layout, color, base(), lit(), position)
            },
    )
}

/** Where each word of a sung line is, row by row, once the line is laid out. */
private class SungWords(
    private val syllables: List<KaraokeSyllable>,
) {
    val text = syllables.joinToString("") { it.content }
    var layout: TextLayoutResult? = null
        private set
    private var rows: List<List<WordSpan>> = emptyList()

    fun onLayout(result: TextLayoutResult) {
        layout = result
        rows = wordRows(result)
    }

    fun draw(
        scope: DrawScope,
        layout: TextLayoutResult,
        color: Color,
        base: Float,
        lit: Float,
        position: () -> Int,
    ) = with(scope) {
        if (lit <= 0.001f) {
            drawText(layout, color = color.copy(alpha = color.alpha * base))
            return@with
        }
        val now = position()
        val width = size.width.coerceAtLeast(1f)
        val lastRow = layout.lineCount - 1
        for (row in 0..lastRow) {
            val spans = rows.getOrNull(row).orEmpty()
            // Generous bands: glyphs that reach past their row (accents, italics) stay whole.
            val top = if (row == 0) -size.height else layout.getLineTop(row)
            val bottom = if (row == lastRow) size.height * 2f else layout.getLineBottom(row)
            clipRect(left = -width, top = top, right = width * 2f, bottom = bottom) {
                drawText(layout, brush = rowBrush(spans, color, base, lit, now, width))
            }
        }
    }

    /** Each word's brightness between its left and right edge (one colour when the row is even). */
    private fun rowBrush(
        spans: List<WordSpan>,
        color: Color,
        base: Float,
        lit: Float,
        position: Int,
        width: Float,
    ): Brush {
        if (spans.isEmpty()) return SolidColor(color.copy(alpha = color.alpha * base))
        val alphas =
            FloatArray(spans.size) { i ->
                val word = syllables[spans[i].syllable]
                lerp(base, 1f, wordFill(word.start, word.end, position) * lit)
            }
        if (alphas.all { it == alphas[0] }) return SolidColor(color.copy(alpha = color.alpha * alphas[0]))
        val stops = ArrayList<Pair<Float, Color>>(spans.size * 2 + 2)
        var last = 0f
        fun stop(
            x: Float,
            alpha: Float,
        ) {
            last = (x / width).coerceIn(last, 1f)
            stops += last to color.copy(alpha = color.alpha * alpha)
        }
        stop(0f, alphas.first())
        spans.forEachIndexed { i, span ->
            stop(span.left, alphas[i])
            stop(span.right, alphas[i])
        }
        stop(width, alphas.last())
        return Brush.horizontalGradient(*stops.toTypedArray(), startX = 0f, endX = width)
    }

    private fun wordRows(result: TextLayoutResult): List<List<WordSpan>> {
        val rows = List(result.lineCount) { ArrayList<WordSpan>() }
        val length = result.layoutInput.text.length
        var offset = 0
        syllables.forEachIndexed { index, syllable ->
            val start = offset
            val end = (offset + syllable.content.length).coerceAtMost(length)
            offset = end
            if (end <= start) return@forEachIndexed
            val firstRow = result.getLineForOffset(start)
            val lastRow = result.getLineForOffset(end - 1)
            for (row in firstRow..lastRow) {
                val from = if (row == firstRow) start else result.getLineStart(row)
                val to = if (row == lastRow) end else result.getLineEnd(row)
                if (to <= from) continue
                val head = result.getBoundingBox(from)
                val tail = result.getBoundingBox(to - 1)
                rows[row] += WordSpan(index, minOf(head.left, tail.left), maxOf(head.right, tail.right))
            }
        }
        rows.forEach { row -> row.sortBy { it.left } }
        return rows
    }
}

/** The part of word [syllable] that lies on one visual row, in px. */
private class WordSpan(
    val syllable: Int,
    val left: Float,
    val right: Float,
)

/** Keeps one blur effect per radius, so a resting line does not rebuild it on every layer update. */
private class LineSoftness {
    private var radius = 0f
    private var effect: RenderEffect? = null

    fun effect(px: Float): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val rounded = (px * 4f).roundToInt() / 4f
        if (rounded < MinBlurPx) return null
        if (rounded != radius || effect == null) {
            radius = rounded
            effect = BlurEffect(rounded, rounded, TileMode.Decal)
        }
        return effect
    }
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

private val LineGap = 18.dp
private const val InactiveScale = 0.94f

/** Brightness of every line that is not lit while the reader browses: even, and easy to read. */
private const val BrowseDim = 0.62f

/** Backing vocals and translations, relative to their line. */
private const val SecondaryAlpha = 0.7f
private const val MinBlurPx = 0.5f
private const val ResyncAfterMillis = 3_000L
