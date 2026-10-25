/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.dock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.vibra.ui.components.MarqueeScroll
import app.vibra.ui.components.PlayerArtworkPx
import app.vibra.ui.components.VibraArtwork
import app.vibra.ui.components.VibraMarquee
import app.vibra.ui.components.VibraPlayButton
import app.vibra.ui.haptics.VibraHaptics
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.motion.GooSurface
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidScope
import app.vibra.ui.motion.LiquidSheetState
import app.vibra.ui.motion.LiquidSpeed
import app.vibra.ui.motion.liquidPress
import app.vibra.ui.player.NowPlaying
import app.vibra.ui.theme.VibraDimens
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.utils.resize

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  THE DOCK (Figma "dock", 362 × 55.5, pinned 20 dp above the navigation bar)
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *   BROWSE   ( ☺ )  ( ✿ Title / Artist          )  ( 🔍 )
 *   SEARCH   ( ☺ )  ( ✿ )  ( Search songs, artists…   ✕ )
 *
 *  The dock is one liquid surface: its three pills are blobs of a GooSurface, laid out in
 *  a LiquidScope. Switching modes only changes the Row's widths; the liquid physics
 *  springs every pill to its new bounds and the goo renderer lets neighbours melt into
 *  each other while they travel.
 *
 *  Search choreography (designed so nothing appears to "jump"):
 *   • the profile pill never moves and stays usable;
 *   • the search button is anchored to the pill's right edge, so the pill grows *leftwards
 *     out of it* while the button itself stays still and morphs 🔍 → ✕;
 *   • the field is revealed by the growth itself: its opacity is a function of how far
 *     the pill has opened (not a separate timeline), and the hint is clipped, never
 *     re-ellipsized, while the width animates;
 *   • the mini-player contracts into its artwork cookie.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

enum class DockMode { Browse, Search }

/** What the leading (left) dock button shows. */
sealed interface DockLeading {
    /** The account avatar (Figma "profileIcon"); opens the profile screen. */
    data class Profile(
        val avatarUrl: String?,
    ) : DockLeading

    /** On the profile screen the slot turns into the settings gear (Figma "newdock"). */
    data object Settings : DockLeading
}

/** Hoisted dock state, owned by the app shell. */
@Stable
class VibraDockState {
    var mode by mutableStateOf(DockMode.Browse)
        private set

    var query by mutableStateOf(TextFieldValue())

    fun openSearch(initialQuery: String = "") {
        query = TextFieldValue(initialQuery, TextRange(initialQuery.length))
        mode = DockMode.Search
    }

    fun closeSearch() {
        mode = DockMode.Browse
    }
}

/** Geometry of the cookie inside the mini-player pill; the player's cover flies from it. */
object MiniPlayerGeometry {
    val CookieInset = 6.dp
    val CookieSize = 44.dp

    /** Gap between the cookie and the track text, and the text's end padding. */
    val TextStart = 12.dp
    val TextEnd = 14.dp

    /** The cookie's rect for a mini-player pill at [pill] (root px). */
    fun cookieIn(
        pill: Rect,
        insetPx: Float,
        sizePx: Float,
    ): Rect = Rect(pill.left + insetPx, pill.top + insetPx, pill.left + insetPx + sizePx, pill.top + insetPx + sizePx)

    /** Player progress at which the title, flying to the player, starts cross-fading into the player's. */
    const val TitleCrossfadeStart = 0.12f

    /** How far (0‥1) the scrolling title is drawn back to its start at player [progress]: all
     *  the way by the time it starts cross-fading into the player's title (which rests there). */
    fun titleHome(progress: Float): Float = (progress / TitleCrossfadeStart).coerceIn(0f, 1f)
}

/**
 * Title + artists exactly as the mini-player draws them. The full player draws the same
 * composable at the same spot when it starts opening, so the text can fly to its place in
 * the player without a blink.
 */
@Composable
fun MiniPlayerTrackText(
    track: MediaMetadata,
    modifier: Modifier = Modifier,
    scroll: MarqueeScroll? = null,
    followScroll: Boolean = false,
    scrollPaused: () -> Boolean = { false },
    scrollHome: () -> Float = { 0f },
) {
    Column(modifier = modifier) {
        VibraMarquee(
            text = track.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            scroll = scroll,
            follow = followScroll,
            paused = scrollPaused,
            home = scrollHome,
        )
        Text(
            text = track.artists.joinToString { it.name },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The floating dock.
 *
 * @param playerProgress 0‥1 expansion of the full player; the mini-player content fades as
 *        the player surface grows out of it.
 * @param onMiniPlayerBounds reports where the mini-player pill sits (root coordinates);
 *        the player uses it as the origin of its morph.
 * @param onLeadingBounds reports the leading button's bounds (profile opens out of it).
 * @param miniTitleScroll the mini-player title's scroll, shared with the copy of it that the
 *        full player flies to its own title.
 */
@Composable
fun VibraDock(
    state: VibraDockState,
    nowPlaying: NowPlaying,
    leading: DockLeading,
    playerSheet: LiquidSheetState,
    playerProgress: () -> Float,
    onLeadingClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSearch: (String) -> Unit,
    onMiniPlayerBounds: (Rect) -> Unit,
    miniTitleScroll: MarqueeScroll,
    modifier: Modifier = Modifier,
    onLeadingBounds: (Rect) -> Unit = {},
) {
    val haptics = rememberVibraHaptics()
    val searching = state.mode == DockMode.Search
    val hasMedia = nowPlaying.hasMedia
    val swipe = remember { MiniPlayerSwipe() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        // Width the search pill reaches when fully open; its live width / this = reveal.
        val pillCount = if (hasMedia) 2 else 1
        val openSearchWidthPx =
            with(density) { (maxWidth - (VibraDimens.DockHeight + VibraDimens.DockGap) * pillCount).toPx() }
        val closedSearchWidthPx = with(density) { VibraDimens.DockHeight.toPx() }

        GooSurface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            gooRadius = 7.dp,
            shadowRadius = 22.dp,
            shadowOffsetY = 6.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(VibraDimens.DockHeight),
        ) {
            LiquidScope {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ── Leading: profile avatar / settings (always present) ──────────────
                    Box(
                        modifier =
                            Modifier
                                .liquid(DockMorphSpeed)
                                .gooBlob("leading")
                                .fillMaxHeight()
                                .width(VibraDimens.DockHeight)
                                .onPlaced { onLeadingBounds(it.boundsInRoot()) },
                    ) {
                        DockLeadingButton(leading = leading, haptics = haptics, onClick = onLeadingClick)
                    }

                    Spacer(Modifier.width(VibraDimens.DockGap))

                    // ── Mini-player (fills in Browse, contracts to its cookie in Search) ──
                    if (hasMedia) {
                        Box(
                            modifier =
                                Modifier
                                    .liquid(DockMorphSpeed)
                                    .offset { IntOffset(swipe.offsetPx.roundToInt(), 0) }
                                    .gooBlob("player")
                                    .fillMaxHeight()
                                    .then(if (searching) Modifier.width(VibraDimens.DockHeight) else Modifier.weight(1f))
                                    .onPlaced { onMiniPlayerBounds(it.boundsInRoot()) },
                        ) {
                            MiniPlayer(
                                nowPlaying = nowPlaying,
                                compact = searching,
                                swipe = swipe,
                                playerSheet = playerSheet,
                                playerProgress = playerProgress,
                                titleScroll = miniTitleScroll,
                                haptics = haptics,
                                onPlayPause = onPlayPause,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSkipPrevious,
                            )
                        }
                        Spacer(Modifier.width(VibraDimens.DockGap))
                    } else if (!searching) {
                        // Nothing is playing: the two buttons sit at the edges; the player pill
                        // will grow out of this space when playback starts.
                        Spacer(Modifier.weight(1f))
                    }

                    // ── Search: a round button in Browse, the search field in Search ───────
                    var searchWidthPx by remember { mutableIntStateOf(0) }
                    Box(
                        modifier =
                            Modifier
                                .liquid(DockMorphSpeed)
                                .gooBlob("search")
                                .fillMaxHeight()
                                .then(if (searching) Modifier.weight(1f) else Modifier.width(VibraDimens.DockHeight))
                                .onSizeChanged { searchWidthPx = it.width },
                    ) {
                        DockSearch(
                            state = state,
                            haptics = haptics,
                            onSearch = onSearch,
                            reveal = {
                                ((searchWidthPx - closedSearchWidthPx) / (openSearchWidthPx - closedSearchWidthPx))
                                    .coerceIn(0f, 1f)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockLeadingButton(
    leading: DockLeading,
    haptics: VibraHaptics,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val description =
        stringResource(if (leading is DockLeading.Settings) R.string.settings else R.string.account)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .liquidPress(interactionSource)
                .clip(CircleShape)
                .clickable(interactionSource = interactionSource, indication = ripple(), role = Role.Button) {
                    haptics.press()
                    onClick()
                }.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val swap = rememberGlyphSwapSpecs()
        AnimatedContent(
            targetState = leading,
            contentKey = { it is DockLeading.Settings },
            transitionSpec = { swap.transform(0.6f) },
            label = "dockLeading",
        ) { slot ->
            when (slot) {
                is DockLeading.Profile ->
                    if (slot.avatarUrl != null) {
                        VibraArtwork(
                            url = slot.avatarUrl,
                            shape = CircleShape,
                            sizePx = 160,
                            fallbackIcon = R.drawable.person,
                            modifier = Modifier.size(44.dp),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                DockLeading.Settings ->
                    Icon(
                        painter = painterResource(R.drawable.settings),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp),
                    )
            }
        }
    }
}

/**
 * Horizontal swipe state of the mini-player pill. The whole pill follows the finger with
 * rubber-band resistance (it can never be pulled further than [MaxPull]), and the next
 * track's text slides in from the side the gesture came from.
 */
@Stable
private class MiniPlayerSwipe {
    val offset = Animatable(0f)
    val offsetPx: Float get() = offset.value

    /** -1 = last skip went forward (content enters from the right), +1 = backward. */
    var lastDirection by mutableIntStateOf(-1)

    /** Raw finger travel accumulated during the current drag (gesture-only, not UI state). */
    var raw = 0f
}

/**
 * Mini-player pill content (Figma "miniPlayer": artwork cookie, title, artist).
 *
 * Gestures:
 *  - tap ............... open the full player
 *  - drag up ........... drags the full player open (progress follows the finger)
 *  - swipe sideways .... the pill is pulled along with resistance; past the threshold a
 *                        haptic marks the commit point, releasing skips
 */
@Composable
private fun MiniPlayer(
    nowPlaying: NowPlaying,
    compact: Boolean,
    swipe: MiniPlayerSwipe,
    playerSheet: LiquidSheetState,
    playerProgress: () -> Float,
    titleScroll: MarqueeScroll,
    haptics: VibraHaptics,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
) {
    val metadata = nowPlaying.metadata ?: return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxPullPx = with(density) { MaxPull.toPx() }
    val swipeSpring = Liquid.spatial<Float>(LiquidSpeed.Default)
    // Text dissolves while the pill contracts into the compact cookie.
    val detailsAlpha by animateFloatAsState(
        targetValue = if (compact) 0f else 1f,
        animationSpec = Liquid.effects(LiquidSpeed.Fast),
        label = "miniPlayerDetails",
    )
    val openDescription = stringResource(R.string.vibra_open_player)
    // Warm the full player's cover while the pill shows the track, so opening never waits.
    val imageLoader = SingletonImageLoader.get(LocalPlatformContext.current)
    val platformContext = LocalPlatformContext.current
    LaunchedEffect(metadata.thumbnailUrl) {
        metadata.thumbnailUrl?.let { url ->
            imageLoader.enqueue(ImageRequest.Builder(platformContext).data(url.resize(PlayerArtworkPx, PlayerArtworkPx)).build())
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .clickable(role = Role.Button) {
                    haptics.press()
                    playerSheet.expand()
                }.semantics { contentDescription = openDescription }
                .pointerInput(compact) {
                    if (compact) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var horizontal = false
                        val slop =
                            awaitTouchSlopOrCancellation(down.id) { change, over ->
                                horizontal = abs(over.x) > abs(over.y)
                                change.consume()
                            } ?: return@awaitEachGesture
                        val tracker = VelocityTracker()
                        var crossed = false
                        swipe.raw = 0f
                        drag(slop.id) { change ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val delta = change.positionChange()
                            change.consume()
                            if (horizontal) {
                                swipe.raw += delta.x
                                // Rubber band: travel grows ever slower and asymptotically
                                // approaches MaxPull, so the pill feels tethered to the dock.
                                val pulled = sign(swipe.raw) * maxPullPx * (1f - exp(-abs(swipe.raw) / maxPullPx))
                                scope.launch { swipe.offset.snapTo(pulled) }
                                val beyond = abs(pulled) > maxPullPx * CommitFraction
                                if (beyond != crossed) {
                                    crossed = beyond
                                    if (beyond) haptics.threshold()
                                }
                            } else {
                                playerSheet.dragBy(delta.y)
                            }
                        }
                        if (horizontal) {
                            val velocity = tracker.calculateVelocity().x
                            val committed =
                                crossed || (abs(velocity) > SkipFlingVelocity && abs(swipe.offset.value) > maxPullPx * 0.25f)
                            if (committed) {
                                haptics.gestureEnd()
                                val forward = swipe.offset.value < 0
                                swipe.lastDirection = if (forward) -1 else 1
                                if (forward) onSkipNext() else onSkipPrevious()
                            }
                            scope.launch { swipe.offset.animateTo(0f, swipeSpring, initialVelocity = velocity * 0.2f) }
                        } else {
                            playerSheet.settle(tracker.calculateVelocity().y)
                        }
                    }
                }.padding(MiniPlayerGeometry.CookieInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VibraPlayButton(
            isPlaying = nowPlaying.isPlaying,
            isBuffering = nowPlaying.isBuffering,
            onClick = onPlayPause,
            artworkUrl = metadata.thumbnailUrl,
            size = MiniPlayerGeometry.CookieSize,
            // The full player's cover takes over from this cookie as soon as it starts opening.
            modifier = Modifier.graphicsLayer { alpha = if (playerProgress() > 0.001f) 0f else 1f },
        )
        if (detailsAlpha > 0.01f) {
            val slide = Liquid.spatial<IntOffset>()
            val fadeInSpec = Liquid.effects<Float>()
            val fadeOutSpec = Liquid.effects<Float>(LiquidSpeed.Fast)
            AnimatedContent(
                targetState = metadata,
                contentKey = { it.id },
                transitionSpec = {
                    val from = swipe.lastDirection
                    (slideInHorizontally(slide) { -from * it / 3 } + fadeIn(fadeInSpec)) togetherWith
                        (slideOutHorizontally(slide) { from * it / 3 } + fadeOut(fadeOutSpec))
                },
                label = "miniPlayerTrack",
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = MiniPlayerGeometry.TextStart, end = MiniPlayerGeometry.TextEnd)
                        .graphicsLayer {
                            // The full player draws this text at the same spot and flies it
                            // to its title, so ours hands over in the very first frame.
                            alpha = if (playerProgress() > 0.001f) 0f else detailsAlpha
                        },
            ) { track ->
                // Only the current track drives the shared scroll (the outgoing one keeps its
                // own). It holds while the player is open or opening, and is drawn back to its
                // start as the player grows, home where the player's title begins to show.
                MiniPlayerTrackText(
                    track = track,
                    scroll = titleScroll.takeIf { track.id == metadata.id },
                    scrollPaused = { playerSheet.isOpenOrOpening },
                    scrollHome = { MiniPlayerGeometry.titleHome(playerProgress()) },
                )
            }
        }
    }
}

/**
 * Search slot: a round 🔍 button in Browse; in Search the pill grows leftwards out of the
 * (unmoving) button, which turns into ✕, and the field is revealed by that growth.
 *
 * @param reveal 0‥1 how far the pill has opened (read in draw only).
 */
@Composable
private fun DockSearch(
    state: VibraDockState,
    haptics: VibraHaptics,
    onSearch: (String) -> Unit,
    reveal: () -> Float,
) {
    val searching = state.mode == DockMode.Search
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(searching) {
        if (searching) {
            // Let the pill grow first: the keyboard lifts the whole dock, and doing both at
            // once read as a jump rather than a morph.
            snapshotFlow { reveal() }.first { it > KeyboardAfterReveal }
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (searching) {
            BasicTextField(
                value = state.query,
                // Capped: an endless query (e.g. a stuck key) made every key event slow.
                onValueChange = { if (it.text.length <= MaxQueryLength) state.query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(state.query.text) }),
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 20.dp)
                        .graphicsLayer {
                            // Fade in over the last 45 % of the growth, so the text never
                            // appears inside a pill that is still too narrow for it.
                            alpha = ((reveal() - 0.55f) / 0.45f).coerceIn(0f, 1f)
                        }.focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.query.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.vibra_search_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        }
                        inner()
                    }
                },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        val closeOrClear =
            if (state.query.text.isEmpty()) R.string.close else R.string.vibra_clear_search
        DockIcon(
            icon = if (searching) R.drawable.close else R.drawable.search,
            description = stringResource(if (searching) closeOrClear else R.string.search),
            haptics = haptics,
            size = VibraDimens.DockHeight,
            onClick = {
                when {
                    !searching -> {
                        haptics.toggle(on = true)
                        state.openSearch()
                    }
                    state.query.text.isNotEmpty() -> state.query = TextFieldValue()
                    else -> state.closeSearch()
                }
            },
            hapticOnClick = searching,
        )
    }
}

/** Borderless icon used inside dock pills, with press squish, haptic and glyph morph. */
@Composable
private fun DockIcon(
    icon: Int,
    description: String,
    haptics: VibraHaptics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    hapticOnClick: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .size(size)
                .liquidPress(interactionSource, pressedScale = 0.85f)
                .clip(CircleShape)
                .clickable(interactionSource = interactionSource, indication = ripple(), role = Role.Button) {
                    if (hapticOnClick) haptics.press()
                    onClick()
                }.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val swap = rememberGlyphSwapSpecs()
        AnimatedContent(
            targetState = icon,
            transitionSpec = { swap.transform(0.5f) },
            label = "dockIcon",
        ) { glyph ->
            Icon(
                painter = painterResource(glyph),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** Springs for swapping one glyph for another (scale + fade), resolved once per call site. */
private class GlyphSwapSpecs(
    private val enterScale: FiniteAnimationSpec<Float>,
    private val fadeInSpec: FiniteAnimationSpec<Float>,
    private val fadeOutSpec: FiniteAnimationSpec<Float>,
    private val exitScale: FiniteAnimationSpec<Float>,
) {
    fun transform(scale: Float): ContentTransform =
        (fadeIn(fadeInSpec) + scaleIn(enterScale, initialScale = scale)) togetherWith
            (fadeOut(fadeOutSpec) + scaleOut(exitScale, targetScale = scale))
}

@Composable
private fun rememberGlyphSwapSpecs(): GlyphSwapSpecs {
    val enterScale = Liquid.spatial<Float>()
    val fadeInSpec = Liquid.effects<Float>()
    val fadeOutSpec = Liquid.effects<Float>(LiquidSpeed.Fast)
    val exitScale = Liquid.effects<Float>()
    return remember(enterScale, fadeInSpec, fadeOutSpec, exitScale) {
        GlyphSwapSpecs(enterScale, fadeInSpec, fadeOutSpec, exitScale)
    }
}

/** Furthest the pill can be pulled sideways (asymptote of the rubber band). */
private val MaxPull = 40.dp

/** Fraction of [MaxPull] past which releasing skips. */
private const val CommitFraction = 0.62f

/** A quick flick skips even before the threshold (px/s). */
private const val SkipFlingVelocity = 1400f

/**
 * The dock's Browse ⇄ Search morph uses the slow spatial spring: on the default one the
 * three pills swapped places too fast to be read as one liquid movement.
 */
private val DockMorphSpeed = LiquidSpeed.Slow

/** Share of the search pill's growth after which the keyboard is requested. */
private const val KeyboardAfterReveal = 0.55f

/** Longest search query the dock accepts. */
private const val MaxQueryLength = 200
