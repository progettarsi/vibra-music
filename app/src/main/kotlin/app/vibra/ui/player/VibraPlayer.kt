/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.vibra.ui.player

import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.navigation.NavController
import app.vibra.ui.motion.LocalAmbientMotion
import app.vibra.ui.components.ArtworkAccent
import app.vibra.ui.components.ArtworkAtmosphere
import app.vibra.ui.components.MarqueeScroll
import app.vibra.ui.components.PlayerArtworkPx
import app.vibra.ui.components.SmallArtworkPx
import app.vibra.ui.components.VibraArtwork
import app.vibra.ui.components.VibraGlyphButton
import app.vibra.ui.components.VibraMarquee
import app.vibra.ui.components.VibraPlayButton
import app.vibra.ui.components.VibraSeekBar
import app.vibra.ui.components.artworkAccent
import app.vibra.ui.components.rememberArtworkColors
import app.vibra.ui.dock.MiniPlayerGeometry
import app.vibra.ui.dock.MiniPlayerTrackText
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidMorphShape
import app.vibra.ui.motion.LiquidSheetState
import app.vibra.ui.motion.LiquidSpeed
import app.vibra.ui.motion.detectOwnedHorizontalDragGestures
import app.vibra.ui.motion.LocalPlaybackSpin
import app.vibra.ui.motion.liquidPress
import app.vibra.ui.motion.nudge
import app.vibra.ui.motion.rememberLiquidNudge
import app.vibra.ui.motion.rememberLiquidSheetState
import app.vibra.ui.motion.rememberScreenCornerRadius
import app.vibra.ui.theme.VibraDimens
import app.vibra.ui.theme.VibraTextStyles
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.SleepTimer
import moe.rukamori.archivetune.playback.stream.ArtworkVideoStream
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.player.LyricsScreen
import moe.rukamori.archivetune.ui.player.PlaybackErrorDialog
import moe.rukamori.archivetune.ui.player.SleepTimerDialog
import moe.rukamori.archivetune.ui.player.toPlaybackErrorInfo
import moe.rukamori.archivetune.ui.screens.buildLoginRoute
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.utils.makeTimeString
import android.graphics.RenderEffect as FrameworkRenderEffect
import android.graphics.Shader as FrameworkShader

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  FULL PLAYER (Figma "playerScreen" 55:592 + "queueOpen" 74:416, Apple-Music-like
 *  atmosphere)
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  The player is the mini-player, grown. One LiquidSheetState (0 = pill, 1 = full screen)
 *  drives three layers, all on a critically damped container spring (no bounce):
 *
 *   1. CONTAINER  the pill's rect grows to the window; its corner radius goes from the
 *                 pill's stadium to the *display's own* corner radius, so the open player
 *                 lines up with the physical screen instead of turning square.
 *   2. COVER      a single artwork element flies from the mini-player's cookie to its slot
 *                 in the player, morphing cookie → square and un-spinning from the cookie's
 *                 current angle. It is the only cover on screen, so there is never a
 *                 cross-fade between two copies. In portrait the slot is full-bleed (as wide
 *                 as the screen, from its top edge, Apple Music / Koda) and the cover's lower
 *                 part dissolves into the backdrop, under the title.
 *   3. TITLE      the mini-player's title/artists are redrawn at the very same spot and
 *                 fly (translating and scaling) to the player's title, cross-fading into
 *                 it on the way: the text travels instead of blinking out and back in.
 *   4. CONTENT    top bar and controls fade in during the second half of the growth.
 *
 *  Closed, the player is not torn down: it stays composed, parked off screen and undrawn,
 *  with its cover decoded and its backdrop baked, so opening is the closing played
 *  backwards. (Building it in the first frame of the opening stalled the drag for a few
 *  hundred ms.) While parked nothing in it ticks: position, wave, marquee and spin wait.
 *
 *  Opening the queue lays a sheet *over* the player while the player recedes (scales down,
 *  dims and, on Android 12+, blurs slightly) — the iOS "card behind a card" depth cue.
 *
 *  Colours come from the current cover only: the backdrop is its palette and every accent
 *  (play button, slider, active toggles) is its most colourful tone ([artworkAccent]), both
 *  cross-faded quickly when the track changes.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/**
 * Hosts the full player above the app content. While collapsed it stays composed but
 * parked: off screen, undrawn, out of reach of touches and with nothing ticking, so the
 * opening starts from a ready player (see the overview above).
 *
 * @param collapsedBounds the mini-player pill in root coordinates (morph origin).
 * @param miniTitleScroll the mini-player title's scroll, followed by its flying copy.
 */
@Composable
fun VibraPlayerSheet(
    sheet: LiquidSheetState,
    collapsedBounds: Rect?,
    connection: PlayerConnection,
    nowPlaying: NowPlaying,
    navController: NavController,
    miniTitleScroll: MarqueeScroll,
    modifier: Modifier = Modifier,
) {
    val metadata = nowPlaying.metadata
    // Built a moment after there is something to play, out of the way of the first screen
    // (or right away if the player is opened before that).
    var warm by remember { mutableStateOf(false) }
    LaunchedEffect(metadata != null) {
        if (metadata == null) return@LaunchedEffect
        delay(WarmUpDelayMillis)
        warm = true
    }
    if (!warm && !sheet.isOpenOrOpening) return
    if (metadata == null) {
        // The queue was cleared while the player was open: fold back into the dock.
        if (sheet.isOpenOrOpening) LaunchedEffect(Unit) { sheet.collapse() }
        return
    }

    val queueSheet = rememberLiquidSheetState()
    var lyricsVisible by rememberSaveable { mutableStateOf(false) }
    // 0 = player, 1 = lyrics. The player turns into the lyrics view: the last two rows
    // sink out at the bottom (slider and transport stay and serve the lyrics), cover,
    // title and top bar rise away, and the lyrics come down from the top.
    val lyricsState =
        animateFloatAsState(
            targetValue = if (lyricsVisible) 1f else 0f,
            animationSpec = Liquid.container(),
            label = "lyricsTransition",
        )
    val lyrics = { lyricsState.value }
    // Closed, the player forgets its queue and lyrics: it always reopens on the player.
    LaunchedEffect(sheet, queueSheet) {
        snapshotFlow { sheet.isOpenOrOpening }.collect { open ->
            if (!open) {
                queueSheet.snapTo(0f)
                lyricsVisible = false
            }
        }
    }

    PlayerBackHandlers(sheet = sheet, queueSheet = queueSheet, lyricsVisible = { lyricsVisible })

    val screenCorner = rememberScreenCornerRadius()
    val spin = LocalPlaybackSpin.current
    val accent = artworkAccent(rememberArtworkColors(metadata.thumbnailUrl))

    CompositionLocalProvider(LocalPlayerAccent provides accent) {
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxSize()
                    // Parked two widths to the right while closed: no touches reach it there.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.place(if (sheet.isOpenOrOpening) 0 else placeable.width * 2, 0)
                        }
                    }.graphicsLayer { alpha = if (sheet.isOpenOrOpening) 1f else 0f },
        ) {
            val density = LocalDensity.current
            val fullHeightPx = constraints.maxHeight.toFloat()
            val fullWidthPx = constraints.maxWidth.toFloat()
            val origin = collapsedBounds ?: Rect(0f, fullHeightPx - 200f, fullWidthPx, fullHeightPx - 120f)
            val cookieOrigin =
                with(density) {
                    MiniPlayerGeometry.cookieIn(origin, MiniPlayerGeometry.CookieInset.toPx(), MiniPlayerGeometry.CookieSize.toPx())
                }
            // A full drag from the pill to the top of the screen maps to progress 0 → 1.
            sheet.dragRangePx = origin.top.coerceAtLeast(1f)
            queueSheet.dragRangePx = fullHeightPx * 0.8f
            // Landscape phones get a two-pane player (artwork | controls).
            val landscape = maxWidth > maxHeight
            val pillCorner = VibraDimens.DockHeight / 2

            // ── 1. Container: pill → window, stadium → display corner radius ───────────────
            Box(
                modifier =
                    Modifier
                        .layout { measurable, constraints ->
                            val p = sheet.progress.coerceIn(0f, 1f)
                            val bounds = playerContainerRect(origin, Rect(0f, 0f, fullWidthPx, fullHeightPx), p)
                            val left = bounds.left
                            val top = bounds.top
                            val right = bounds.right
                            val bottom = bounds.bottom
                            val placeable =
                                measurable.measure(
                                    Constraints.fixed(
                                        (right - left).toInt().coerceAtLeast(0),
                                        (bottom - top).toInt().coerceAtLeast(0),
                                    ),
                                )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.place(left.toInt(), top.toInt())
                            }
                        }.graphicsLayer {
                            val p = sheet.progress.coerceIn(0f, 1f)
                            shape = RoundedCornerShape(lerp(pillCorner, screenCorner, p))
                            clip = true
                        }.background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                // Colour field only: the cover's texture (a full-screen blur) would be redrawn on
                // every frame, and in portrait the baked backdrop covers it anyway.
                ArtworkAtmosphere(
                    url = metadata.thumbnailUrl,
                    textureAlpha = 0f,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = ((sheet.progress - 0.1f) / 0.5f).coerceIn(0f, 1f) },
                )
            }

            // ── 2 + 3. Player foreground (recedes while the queue is open) ──────────────────
            // Where the cover lands, measured inside the foreground *below* its recede layer, so
            // the queue's scale-down applies to the cover once (through the layer), not twice.
            val geometry = remember { SlotGeometry() }
            val coverSwipe = remember { Animatable(0f) }
            val coverHandoff = remember { CoverHandoff() }
            val coverCrossfade = rememberCoverCrossfade(metadata.thumbnailUrl, coverHandoff)
            val coverSwipeGesture = rememberCoverSwipeGesture(coverSwipe, nowPlaying, connection, coverHandoff, coverCrossfade)
            // The pages a swipe reveals: the previous and next tracks' covers, prefetched.
            val (previousCover, nextCover) =
                remember(metadata.id, nowPlaying.canSkipNext, nowPlaying.canSkipPrevious, nowPlaying.shuffleEnabled) {
                    connection.player.neighbourArtwork()
                }
            PrefetchCovers(listOf(previousCover, nextCover))
            // The backdrops are baked once per cover (blurring them every frame made the
            // lyrics, which keep the player redrawing, crawl); neighbours are baked ahead.
            val backdropBaker = rememberBackdropBaker()
            // A music video plays in place of the cover (see ArtworkVideo): only on the settled
            // player, uncovered and in the foreground, and it gives way to the cover as soon as
            // the player starts closing.
            val artworkVideo =
                rememberArtworkVideoStream(
                    metadata,
                    // In battery saver a running video stops too and the cover takes its place.
                    enabled = sheet.isOpenOrOpening && LocalAmbientMotion.current,
                    request = connection.service::requestArtworkVideo,
                )
            val currentVideo = remember { CurrentArtworkVideo() }
            // The cover's content (image and video), drawn a second time, blurred, as the live
            // backdrop (see LiveCoverExtension).
            val coverContent = rememberGraphicsLayer()
            val artworkFlight = remember { PlayerArtworkFlight() }
            val unifiedFlight = !landscape && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val flightFrame = {
                artworkFlight.sample(sheet.progress, if (sheet.isOpenOrOpening) spin?.degrees ?: 0f else 0f)
            }
            val cropTail by remember { derivedStateOf(structuralEqualityPolicy()) { flightFrame().tail } }
            val unifiedSurface = { unifiedFlight }
            val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
            val videoVisibility = { if (unifiedFlight) flightFrame().videoOpacity else ((sheet.progress - VideoFadeStart) / (1f - VideoFadeStart)).coerceIn(0f, 1f) }
            val videoAllowed = {
                queueSheet.progress < 0.01f && lyrics() < 0.99f &&
                    lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
            }
            val videoPrepare = { sheet.isOpenOrOpening && videoAllowed() }
            val videoActive = {
                (if (unifiedFlight) videoVisibility() > 0.001f else sheet.progress >= 0.999f) && videoAllowed()
            }
            val swipeSide by remember { derivedStateOf { kotlin.math.sign(coverSwipe.value) } }
            // Captured when the swipe starts: the neighbours change as soon as the swipe has
            // switched track, but the page on screen must stay the one that slid in.
            val revealed = remember(swipeSide) { if (swipeSide < 0f) nextCover else if (swipeSide > 0f) previousCover else null }
            // The full-bleed cover is as wide as the player.
            val revealedOffset = { coverSwipe.value + if (coverSwipe.value < 0f) fullWidthPx else -fullWidthPx }
            var coverSlot by remember { mutableStateOf<Rect?>(null) }
            var titleSlot by remember { mutableStateOf<Rect?>(null) }
            val measureSlot = {
                geometry.rectOf(geometry.cover)?.let { coverSlot = it }
                geometry.rectOf(geometry.title)?.let { titleSlot = it }
            }
            // Where the mini-player draws its track text (see MiniPlayer in VibraDock).
            val miniText =
                with(density) {
                    Rect(
                        left = cookieOrigin.right + MiniPlayerGeometry.TextStart.toPx(),
                        top = origin.top,
                        right = origin.right - MiniPlayerGeometry.CookieInset.toPx() - MiniPlayerGeometry.TextEnd.toPx(),
                        bottom = origin.bottom,
                    )
                }
            LaunchedEffect(coverSlot, titleSlot?.top, fullWidthPx, fullHeightPx, metadata.thumbnailUrl, previousCover, nextCover) {
                val slot = coverSlot ?: return@LaunchedEffect
                val size = IntSize(fullWidthPx.toInt(), fullHeightPx.toInt())
                val titleY = titleSlot?.top ?: (slot.bottom + slot.height * 0.06f)
                // The current backdrop is baked while the player is still closed, so the opening
                // never waits for it (or competes with it for frames); the neighbours, needed
                // only for a swipe, a moment after the player has settled open (each bake is a burst
                // of GPU work: it must not land on a frame that moves).
                backdropBaker.bake(slot = slot, size = size, urls = listOf(metadata.thumbnailUrl), titleY = titleY)
                snapshotFlow { sheet.progress >= 1f }.collectLatest { settled ->
                    if (!settled) return@collectLatest
                    delay(NeighbourBakeDelayMillis)
                    backdropBaker.bake(slot = slot, size = size, urls = listOf(metadata.thumbnailUrl, nextCover, previousCover), titleY = titleY)
                }
            }
            // Closed, only the current backdrop is kept.
            val currentUrl by rememberUpdatedState(metadata.thumbnailUrl)
            LaunchedEffect(backdropBaker) {
                snapshotFlow { sheet.isOpenOrOpening }.collect { open -> if (!open) backdropBaker.retainOnly(currentUrl) }
            }
            val titleFlight =
                TitleFlight(
                    miniText = miniText,
                    slot = { titleSlot?.takeIf { !landscape } },
                    progress = { sheet.progress.coerceIn(0f, 1f) },
                    scale = VibraTextStyles.PlayerTitle.fontSize.value / MaterialTheme.typography.titleMedium.fontSize.value,
                )
            val recedeBlur = with(density) { 14.dp.toPx() }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // While growing, nothing may show outside the growing container.
                            val p = sheet.progress.coerceIn(0f, 1f)
                            clip = p < 0.9999f
                            if (clip) {
                                shape =
                                    PlayerContainerShape(
                                        bounds = playerContainerRect(origin, Rect(0f, 0f, fullWidthPx, fullHeightPx), p),
                                        cornerPx = lerp(pillCorner, screenCorner, p).toPx(),
                                    )
                            }
                            val q = queueSheet.progress.coerceIn(0f, 1f)
                            val scale = lerp(1f, 0.9f, q)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, 0.35f, q)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && q > 0.01f) {
                                renderEffect =
                                    FrameworkRenderEffect
                                        .createBlurEffect(recedeBlur * q, recedeBlur * q, FrameworkShader.TileMode.DECAL)
                                        .asComposeRenderEffect()
                            }
                        }.onPlaced {
                            geometry.foreground = it
                            measureSlot()
                        }
                        .draggable(
                            orientation = Orientation.Vertical,
                            enabled = !queueSheet.isOpenOrOpening && !lyricsVisible,
                            state = rememberDraggableState { delta -> sheet.dragBy(delta) },
                            onDragStopped = { velocity -> sheet.settle(velocity) },
                        ),
            ) {
                ArtworkFlightScene(
                    slot = coverSlot ?: cookieOrigin,
                    cookie = cookieOrigin,
                    source = coverContent,
                    frame = { if (unifiedFlight) flightFrame() else ArtworkFlightFrame(sheet.progress, 0f, videoVisibility(), 0f) },
                    container = { playerContainerRect(origin, Rect(0f, 0f, fullWidthPx, fullHeightPx), sheet.progress) },
                    enabled = unifiedFlight,
                ) {
                    // The backdrop as the cover's own continuation (portrait, Android 12+); during
                    // a swipe the neighbouring track's page comes in beside it.
                    if (!landscape) {
                        val backdropVisibility = { if (unifiedSurface()) 1f else ((sheet.progress - 0.25f) / 0.6f).coerceIn(0f, 1f) }
                        CoverExtension(
                            url = metadata.thumbnailUrl,
                            baker = backdropBaker,
                            visibility = backdropVisibility,
                            offsetX = { coverSwipe.value },
                            // After a completed swipe the new page is already on screen: no second fade.
                            dissolve = { coverCrossfade.fading != null },
                        )
                        LiveCoverExtension(
                            video = currentVideo,
                            content = coverContent,
                            url = metadata.thumbnailUrl,
                            slot = coverSlot,
                            titleY = titleSlot?.top,
                            visibility = backdropVisibility,
                            videoMix = { videoVisibility() * (currentVideo.playback?.reveal?.value ?: 0f) },
                            offsetX = { coverSwipe.value },
                        )
                        revealed?.let { url ->
                            CoverExtension(url = url, baker = backdropBaker, visibility = backdropVisibility, offsetX = revealedOffset)
                        }
                    }

                    // Until the slot is measured (first frame) the cover waits on the cookie itself.
                    FlyingCover(
                        url = metadata.thumbnailUrl,
                        from = cookieOrigin,
                        to = coverSlot ?: cookieOrigin,
                        progress = { sheet.progress.coerceIn(0f, 1f) },
                        // Parked, the spin is not read, so it does not redraw the hidden cover.
                        startRotation = { if (sheet.isOpenOrOpening) spin?.degrees ?: 0f else 0f },
                        fullBleed = !landscape,
                        unifiedSurface = unifiedSurface,
                        cropProgress = { cropTail },
                        titleY = titleSlot?.top,
                        swipeOffset = { coverSwipe.value },
                        lyrics = lyrics,
                        fadingUrl = coverCrossfade.fading,
                        fadingAlpha = coverCrossfade::fadingAlpha,
                        onCoverDrawn = coverCrossfade::onCoverDrawn,
                        video = artworkVideo,
                        contentLayer = coverContent,
                        videoLayer = { video, current, pictureAspect ->
                            ArtworkVideoSurface(
                                stream = video,
                                master = connection.player,
                                active = { current && videoActive() },
                                prepareWhen = { current && videoPrepare() },
                                visibility = videoVisibility,
                                current = currentVideo.takeIf { current },
                                pictureAspect = pictureAspect,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        contentDescription = metadata.title,
                    )
                    if (!landscape) {
                        val slot = coverSlot
                        if (slot != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) UnifiedCoverField(
                            content = coverContent, slot = slot,
                            titleY = titleSlot?.top ?: (slot.bottom + slot.height * 0.06f),
                            crop = rememberCoverCrop(metadata.thumbnailUrl),
                            visibility = { (if (unifiedSurface()) 1f else smoothstep((sheet.progress - 0.85f) / 0.15f)) * (1f - lyrics()) },
                            offsetX = { coverSwipe.value },
                        )
                        if (revealed != null && slot != null) NeighbourCover(url = revealed, slot = slot, offsetX = revealedOffset, titleY = titleSlot?.top)
                    }
                }
                if (!landscape) {
                    // Lyrics read on a darker backdrop: the darkening the whole cover needs for white
                    // text (the backdrop itself is darkened only behind the controls), and a little more.
                    // It changes with the track as the backdrop does, in one dissolve.
                    val coverDim by animateFloatAsState(
                        targetValue = rememberCoverCrop(metadata.thumbnailUrl)?.dim ?: 0f,
                        animationSpec = tween(DissolveMillis),
                        label = "lyricsBackdropDim",
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = lyrics() }
                            .drawBehind { drawRect(Color.Black.copy(alpha = 1f - (1f - LyricsBackdropDim) * (1f - coverDim))) },
                    )
                }

                PlayerContent(
                    connection = connection,
                    nowPlaying = nowPlaying,
                    metadata = metadata,
                    navController = navController,
                    sheet = sheet,
                    queueSheet = queueSheet,
                    landscape = landscape,
                    onShowLyrics = { lyricsVisible = !lyricsVisible },
                    onCoverSlot = { slot ->
                        geometry.cover = slot
                        measureSlot()
                    },
                    onTitleSlot = { slot ->
                        geometry.title = slot
                        measureSlot()
                    },
                    titleFlight = titleFlight,
                    coverGesture = coverSwipeGesture,
                    lyrics = lyrics,
                )

                FlyingMiniText(metadata = metadata, flight = titleFlight, sheet = sheet, scroll = miniTitleScroll)
            }

            // ── Queue: a sheet over the receding player ─────────────────────────────────────
            VibraQueuePanel(
                connection = connection,
                nowPlaying = nowPlaying,
                queueSheet = queueSheet,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = (1f - queueSheet.progress) * fullHeightPx },
            )

            // Persistent bottom row (present in both Figma frames), above the queue sheet.
            if (!landscape) {
                PlayerBottomRow(
                    nowPlaying = nowPlaying,
                    connection = connection,
                    queueSheet = queueSheet,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .graphicsLayer {
                                alpha = ((sheet.progress - 0.5f) / 0.5f).coerceIn(0f, 1f) * (1f - lyrics() * 1.6f).coerceIn(0f, 1f)
                                translationY = lyrics() * BottomRowSink.toPx()
                            }.navigationBarsPadding()
                            .padding(horizontal = VibraDimens.Gutter, vertical = 20.dp),
                )
            }

            val lyricsComposed by remember { derivedStateOf { lyricsState.value > 0.001f } }
            if (lyricsVisible || lyricsComposed) {
                var lyricsSyncOffset by rememberSaveable(metadata.id) { mutableIntStateOf(0) }
                LyricsScreen(
                    mediaMetadata = metadata,
                    onBackClick = { lyricsVisible = false },
                    navController = navController,
                    lyricsSyncOffset = lyricsSyncOffset,
                    onLyricsSyncOffsetChange = { lyricsSyncOffset = it },
                    onQueueClick = {
                        lyricsVisible = false
                        queueSheet.expand()
                    },
                    // Portrait reuses the player's own slider and transport (landscape keeps
                    // the lyrics screen's, as the two-pane player has no room to share).
                    embeddedControlsInset = if (landscape) null else LyricsControlsInset,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val l = lyrics()
                                alpha = ((l - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                translationY = -(1f - l) * LyricsDrop.toPx()
                            },
                )
            }
        }
    }

    PlaybackErrorHost(connection = connection, navController = navController, sheet = sheet)
}

/** Back: close lyrics → close queue → collapse player, each with predictive preview. */
@Composable
private fun PlayerBackHandlers(
    sheet: LiquidSheetState,
    queueSheet: LiquidSheetState,
    lyricsVisible: () -> Boolean,
) {
    PredictiveBackHandler(enabled = sheet.isExpanded && !queueSheet.isOpenOrOpening && !lyricsVisible()) { events ->
        try {
            events.collect { sheet.previewBack(it.progress) }
            sheet.collapse()
        } catch (cancelled: CancellationException) {
            sheet.expand()
            throw cancelled
        }
    }
    PredictiveBackHandler(enabled = queueSheet.isExpanded) { events ->
        try {
            events.collect { queueSheet.previewBack(it.progress) }
            queueSheet.collapse()
        } catch (cancelled: CancellationException) {
            queueSheet.expand()
            throw cancelled
        }
    }
}

/** Layout coordinates of the slots the flying elements land in (set from onPlaced). */
private class SlotGeometry {
    var foreground: LayoutCoordinates? = null
    var cover: LayoutCoordinates? = null
    var title: LayoutCoordinates? = null

    /** [slot]'s rect in foreground space, once both are attached. */
    fun rectOf(slot: LayoutCoordinates?): Rect? {
        val parent = foreground?.takeIf { it.isAttached } ?: return null
        val child = slot?.takeIf { it.isAttached } ?: return null
        // La maschera animata dei controlli non deve ridimensionare le coordinate canoniche.
        return parent.localBoundingBoxOf(child, clipBounds = false)
    }
}

/**
 * Shared path of the title while the player opens: from the mini-player text ([miniText],
 * vertically centred in the pill) to the player's title block ([slot]), growing by [scale]
 * (player title size / mini title size). Both texts follow the same path and scale from
 * their top-start corner; only their opacity differs, so the eye sees one moving text.
 */
private class TitleFlight(
    val miniText: Rect,
    val slot: () -> Rect?,
    val progress: () -> Float,
    val scale: Float,
) {
    /** 0 = mini text fully visible, 1 = player title fully visible. */
    fun crossfade(): Float = ((progress() - TitleCrossfadeStart) / (TitleCrossfadeEnd - TitleCrossfadeStart)).coerceIn(0f, 1f)
}

/**
 * The mini-player's text, redrawn in the player and flown towards the title. It shows the
 * mini-player's own scroll position ([scroll]), so the hand-over never jumps, and exists
 * only while it can be seen (open or opening, before the cross-fade is over).
 */
@Composable
private fun FlyingMiniText(
    metadata: MediaMetadata,
    flight: TitleFlight,
    sheet: LiquidSheetState,
    scroll: MarqueeScroll,
) {
    val visible by remember(sheet) { derivedStateOf { sheet.isOpenOrOpening && sheet.progress < TitleCrossfadeEnd } }
    if (!visible) return
    MiniPlayerTrackText(
        track = metadata,
        scroll = scroll,
        followScroll = true,
        scrollHome = { MiniPlayerGeometry.titleHome(flight.progress()) },
        modifier =
            Modifier
                .layout { measurable, _ ->
                    val width = flight.miniText.width.toInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints(minWidth = width, maxWidth = width))
                    layout(placeable.width, placeable.height) {
                        placeable.place(
                            flight.miniText.left.toInt(),
                            (flight.miniText.center.y - placeable.height / 2f).toInt(),
                        )
                    }
                }.graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    alpha = 1f - flight.crossfade()
                    val slot = flight.slot() ?: return@graphicsLayer
                    val p = flight.progress()
                    val top = flight.miniText.center.y - size.height / 2f
                    translationX = (slot.left - flight.miniText.left) * p
                    translationY = (slot.top - top) * p
                    val scale = lerp(1f, flight.scale, p)
                    scaleX = scale
                    scaleY = scale
                },
    )
}

/**
 * The one cover on screen while the player is open: flies [from] the mini-player cookie
 * [to] its slot, morphing cookie → rounded square and un-spinning to upright. It keeps the
 * cookie's dimming at first and clears it on the way, and shows the mini-player's (already
 * cached) small cover until the large one is decoded, so it never blinks empty.
 */
@Composable
private fun FlyingCover(
    url: String?,
    from: Rect,
    to: Rect,
    progress: () -> Float,
    startRotation: () -> Float,
    fullBleed: Boolean,
    unifiedSurface: () -> Boolean,
    cropProgress: () -> Float,
    titleY: Float?,
    swipeOffset: () -> Float,
    lyrics: () -> Float,
    fadingUrl: String?,
    fadingAlpha: () -> Float,
    onCoverDrawn: (String?) -> Unit,
    video: ArtworkVideoStream?,
    contentLayer: GraphicsLayer,
    videoLayer: @Composable (video: ArtworkVideoStream, current: Boolean, pictureAspect: Float?) -> Unit,
    contentDescription: String,
) {
    val density = LocalDensity.current
    // The video each cover layer shows: the previous track's keeps its own and dissolves with it.
    val layerVideos = remember { HashMap<String, ArtworkVideoStream>() }
    if (url != null) {
        if (video != null) layerVideos[url] = video else layerVideos.remove(url)
    }
    layerVideos.keys.retainAll(setOfNotNull(url, fadingUrl))
    val cornerPx = if (fullBleed) 0f else with(density) { CoverCorner.toPx() }
    val morph =
        remember(to.width, cornerPx) {
            val rounding = (cornerPx / to.width.coerceAtLeast(1f)).coerceIn(0f, 0.5f)
            Morph(
                MaterialShapes.Cookie12Sided,
                RoundedPolygon.rectangle(width = 1f, height = 1f, centerX = 0.5f, centerY = 0.5f, rounding = CornerRounding(rounding)),
            )
        }
    val finalShape = if (fullBleed) RectangleShape else RoundedCornerShape(CoverCorner)
    val coverCrop = rememberCoverCrop(url)

    Box(
        modifier =
            Modifier
                .layout { measurable, _ ->
                    val rect = if (unifiedSurface()) to else lerp(from, to, progress())
                    val placeable =
                        measurable.measure(
                            Constraints.fixed(rect.width.toInt().coerceAtLeast(1), rect.height.toInt().coerceAtLeast(1)),
                        )
                    layout(placeable.width, placeable.height) {
                        placeable.place(rect.left.toInt(), rect.top.toInt())
                    }
                }.graphicsLayer {
                    // Nel volo comune questa superficie resta ferma e alla misura canonica.
                    val p = if (unifiedSurface()) 1f else progress()
                    shape = if (p >= 0.999f) finalShape else {
                        // Solo il percorso di apertura precedente usa il suo arco breve.
                        val spun = startRotation() % 30f
                        val unspin = if (spun > 15f) spun - 30f else spun
                        LiquidMorphShape(morph, p, unspin * (1f - p))
                    }
                    clip = true
                    // Cover and backdrop are one page: it slides whole, at full opacity.
                    translationX = swipeOffset()
                    // Opening the lyrics, the cover stays put and blurs into its own backdrop
                    // (moving and fading it over the backdrop's copy looked like a ghost).
                    val l = lyrics()
                    if (fullBleed && l > 0.01f) {
                        val radius = l * LyricsCoverBlur.toPx()
                        renderEffect = BlurEffect(radius, radius)
                    }
                    alpha = if (fullBleed) 1f - ((l - 0.6f) / 0.4f).coerceIn(0f, 1f) else 1f - l
                    if (!fullBleed) shadowElevation = 18.dp.toPx() * p
                }.then(
                    // As the cover lands its top and bottom dissolve into the backdrop, which
                    // continues the cover (CoverExtension) — one image, not two.
                    if (fullBleed) Modifier.coverEdgeFade(
                        edge = { if (unifiedSurface()) 1f else 1f - progress() },
                        bottomScrim = {
                            val unified = if (unifiedSurface()) 1f else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                smoothstep((progress() - 0.85f) / 0.15f) else 0f
                            (coverCrop?.bottomScrim ?: 0f) * (1f - unified)
                        },
                        titleFraction = { titleY?.let { (it - to.top) / to.height } ?: 1.06f },
                    ) else Modifier,
                ),
    ) {
        // One keyed layer per cover (its image, and its video if any): after a track change the
        // previous layer keeps what it shows and stays on top (fully opaque until the new cover
        // is drawn), then dissolves with a light blur. Keys keep each layer's state when the
        // order changes. They are recorded into [contentLayer], which the live backdrop draws
        // again.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    // Coordinate canoniche durante tutto il volo: il campo non deve conservare
                    // la matrice della mini-copertina quando il layer diventa grande.
                    val drawnSize = size
                    val canonical = IntSize(to.width.roundToInt().coerceAtLeast(1), to.height.roundToInt().coerceAtLeast(1))
                    contentLayer.record(canonical) {
                        scale(canonical.width / drawnSize.width, canonical.height / drawnSize.height, Offset.Zero) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    val unified = if (unifiedSurface()) 1f else if (fullBleed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) smoothstep((progress() - 0.85f) / 0.15f) else 0f
                    if (unified < 1f) {
                        if (unified > 0f) drawContext.canvas.saveLayer(Rect(Offset.Zero, drawnSize), Paint().apply { alpha = 1f - unified })
                        scale(drawnSize.width / canonical.width, drawnSize.height / canonical.height, Offset.Zero) {
                            drawLayer(contentLayer)
                        }
                        if (unified > 0f) drawContext.canvas.restore()
                    }
                },
        ) {
            listOfNotNull(url, fadingUrl?.takeIf { it != url }).forEach { layerUrl ->
                key(layerUrl) {
                    val isFading = layerUrl != url
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (isFading) {
                                    Modifier.graphicsLayer {
                                        val a = fadingAlpha()
                                        alpha = a
                                        val radius = (1f - a) * CrossfadeBlur.toPx()
                                        if (radius > 0.5f) renderEffect = BlurEffect(radius, radius)
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        VibraArtwork(
                            url = layerUrl,
                            shape = RectangleShape,
                            sizePx = PlayerArtworkPx,
                            placeholderSizePx = SmallArtworkPx,
                            contentDescription = if (isFading) null else contentDescription,
                            onLoaded = { if (!isFading) onCoverDrawn(layerUrl) },
                            modifier =
                                Modifier.fillMaxSize().graphicsLayer {
                                    // A letterboxed thumbnail is enlarged past its black bands, so
                                    // the sharp cover shows the same square as its backdrop.
                                    val zoom = cachedCoverZoom(layerUrl)
                                    // Il mini-player usa la foto originale, comprese eventuali bande.
                                    val original = if (unifiedSurface()) cropProgress() else 0f
                                    scaleX = lerp(zoom.scale, 1f, original)
                                    scaleY = scaleX
                                    translationX = zoom.shiftX * size.width * (1f - original)
                                    translationY = zoom.shiftY * size.height * (1f - original)
                                },
                        )
                        layerVideos[layerUrl]?.let { layerVideo -> videoLayer(layerVideo, !isFading, cachedCoverCrop(layerUrl)?.contentAspectRatio) }
                    }
                }
            }
        }
        // The mini-player dims its cookie under the play glyph; clear that on the way up.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black, alpha = if (unifiedSurface()) 0f else CookieDim * (1f - progress()).coerceIn(0f, 1f)) },
        )
    }
}

/** Top bar, cover slot, title, progress, transport and secondary actions. */
@Composable
private fun PlayerContent(
    connection: PlayerConnection,
    nowPlaying: NowPlaying,
    metadata: MediaMetadata,
    navController: NavController,
    sheet: LiquidSheetState,
    queueSheet: LiquidSheetState,
    landscape: Boolean,
    onShowLyrics: () -> Unit,
    onCoverSlot: (LayoutCoordinates) -> Unit,
    onTitleSlot: (LayoutCoordinates) -> Unit,
    titleFlight: TitleFlight,
    coverGesture: Modifier,
    lyrics: () -> Float,
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val density = LocalDensity.current
    var showSleepDialog by remember { mutableStateOf(false) }
    val sleepTimer = connection.service.sleepTimer
    val haptics = rememberVibraHaptics()

    // Content fades in over the second half of the growth; the controls also rise a
    // little. Only alpha/translation of *siblings* of the cover slot change, so the slot
    // geometry (and therefore the flying cover's destination) stays fixed.
    val contentAlpha: () -> Float = { ((sheet.progress - 0.4f) / 0.6f).coerceIn(0f, 1f) }
    val riseModifier =
        Modifier.graphicsLayer {
            alpha = contentAlpha()
            translationY = (1f - sheet.progress.coerceIn(0f, 1f)) * with(density) { 56.dp.toPx() }
            // In lyrics the controls sink so that only the slider and transport remain.
            if (!landscape) translationY += lyrics() * ControlsSink.toPx()
        }

    // On a bright cover the top bar's veil darkens as much as the backdrop's own scrim there would
    // need, so the buttons keep their contrast without a dark band across the screen.
    val topScrim = rememberCoverCrop(metadata.thumbnailUrl)?.topScrim ?: MinTopScrim
    val topBarVeil = CoverScrim.copy(alpha = CoverScrim.alpha + topScrim - MinTopScrim)
    val topBar: @Composable (Modifier) -> Unit = { barModifier ->
        Row(
            modifier =
                barModifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .graphicsLayer {
                        alpha = contentAlpha() * (1f - lyrics())
                        translationY = -lyrics() * LyricsRise.toPx()
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackdropCircleButton(
                icon = R.drawable.expand_more,
                contentDescription = stringResource(R.string.vibra_close_player),
                onClick = sheet::collapse,
                size = 44.dp,
                container = topBarVeil,
            )
            Spacer(Modifier.weight(1f))
            BackdropCircleButton(
                icon = R.drawable.more_horiz,
                contentDescription = stringResource(R.string.vibra_more),
                size = 44.dp,
                container = topBarVeil,
                onClick = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = metadata,
                            navController = navController,
                            onCollapsePlayer = sheet::collapse,
                            sleepTimerEnabled = sleepTimer.isActive,
                            onSleepTimerClick = { showSleepDialog = true },
                            onShowDetailsDialog = { bottomSheetPageState.show { ShowMediaInfo(metadata.id) } },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
            )
        }
    }
    val title: @Composable () -> Unit = {
        PlayerTitleBlock(
            metadata = metadata,
            navController = navController,
            sheet = sheet,
            modifier =
                Modifier
                    .onPlaced(onTitleSlot)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        val slot = titleFlight.slot()
                        if (slot == null) {
                            // Landscape fades with its column; portrait waits for the slot.
                            alpha = if (landscape) 1f else 0f
                            return@graphicsLayer
                        }
                        // The mirror of FlyingMiniText: starts where (and as small as)
                        // the mini-player text is, lands in place at full size.
                        alpha = titleFlight.crossfade() * (1f - lyrics())
                        val rest = 1f - titleFlight.progress()
                        val miniTop = titleFlight.miniText.center.y - size.height / titleFlight.scale / 2f
                        translationX = (titleFlight.miniText.left - slot.left) * rest
                        translationY = (miniTop - slot.top) * rest - lyrics() * LyricsRise.toPx()
                        val scale = lerp(1f / titleFlight.scale, 1f, titleFlight.progress())
                        scaleX = scale
                        scaleY = scale
                    },
        )
    }
    val controls: @Composable ColumnScope.() -> Unit = {
        PlayerControls(
            connection = connection,
            nowPlaying = nowPlaying,
            metadata = metadata,
            onShowLyrics = onShowLyrics,
            onShowSleepDialog = { showSleepDialog = true },
            compact = landscape,
            lyrics = lyrics,
            shown = { sheet.isOpenOrOpening },
        )
    }

    if (landscape) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = VibraDimens.Gutter),
        ) {
            topBar(Modifier)
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(
                    Modifier
                        .aspectRatio(1f)
                        .onPlaced(onCoverSlot)
                        .fillMaxHeight(0.9f)
                        .then(coverGesture),
                )
                Spacer(Modifier.width(28.dp))
                Column(
                    modifier =
                        riseModifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) {
                    title()
                    controls()
                    Spacer(Modifier.height(8.dp))
                    PlayerBottomRow(nowPlaying = nowPlaying, connection = connection, queueSheet = queueSheet)
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            // Full-bleed cover slot: as wide as the screen, just below the top bar, so the
            // cover's blurred continuation shows above it too. The controls are anchored to
            // the bottom and may overlap the cover's faded lower part.
            Spacer(
                Modifier
                    .statusBarsPadding()
                    .padding(top = CoverTopInset)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .onPlaced(onCoverSlot)
                    .then(coverGesture),
            )
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = VibraDimens.Gutter),
            ) {
                title()
                Column(modifier = riseModifier) { controls() }
                // Room for the persistent bottom row (53 dp pill + 20 dp margins).
                Spacer(Modifier.height(53.dp + 40.dp))
            }
            topBar(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = VibraDimens.Gutter),
            )
        }
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepDialog = false },
            onConfirm = { minutes ->
                showSleepDialog = false
                sleepTimer.start(minutes)
                haptics.confirm()
            },
            onEndOfSong = {
                showSleepDialog = false
                sleepTimer.start(-1)
                haptics.confirm()
            },
        )
    }
}

/**
 * Swipe the cover sideways to change track: it follows the finger, and past a third of
 * its width (or with a fling) it leaves on that side while the next one slides in from
 * the other. With nothing to skip to it only stretches a little and returns.
 */
@Composable
private fun rememberCoverSwipeGesture(
    offset: Animatable<Float, AnimationVector1D>,
    nowPlaying: NowPlaying,
    connection: PlayerConnection,
    handoff: CoverHandoff,
    crossfade: CoverCrossfade,
): Modifier {
    val haptics = rememberVibraHaptics()
    val scope = rememberCoroutineScope()
    val settle = Liquid.spatial<Float>(LiquidSpeed.Default)
    val leave = Liquid.effects<Float>(LiquidSpeed.Fast)
    val canNext by rememberUpdatedState(nowPlaying.canSkipNext)
    val canPrevious by rememberUpdatedState(nowPlaying.canSkipPrevious)
    val trackId by rememberUpdatedState(nowPlaying.metadata?.id)
    val cover by rememberUpdatedState(nowPlaying.metadata?.thumbnailUrl)
    val nowPlayingCover = { cover }
    return Modifier.pointerInput(connection) {
        val tracker = VelocityTracker()
        var crossed = false
        detectOwnedHorizontalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                crossed = false
            },
            onDragEnd = {
                val width = size.width.toFloat()
                val velocity = tracker.calculateVelocity().x
                val forward = offset.value < 0f
                val allowed = if (forward) canNext else canPrevious
                val committed = allowed && (crossed || abs(velocity) > SwipeFlingVelocity && abs(offset.value) > width * 0.1f)
                scope.launch {
                    if (!committed) {
                        offset.animateTo(0f, settle, initialVelocity = velocity)
                        return@launch
                    }
                    haptics.gestureEnd()
                    // The neighbour's page slides fully in; then that track starts and the
                    // page simply stays (no second slide, no cross-fade).
                    offset.animateTo(if (forward) -width else width, leave, initialVelocity = velocity)
                    val leaving = trackId
                    handoff.fromSwipe = true
                    connection.player.run {
                        // A swipe always means another track (seekToPrevious would restart this one).
                        if (forward) seekToNextMediaItem() else seekToPreviousMediaItem()
                        prepare()
                        playWhenReady = true
                    }
                    withTimeoutOrNull(TrackChangeWaitMillis) { snapshotFlow { trackId }.first { it != leaving } }
                    // The incoming page stays in place until the new track's cover is drawn
                    // underneath it, so taking it away never shows an empty frame.
                    crossfade.awaitDrawn(nowPlayingCover())
                    offset.snapTo(0f)
                }
            },
            onDragCancel = { scope.launch { offset.animateTo(0f, settle) } },
        ) { change, dragAmount ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            val forward = offset.value + dragAmount < 0f
            val resistance = if (if (forward) canNext else canPrevious) 1f else 0.25f
            val next = offset.value + dragAmount * resistance
            val beyond = abs(next) > size.width * SwipeCommitFraction && resistance == 1f
            if (beyond != crossed) {
                crossed = beyond
                if (beyond) haptics.threshold()
            }
            scope.launch { offset.snapTo(next) }
        }
    }
}

/** Title and artists; the title block the mini-player text flies into. */
@Composable
private fun PlayerTitleBlock(
    metadata: MediaMetadata,
    navController: NavController,
    sheet: LiquidSheetState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        VibraMarquee(
            text = metadata.title,
            style = VibraTextStyles.PlayerTitle,
            color = OnBackdrop,
            modifier = Modifier.fillMaxWidth(),
            // Closing, it is drawn back to its start before it cross-fades into the mini-player
            // text, which rests there.
            paused = { !sheet.isExpanded },
            home = { ((1f - sheet.progress) / (1f - TitleCrossfadeEnd)).coerceIn(0f, 1f) },
        )
        PlayerArtists(
            artists = metadata.artists,
            onArtist = { id ->
                sheet.collapse()
                navController.navigateToArtist(id)
            },
        )
    }
}

/** Artists as separate links: each name opens its own page, not always the first one. */
@Composable
private fun PlayerArtists(
    artists: List<MediaMetadata.Artist>,
    onArtist: (String) -> Unit,
) {
    val haptics = rememberVibraHaptics()
    val currentOnArtist by rememberUpdatedState(onArtist)
    val text =
        remember(artists) {
            buildAnnotatedString {
                artists.forEachIndexed { index, artist ->
                    if (index > 0) append(", ")
                    val id = artist.id
                    if (id == null) {
                        append(artist.name)
                    } else {
                        withLink(
                            LinkAnnotation.Clickable(tag = id, styles = ArtistLinkStyles) {
                                haptics.press()
                                currentOnArtist(id)
                            },
                        ) { append(artist.name) }
                    }
                }
            }
        }
    Text(
        text = text,
        style = VibraTextStyles.PlayerArtist,
        color = OnBackdropMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Progress, transport (lyrics · previous · PLAY · next · like, five equal slots) and the
 * secondary row (Start radio · sleep timer · add to playlist). Share, download and the
 * rest live in "⋯".
 */
@Composable
private fun PlayerControls(
    connection: PlayerConnection,
    nowPlaying: NowPlaying,
    metadata: MediaMetadata,
    onShowLyrics: () -> Unit,
    onShowSleepDialog: () -> Unit,
    compact: Boolean,
    lyrics: () -> Float,
    shown: () -> Boolean,
) {
    val haptics = rememberVibraHaptics()
    val accent = LocalPlayerAccent.current
    val player = connection.player
    val position = rememberPlaybackPosition(player, nowPlaying.isPlaying, metadata.id, active = shown)
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val librarySong by connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val liked = librarySong?.song?.liked ?: metadata.liked
    val showAddToPlaylist = rememberAddToPlaylistLauncher(metadata)

    Spacer(Modifier.height(10.dp))

    // ── Progress ────────────────────────────────────────────────────────────────────
    val progress = position.progress
    VibraSeekBar(
        fraction = scrubFraction ?: progress.fraction,
        durationMs = progress.durationMs,
        onScrub = { scrubFraction = it },
        onSeek = { fraction ->
            player.seekTo((fraction * progress.durationMs).toLong())
            position.resample()
        },
        trackColor = OnBackdrop.copy(alpha = 0.24f),
        fillColor = accent.color,
        wavy = nowPlaying.isPlaying,
        waveMoving = shown,
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        val shownMs = scrubFraction?.let { (it * progress.durationMs).toLong() } ?: progress.positionMs
        Text(
            text = makeTimeString(shownMs),
            style = MaterialTheme.typography.labelMedium,
            color = OnBackdropMuted,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (progress.durationMs > 0) makeTimeString(progress.durationMs) else "",
            style = MaterialTheme.typography.labelMedium,
            color = OnBackdropMuted,
        )
    }

    Spacer(Modifier.height(if (compact) 6.dp else 14.dp))

    // ── Transport: lyrics · previous · PLAY · next · like ──────────────────────────────
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Toggles the lyrics view; lit while it is open (the button stays on screen there).
        val lyricsOpen by remember { derivedStateOf { lyrics() > 0.5f } }
        BackdropCircleButton(
            icon = R.drawable.lyrics,
            contentDescription = stringResource(R.string.vibra_lyrics),
            onClick = {
                haptics.toggle(on = !lyricsOpen)
                onShowLyrics()
            },
            active = lyricsOpen,
            size = TransportButton,
        )
        SkipButton(
            icon = R.drawable.vibra_skip_previous,
            contentDescription = stringResource(R.string.vibra_skip_previous),
            enabled = nowPlaying.canSkipPrevious,
            towardsEnd = false,
            onClick = connection::seekToPrevious,
        )
        VibraPlayButton(
            isPlaying = nowPlaying.isPlaying,
            isBuffering = nowPlaying.isBuffering,
            onClick = player::togglePlayPause,
            size = TransportPlayButton,
            containerColor = accent.color,
            contentColor = accent.on,
        )
        SkipButton(
            icon = R.drawable.vibra_skip_next,
            contentDescription = stringResource(R.string.vibra_skip_next),
            enabled = nowPlaying.canSkipNext,
            towardsEnd = true,
            onClick = connection::seekToNext,
        )
        BackdropCircleButton(
            icon = if (liked) R.drawable.favorite else R.drawable.favorite_border,
            contentDescription = stringResource(if (liked) R.string.vibra_unlike else R.string.vibra_like),
            active = liked,
            size = TransportButton,
            onClick = {
                haptics.toggle(on = !liked)
                connection.toggleLike()
            },
        )
    }

    Spacer(Modifier.height(if (compact) 8.dp else 16.dp))

    // ── Secondary: Start radio · sleep timer · add to playlist ──────────────────────────
    Row(
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = (1f - lyrics() * 1.6f).coerceIn(0f, 1f) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BackdropPill(
            icon = R.drawable.radio,
            text = stringResource(R.string.start_radio),
            onClick = {
                haptics.press()
                connection.startRadioSeamlessly()
            },
        )
        Spacer(Modifier.weight(1f))
        SleepTimerButton(timer = connection.service.sleepTimer, onStart = onShowSleepDialog)
        BackdropCircleButton(
            icon = R.drawable.playlist_add,
            contentDescription = stringResource(R.string.add_to_playlist),
            onClick = showAddToPlaylist,
            size = 44.dp,
        )
    }
}

/** Previous/next: a press also leans the button towards the side it skips to. */
@Composable
private fun SkipButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    enabled: Boolean,
    towardsEnd: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberVibraHaptics()
    val lean = rememberLiquidNudge()
    BackdropCircleButton(
        icon = icon,
        contentDescription = contentDescription,
        enabled = enabled,
        size = TransportButton,
        iconScale = 0.5f,
        modifier = Modifier.nudge(lean, shift = if (towardsEnd) SkipLean else -SkipLean),
        onClick = {
            haptics.press()
            lean.kick()
            onClick()
        },
    )
}

/**
 * Sleep timer: a round button that, while a timer runs, grows leftwards into a pill
 * showing the time left (or "End of song"). Tapping a running timer cancels it.
 */
@Composable
private fun SleepTimerButton(
    timer: SleepTimer,
    onStart: () -> Unit,
) {
    val haptics = rememberVibraHaptics()
    val accent = LocalPlayerAccent.current
    val active = timer.isActive
    val endOfSong = stringResource(R.string.end_of_song)
    val label by produceState(initialValue = "", timer.triggerTime, timer.pauseWhenSongEnd) {
        while (true) {
            value =
                when {
                    timer.triggerTime > 0 -> makeTimeString((timer.triggerTime - System.currentTimeMillis()).coerceAtLeast(0L))
                    timer.pauseWhenSongEnd -> endOfSong
                    else -> ""
                }
            if (timer.triggerTime <= 0) break
            delay(1_000)
        }
    }
    val container by animateColorAsState(if (active) accent.color else BackdropContainer, Liquid.effects(), label = "sleepContainer")
    val content by animateColorAsState(if (active) accent.on else OnBackdrop, Liquid.effects(), label = "sleepContent")
    val interactionSource = remember { MutableInteractionSource() }
    val description = stringResource(if (active) R.string.vibra_sleep_timer_active else R.string.sleep_timer)
    Row(
        modifier =
            Modifier
                .height(44.dp)
                .defaultMinSize(minWidth = 44.dp)
                .liquidPress(interactionSource, pressedScale = 0.92f)
                .clip(CircleShape)
                .background(container)
                .clickable(interactionSource = interactionSource, indication = ripple(), role = Role.Button) {
                    if (active) {
                        timer.clear()
                        haptics.toggle(on = false)
                    } else {
                        onStart()
                    }
                }.semantics { contentDescription = description }
                .animateContentSize(Liquid.spatial())
                .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(painter = painterResource(R.drawable.bedtime), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        if (active && label.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        }
    }
}

/**
 * "From: <source>" pill (opens the queue) · repeat · shuffle.
 * Toggle state is shown Apple-Music-style by colour and opacity only: off = dim white,
 * on = full-strength primary, cross-faded.
 */
@Composable
private fun PlayerBottomRow(
    nowPlaying: NowPlaying,
    connection: PlayerConnection,
    queueSheet: LiquidSheetState,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberVibraHaptics()
    val source =
        nowPlaying.queueTitle?.takeIf { it.isNotBlank() }
            ?: nowPlaying.metadata?.album?.title
            ?: stringResource(R.string.vibra_now_playing)
    val toggleQueue = {
        haptics.press()
        if (queueSheet.isExpanded) queueSheet.collapse() else queueSheet.expand()
    }
    val queueOpen = queueSheet.isExpanded

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .height(53.dp)
                    .clip(CircleShape)
                    .background(if (queueOpen) MaterialTheme.colorScheme.surfaceContainerHighest else BackdropContainer)
                    .clickable(role = Role.Button, onClick = toggleQueue)
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val contentColor = if (queueOpen) MaterialTheme.colorScheme.onSurface else OnBackdrop
            // The queue's close control is its ✕ at the top; the pill keeps its look (Figma).
            Icon(
                painter = painterResource(R.drawable.queue_music),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.vibra_from_source, source),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        val (repeatIcon, repeatLabel) =
            when (nowPlaying.repeatMode) {
                REPEAT_MODE_ALL -> R.drawable.repeat to R.string.vibra_repeat_all
                REPEAT_MODE_ONE -> R.drawable.repeat_one to R.string.vibra_repeat_one
                else -> R.drawable.repeat to R.string.vibra_repeat_off
            }
        StateGlyph(
            icon = repeatIcon,
            contentDescription = stringResource(repeatLabel),
            active = nowPlaying.repeatMode != REPEAT_MODE_OFF,
            onClick = {
                haptics.tick()
                connection.player.toggleRepeatMode()
            },
        )
        StateGlyph(
            icon = R.drawable.shuffle,
            contentDescription =
                stringResource(if (nowPlaying.shuffleEnabled) R.string.vibra_shuffle_on else R.string.vibra_shuffle_off),
            active = nowPlaying.shuffleEnabled,
            onClick = {
                val enable = !nowPlaying.shuffleEnabled
                haptics.toggle(on = enable)
                connection.player.shuffleModeEnabled = enable
            },
        )
    }
}

/** Icon whose on/off state is carried by an animated tint + opacity. */
@Composable
private fun StateGlyph(
    @DrawableRes icon: Int,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (active) LocalPlayerAccent.current.color else OnBackdrop.copy(alpha = 0.42f),
        animationSpec = Liquid.effects(),
        label = "stateGlyphTint",
    )
    VibraGlyphButton(
        icon = icon,
        contentDescription = contentDescription,
        tint = tint,
        iconSize = 28.dp,
        onClick = onClick,
    )
}

/** Translucent round button for use on the player backdrop (Apple Music style). */
@Composable
private fun BackdropCircleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 50.dp,
    iconScale: Float = 0.44f,
    container: Color = BackdropContainer,
) {
    val accent = LocalPlayerAccent.current
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (active) accent.color else container,
        animationSpec = Liquid.effects(),
        label = "backdropButtonContainer",
    )
    val content by animateColorAsState(
        targetValue = if (active) accent.on else OnBackdrop,
        animationSpec = Liquid.effects(),
        label = "backdropButtonContent",
    )
    Box(
        modifier =
            modifier
                .size(size)
                .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
                .liquidPress(interactionSource, pressedScale = 0.88f)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(size * iconScale))
    }
}

/** Translucent labelled pill for the player backdrop. */
@Composable
private fun BackdropPill(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier
                .height(44.dp)
                .liquidPress(interactionSource, pressedScale = 0.94f)
                .clip(CircleShape)
                .background(BackdropContainer)
                .clickable(interactionSource = interactionSource, indication = ripple(), role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = OnBackdrop, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = OnBackdrop)
    }
}

/** Shows the engine's playback-error dialog (retry / sign in) while the player is open. */
@Composable
private fun PlaybackErrorHost(
    connection: PlayerConnection,
    navController: NavController,
    sheet: LiquidSheetState,
) {
    if (!sheet.isOpenOrOpening) return
    val error by connection.error.collectAsStateWithLifecycle()
    val haptics = rememberVibraHaptics()
    val activeError = error ?: return
    val info = remember(activeError) { activeError.toPlaybackErrorInfo() }
    LaunchedEffect(activeError) { haptics.reject() }
    PlaybackErrorDialog(
        error = activeError,
        showLoginAction = info.loginRecoveryUrl != null,
        onRetry = {
            connection.player.prepare()
            connection.player.play()
        },
        onClose = connection::dismissPlaybackError,
        onLogin = {
            connection.dismissPlaybackError()
            sheet.collapse()
            navController.navigate(buildLoginRoute(info.loginRecoveryUrl)) { launchSingleTop = true }
        },
    )
}

/* Colours on the artwork backdrop: always light, whatever the app theme (the backdrop is
 * a dimmed colour field), with translucent containers instead of tonal fills. */
private val OnBackdrop = Color.White
private val OnBackdropMuted = Color.White.copy(alpha = 0.64f)
private val BackdropContainer = Color.White.copy(alpha = 0.14f)

/** Artist names look like plain text; a press only brightens them. */
private val ArtistLinkStyles =
    TextLinkStyles(
        style = SpanStyle(color = OnBackdropMuted, textDecoration = TextDecoration.None),
        pressedStyle = SpanStyle(color = OnBackdrop, textDecoration = TextDecoration.None),
    )

/** Dark veil behind buttons that sit on the cover itself (top bar); deeper on bright covers. */
private val CoverScrim = Color.Black.copy(alpha = 0.28f)

/** Accent taken from the current cover; see [artworkAccent]. */
private val LocalPlayerAccent = compositionLocalOf { ArtworkAccent(Color.White, Color.Black) }

/** The five transport controls share one size; the cookie is a little larger because its
 *  scalloped edge reads smaller than a circle of the same diameter. */
private val TransportButton = 58.dp
private val TransportPlayButton = 64.dp
private val SkipLean = 7.dp

/** Cover swipe: share of the cover's width that commits a skip, and fling speed (px/s). */
private const val SwipeCommitFraction = 0.33f
private const val SwipeFlingVelocity = 1_200f
private const val TrackChangeWaitMillis = 1_500L

/*
 * Player → lyrics. The controls column sinks by the secondary row (16 + 44 dp) plus the
 * room kept for the bottom row (93 dp), minus a 16 dp margin: the transport then rests
 * 16 dp above the navigation bar. The lyrics end above slider + times + transport.
 */
private val ControlsSink = 137.dp
private val BottomRowSink = 140.dp
private val LyricsControlsInset = 148.dp
private val LyricsRise = 56.dp
private val LyricsCoverBlur = 44.dp
private const val LyricsBackdropDim = 0.34f
private val LyricsDrop = 72.dp

/** Blur the previous cover reaches while it dissolves after a track change. */
private val CrossfadeBlur = 18.dp

/** Room above the full-bleed cover (the top bar sits on its blurred continuation). */
private val CoverTopInset = 52.dp

private val CoverCorner = 28.dp

/** Opacity of the black veil on the mini-player cookie (see VibraPlayButton). */
private const val CookieDim = 0.32f

/** Opening progress over which the mini-player text hands over to the player title. */
private const val TitleCrossfadeStart = MiniPlayerGeometry.TitleCrossfadeStart

/** How long after there is something to play the closed player is built (see VibraPlayerSheet). */
private const val WarmUpDelayMillis = 1_200L

/** How long the player rests open before the neighbouring tracks' backdrops are baked. */
private const val NeighbourBakeDelayMillis = 600L

private const val TitleCrossfadeEnd = 0.55f

/** Sheet progress below which a music video has given way to the cover again (closing). */
private const val VideoFadeStart = 0.85f
