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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.AboutViewModel

@Composable
fun AboutScreen(navController: NavController) {
    InformationPage(navController, stringResource(R.string.about)) {
        // Vibra: an identity card instead of a plain title.
        app.vibra.ui.components.VibraAboutHero(
            version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.about_vibra_attribution),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp),
        )
        PreferenceEntry(title = { Text("GPL-3.0") },
            onClick = { navController.navigate("settings/about/license/gpl") })
        PreferenceEntry(title = { Text(stringResource(R.string.about_third_party_licenses)) },
            onClick = { navController.navigate("settings/about/licenses") })
        PreferenceEntry(title = { Text(stringResource(R.string.about_font_licenses)) },
            description = "SIL Open Font License 1.1",
            onClick = { navController.navigate("settings/about/fonts") })
    }
}

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
