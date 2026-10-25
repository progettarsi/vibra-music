/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.R

@Composable
@Suppress("UNUSED_PARAMETER")
fun buildSettingsGroups(
    navController: NavController,
    isAndroid12OrLater: Boolean,
    hasUpdate: Boolean,
    context: Context,
): List<SettingsGroup> {
    val topics = listOf(
        Triple("account", R.drawable.account, R.string.account),
        Triple("player", R.drawable.music_note, R.string.settings_playback_audio),
        Triple("storage", R.drawable.storage, R.string.settings_downloads_storage),
        Triple("lyrics", R.drawable.lyrics, R.string.lyrics),
        Triple("appearance", R.drawable.palette, R.string.appearance),
        Triple("privacy", R.drawable.security, R.string.settings_privacy_history),
        Triple("backup_restore", R.drawable.backup, R.string.backup_restore),
        Triple("about", R.drawable.info, R.string.about),
        Triple("advanced", R.drawable.tune, R.string.settings_advanced),
    )
    return listOf(SettingsGroup(
        title = stringResource(R.string.settings),
        items = topics.map { (route, icon, title) ->
            SettingsItem(
                key = route,
                icon = painterResource(icon),
                title = stringResource(title),
                accentColor = MaterialTheme.colorScheme.primary,
                onClick = { navController.navigate("settings/$route") },
            )
        },
    ))
}
