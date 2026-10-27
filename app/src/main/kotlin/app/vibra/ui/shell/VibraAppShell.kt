/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package app.vibra.ui.shell

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import app.vibra.ui.components.MarqueeScroll
import app.vibra.ui.dock.DockLeading
import app.vibra.ui.dock.DockMode
import app.vibra.ui.dock.VibraDock
import app.vibra.ui.dock.VibraDockState
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSheetState
import app.vibra.ui.motion.LiquidSpeed
import app.vibra.ui.motion.LocalPlaybackSpin
import app.vibra.ui.motion.rememberLiquidSheetState
import app.vibra.ui.motion.rememberPlaybackSpin
import app.vibra.ui.player.NowPlaying
import app.vibra.ui.player.VibraPlayerSheet
import app.vibra.ui.player.collectNowPlaying
import app.vibra.ui.profile.VibraProfileScreen
import app.vibra.ui.theme.VibraDimens
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.PauseSearchHistoryKey
import moe.rukamori.archivetune.db.entities.SearchHistory
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionDetailsRoute
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionRoute
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.screens.LOGIN_ROUTE
import moe.rukamori.archivetune.ui.screens.navigationBuilder
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchScreen
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.HomeViewModel

/** Routes added by Vibra on top of the engine's navigation graph. */
object VibraRoutes {
    const val Profile = "vibra_profile"
    const val Stats = "vibra_stats"
}

/**
 * Hoistable requests the hosting activity can make (deep links / shortcuts).
 */
class VibraShellController {
    internal val dock = VibraDockState()

    /** Opens the dock search, e.g. for the "Search" launcher shortcut. */
    fun openSearch(initialQuery: String = "") = dock.openSearch(initialQuery)
}

/**
 * The Vibra application frame.
 *
 *   ┌─────────────────────────────┐
 *   │  NavHost (engine + Vibra    │  ← every screen, edge-to-edge
 *   │  screens)                   │
 *   │                             │
 *   │  search overlay (on demand) │  ← rises behind the dock while searching
 *   │                             │
 *   │  ( ☺ )( ✿ mini-player )( 🔍 )│  ← floating liquid dock
 *   └─────────────────────────────┘
 *     full player: grows out of the mini-player pill and covers everything.
 *
 * The shell owns no playback state: everything flows from [connection].
 */
@Composable
fun VibraAppShell(
    navController: NavHostController,
    connection: PlayerConnection?,
    homeViewModel: HomeViewModel,
    controller: VibraShellController,
    startDestination: String,
    latestVersionName: () -> String,
    onClearUpdateBadge: () -> Unit,
    disableAnimations: Boolean,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val dock = controller.dock
    val playerSheet = rememberLiquidSheetState()
    var miniPlayerBounds by remember { mutableStateOf<Rect?>(null) }
    val miniTitleScroll = remember { MarqueeScroll() }
    // Every page goes back the way it came: into the element it was opened from, or to
    // the right (see PageMotion). Taps on the player or the search overlay never count.
    val pageMotion = remember { PageMotion() }
    val view = LocalView.current
    pageMotion.window = LocalWindowInfo.current.containerSize
    pageMotion.maxOriginCornerPx = with(LocalDensity.current) { MaxOriginCorner.toPx() }
    pageMotion.semantics = { (view as? RootForTest)?.semanticsOwner }
    var dockBounds by remember { mutableStateOf<Rect?>(null) }
    var shellOffset by remember { mutableStateOf(Offset.Zero) }
    val nowPlaying = connection?.collectNowPlaying() ?: NoMedia
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()
    val database = LocalDatabase.current
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    val spin = rememberPlaybackSpin(nowPlaying.isPlaying)

    // Screens that own the whole display hide the dock (and its mini-player).
    val dockVisible = route == null || route !in FullScreenRoutes && !route.startsWith(LoginRoutePrefix)

    // Content insets = system bars + (top) the engine's app-bar slot + (bottom) the dock
    // and its margins, so lists never end under the dock.
    val contentInsets =
        WindowInsets.systemBars
            .add(
                WindowInsets(
                    top = AppBarHeight,
                    bottom = if (dockVisible) VibraDimens.DockHeight + VibraDimens.DockBottomMargin + 16.dp else 0.dp,
                ),
            )

    SystemBarsForPlayer(playerSheet)

    // Folding the search away on navigation (back closes it from inside the overlay).
    LaunchedEffect(route) { if (dock.mode == DockMode.Search && route?.startsWith("search/") == true) dock.closeSearch() }

    val submitSearch: (String) -> Unit = { raw ->
        val query = raw.trim()
        if (query.isNotEmpty()) {
            if (!pauseSearchHistory) database.query { insert(SearchHistory(query = query)) }
            dock.closeSearch()
            playerSheet.collapse()
            navController.navigate(onlineSearchResultRoute(query))
        }
    }

    CompositionLocalProvider(LocalPlaybackSpin provides spin) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onPlaced { shellOffset = it.positionInWindow() }
                .pointerInput(pageMotion, playerSheet, dock) {
                    // Watches (never consumes) where each tap lands, so a page opened by it
                    // can grow out of the element under the finger.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val position = down.position + shellOffset
                        val onOverlay =
                            playerSheet.isOpenOrOpening ||
                                dock.mode == DockMode.Search && dockBounds?.contains(position) != true
                        pageMotion.onPress(position, excluded = onOverlay)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        pageMotion.onRelease(SystemClock.uptimeMillis())
                    }
                }.background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background),
    ) {
        // ── Content ────────────────────────────────────────────────────────────────
        CompositionLocalProvider(
            LocalPlayerAwareWindowInsets provides contentInsets,
            LocalPageMotion provides pageMotion,
        ) {
            VibraNavHost(
                navController = navController,
                homeViewModel = homeViewModel,
                startDestination = startDestination,
                latestVersionName = latestVersionName,
                onClearUpdateBadge = onClearUpdateBadge,
                pageMotion = pageMotion.takeUnless { disableAnimations },
            )
        }

        // ── Search overlay: rises from the dock while the search pill is open ──────────
        AnimatedVisibility(
            visible = dock.mode == DockMode.Search,
            // Calm entrance: the dock pill does the travelling; the page only settles in.
            enter = fadeIn(Liquid.effects(LiquidSpeed.Slow)) + slideInVertically(Liquid.spatial()) { it / 24 },
            exit = fadeOut(Liquid.effects(LiquidSpeed.Fast)) + slideOutVertically(Liquid.effects()) { it / 32 },
            modifier = Modifier.fillMaxSize(),
        ) {
            // Registered when the overlay appears, so it comes after the NavHost's own back
            // handling and wins: back closes the search, not the page beneath it.
            BackHandler(enabled = dock.mode == DockMode.Search) { dock.closeSearch() }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                OnlineSearchScreen(
                    query = dock.query.text,
                    onQueryChange = { dock.query = it },
                    navController = navController,
                    onSearch = submitSearch,
                    onDismiss = dock::closeSearch,
                    pureBlack = pureBlack,
                    bottomContentPadding = VibraDimens.DockHeight + VibraDimens.DockBottomMargin * 2,
                )
            }
        }

        // ── Dock shadow: a soft scrim rising from below the dock to just above it, so the
        //    dock always stands out from the content scrolling underneath ─────────────
        AnimatedVisibility(
            visible = dockVisible,
            enter = fadeIn(Liquid.effects()),
            exit = fadeOut(Liquid.effects()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            DockScrim(color = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
        }

        // ── Dock ──────────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = dockVisible,
            enter = slideInVertically(Liquid.spatial()) { it * 2 } + fadeIn(Liquid.effects()),
            exit = slideOutVertically(Liquid.spatial()) { it * 2 } + fadeOut(Liquid.effects()),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(horizontal = VibraDimens.Gutter)
                    .padding(bottom = VibraDimens.DockBottomMargin)
                    .onPlaced { dockBounds = it.boundsInWindow() },
        ) {
            VibraDock(
                state = dock,
                nowPlaying = nowPlaying,
                leading =
                    if (route == VibraRoutes.Profile) {
                        DockLeading.Settings
                    } else {
                        DockLeading.Profile(accountImageUrl)
                    },
                playerSheet = playerSheet,
                playerProgress = { playerSheet.progress },
                onLeadingClick = {
                    // Never stack profile → settings → profile → …: if the destination is
                    // already in the back stack, go back to it instead of pushing a copy, so
                    // back always reads settings → profile → home.
                    val target = if (route == VibraRoutes.Profile) SettingsRoute else VibraRoutes.Profile
                    if (!navController.popBackStack(target, inclusive = false)) {
                        navController.navigate(target) { launchSingleTop = true }
                    }
                },
                onPlayPause = { connection?.player?.togglePlayPause() },
                onSkipNext = { connection?.seekToNext() },
                onSkipPrevious = { connection?.seekToPrevious() },
                onSearch = submitSearch,
                onMiniPlayerBounds = { miniPlayerBounds = it },
                miniTitleScroll = miniTitleScroll,
                modifier =
                    Modifier.graphicsLayer {
                        // The other pills step back while the player grows out of its pill.
                        alpha = 1f - (playerSheet.progress * 2f).coerceIn(0f, 1f)
                    },
            )
        }

        // ── Full player (on top of everything while open) ─────────────────────────────
        if (connection != null) {
            VibraPlayerSheet(
                sheet = playerSheet,
                collapsedBounds = miniPlayerBounds,
                miniTitleScroll = miniTitleScroll,
                connection = connection,
                nowPlaying = nowPlaying,
                navController = navController,
                modifier = Modifier.zIndex(2f),
            )
        }

        // ── Update ready, when Android needs a tap to install it (see VibraUpdates) ─────
        VibraUpdatePrompt()
    }
    }
}

/**
 * Gradient behind the dock: transparent at the top, ~85 % background at the bottom edge,
 * covering the navigation bar, the dock and [DockScrimReach] above it.
 */
@Composable
private fun DockScrim(color: Color) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(navigationBar + VibraDimens.DockBottomMargin + VibraDimens.DockHeight + DockScrimReach)
                .background(
                    Brush.verticalGradient(
                        0f to color.copy(alpha = 0f),
                        0.45f to color.copy(alpha = 0.55f),
                        1f to color.copy(alpha = 0.88f),
                    ),
                ),
    )
}

private val DockScrimReach = 40.dp

/**
 * The engine navigation graph with Vibra's routes. The NavHost plays no transition itself:
 * it tells [pageMotion] which page moves and how, and every page draws that in its
 * PageFrame (null [pageMotion]: animations are off, pages switch at once).
 */
@Composable
private fun VibraNavHost(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    startDestination: String,
    latestVersionName: () -> String,
    onClearUpdateBadge: () -> Unit,
    pageMotion: PageMotion?,
) {
    // The engine screens expect collapsing app-bar behaviours; Vibra keeps them so every
    // inherited screen scrolls exactly as before.
    val topAppBarScrollBehavior = appBarScrollBehavior()
    val libraryScrollBehavior = appBarScrollBehavior()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            pageMotion?.forward(from = initialState, to = targetState)
            EnterTransition.None
        },
        exitTransition = {
            pageMotion?.forward(from = initialState, to = targetState)
            ExitTransition.None
        },
        popEnterTransition = {
            pageMotion?.pop(from = initialState, to = targetState, predictive = false)
            EnterTransition.None
        },
        popExitTransition = {
            pageMotion?.pop(from = initialState, to = targetState, predictive = false)
            ExitTransition.None
        },
        predictivePopEnterTransition = {
            pageMotion?.pop(from = initialState, to = targetState, predictive = true)
            EnterTransition.None
        },
        predictivePopExitTransition = {
            pageMotion?.pop(from = initialState, to = targetState, predictive = true)
            ExitTransition.None
        },
        // No size modifier on purpose: engine screens emit their TopAppBar as a sibling of
        // the content; under fixed (fillMaxSize) constraints that transparent bar would be
        // stretched over the whole screen and swallow every touch (the "dead" artist page).
        // The nested-scroll hookup is what the engine's collapsing top bars expect.
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        navigationBuilder(
            navController = navController,
            scrollBehavior = topAppBarScrollBehavior,
            homeViewModel = homeViewModel,
            latestVersionName = latestVersionName,
            onClearUpdateBadge = onClearUpdateBadge,
            libraryScrollBehavior = libraryScrollBehavior,
        )
        composable(route = VibraRoutes.Profile) {
            VibraProfileScreen(navController = navController, homeViewModel = homeViewModel)
        }
        composable(route = VibraRoutes.Stats) {
            app.vibra.ui.profile.VibraStatsScreen(navController = navController)
        }
    }
}

/**
 * While the player is open its backdrop is a dark, blurred cover, so status-bar icons
 * must be light regardless of theme; restore the theme default when it closes.
 */
@Composable
private fun SystemBarsForPlayer(sheet: LiquidSheetState) {
    // Read here, not in the shell: the switch then recomposes only this.
    val expanded = sheet.isExpanded
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.luminanceIsDark()
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val lightIcons = expanded || darkTheme
        controller.isAppearanceLightStatusBars = !lightIcons
        controller.isAppearanceLightNavigationBars = !lightIcons
    }
}

private fun Color.luminanceIsDark(): Boolean = (0.2126f * red + 0.7152f * green + 0.0722f * blue) < 0.5f

/** Corner radius of a page shrunk into a row or card (pills and circles keep theirs). */
private val MaxOriginCorner = 28.dp

/** Root of the engine's settings graph (opened from the profile's dock button). */
private const val SettingsRoute = "settings"

/** Routes where the dock would get in the way of a full-display experience. */
private val FullScreenRoutes = setOf("year_in_music?year={year}", MusicRecognitionRoute, MusicRecognitionDetailsRoute)
private const val LoginRoutePrefix = LOGIN_ROUTE

private val NoMedia =
    NowPlaying(
        metadata = null,
        isPlaying = false,
        isBuffering = false,
        repeatMode = 0,
        shuffleEnabled = false,
        canSkipPrevious = false,
        canSkipNext = false,
        queueTitle = null,
    )
