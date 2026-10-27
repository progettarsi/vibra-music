/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.shell

import android.os.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibra.ui.components.VibraDialogButton
import app.vibra.update.VibraUpdates
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.DefaultDialog

/**
 * Offers a downloaded update when Android needs the user's confirmation to install it
 * (the first time, or before Android 12). Once allowed, updates install by themselves
 * while the app is in the background and this never shows again.
 */
@Composable
internal fun VibraUpdatePrompt() {
    val release by VibraUpdates.prompt.collectAsStateWithLifecycle()
    val offered = release ?: return
    DefaultDialog(
        onDismiss = VibraUpdates::postpone,
        icon = { Icon(painterResource(R.drawable.update), contentDescription = null) },
        title = { Text(stringResource(R.string.vibra_update_title, offered.versionName)) },
        buttons = {
            VibraDialogButton(onClick = VibraUpdates::postpone) {
                Text(stringResource(R.string.vibra_update_later))
            }
            VibraDialogButton(onClick = VibraUpdates::installNow, primary = true) {
                Text(stringResource(R.string.vibra_update_install))
            }
        },
    ) {
        Text(
            text =
                stringResource(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        R.string.vibra_update_text_once
                    } else {
                        R.string.vibra_update_text
                    },
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
