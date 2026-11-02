/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import app.vibra.ui.motion.VibraTextButton as TextButton
import app.vibra.ui.motion.VibraFilledTonalButton
import app.vibra.update.VibraUpdates
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.AboutViewModel

@Composable
fun AboutScreen(navController: NavController) {
    val uriHandler = LocalUriHandler.current
    InformationPage(navController, stringResource(R.string.about)) {
        // Vibra: the wave and the name on the page, then one group of entries.
        app.vibra.ui.components.VibraAboutHero(modifier = Modifier.padding(top = 16.dp))
        PreferenceGroup(modifier = Modifier.padding(top = 12.dp)) {
            item { VibraUpdateEntry() }
            item {
                PreferenceEntry(title = { Text(stringResource(R.string.vibra_about_whats_new)) },
                    icon = { Icon(painterResource(R.drawable.newspaper), null) },
                    onClick = { uriHandler.openUri("$VibraRepositoryUrl/releases/tag/v${BuildConfig.VERSION_NAME}") })
            }
            item {
                PreferenceEntry(title = { Text(stringResource(R.string.vibra_about_open_source)) },
                    icon = { Icon(painterResource(R.drawable.lock_open), null) },
                    onClick = { navController.navigate("settings/about/open-source") })
            }
            item {
                PreferenceEntry(title = { Text(stringResource(R.string.vibra_about_source_code)) },
                    icon = { Icon(painterResource(R.drawable.github), null) },
                    onClick = { uriHandler.openUri(VibraRepositoryUrl) })
            }
        }
        // GPL-3.0 §5: the interface keeps showing where the program comes from.
        Text(
            text = stringResource(R.string.about_vibra_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = app.vibra.ui.theme.VibraDimens.Gutter, vertical = 24.dp),
        )
    }
}

/** The installed version, with a manual check for a newer one (Vibra's own updater). */
@Composable
private fun VibraUpdateEntry() {
    val haptics = app.vibra.ui.haptics.rememberVibraHaptics()
    val manual by VibraUpdates.manualCheck.collectAsStateWithLifecycle()
    val background by VibraUpdates.state.collectAsStateWithLifecycle()
    val status = stringResource(
        when (manual) {
            VibraUpdates.CheckStatus.Checking -> R.string.vibra_update_checking
            VibraUpdates.CheckStatus.UpToDate -> R.string.vibra_update_current
            VibraUpdates.CheckStatus.Ready -> R.string.vibra_update_ready
            VibraUpdates.CheckStatus.Failed -> R.string.vibra_update_check_failed
            VibraUpdates.CheckStatus.Unavailable -> R.string.vibra_update_check_unavailable
            VibraUpdates.CheckStatus.Idle -> when (background) {
                is VibraUpdates.State.Ready -> R.string.vibra_update_ready
                is VibraUpdates.State.Downloading -> R.string.vibra_update_downloading
                VibraUpdates.State.Idle -> R.string.vibra_update_check_hint
            }
        },
    )
    PreferenceEntry(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        title = { Text(stringResource(R.string.vibra_about_version, BuildConfig.VERSION_NAME)) },
        description = status,
        icon = { Icon(painterResource(R.drawable.update), null) },
        trailingContent = {
            VibraFilledTonalButton(
                onClick = {
                    haptics.press()
                    VibraUpdates.checkNow()
                },
                enabled = manual != VibraUpdates.CheckStatus.Checking,
            ) { Text(stringResource(R.string.vibra_update_check_action)) }
        },
    )
}

/** GPL-3.0, the libraries and the fonts, behind the single "Open source licenses" entry. */
@Composable
fun OpenSourceLicensesScreen(navController: NavController) {
    InformationPage(navController, stringResource(R.string.vibra_about_open_source)) {
        PreferenceGroup {
            item {
                PreferenceEntry(title = { Text("GPL-3.0") },
                    onClick = { navController.navigate("settings/about/license/gpl") })
            }
            item {
                PreferenceEntry(title = { Text(stringResource(R.string.about_third_party_licenses)) },
                    onClick = { navController.navigate("settings/about/licenses") })
            }
            item {
                PreferenceEntry(title = { Text(stringResource(R.string.about_font_licenses)) },
                    description = "SIL Open Font License 1.1",
                    onClick = { navController.navigate("settings/about/fonts") })
            }
        }
    }
}

private const val VibraRepositoryUrl = "https://github.com/progettarsi/vibra-music"

@Composable
fun ThirdPartyLicensesScreen(navController: NavController, viewModel: AboutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InformationPage(navController, stringResource(R.string.about_third_party_licenses), scrollable = false) {
        when {
            state.loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            state.failed -> {
                Text(stringResource(R.string.error_unknown), modifier = Modifier.padding(16.dp))
                TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.licenses.size) { index ->
                    val license = state.licenses[index]
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(license.name, style = MaterialTheme.typography.titleMedium)
                        license.version?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        license.licenses?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun FontLicensesScreen(navController: NavController) {
    InformationPage(navController, stringResource(R.string.about_font_licenses)) {
        PreferenceEntry(title = { Text("Google Sans Flex") }, description = "SIL Open Font License 1.1",
            onClick = { navController.navigate("settings/about/license/google-sans") })
        PreferenceEntry(title = { Text("Roboto / Roboto Italic") }, description = "SIL Open Font License 1.1",
            onClick = { navController.navigate("settings/about/license/roboto") })
    }
}

@Composable
fun LicenseTextScreen(navController: NavController, kind: String) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val (title, asset) = when (kind) {
        "google-sans" -> "Google Sans Flex — OFL 1.1" to "OFL-GoogleSansFlex.txt"
        "roboto" -> "Roboto — OFL 1.1" to "OFL-Roboto.txt"
        else -> "GPL-3.0" to "GPL-3.0.txt"
    }
    val license by produceState("", asset) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("licenses/$asset").bufferedReader().use { it.readText() } }
                .getOrElse { resources.getString(R.string.error_unknown) }
        }
    }
    InformationPage(navController, title) {
        SelectionContainer {
            Text(license, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InformationPage(navController: NavController, title: String, scrollable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background, topBar = {
        moe.rukamori.archivetune.ui.component.EnginePageTopBar(title = title, navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back_button_desc))
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(bottom = SettingsDimensions.ScreenBottomPadding), content = content)
    }
}
