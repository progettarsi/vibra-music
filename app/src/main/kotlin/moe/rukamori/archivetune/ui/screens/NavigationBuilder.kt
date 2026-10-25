/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import app.vibra.ui.shell.composable
import androidx.navigation.navArgument
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.defaultUpdateChannel
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionRoute
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionDetailsRoute
import moe.rukamori.archivetune.ui.screens.BrowseScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistAlbumsScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistItemsScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistSongsScreen
import moe.rukamori.archivetune.ui.screens.library.LibraryScreen
import moe.rukamori.archivetune.ui.screens.library.LocalSongScreen
import moe.rukamori.archivetune.ui.screens.musicrecognition.MusicRecognitionScreen
import moe.rukamori.archivetune.ui.screens.musicrecognition.MusicRecognitionDetailsScreen
import moe.rukamori.archivetune.ui.screens.playlist.AutoPlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.CachePlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.LocalPlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.OnlinePlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.SpotifyLikedSongsScreen
import moe.rukamori.archivetune.ui.screens.playlist.SpotifyPlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.TopPlaylistScreen
import moe.rukamori.archivetune.ui.screens.podcast.PodcastRoute
import moe.rukamori.archivetune.ui.screens.podcast.PodcastScreen
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResult
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultArgument
import moe.rukamori.archivetune.ui.screens.search.decodeOnlineSearchQuery
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoute
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoutePrefix
import moe.rukamori.archivetune.ui.screens.search.SearchScreen
import moe.rukamori.archivetune.ui.screens.settings.AboutScreen
import moe.rukamori.archivetune.ui.screens.settings.ThirdPartyLicensesScreen
import moe.rukamori.archivetune.ui.screens.settings.FontLicensesScreen
import moe.rukamori.archivetune.ui.screens.settings.LicenseTextScreen
import moe.rukamori.archivetune.ui.screens.settings.AccountSettings
import moe.rukamori.archivetune.ui.screens.settings.AiIntegrationSettings
import moe.rukamori.archivetune.ui.screens.settings.AppearanceSettings
import moe.rukamori.archivetune.ui.screens.settings.BackupAndRestore
import moe.rukamori.archivetune.ui.screens.settings.ChangelogScreen
import moe.rukamori.archivetune.ui.screens.settings.AdvancedContentSettings
import moe.rukamori.archivetune.ui.screens.settings.AdvancedSettings
import moe.rukamori.archivetune.ui.screens.settings.AdvancedAccountSettings
import moe.rukamori.archivetune.ui.screens.settings.LastFMSettings
import moe.rukamori.archivetune.ui.screens.settings.LogcatScreen
import moe.rukamori.archivetune.ui.screens.settings.LyricsSettings
import moe.rukamori.archivetune.ui.screens.settings.PlayerSettings
import moe.rukamori.archivetune.ui.screens.settings.PrivacySettings
import moe.rukamori.archivetune.ui.screens.settings.SettingsScreen
import moe.rukamori.archivetune.ui.screens.settings.StorageSettings
import moe.rukamori.archivetune.ui.screens.settings.UpdateScreen
import moe.rukamori.archivetune.viewmodels.HomeViewModel
import moe.rukamori.archivetune.viewmodels.OnlineSearchSort

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    homeViewModel: HomeViewModel,
    latestVersionName: () -> String,
    disableAnimations: Boolean = false,
    onClearUpdateBadge: () -> Unit = {},
    homeScrollConnection: NestedScrollConnection? = null,
    searchScrollConnection: NestedScrollConnection? = null,
    onlineSearchSort: OnlineSearchSort = OnlineSearchSort.DEFAULT,
    libraryScrollBehavior: TopAppBarScrollBehavior,
) {
    composable(Screens.Home.route) {
        // Vibra: the Figma home replaces the engine home; same HomeViewModel/data.
        app.vibra.ui.home.VibraHomeScreen(
            navController = navController,
            viewModel = homeViewModel,
        )
    }
    composable(Screens.Library.route) {
        // Vibra: the library is a block screen ordered by the user's habits.
        app.vibra.ui.library.VibraLibraryScreen(navController = navController)
    }
    composable(
        route = "${app.vibra.ui.library.LibraryListRoute}?filter={filter}",
        arguments = listOf(navArgument("filter") { type = NavType.StringType; defaultValue = "LIBRARY" }),
    ) { entry ->
        // The engine's tabbed lists ("see all" from library blocks), opened on the block's
        // tab, with a back button in the app-bar space it reserves.
        val filter =
            moe.rukamori.archivetune.constants.LibraryFilter.entries
                .firstOrNull { it.name == entry.arguments?.getString("filter") }
                ?: moe.rukamori.archivetune.constants.LibraryFilter.LIBRARY
        app.vibra.ui.components.WithVibraTopBar(
            title = stringResource(R.string.filter_library),
            onBack = if (navController.previousBackStackEntry != null) ({ navController.navigateUp() }) else null,
        ) {
            LibraryScreen(navController = navController, scrollBehavior = libraryScrollBehavior, initialFilter = filter)
        }
    }
    composable(Screens.Search.route) {
        SearchScreen(
            navController = navController,
            onSearchClick = {
                navController.currentBackStackEntry
                    ?.savedStateHandle
                    ?.set("openSearch", true)
            },
            headerScrollConnection = searchScrollConnection,
        )
    }
    composable("local_songs") {
        LocalSongScreen(navController)
    }
    composable("history") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        HistoryScreen(navController)
            }
    }
    composable("stats") {
        StatsScreen(navController)
    }
    composable("news") {
        NewsScreen(navController)
    }
    composable(
        route = "view_news/{newsId}",
        arguments =
            listOf(
                navArgument("newsId") { type = NavType.StringType },
            ),
    ) {
        ViewNewsScreen(navController)
    }
    composable(
        route = "year_in_music?year={year}",
        arguments =
            listOf(
                navArgument("year") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
    ) { backStackEntry ->
        val selectedYear = backStackEntry.arguments?.getInt("year")?.takeIf { it > 0 }
        YearInMusicScreen(
            navController = navController,
            initialYear = selectedYear,
        )
    }
    composable(MusicRecognitionRoute) {
        MusicRecognitionScreen(navController)
    }
    composable(MusicRecognitionDetailsRoute) { backStackEntry ->
        val encodedTrack = backStackEntry.arguments?.getString("encodedTrack").orEmpty()
        MusicRecognitionDetailsScreen(navController, encodedTrack)
    }
    composable(Screens.MoodAndGenres.route) {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        MoodAndGenresScreen(navController)
            }
    }
    composable("account") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        AccountScreen(navController, scrollBehavior)
            }
    }
    composable("new_release") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        NewReleaseScreen(navController, scrollBehavior)
            }
    }
    composable("charts_screen") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        ChartsScreen(navController)
            }
    }
    composable(
        route = "browse/{browseId}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                },
            ),
    ) {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        BrowseScreen(
            navController,
            scrollBehavior,
        )
            }
    }
    composable(
        route = OnlineSearchResultRoute,
        arguments =
            listOf(
                navArgument(OnlineSearchResultArgument) {
                    type = NavType.StringType
                },
            ),
        enterTransition = {
            if (disableAnimations) {
                fadeIn(tween(0))
            } else {
                fadeIn(tween(250))
            }
        },
        exitTransition = {
            if (disableAnimations) {
                fadeOut(tween(0))
            } else if (targetState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (disableAnimations) {
                fadeIn(tween(0))
            } else if (initialState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            if (disableAnimations) {
                fadeOut(tween(0))
            } else {
                fadeOut(tween(200))
            }
        },
    ) { backStackEntry ->
        // Vibra: results show their query in a Vibra top bar (the space ArchiveTune's
        // global search bar used to occupy).
        val query =
            decodeOnlineSearchQuery(backStackEntry.arguments?.getString(OnlineSearchResultArgument).orEmpty())
        app.vibra.ui.components.WithVibraTopBar(
            title = query,
            onBack = { navController.navigateUp() },
        ) {
            OnlineSearchResult(
                navController = navController,
                searchSort = onlineSearchSort,
            )
        }
    }
    composable(
        route = "album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        AlbumScreen(navController, scrollBehavior)
    }
    composable(
        route = PodcastRoute,
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                },
            ),
    ) {
        PodcastScreen(navController)
    }
    composable(
        route = "artist/{artistId}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/songs",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        ArtistSongsScreen(navController, scrollBehavior)
            }
    }
    composable(
        route = "artist/{artistId}/albums",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        ArtistAlbumsScreen(navController, scrollBehavior)
            }
    }
    composable(
        route = "artist/{artistId}/items?browseId={browseId}&params={params}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        ArtistItemsScreen(navController, scrollBehavior)
            }
    }
    composable(
        route = "online_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        OnlinePlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "local_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        LocalPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "spotify_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        SpotifyPlaylistScreen(navController, scrollBehavior)
    }
    composable(route = "spotify_liked_songs") {
        SpotifyLikedSongsScreen(navController, scrollBehavior)
    }
    composable(
        route = "auto_playlist/{playlist}?tab={tab}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = "downloaded"
                },
            ),
    ) {
        AutoPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        CachePlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "top_playlist/{top}",
        arguments =
            listOf(
                navArgument("top") {
                    type = NavType.StringType
                },
            ),
    ) {
        TopPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        YouTubeBrowseScreen(navController)
            }
    }
    composable("settings") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        SettingsScreen(navController, latestVersionName())
            }
    }
    composable("settings/account") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        AccountSettings(
            navController = navController,
            latestVersionName = latestVersionName(),
            viewModel = homeViewModel,
        )
            }
    }

    composable("settings/appearance") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        AppearanceSettings(navController)
            }
    }


    composable("settings/advanced/content") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        AdvancedContentSettings(navController)
            }
    }
    composable("settings/lyrics") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        LyricsSettings(navController)
            }
    }
    composable("settings/advanced/account") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface { AdvancedAccountSettings(navController)         }
    }
    composable("settings/advanced") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        AdvancedSettings(navController)
            }
    }
    composable("settings/advanced/playback") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface { PlayerSettings(navController, advanced = true)         }
    }
    composable("settings/player") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        PlayerSettings(navController)
            }
    }

    composable("settings/storage") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        StorageSettings(navController)
            }
    }
    composable("settings/privacy") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        PrivacySettings(navController)
            }
    }
    composable("settings/backup_restore") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        BackupAndRestore(navController)
            }
    }

    composable("settings/integrations") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        moe.rukamori.archivetune.ui.screens.settings.IntegrationsSettings(navController)
            }
    }
    composable("settings/ai_integration") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        AiIntegrationSettings(navController)
            }
    }
    composable("settings/lastfm") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        LastFMSettings(navController)
            }
    }

    composable("settings/logcat") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        LogcatScreen(navController)
            }
    }
    if (BuildConfig.UPDATER_AVAILABLE) {
        composable("settings/update") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
            UpdateScreen(navController, onUpToDate = onClearUpdateBadge)
                }
    }
    }
    composable(
        route = "settings/changelog?channel={channel}",
        arguments =
            listOf(
                navArgument("channel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        val channelName = backStackEntry.arguments?.getString("channel")
        val channel = UpdateChannel.fromStoredName(channelName, defaultUpdateChannel)
        ChangelogScreen(navController, channel = channel)
            }
    }
    composable("settings/about/licenses") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface { ThirdPartyLicensesScreen(navController)         }
    }
    composable("settings/about/fonts") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface { FontLicensesScreen(navController)         }
    }
    composable("settings/about/license/{kind}") { entry ->
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        LicenseTextScreen(navController, entry.arguments?.getString("kind").orEmpty())
            }
    }
    composable("settings/about") {
        moe.rukamori.archivetune.ui.component.EnginePageSurface {
        AboutScreen(navController)
            }
    }

    composable(
        route = "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT={$LOGIN_URL_ARGUMENT}",
        arguments =
            listOf(
                navArgument(LOGIN_URL_ARGUMENT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        LoginScreen(
            navController,
            startUrl = backStackEntry.arguments?.getString(LOGIN_URL_ARGUMENT)?.let(Uri::decode),
        )
    }
}
