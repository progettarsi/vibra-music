/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.component

// Vibra: independent radii follow the global rounding style.
import app.vibra.ui.theme.adaptiveRoundedCornerShape

import android.app.Activity
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import app.vibra.ui.motion.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import app.vibra.ui.components.artworkAccent
import app.vibra.ui.lyrics.LyricsEntrance
import app.vibra.ui.lyrics.LyricsCardContent
import app.vibra.ui.lyrics.LyricsExportDialog
import app.vibra.ui.lyrics.LyricsSelectionBar
import app.vibra.ui.lyrics.MaxExportVerses
import app.vibra.ui.lyrics.VibraLyricsList
import app.vibra.ui.lyrics.VibraLyricsSkeleton
import app.vibra.ui.lyrics.rememberLyricsEntrance
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSpeed
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsSourceFormat
import moe.rukamori.archivetune.lyrics.LyricsSyncType
import moe.rukamori.archivetune.lyrics.LyricsTextDirection
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedTranslationTextForEntry
import moe.rukamori.archivetune.lyrics.WordTimestamp
import moe.rukamori.archivetune.lyrics.toLyricsEntries
import moe.rukamori.archivetune.ui.theme.rememberArchiveTuneLyricsFontFamily
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.viewmodels.LyricsRenderScreenState
import moe.rukamori.archivetune.viewmodels.LyricsRenderViewModel

private const val LRC_LEAD_MS = 300L
private const val MIN_KARAOKE_SYLLABLE_DURATION_MS = 1

@Composable
fun LyricsEnhanced(
    lyricsState: LyricsRenderScreenState,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    saveLegacy: suspend (Bitmap) -> Unit,
    exportTint: Color,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
    lyricsListModifier: Modifier = Modifier,
    lyricsRenderViewModel: LyricsRenderViewModel = hiltViewModel(),
    paneActive: Boolean = true,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val preparedLyrics = (lyricsState as? LyricsRenderScreenState.Success)?.lyrics
    val preferences = preparedLyrics?.preferences
    val lyricsClick = preferences?.clickEnabled ?: true
    val lyricsTextSize = preferences?.textSizeSp ?: 26f

    val lyricsFontFamily = rememberArchiveTuneLyricsFontFamily()

    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val textColor =
        textColorOverride ?: if (playerBackground == PlayerBackgroundStyle.DEFAULT) {
            MaterialTheme.colorScheme.onBackground
        } else {
            Color.White
        }

    val showTranslations =
        preparedLyrics?.hasTranslations == true && preferences?.showTranslations == true
    val showPhonetics =
        preparedLyrics?.lines?.any { line -> line.romanizedText != null || line.phonetics.isNotEmpty() } == true
    val baseLayoutDirection = LocalLayoutDirection.current
    val lyricsLayoutDirection =
        remember(preparedLyrics?.lines, baseLayoutDirection) {
            if (preparedLyrics?.lines?.firstOrNull { line -> line.text.isNotBlank() }?.direction == LyricsTextDirection.RTL) {
                LayoutDirection.Rtl
            } else {
                baseLayoutDirection
            }
        }
    val lyricsSessionKey =
        remember(mediaMetadata?.id, preparedLyrics?.lines) {
            mediaMetadata?.id.orEmpty() to preparedLyrics?.lines?.map { line -> line.id }
        }

    val isSynced = preparedLyrics?.syncType != null && preparedLyrics.syncType != LyricsSyncType.PLAIN
    val isWordSyncedFormat = preparedLyrics?.syncType == LyricsSyncType.WORD

    val lyricsEntries: List<LyricsEntry> =
        remember(preparedLyrics) {
            preparedLyrics?.toLyricsEntries().orEmpty()
        }

    val romanizationMap =
        remember(preparedLyrics) {
            preparedLyrics
                ?.lines
                ?.mapIndexed { index, line ->
                    val phonetics = MutableList<String?>(line.main.words.size) { null }
                    line.phonetics.forEach { phonetic ->
                        if (phonetic.wordIndex in phonetics.indices) {
                            phonetics[phonetic.wordIndex] = phonetic.text
                        }
                    }
                    index to if (phonetics.any { it != null }) phonetics else listOf(line.romanizedText)
                }?.toMap()
                .orEmpty()
        }
    val syncedLyrics =
        remember(lyricsEntries, isWordSyncedFormat, romanizationMap) {
            buildSyncedLyrics(lyricsEntries, isWordSyncedFormat, romanizationMap)
        }

    val leadMs =
        if (isWordSyncedFormat || preparedLyrics?.sourceFormat == LyricsSourceFormat.TTML) 0L else LRC_LEAD_MS

    val latestSliderPositionProvider = rememberUpdatedState(sliderPositionProvider)
    val latestLyricsSyncOffset = rememberUpdatedState(lyricsSyncOffset)
    val latestLeadMs = rememberUpdatedState(leadMs)
    val playbackPositionMs = lyricsRenderViewModel.playbackPositionMs.collectAsStateWithLifecycle()
    val plainLyricsListState = key(lyricsSessionKey) { rememberLazyListState() }

    var selectedIndices by remember(lyricsSessionKey) { mutableStateOf(emptySet<Int>()) }
    var preview by remember(lyricsSessionKey) { mutableStateOf<LyricsCardContent?>(null) }
    val selecting = selectedIndices.isNotEmpty()
    BackHandler(enabled = selecting && preview == null) { selectedIndices = emptySet() }

    fun toggleVerse(index: Int) {
        selectedIndices = when {
            index in selectedIndices -> selectedIndices - index
            selectedIndices.size < MaxExportVerses -> selectedIndices + index
            else -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.max_selection_limit, MaxExportVerses),
                    Toast.LENGTH_SHORT,
                ).show()
                selectedIndices
            }
        }
    }

    DisposableEffect(player, lyricsRenderViewModel) {
        val listener =
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (
                        events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                        events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                        events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                        events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
                    ) {
                        lyricsRenderViewModel.onPlaybackPositionChanged(
                            latestSliderPositionProvider.value() ?: player.currentPosition,
                        )
                    }
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, lifecycleOwner, lyricsRenderViewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            lyricsRenderViewModel.onPlaybackPositionChanged(
                latestSliderPositionProvider.value() ?: player.currentPosition,
            )
            while (isActive) {
                withFrameNanos {
                    val sliderPosition = latestSliderPositionProvider.value()
                    lyricsRenderViewModel.onPlaybackFrame(
                        positionMs = sliderPosition ?: player.currentPosition,
                        sampleTimeNanos = SystemClock.elapsedRealtimeNanos(),
                        playbackSpeed = player.playbackParameters.speed,
                        isAdvancing = player.isPlaying && sliderPosition == null,
                        durationMs = player.duration,
                    )
                }
            }
        }
    }

    val playbackSyncPosition: () -> Int =
        remember(playbackPositionMs) {
            {
                (
                    playbackPositionMs.value +
                        latestLyricsSyncOffset.value.toLong() +
                        latestLeadMs.value
                ).coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
            }
        }
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val normalTextStyle =
        MaterialTheme.typography.headlineMedium.copy(
            fontSize = lyricsTextSize.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
        )
    val accompanimentTextStyle =
        MaterialTheme.typography.titleLarge.copy(
            fontSize = (lyricsTextSize * 0.82f).sp,
            fontFamily = lyricsFontFamily ?: MaterialTheme.typography.titleLarge.fontFamily,
        )
    val plainLyrics =
        remember(lyricsEntries, isSynced) {
            PlainLyrics(
                items =
                    if (isSynced) {
                        emptyList()
                    } else {
                        lyricsEntries.mapIndexedNotNull { index, entry ->
                            entry.text.trim().takeIf(String::isNotBlank)?.let { PlainLyricLine(index, it) }
                        }
                    },
            )
        }
    fun selectedCard(): LyricsCardContent? {
        val metadata = mediaMetadata ?: return null
        val verses =
            if (isSynced) {
                selectedIndices.sorted().mapNotNull { index ->
                    syncedLyrics.lines.getOrNull(index)?.lineText()?.takeIf(String::isNotBlank)
                }
            } else {
                plainLyrics.items.filter { it.index in selectedIndices }.map { it.text }
            }
        if (verses.isEmpty()) return null
        return LyricsCardContent(
            title = metadata.title,
            subtitle = metadata.album?.title?.takeIf { it.isNotBlank() && it != metadata.title }
                ?: metadata.artists.joinToString { it.name },
            coverUrl = metadata.thumbnailUrl,
            verses = verses,
        )
    }

    // Vibra: loading, lyrics and "not found" cross-fade; a view on its way out keeps its own
    // lines, so the old song's lyrics fade instead of vanishing when the track changes.
    val lyricsView: LyricsView =
        when {
            lyricsState is LyricsRenderScreenState.Loading -> LyricsView.Loading
            lyricsState !is LyricsRenderScreenState.Success -> LyricsView.Missing
            isSynced && syncedLyrics.lines.isEmpty() -> LyricsView.Missing
            !isSynced && plainLyrics.items.isEmpty() -> LyricsView.Missing
            !isSynced -> LyricsView.Plain(plainLyrics, lyricsSessionKey)
            else -> LyricsView.Synced(syncedLyrics, lyricsSessionKey, lyricsLayoutDirection)
        }
    val loading = lyricsView == LyricsView.Loading
    val hasLyrics = lyricsView is LyricsView.Plain || lyricsView is LyricsView.Synced
    val entrance = rememberLyricsEntrance(mediaMetadata?.id, paneActive, loading, hasLyrics)
    val animationsDisabled = LocalAnimationsDisabled.current
    val viewEnter = Liquid.effects<Float>(LiquidSpeed.Slow)
    val viewExit = Liquid.effects<Float>(LiquidSpeed.Fast)

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().then(lyricsListModifier)) {
            AnimatedContent(
                targetState = lyricsView,
                transitionSpec = {
                    // Vibra: the skeleton and the first visible verses own this handoff.
                    val transition =
                        if (initialState == LyricsView.Loading || animationsDisabled) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            fadeIn(viewEnter) togetherWith fadeOut(viewExit)
                        }
                    transition using null
                },
                contentKey = { view -> view.key },
                contentAlignment = Alignment.Center,
                label = "lyricsView",
                modifier = Modifier.fillMaxSize(),
            ) { view ->
                when (view) {
                    LyricsView.Loading -> Unit

                    LyricsView.Missing -> LyricsMissing(textColor = textColor)

                    is LyricsView.Plain -> {
                        val isCurrent = view.session == lyricsSessionKey
                        PlainLyricsView(
                            lines = view.lyrics,
                            listState = plainLyricsListState,
                            selectedIndices = if (isCurrent) selectedIndices else emptySet(),
                            textColor = textColor,
                            textStyle = normalTextStyle,
                            onLineClicked = { index ->
                                if (isCurrent && selecting) toggleVerse(index)
                            },
                            onLinePressed = { index ->
                                if (isCurrent) toggleVerse(index)
                            },
                            modifier = Modifier.fillMaxSize(),
                            entrance = entrance.takeIf { view.key == lyricsView.key },
                        )
                    }

                    is LyricsView.Synced -> {
                        val isCurrent = view.session == lyricsSessionKey
                        CompositionLocalProvider(LocalLayoutDirection provides view.direction) {
                            key(view.session, view.lyrics) {
                                VibraLyricsList(
                                    lyrics = view.lyrics,
                                    currentPosition = playbackSyncPosition,
                                    selectedIndices = if (isCurrent) selectedIndices else emptySet(),
                                    onLineClicked = { index, line ->
                                        when {
                                            !isCurrent -> false
                                            selecting -> {
                                                if (line.lineText().isNotBlank()) toggleVerse(index)
                                                false
                                            }
                                            lyricsClick && line.start > 0 -> {
                                                player.seekTo(line.start.toLong())
                                                true
                                            }
                                            else -> false
                                        }
                                    },
                                    onLinePressed = { index, line ->
                                        if (isCurrent && line.lineText().isNotBlank()) toggleVerse(index)
                                    },
                                    textColor = textColor,
                                    textStyle = normalTextStyle,
                                    accompanimentStyle = accompanimentTextStyle,
                                    showTranslation = showTranslations,
                                    modifier = Modifier.fillMaxSize(),
                                    entrance = entrance.takeIf { view.key == lyricsView.key },
                                )
                            }
                        }
                    }
                }
            }
            if (entrance.shown && (loading || hasLyrics)) {
                VibraLyricsSkeleton(entrance, textColor, normalTextStyle.fontSize, loading)
            }
        }
        if (selecting) {
            LyricsSelectionBar(
                exportTint = exportTint,
                onCancel = { selectedIndices = emptySet() },
                onPreview = { preview = selectedCard() },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }

    preview?.let { content ->
        LyricsExportDialog(
            content = content,
            // Taken once when the card opens, so the preview is not redrawn while the accent fades.
            accent = remember(content) { artworkAccent(exportTint) },
            onDismiss = { preview = null },
            saveLegacy = saveLegacy,
        )
    }
}

@Immutable
private data class PlainLyrics(
    val items: List<PlainLyricLine>,
)

/** What the lyrics pane shows; the lines travel with it (see the cross-fade in LyricsEnhanced). */
@Immutable
private sealed interface LyricsView {
    /** Identity for the cross-fade: a new song's lyrics are a new view, a re-prepared one is not. */
    val key: Any

    data object Loading : LyricsView {
        override val key: Any get() = this
    }

    data object Missing : LyricsView {
        override val key: Any get() = this
    }

    data class Plain(
        val lyrics: PlainLyrics,
        val session: Any,
    ) : LyricsView {
        override val key: Any get() = "plain" to session
    }

    data class Synced(
        val lyrics: SyncedLyrics,
        val session: Any,
        val direction: LayoutDirection,
    ) : LyricsView {
        override val key: Any get() = "synced" to session
    }
}

@Composable
private fun LyricsMissing(textColor: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.lyrics_not_found),
            style = MaterialTheme.typography.bodyLarge,
            color = textColor.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

@Immutable
private data class PlainLyricLine(val index: Int, val text: String)

@Composable
private fun PlainLyricsView(
    lines: PlainLyrics,
    listState: LazyListState,
    selectedIndices: Set<Int>,
    textColor: Color,
    textStyle: TextStyle,
    onLineClicked: (Int) -> Unit,
    onLinePressed: (Int) -> Unit,
    modifier: Modifier = Modifier,
    entrance: LyricsEntrance? = null,
) {
    val reveal = entrance?.takeIf { it.shouldReveal }
    val waitingForRows = reveal != null && reveal.rowRanks == null
    LaunchedEffect(reveal, listState) {
        if (reveal != null && reveal.rowRanks == null) {
            val layout = snapshotFlow { listState.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
            reveal.onPlaced(layout)
        }
    }
    val contentPadding =
        remember {
            PaddingValues(
                start = 12.dp,
                top = 120.dp,
                end = 12.dp,
                bottom = 96.dp,
            )
        }

    LazyColumn(
        state = listState,
        modifier = modifier.semantics { if (waitingForRows) invisibleToUser() },
        userScrollEnabled = !waitingForRows,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            items = lines.items,
            key = { _, line -> line.index },
            contentType = { _, _ -> "plain_lyric_line" },
        ) { index, line ->
            PlainLyricLineItem(
                line = line,
                selected = line.index in selectedIndices,
                textColor = textColor,
                textStyle = textStyle,
                onLineClicked = onLineClicked,
                onLinePressed = onLinePressed,
                modifier = reveal?.rowModifier(index) ?: Modifier,
                enabled = !waitingForRows,
            )
        }
    }
}

@Composable
private fun PlainLyricLineItem(
    line: PlainLyricLine,
    selected: Boolean,
    textColor: Color,
    textStyle: TextStyle,
    onLineClicked: (Int) -> Unit,
    onLinePressed: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = line.text,
        style = textStyle,
        color = textColor,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(
                    if (selected) Color.White.copy(alpha = 0.20f) else Color.Transparent,
                    RoundedCornerShape(16.dp),
                ).semantics { this.selected = selected }
                .combinedClickable(
                    enabled = enabled,
                    onClick = { onLineClicked(line.index) },
                    onLongClickLabel = stringResource(R.string.vibra_lyrics_select_verse),
                    onLongClick = { onLinePressed(line.index) },
                ).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun ISyncedLine.lineText(): String =
    when (this) {
        is KaraokeLine -> syllables.joinToString("") { it.content }
        is SyncedLine -> content
        else -> ""
    }

private fun List<WordTimestamp>.toKaraokeSyllables(phonetics: List<String?>): List<KaraokeSyllable> =
    mapIndexed { index, word ->
        val start = word.startTime.toMilliseconds()
        val end = word.endTime.toMilliseconds()

        KaraokeSyllable(
            content = word.text,
            start = start,
            end = end.coerceAtLeast(start + MIN_KARAOKE_SYLLABLE_DURATION_MS),
            phonetic = phonetics.getOrNull(index),
        )
    }

private fun Double.toMilliseconds(): Int = (this * 1000.0).roundToInt().coerceAtLeast(0)

private fun buildSyncedLyrics(
    entries: List<LyricsEntry>,
    isWordSynced: Boolean,
    romanizationMap: Map<Int, List<String?>>,
): SyncedLyrics {
    if (entries.isEmpty()) return SyncedLyrics(emptyList())
    val lines = mutableListOf<ISyncedLine>()

    entries.forEachIndexed { index, entry ->
        if (entry.time < 0L) return@forEachIndexed
        if (entry.isInstrumental) return@forEachIndexed
        if (entry.text.isBlank() && entry.words.isNullOrEmpty()) return@forEachIndexed

        if (isWordSynced && entry.words != null) {
            val translation = providedTranslationTextForEntry(entry)
            val mainWords = entry.words!!.filter { !it.isBackground }
            val bgWords = entry.words!!.filter { it.isBackground }
            val alignment =
                when (entry.agent?.lowercase()) {
                    "v2" -> KaraokeAlignment.End
                    else -> KaraokeAlignment.Start
                }

            val wordsForMain = if (mainWords.isNotEmpty()) mainWords else entry.words!!
            val wordPhonetics = romanizationMap[index] ?: emptyList()
            val mainSyllables = wordsForMain.toKaraokeSyllables(wordPhonetics)

            val lineStart = entry.time.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            val lineEnd =
                if (entry.durationMs > 0L) {
                    (entry.time + entry.durationMs).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                } else {
                    mainSyllables.maxOf(KaraokeSyllable::end)
                }
            if (lineEnd <= lineStart) return@forEachIndexed

            val accompanimentLines =
                if (mainWords.isNotEmpty() && bgWords.isNotEmpty()) {
                    val bgSyllables = bgWords.toKaraokeSyllables(emptyList())
                    val bgStart = bgSyllables.minOf(KaraokeSyllable::start)
                    val bgEnd = bgSyllables.maxOf(KaraokeSyllable::end)
                    if (bgEnd > bgStart) {
                        listOf(
                            KaraokeLine.AccompanimentKaraokeLine(
                                syllables = bgSyllables,
                                translation = null,
                                alignment = alignment,
                                start = bgStart,
                                end = bgEnd,
                                phonetic = null,
                            ),
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }

            lines.add(
                KaraokeLine.MainKaraokeLine(
                    syllables = mainSyllables,
                    translation = translation,
                    alignment = alignment,
                    start = lineStart,
                    end = lineEnd,
                    phonetic = null,
                    accompanimentLines = accompanimentLines,
                ),
            )
        } else {
            val nextEntry = entries.getOrNull(index + 1)
            val lineEnd =
                if (entry.durationMs > 0L) {
                    (entry.time + entry.durationMs).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                } else if (nextEntry != null && nextEntry.time > entry.time) {
                    val gap = nextEntry.time - entry.time
                    if (gap > 3000L) {
                        minOf((nextEntry.time - 1L).toInt(), (entry.time + 4000L).toInt())
                            .coerceAtLeast(entry.time.toInt() + 1)
                    } else {
                        (nextEntry.time - 1L).coerceAtLeast(entry.time + 1L).toInt()
                    }
                } else {
                    (entry.time + 4000L).toInt()
                }
            lines.add(
                buildLineSyncedLrcLine(
                    entry = entry,
                    romanizedText = romanizationMap[index]?.firstOrNull(),
                    start = entry.time.toInt(),
                    end = lineEnd,
                ),
            )
        }
    }

    return SyncedLyrics(lines = lines)
}

private fun buildLineSyncedLrcLine(
    entry: LyricsEntry,
    romanizedText: String?,
    start: Int,
    end: Int,
): ISyncedLine {
    val translation = providedTranslationTextForEntry(entry)
    val normalizedRomanizedText = romanizedText?.trim()?.takeIf { it.isNotEmpty() }

    if (normalizedRomanizedText == null) {
        return SyncedLine(
            content = entry.text,
            translation = translation,
            start = start,
            end = end,
        )
    }

    val syllables =
        buildWrappingKaraokeSyllables(
            content = entry.text,
            romanizedText = normalizedRomanizedText,
            start = start,
            end = end,
        )

    return KaraokeLine.MainKaraokeLine(
        syllables = syllables,
        translation = translation,
        alignment = KaraokeAlignment.Start,
        start = start,
        end = end,
    )
}

private fun buildWrappingKaraokeSyllables(
    content: String,
    romanizedText: String,
    start: Int,
    end: Int,
): List<KaraokeSyllable> {
    val contentUnits = content.toLyricsWrappingUnits().ifEmpty { listOf(content) }
    val phoneticWords = romanizedText.split(Regex("\\s+")).filter(String::isNotEmpty)
    val phoneticAnchorIndices =
        contentUnits.indices.filter { index ->
            contentUnits[index].any(Char::isLetterOrDigit)
        }
    val phoneticsByUnit = MutableList<String?>(contentUnits.size) { null }

    if (phoneticAnchorIndices.isNotEmpty()) {
        phoneticWords.forEachIndexed { wordIndex, word ->
            val anchorIndex = wordIndex * phoneticAnchorIndices.size / phoneticWords.size
            val unitIndex = phoneticAnchorIndices[anchorIndex]
            phoneticsByUnit[unitIndex] = listOfNotNull(phoneticsByUnit[unitIndex], word).joinToString(" ")
        }
    }

    val duration = (end - start).coerceAtLeast(contentUnits.size)
    return contentUnits.mapIndexed { index, unit ->
        val unitStart = start + (duration.toLong() * index / contentUnits.size).toInt()
        val unitEnd = start + (duration.toLong() * (index + 1) / contentUnits.size).toInt()
        KaraokeSyllable(
            content = unit,
            start = unitStart,
            end = unitEnd.coerceAtLeast(unitStart + MIN_KARAOKE_SYLLABLE_DURATION_MS),
            phonetic = phoneticsByUnit[index],
        )
    }
}
