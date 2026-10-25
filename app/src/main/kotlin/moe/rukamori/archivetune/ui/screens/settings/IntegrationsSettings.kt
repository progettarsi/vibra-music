/*
 * Vibra (2026) - GPL-3.0 License.
 * Derived from ArchiveTune c Rukamori (github.com/rukamori).
 */
package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.ui.component.EnginePageTopBar
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup

/** Third-party connections live under Advanced, apart from the primary music account. */
@Composable
fun IntegrationsSettings(navController: NavController) {
    Scaffold(
        topBar = {
            EnginePageTopBar(stringResource(R.string.settings_integrations), onBack = { navController.navigateUp() })
        },
    ) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding())
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState()),
        ) {
            AccountServices(navController)
            PreferenceGroup(title = stringResource(R.string.ai_integration)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ai_integration)) },
                        onClick = { navController.navigate("settings/ai_integration") },
                    )
                }
            }
        }
    }
}
