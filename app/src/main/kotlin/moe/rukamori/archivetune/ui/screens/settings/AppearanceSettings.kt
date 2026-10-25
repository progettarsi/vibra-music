/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.theme.CustomFontLoader
import moe.rukamori.archivetune.utils.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(navController: NavController) {
    val context = LocalContext.current
    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, DarkModeDefault)
    val (pureBlack, onPureBlackChange) = rememberPreference(PureBlackKey, false)
    val (disableAnimations, onDisableAnimationsChange) = rememberPreference(DisableAnimationsKey, context.isLowRamDevice())
    val (enableHapticFeedback, onEnableHapticFeedbackChange) = rememberPreference(EnableHapticFeedbackKey, true)
    val (fontPreference, onFontPreferenceChange) = rememberEnumPreference(FontPreferenceKey, AppFontPreference.DEFAULT)
    val (customFontUri, onCustomFontUriChange) = rememberPreference(CustomFontUriKey, "")
    val (customFontName, onCustomFontNameChange) = rememberPreference(CustomFontNameKey, "")
    val customFontPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            if (!CustomFontLoader.isSupportedTtf(context, uri)) {
                Toast.makeText(context, context.getString(R.string.custom_font_invalid), Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            if (customFontUri.isNotBlank() && customFontUri != uri.toString()) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(customFontUri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }

            onCustomFontUriChange(uri.toString())
            onCustomFontNameChange(CustomFontLoader.displayName(context, uri))
            onFontPreferenceChange(AppFontPreference.CUSTOM)
        }
    val pickCustomFont =
        remember(customFontPickerLauncher) {
            {
                customFontPickerLauncher.launch(CustomFontLoader.supportedMimeTypes)
            }
        }
    val onFontPreferenceSelected =
        remember(customFontUri, onFontPreferenceChange, pickCustomFont) {
            { value: AppFontPreference ->
                onFontPreferenceChange(value)
                if (value == AppFontPreference.CUSTOM && customFontUri.isBlank()) {
                    pickCustomFont()
                }
            }
        }

    Scaffold(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background, topBar = {
        moe.rukamori.archivetune.ui.component.EnginePageTopBar(title = stringResource(R.string.appearance), navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back_button_desc))
            }
        })
    }) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState()).padding(bottom = SettingsDimensions.ScreenBottomPadding)) {
            PreferenceGroup(title = stringResource(R.string.theme)) {
                item {
                    EnumListPreference(title = { Text(stringResource(R.string.dark_theme)) },
                        icon = { Icon(painterResource(R.drawable.dark_mode), null) },
                        selectedValue = darkMode, onValueSelected = onDarkModeChange,
                        valueText = { when(it) {
                            DarkMode.ON -> stringResource(R.string.dark_theme_on)
                            DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                            DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
                        } })
                }
                item {
                    SwitchPreference(title = { Text(stringResource(R.string.pure_black)) },
                        checked = pureBlack, onCheckedChange = onPureBlackChange)
                }
                item {
                    SwitchPreference(title = { Text(stringResource(R.string.disable_animations)) },
                        checked = disableAnimations, onCheckedChange = onDisableAnimationsChange)
                }
                item {
                    SwitchPreference(title = { Text(stringResource(R.string.haptics)) },
                        checked = enableHapticFeedback, onCheckedChange = onEnableHapticFeedbackChange)
                }
                item {
                    EnumListPreference(title = { Text(stringResource(R.string.font_preference)) },
                        icon = { Icon(painterResource(R.drawable.text_fields), null) },
                        selectedValue = fontPreference, onValueSelected = onFontPreferenceSelected,
                        valueText = { when(it) {
                            AppFontPreference.DEFAULT -> stringResource(R.string.font_preference_default)
                            AppFontPreference.SYSTEM -> stringResource(R.string.font_preference_system)
                            AppFontPreference.CUSTOM -> stringResource(R.string.font_preference_custom)
                        } })
                }
                item(visible = fontPreference == AppFontPreference.CUSTOM) {
                    PreferenceEntry(title = { Text(stringResource(R.string.custom_font)) },
                        description = customFontName.ifBlank { stringResource(R.string.custom_font_desc) },
                        onClick = pickCustomFont)
                }
            }
        }
    }
}

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    LIBRARY,
}

enum class PlayerTextAlignment {
    SIDED,
    CENTER,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}
