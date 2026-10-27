/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.vibra.ui.components.VibraDialogButton
import app.vibra.ui.shell.VibraAppShell
import app.vibra.ui.shell.VibraShellController
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.CustomFontUriKey
import moe.rukamori.archivetune.constants.CustomThemeColorKey
import moe.rukamori.archivetune.constants.DarkModeDefault
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisableScreenshotKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.FontPreferenceKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.constants.UpdateChannelKey
import moe.rukamori.archivetune.constants.UseSystemFontKey
import moe.rukamori.archivetune.constants.UseSystemLanguageKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.musicrecognition.ACTION_MUSIC_RECOGNITION
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionRoute
import moe.rukamori.archivetune.musicrecognition.openMusicRecognition
import moe.rukamori.archivetune.onboarding.OnboardingScreenState
import moe.rukamori.archivetune.onboarding.OnboardingViewModel
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.MusicService.MusicBinder
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.BottomSheetMenu
import moe.rukamori.archivetune.ui.component.BottomSheetPage
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MarkdownText
import moe.rukamori.archivetune.ui.component.NetworkStatusBanner
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerTheme
import moe.rukamori.archivetune.ui.screens.LOGIN_URL_ARGUMENT
import moe.rukamori.archivetune.ui.screens.LoginScreen
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.buildLoginRoute
import app.vibra.power.PowerSave
import app.vibra.power.PowerSaveLevel
import app.vibra.ui.motion.LocalAmbientMotion
import moe.rukamori.archivetune.spotify.SpotifyOnboardingLogin
import app.vibra.ui.onboarding.VibraOnboarding
import app.vibra.ui.onboarding.VibraOnboardingViewModel
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import moe.rukamori.archivetune.ui.theme.DefaultThemeColor
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.updates.ObserveUpdateSettingsUseCase
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.Updater
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.isLowRamDevice
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.setAppLocale
import moe.rukamori.archivetune.viewmodels.BackupCategory
import moe.rukamori.archivetune.viewmodels.BackupRestoreViewModel
import moe.rukamori.archivetune.viewmodels.GatekeeperViewModel
import moe.rukamori.archivetune.viewmodels.HomeViewModel
import moe.rukamori.archivetune.viewmodels.NetworkBannerViewModel

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var observeUpdateSettings: ObserveUpdateSettingsUseCase

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null
    private var pendingDeepLinkQueue: Queue? = null
    private var pendingVoiceSearchQuery: String? = null
    private var pendingBackupRestoreUri by mutableStateOf<Uri?>(null)
    private var latestVersionName by mutableStateOf(BuildConfig.VERSION_NAME)
    private var latestUpdateChannel by mutableStateOf(defaultUpdateChannel)

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var isMusicServiceBound = false
    private var immersiveStatusBarsHidden = false

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                isMusicServiceBound = true
                if (service is MusicBinder) {
                    playerConnection =
                        PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    playPendingDeepLinkQueueIfReady()
                    playPendingVoiceSearchIfReady()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isMusicServiceBound = false
                playerConnection?.dispose()
                playerConnection = null
            }
        }

    private fun playPendingDeepLinkQueueIfReady() {
        val pending = pendingDeepLinkQueue ?: return
        val connection = playerConnection ?: return
        pendingDeepLinkQueue = null
        connection.playQueue(pending)
    }

    private fun playPendingVoiceSearchIfReady() {
        val query = pendingVoiceSearchQuery ?: return
        val connection = playerConnection ?: return
        pendingVoiceSearchQuery = null
        connection.playFromVoiceSearch(query)
    }

    private fun hasRestorablePlayback(connection: PlayerConnection): Boolean =
        (
            connection.player.currentMediaItem != null ||
                connection.player.mediaItemCount > 0 ||
                connection.mediaMetadata.value != null
        )

    private suspend fun awaitRestorablePlayback(connection: PlayerConnection): Boolean {
        repeat(15) {
            if (hasRestorablePlayback(connection)) return true
            delay(100)
        }

        return hasRestorablePlayback(connection)
    }

    override fun onStart() {
        super.onStart()
        val highestRate = window.decorView.display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 0f
        window.attributes = window.attributes.apply { preferredRefreshRate = highestRate }
        isMusicServiceBound =
            bindService(
                Intent(this, MusicService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        playPendingDeepLinkQueueIfReady()
    }

    private fun safeUnbindMusicService() {
        if (!isMusicServiceBound) return
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
        } catch (e: Exception) {
            reportException(e)
        } finally {
            isMusicServiceBound = false
        }
    }

    override fun onStop() {
        if (!isMusicServiceBound) {
            super.onStop()
            return
        }
        safeUnbindMusicService()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()

        val shouldStopOnTaskClear =
            if (!isFinishing) {
                false
            } else {
                dataStore.get(StopMusicOnTaskClearKey, false)
            }

        if (shouldStopOnTaskClear) {
            playerConnection?.service?.stopAndClearPlayback(clearPersistentState = true)
            safeUnbindMusicService()
            stopService(Intent(this, MusicService::class.java))
            playerConnection = null
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && immersiveStatusBarsHidden) {
            setStatusBarsHidden(true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::navController.isInitialized) {
            handleIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val initialLocale =
            if (PreferenceStore.get(UseSystemLanguageKey) ?: true) {
                Locale.getDefault()
            } else {
                Locale.ENGLISH
            }
        setAppLocale(this, initialLocale)

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                dataStore.data.first().let { prefs ->
                    if (prefs[UseSystemLanguageKey] ?: true) {
                        Locale.getDefault()
                    } else {
                        Locale.ENGLISH
                    }
                }
            }.onSuccess { targetLocale ->
                if (targetLocale != initialLocale) {
                    withContext(Dispatchers.Main) {
                        setAppLocale(this@MainActivity, targetLocale)
                        recreate()
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    withContext(Dispatchers.Main) {
                        if (it) {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE,
                            )
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                }
        }

        setContent {
            val gatekeeperViewModel: GatekeeperViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
                gatekeeperViewModel.blockedMessages.collect { message ->
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }

            val updateChannel by rememberEnumPreference(UpdateChannelKey, defaultValue = defaultUpdateChannel)
            val updateSettingsFlow = remember(observeUpdateSettings) { observeUpdateSettings() }
            val updateSettings by
                updateSettingsFlow.collectAsStateWithLifecycle(
                    initialValue = null,
                )

            LaunchedEffect(Unit) {
                while (playerConnection == null) {
                    delay(100)
                }
                delay(500)

                try {
                    val redownload = withContext(Dispatchers.IO) {
                        dataStore.data.first()[moe.rukamori.archivetune.constants.RedownloadOnRestoreKey] ?: false
                    }
                    if (redownload) {
                        val downloaded = withContext(Dispatchers.IO) {
                            database.downloadedSongsList()
                        }
                        if (downloaded.isNotEmpty()) {
                            downloaded.forEach { song ->
                                val downloadRequest = androidx.media3.exoplayer.offline.DownloadRequest
                                    .Builder(song.id, song.id.toUri())
                                    .setCustomCacheKey(song.id)
                                    .setData(song.title.toByteArray())
                                    .build()
                                androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                                    this@MainActivity,
                                    moe.rukamori.archivetune.playback.ExoDownloadService::class.java,
                                    downloadRequest,
                                    false,
                                )
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Re-downloading ${downloaded.size} offline songs...", Toast.LENGTH_LONG).show()
                            }
                        }
                        withContext(Dispatchers.IO) {
                            dataStore.edit { prefs ->
                                prefs[moe.rukamori.archivetune.constants.RedownloadOnRestoreKey] = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    moe.rukamori.archivetune.utils.reportException(e)
                }

            }

            LaunchedEffect(
                updateSettings?.automaticChecksEnabled,
                updateSettings?.notificationsEnabled,
                updateChannel,
            ) {
                val currentUpdateSettings = updateSettings ?: return@LaunchedEffect
                if (
                    currentUpdateSettings.automaticChecksEnabled &&
                    BuildConfig.UPDATER_AVAILABLE &&
                    updateChannel != UpdateChannel.ARTIFACT &&
                    System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds
                ) {
                    Updater.getLatestVersionName().onSuccess { latestVersion ->
                        if (Updater.isUpdateAvailable(latestVersion, BuildConfig.VERSION_NAME)) {
                            latestUpdateChannel = UpdateChannel.STABLE
                            latestVersionName = latestVersion
                        }
                    }
                }

                moe.rukamori.archivetune.utils.UpdateNotificationManager
                    .checkForUpdates(this@MainActivity)
            }

            // Use remembered instances so the same state object is used everywhere
            // (previously retrieving the composition local directly created different
            // instances in different composition scopes which caused the update
            // bottom sheet to not appear and overlay interactions to be blocked).
            val bottomSheetPageState =
                remember {
                    moe.rukamori.archivetune.ui.component
                        .BottomSheetPageState()
                }
            val menuState =
                remember {
                    moe.rukamori.archivetune.ui.component
                        .MenuState()
                }
            val releaseNotesState = remember { mutableStateOf<String?>(null) }
            val updateSheetContent: @Composable ColumnScope.() -> Unit = {
                // receiver: ColumnScope
                Text(
                    text = stringResource(R.string.new_update_available),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 16.dp),
                )

                Spacer(Modifier.height(8.dp))

                androidx.compose.material3.OutlinedButton(
                    onClick = {},
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 5.dp,
                            vertical = 5.dp,
                        ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = latestVersionName, style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                ) {
                    val notes = releaseNotesState.value
                    if (notes != null && notes.isNotBlank()) {
                        MarkdownText(
                            markdown = notes,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.release_notes_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                androidx.compose.material3.Button(
                    onClick = {
                        bottomSheetPageState.dismiss()
                        this@MainActivity.navController.navigate("settings/update") {
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.update_text))
                }
            }

            // fetch release notes and show sheet when a new version is detected
            LaunchedEffect(latestVersionName, latestUpdateChannel, updateChannel) {
                if (
                    BuildConfig.UPDATER_AVAILABLE &&
                    latestUpdateChannel == updateChannel &&
                    latestUpdateChannel != UpdateChannel.ARTIFACT &&
                    Updater.isUpdateAvailable(latestVersionName, BuildConfig.VERSION_NAME)
                ) {
                    val releaseNotesResult =
                        when (latestUpdateChannel) {
                            UpdateChannel.STABLE -> Updater.getLatestReleaseNotes()
                            else -> return@LaunchedEffect
                        }
                    releaseNotesResult
                        .onSuccess {
                            releaseNotesState.value = it
                        }.onFailure {
                            releaseNotesState.value = null
                        }

                    bottomSheetPageState.show(updateSheetContent)
                }
            }

            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkModeDefault)
            val defaultDisableAnimations = remember(this@MainActivity) { applicationContext.isLowRamDevice() }
            val userDisabledAnimations by rememberPreference(
                DisableAnimationsKey,
                defaultValue = defaultDisableAnimations,
            )
            // Battery saver (app.vibra.power): Normal stops ambient motion (LocalAmbientMotion),
            // Max turns every animation off, exactly like the "disable animations" setting.
            val powerSaveLevel by PowerSave.level.collectAsStateWithLifecycle()
            val disableAnimations = userDisabledAnimations || powerSaveLevel == PowerSaveLevel.MAX
            val fontPreference by rememberEnumPreference(FontPreferenceKey, defaultValue = AppFontPreference.DEFAULT)
            val customFontUri by rememberPreference(CustomFontUriKey, defaultValue = "")
            val legacyUseSystemFont by rememberPreference(UseSystemFontKey, defaultValue = false)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme =
                remember(darkTheme, isSystemInDarkTheme) {
                    if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
                }
            val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
            val pureBlack = pureBlackEnabled && useDarkTheme
            // Vibra: the main colour picked in the profile's edit mode seeds the theme.
            val customThemeColor by rememberPreference(CustomThemeColorKey, defaultValue = "default")
            val themeColor =
                remember(customThemeColor) {
                    customThemeColor
                        .takeIf { it.startsWith("#") }
                        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                        ?: DefaultThemeColor
                }

            LaunchedEffect(legacyUseSystemFont) {
                if (!legacyUseSystemFont) return@LaunchedEffect
                val preferences = dataStore.data.first()
                if (preferences[FontPreferenceKey] == null) {
                    dataStore.edit { it[FontPreferenceKey] = AppFontPreference.SYSTEM.name }
                }
            }

            ArchiveTuneTheme(
                darkTheme = useDarkTheme,
                pureBlack = pureBlack,
                themeColor = themeColor,
                seedPalette = null,
                disableAnimations = disableAnimations,
                fontPreference = fontPreference,
                customFontUri = customFontUri,
            ) {
                val navController = rememberNavController()
                val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                val onboardingState by onboardingViewModel.screenState.collectAsStateWithLifecycle()
                var showOnboardingLogin by rememberSaveable { mutableStateOf(false) }
                val shouldShowOnboarding =
                    when (val state = onboardingState) {
                        OnboardingScreenState.Loading -> true
                        OnboardingScreenState.Empty -> true
                        is OnboardingScreenState.Error -> false
                        is OnboardingScreenState.Success -> state.uiState.shouldShowOnboarding
                    }

                if (shouldShowOnboarding) {
                    // Vibra onboarding (app.vibra.ui.onboarding). The sign-in screens take its
                    // place while open; its state lives in an activity-scoped ViewModel, so it
                    // resumes where it was when they close.
                    val vibraOnboarding: VibraOnboardingViewModel = hiltViewModel()
                    var showOnboardingSpotify by rememberSaveable { mutableStateOf(false) }
                    if (showOnboardingLogin) {
                        CompositionLocalProvider(
                            LocalPlayerAwareWindowInsets provides WindowInsets.systemBars,
                        ) {
                            LoginScreen(
                                navController = navController,
                                onLoginComplete = {
                                    showOnboardingLogin = false
                                    vibraOnboarding.onYouTubeSignedIn()
                                },
                                onNavigateBack = { showOnboardingLogin = false },
                            )
                        }
                    } else {
                        VibraOnboarding(
                            viewModel = vibraOnboarding,
                            onSignInYouTube = { showOnboardingLogin = true },
                            onSignInSpotify = { showOnboardingSpotify = true },
                            onFinished = onboardingViewModel::complete,
                        )
                        // Spotify only imports artists: its sign-in sheet opens over the
                        // onboarding, which then moves on to the Artists step.
                        if (showOnboardingSpotify) {
                            SpotifyOnboardingLogin(
                                onDismiss = { showOnboardingSpotify = false },
                                onConnected = {
                                    showOnboardingSpotify = false
                                    vibraOnboarding.onSpotifySignedIn()
                                },
                            )
                        }
                    }
                    return@ArchiveTuneTheme
                }

                // ── Vibra shell ───────────────────────────────────────────────────────
                // The legacy scaffold (top app bar, navigation bar/rail and bottom-sheet
                // player) is replaced by the Vibra shell (app.vibra.ui.shell). Engine
                // screens, services, intents and composition locals are unchanged.
                DisposableEffect(navController) {
                    this@MainActivity.navController = navController
                    onDispose {}
                }
                val homeViewModel: HomeViewModel = hiltViewModel()
                val networkBannerViewModel: NetworkBannerViewModel = hiltViewModel()
                val networkBannerState by networkBannerViewModel.bannerState.collectAsStateWithLifecycle()
                val shellController = remember { VibraShellController() }
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val startDestination =
                    when {
                        intent?.action == ACTION_MUSIC_RECOGNITION -> MusicRecognitionRoute
                        intent?.action == ACTION_LIBRARY -> Screens.Library.route
                        else -> Screens.Home.route
                    }

                // Year in Music is an immersive story: hide the status bar there.
                val isYearInMusicScreen = currentRoute?.startsWith("year_in_music") == true
                LaunchedEffect(isYearInMusicScreen) {
                    setStatusBarsHidden(isYearInMusicScreen)
                }

                // Deep links, shared links, voice search and launcher shortcuts.
                LaunchedEffect(Unit) {
                    val initialIntent = pendingIntent ?: intent
                    pendingIntent = null
                    handleIntent(initialIntent, navController)
                    if (initialIntent?.action == ACTION_SEARCH) shellController.openSearch()
                }
                // Engine screens request the search UI through their saved state.
                val openSearchFromRoute =
                    navBackStackEntry
                        ?.savedStateHandle
                        ?.getStateFlow("openSearch", false)
                        ?.collectAsStateWithLifecycle()
                LaunchedEffect(openSearchFromRoute?.value) {
                    if (openSearchFromRoute?.value == true) {
                        navBackStackEntry?.savedStateHandle?.set("openSearch", false)
                        shellController.openSearch()
                    }
                }

                val haptic = LocalHapticFeedback.current
                val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
                val customHaptic =
                    remember(haptic, enableHapticFeedback) {
                        object : HapticFeedback {
                            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                                if (enableHapticFeedback) {
                                    haptic.performHapticFeedback(hapticFeedbackType)
                                }
                            }
                        }
                    }

                CompositionLocalProvider(
                    LocalHapticFeedback provides customHaptic,
                    LocalAnimationsDisabled provides disableAnimations,
                    LocalAmbientMotion provides (powerSaveLevel == PowerSaveLevel.OFF),
                    LocalDatabase provides database,
                    LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                    LocalPlayerConnection provides playerConnection,
                    LocalDownloadUtil provides downloadUtil,
                    LocalShimmerTheme provides ShimmerTheme,
                    LocalSyncUtils provides syncUtils,
                    moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState provides bottomSheetPageState,
                    moe.rukamori.archivetune.ui.component.LocalMenuState provides menuState,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        VibraAppShell(
                            navController = navController,
                            connection = playerConnection,
                            homeViewModel = homeViewModel,
                            controller = shellController,
                            startDestination = startDestination,
                            latestVersionName = { latestVersionName },
                            onClearUpdateBadge = { latestVersionName = BuildConfig.VERSION_NAME },
                            disableAnimations = disableAnimations,
                            pureBlack = pureBlack,
                        )

                        BottomSheetMenu(
                            state = LocalMenuState.current,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )

                        BottomSheetPage(
                            state = LocalBottomSheetPageState.current,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )

                        NetworkStatusBanner(
                            state = networkBannerState,
                            modifier =
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                                    .zIndex(10f),
                        )
                    }
                }

                pendingBackupRestoreUri?.let { uri ->
                    BackupRestoreFromIntentDialog(uri = uri)
                }
            }
        }
    }

    private fun isBackupUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val path = uri.lastPathSegment?.lowercase(Locale.US)
        return path?.endsWith(".backup") == true || uri.toString().lowercase(Locale.US).endsWith(".backup")
    }

    private fun handleIntent(
        intent: Intent?,
        navController: NavHostController,
    ) {
        if (intent == null) return
        intent.getStringExtra("navigate_to")?.takeIf { it.isNotBlank() }?.let { route ->
            navController.navigate(route) {
                launchSingleTop = true
            }
            intent.removeExtra("navigate_to")
            return
        }
        val dataUri = intent.data
        if (isBackupUri(dataUri)) {
            pendingBackupRestoreUri = dataUri
            return
        }
        if (intent.action == ACTION_MUSIC_RECOGNITION) {
            navController.openMusicRecognition(0L)
            return
        }

        if (intent.action == "android.media.action.MEDIA_PLAY_FROM_SEARCH") {
            val query =
                (
                    intent.getStringExtra("query")
                        ?: intent.getStringExtra("android.intent.extra.TITLE")
                        ?: ""
                ).trim()
            if (query.isNotBlank()) {
                pendingVoiceSearchQuery = query
                startMusicServiceSafely()
                playPendingVoiceSearchIfReady()
            }
            return
        }
        if (handleExternalAudioIntent(intent)) {
            return
        }
        handleDeepLinkIntent(intent, navController)
    }

    private fun handleExternalAudioIntent(intent: Intent): Boolean {
        val incomingUris =
            buildList {
                intent.data?.let(::add)
                when (intent.action) {
                    Intent.ACTION_SEND -> {
                        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::add)
                    }

                    Intent.ACTION_SEND_MULTIPLE -> {
                        addAll(
                            IntentCompat
                                .getParcelableArrayListExtra(
                                    intent,
                                    Intent.EXTRA_STREAM,
                                    Uri::class.java,
                                ).orEmpty(),
                        )
                    }
                }
            }.distinct()

        if (incomingUris.isEmpty()) return false

        val fallbackMimeType = intent.type
        val playableUris =
            incomingUris.filter { uri ->
                val mimeType = contentResolver.getType(uri)
                mimeType.isAudioMimeType() || fallbackMimeType.isAudioMimeType() || uri.hasAudioExtension()
            }
        if (playableUris.isEmpty()) return false

        pendingDeepLinkQueue = ListQueue(items = playableUris.map(::toExternalAudioMediaItem))
        startMusicServiceSafely()
        playPendingDeepLinkQueueIfReady()
        return true
    }

    private fun toExternalAudioMediaItem(uri: Uri): MediaItem {
        val mediaId = uri.toString()
        val title = resolveExternalAudioTitle(uri)
        val metadata =
            moe.rukamori.archivetune.models.MediaMetadata(
                id = mediaId,
                title = title,
                artists = emptyList(),
                duration = -1,
            )
        return MediaItem
            .Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setTag(metadata)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setIsPlayable(true)
                    .setMediaType(MEDIA_TYPE_MUSIC)
                    .build(),
            ).build()
    }

    private fun resolveExternalAudioTitle(uri: Uri): String {
        val displayName =
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex >= 0 && cursor.moveToFirst()) cursor.getString(columnIndex) else null
                }
            }.getOrNull()
        return displayName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBefore('?')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.unknown)
    }

    private fun String?.isAudioMimeType(): Boolean = this?.startsWith("audio/", ignoreCase = true) == true

    private fun Uri.hasAudioExtension(): Boolean {
        val extension = MimeTypeMap.getFileExtensionFromUrl(toString()).orEmpty()
        val normalized = extension.lowercase(Locale.US)
        return normalized in setOf("aac", "flac", "m4a", "mp3", "ogg", "opus", "wav", "webm")
    }

    private fun handleDeepLinkIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        val coroutineScope = lifecycleScope

        val authority = uri.authority?.lowercase()
        if (uri.scheme.equals("archivetune", ignoreCase = true) && authority == "login") {
            navController.navigate(buildLoginRoute(uri.getQueryParameter(LOGIN_URL_ARGUMENT)))
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> {
                uri.getQueryParameter("list")?.let { playlistId ->
                    if (playlistId.startsWith("OLAK5uy_")) {
                        coroutineScope.launch {
                            YouTube
                                .albumSongs(playlistId)
                                .onSuccess { songs ->
                                    songs.firstOrNull()?.album?.id?.let { browseId ->
                                        navController.navigateToAlbum(browseId)
                                    }
                                }.onFailure { reportException(it) }
                        }
                    } else {
                        navController.navigate("online_playlist/$playlistId")
                    }
                }
            }

            "browse" -> {
                uri.lastPathSegment?.let { browseId ->
                    navController.navigateToAlbum(browseId)
                }
            }

            "channel", "c" -> {
                uri.lastPathSegment?.let { artistId ->
                    navController.navigateToArtist(artistId)
                }
            }

            else -> {
                val videoId =
                    when {
                        path == "watch" -> uri.getQueryParameter("v")
                        uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                        else -> null
                    }
                val playlistId = uri.getQueryParameter("list")
                val shouldShufflePlaylist = uri.requestsShuffledPlayback()

                videoId?.let { vid ->
                    coroutineScope.launch {
                        val result =
                            withContext(Dispatchers.IO) {
                                YouTube.queue(listOf(vid), playlistId)
                            }

                        result
                            .onSuccess { queued ->
                                val mediaItem =
                                    queued.firstOrNull { it.id == vid }?.toMediaItem()
                                        ?: queued.firstOrNull()?.toMediaItem()
                                        ?: MediaItem
                                            .Builder()
                                            .setMediaId(vid)
                                            .setUri(vid)
                                            .setCustomCacheKey(vid)
                                            .build()
                                pendingDeepLinkQueue = ListQueue(items = listOf(mediaItem))
                                startMusicServiceSafely()
                                playPendingDeepLinkQueueIfReady()
                            }.onFailure {
                                reportException(it)
                            }
                    }
                    return
                }

                if (path == "watch" && !playlistId.isNullOrBlank()) {
                    coroutineScope.launch {
                        val result =
                            withContext(Dispatchers.IO) {
                                YouTube.playlist(playlistId)
                            }

                        result
                            .onSuccess { playlistPage ->
                                val endpoint =
                                    when {
                                        shouldShufflePlaylist -> {
                                            playlistPage.playlist.shuffleEndpoint
                                                ?: playlistPage.playlist.playEndpoint
                                        }

                                        else -> {
                                            playlistPage.playlist.playEndpoint
                                                ?: playlistPage.playlist.shuffleEndpoint
                                        }
                                    }

                                endpoint?.let {
                                    pendingDeepLinkQueue = YouTubeQueue.playlist(it)
                                    startMusicServiceSafely()
                                    playPendingDeepLinkQueueIfReady()
                                } ?: navController.navigate("online_playlist/$playlistId")
                            }.onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    private fun android.net.Uri.requestsShuffledPlayback(): Boolean {
        val value = getQueryParameter("shuffle")?.trim()?.lowercase(Locale.US) ?: return false
        return value == "1" || value == "true"
    }

    private fun startMusicServiceSafely() {
        runCatching { startService(Intent(this, moe.rukamori.archivetune.playback.MusicService::class.java)) }
            .onFailure { reportException(it) }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

    private fun setStatusBarsHidden(hidden: Boolean) {
        immersiveStatusBarsHidden = hidden
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes =
                window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        if (hidden) {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        } else {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                        }
                }
        }

        if (hidden) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    @Composable
    private fun BackupRestoreFromIntentDialog(
        uri: Uri,
        viewModel: BackupRestoreViewModel = hiltViewModel(),
    ) {
        var selected by remember { mutableStateOf(BackupCategory.entries.toSet()) }

        AlertDialog(
            onDismissRequest = { pendingBackupRestoreUri = null },
            icon = { Icon(painterResource(R.drawable.restore), null) },
            title = { Text(stringResource(R.string.restore_options_title)) },
            text = {
                Column {
                    BackupCategory.entries.forEach { category ->
                        val isChecked = category in selected
                        val labelRes =
                            when (category) {
                                BackupCategory.LIBRARY -> R.string.backup_category_library
                                BackupCategory.ACCOUNT -> R.string.backup_category_account
                                BackupCategory.SETTINGS -> R.string.backup_category_settings
                                BackupCategory.DOWNLOADS -> R.string.backup_category_downloads
                            }
                        val descRes =
                            when (category) {
                                BackupCategory.LIBRARY -> R.string.backup_category_library_desc
                                BackupCategory.ACCOUNT -> R.string.backup_category_account_desc
                                BackupCategory.SETTINGS -> R.string.backup_category_settings_desc
                                BackupCategory.DOWNLOADS -> R.string.backup_category_downloads_desc
                            }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = Color.Transparent,
                            onClick = {
                                selected = if (isChecked) selected - category else selected + category
                            },
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 72.dp)
                                        .padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(descRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                androidx.compose.material3.Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + category else selected - category
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                VibraDialogButton(
                    primary = true,
                    onClick = {
                        val uri = pendingBackupRestoreUri ?: return@VibraDialogButton
                        pendingBackupRestoreUri = null
                        viewModel.restore(this@MainActivity, uri, selected)
                    },
                    enabled = selected.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.action_restore))
                }
            },
            dismissButton = {
                VibraDialogButton(
                    onClick = { pendingBackupRestoreUri = null },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    companion object {
        const val ACTION_SEARCH = "moe.rukamori.archivetune.action.SEARCH"
        const val ACTION_LIBRARY = "moe.rukamori.archivetune.action.LIBRARY"
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection =
    staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
