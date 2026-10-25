/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import app.vibra.ui.components.ArtworkAccent
import app.vibra.ui.components.ArtworkAtmosphere
import app.vibra.ui.components.SmallArtworkPx
import app.vibra.ui.components.VibraArtwork
import app.vibra.ui.components.VibraGlyphButton
import app.vibra.ui.components.VibraMarquee
import app.vibra.ui.components.VibraPlayButton
import app.vibra.ui.components.VibraSeekBar
import app.vibra.ui.components.artworkAccent
import app.vibra.ui.components.rememberArtworkColors
import app.vibra.ui.motion.liquidPress
import app.vibra.ui.theme.VibraDimens
import app.vibra.ui.theme.VibraShapes
import dagger.hilt.android.EntryPointAccessors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.LyricsV2
import moe.rukamori.archivetune.ui.menu.LyricsMenu
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LyricsRenderScreenState
import moe.rukamori.archivetune.viewmodels.LyricsRenderViewModel

@Composable
fun LyricsScreen(
    mediaMetadata: MediaMetadata,
    onBackClick: () -> Unit,
    navController: NavController,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onQueueClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backHandlerEnabled: Boolean = true,
    // Vibra: when set, the lyrics are part of the player — no backdrop and no controls of
    // their own; they end this far above the navigation bar, where the player keeps its
    // slider and transport (and those stay touchable).
    embeddedControlsInset: Dp? = null,
    lyricsRenderViewModel: LyricsRenderViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val view = LocalView.current

    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val lyricsRenderState by lyricsRenderViewModel.state.collectAsStateWithLifecycle()

    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val lyricsMode = LyricsMode.ENHANCED
    // Vibra: one fixed design on the cover's colour field, like the player.
    val accent = artworkAccent(rememberArtworkColors(mediaMetadata.thumbnailUrl))

    val hapticClick =
        remember(enableHapticFeedback, view) {
            {
                if (enableHapticFeedback) {
                    view.performHapticFeedback(
                        HapticFeedbackConstants.CONTEXT_CLICK,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                }
            }
        }
    val lyricsHelper =
        remember(context) {
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    moe.rukamori.archivetune.di.LyricsHelperEntryPoint::class.java,
                ).lyricsHelper()
    }

    LaunchedEffect(mediaMetadata.id, currentLyrics?.lyrics) {
        if (mediaMetadata.isPodcast) return@LaunchedEffect
        if (currentLyrics != null) return@LaunchedEffect
        try {
            val existingLyrics =
                withContext(Dispatchers.IO) {
                    database.lyrics(mediaMetadata.id).first()
                }
            if (existingLyrics != null) return@LaunchedEffect

            val lyrics =
                withContext(Dispatchers.IO) {
                    lyricsHelper.getLyrics(mediaMetadata)
                }
            withContext(Dispatchers.IO) {
                database.query {
                    insertLyricsIfAbsent(
                        id = mediaMetadata.id,
                        lyrics = lyrics,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    val positionState = remember(mediaMetadata.id) { mutableLongStateOf(0L) }
    val durationState = remember(mediaMetadata.id) { mutableLongStateOf(C.TIME_UNSET) }
    var sliderPosition by remember(mediaMetadata.id) { mutableStateOf<Long?>(null) }

    val lyricsDurationMs = durationState.longValue.takeIf { it != C.TIME_UNSET } ?: 0L
    LaunchedEffect(mediaMetadata.id, lyricsDurationMs) {
        lyricsRenderViewModel.bind(mediaMetadata.id, lyricsDurationMs)
    }

    LaunchedEffect(player, playbackState, mediaMetadata.id) {
        if (playbackState != STATE_READY && playbackState != STATE_BUFFERING) return@LaunchedEffect
        while (isActive) {
            positionState.longValue = player.currentPosition.coerceAtLeast(0L)
            durationState.longValue = player.duration
            delay(250)
        }
    }

    val showLyricsMenu = {
        menuState.show {
            LyricsMenu(
                lyricsProvider = { currentLyrics },
                mediaMetadataProvider = { mediaMetadata },
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                onDismiss = menuState::dismiss,
                lyricsRenderViewModel = lyricsRenderViewModel,
            )
        }
    }

    val isLoading = playbackState == STATE_BUFFERING || sliderPosition != null
    val orientation = LocalConfiguration.current.orientation
    val seekTo = { position: Long ->
        player.seekTo(position)
        positionState.longValue = position
    }
    val controls: @Composable (Modifier) -> Unit = { controlsModifier ->
        VibraLyricsControls(
            positionProvider = { positionState.longValue },
            durationProvider = { durationState.longValue },
            isPlaying = isPlaying,
            isLoading = isLoading,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            accent = accent,
            onScrub = { fraction ->
                sliderPosition = fraction?.let { (it * durationState.longValue.coerceAtLeast(0L)).toLong() }
            },
            onSeek = { fraction ->
                seekTo((fraction * durationState.longValue.coerceAtLeast(0L)).toLong())
                sliderPosition = null
            },
            onPreviousClick = {
                hapticClick()
                playerConnection.seekToPrevious()
            },
            onPlayPauseClick = player::togglePlayPause,
            onNextClick = {
                hapticClick()
                playerConnection.seekToNext()
            },
            modifier = controlsModifier,
        )
    }

    BackHandler(enabled = backHandlerEnabled, onBack = onBackClick)

    val embedded = embeddedControlsInset != null
    Box(
        modifier =
            if (embedded) {
                modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = embeddedControlsInset)
            } else {
                modifier.fillMaxSize()
            },
    ) {
        if (!embedded) ArtworkAtmosphere(url = mediaMetadata.thumbnailUrl, dim = 0.45f, modifier = Modifier.fillMaxSize())

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .consumeUnhandledPointerInput(),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(if (embedded) WindowInsets.statusBars else WindowInsets.systemBars),
        ) {
            LyricsTrackHeader(
                mediaMetadata = mediaMetadata,
                onMoreClick = showLyricsMenu,
                // Embedded, the player's lit lyrics button closes them: no second ✕.
                onDismissClick = onBackClick.takeUnless { embedded },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VibraDimens.Gutter, vertical = 12.dp),
            )

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LyricsPane(
                        lyricsMode = lyricsMode,
                        lyricsState = lyricsRenderState,
                        sliderPositionProvider = { sliderPosition },
                        lyricsSyncOffset = lyricsSyncOffset,
                        modifier =
                            Modifier
                                .weight(1.15f)
                                .fillMaxHeight()
                                .padding(end = 32.dp),
                    )
                    controls(
                        Modifier
                            .weight(0.85f)
                            .widthIn(max = 420.dp),
                    )
                }
            } else {
                LyricsPane(
                    lyricsMode = lyricsMode,
                    lyricsState = lyricsRenderState,
                    sliderPositionProvider = { sliderPosition },
                    lyricsSyncOffset = lyricsSyncOffset,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                )
                if (!embedded) {
                    controls(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = VibraDimens.Gutter, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

/** Cover, title and artists, then close (✕, when [onDismissClick] is set) and the lyrics menu (⋯). */
@Composable
private fun LyricsTrackHeader(
    mediaMetadata: MediaMetadata,
    onMoreClick: () -> Unit,
    onDismissClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        VibraArtwork(
            url = mediaMetadata.thumbnailUrl,
            shape = VibraShapes.RowThumbnail,
            sizePx = SmallArtworkPx,
            modifier = Modifier.size(52.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            VibraMarquee(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = mediaMetadata.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.64f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        onDismissClick?.let {
            LyricsHeaderButton(iconRes = R.drawable.close, contentDescription = stringResource(R.string.close), onClick = it)
            Spacer(modifier = Modifier.width(8.dp))
        }
        LyricsHeaderButton(iconRes = R.drawable.more_horiz, contentDescription = stringResource(R.string.more_options), onClick = onMoreClick)
    }
}

/** Translucent round button on the colour field (same look as the player's). */
@Composable
private fun LyricsHeaderButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .liquidPress(interactionSource, pressedScale = 0.88f)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable(interactionSource = interactionSource, indication = ripple(), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The lyrics themselves; lines fade out towards both edges so they never end on a cut. */
@Composable
private fun LyricsPane(
    lyricsMode: LyricsMode,
    lyricsState: LyricsRenderScreenState,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
) {
    LyricsContent(
        lyricsMode = lyricsMode,
        lyricsState = lyricsState,
        sliderPositionProvider = sliderPositionProvider,
        lyricsSyncOffset = lyricsSyncOffset,
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                EdgeFade to Color.Black,
                                1f - EdgeFade to Color.Black,
                                1f to Color.Transparent,
                            ),
                        blendMode = BlendMode.DstIn,
                    )
                }.padding(horizontal = VibraDimens.Gutter),
        textColor = Color.White,
    )
}

/**
 * Progress and transport, the same components as the player: the thin wavy slider and
 * the cookie play button in the cover's accent, flanked by the skip glyphs.
 */
@Composable
private fun VibraLyricsControls(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    accent: ArtworkAccent,
    onScrub: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = durationProvider().takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val position = scrubFraction?.let { (it * duration).toLong() } ?: positionProvider()
    Column(modifier = modifier) {
        VibraSeekBar(
            fraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            durationMs = duration,
            onScrub = {
                scrubFraction = it
                onScrub(it)
            },
            onSeek = onSeek,
            trackColor = Color.White.copy(alpha = 0.24f),
            fillColor = accent.color,
            wavy = isPlaying,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = makeTimeString(position), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.64f))
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (duration > 0L) "-" + makeTimeString((duration - position).coerceAtLeast(0L)) else "",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.64f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VibraGlyphButton(
                icon = R.drawable.vibra_skip_previous,
                contentDescription = stringResource(R.string.vibra_skip_previous),
                onClick = onPreviousClick,
                enabled = canSkipPrevious,
                tint = Color.White,
                size = 56.dp,
                iconSize = 38.dp,
            )
            VibraPlayButton(
                isPlaying = isPlaying,
                isBuffering = isLoading,
                onClick = onPlayPauseClick,
                size = 64.dp,
                containerColor = accent.color,
                contentColor = accent.on,
            )
            VibraGlyphButton(
                icon = R.drawable.vibra_skip_next,
                contentDescription = stringResource(R.string.vibra_skip_next),
                onClick = onNextClick,
                enabled = canSkipNext,
                tint = Color.White,
                size = 56.dp,
                iconSize = 38.dp,
            )
        }
    }
}

/** Share of the lyrics pane's height over which lines fade in/out at the edges (the only edge fade). */
private const val EdgeFade = 0.1f

@Composable
private fun LyricsContent(
    lyricsMode: LyricsMode,
    lyricsState: LyricsRenderScreenState,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    when (lyricsMode) {
        LyricsMode.V2 -> {
            LyricsV2(
                lyricsState = lyricsState,
                sliderPositionProvider = sliderPositionProvider,
                lyricsSyncOffset = lyricsSyncOffset,
                modifier = modifier,
                textColorOverride = textColor,
            )
        }

        LyricsMode.ENHANCED -> {
            LyricsEnhanced(
                lyricsState = lyricsState,
                sliderPositionProvider = sliderPositionProvider,
                lyricsSyncOffset = lyricsSyncOffset,
                modifier = modifier,
                textColorOverride = textColor,
            )
        }
    }
}
