/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.vibra.ui.theme.VibraDimens
import moe.rukamori.archivetune.R

/**
 * The About page's identity: the VIBRA wave beside the name, and the tagline under it.
 * Plain text on the page background, so the page breathes around it.
 */
@Composable
fun VibraAboutHero(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = VibraDimens.Gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            VibraLogoMark(state = null, color = scheme.primary, modifier = Modifier.size(LogoWidth, LogoHeight))
            Text(
                text = "vibra",
                style = MaterialTheme.typography.displayLarge,
                color = scheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            text = stringResource(R.string.vibra_about_tagline),
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

// The wave keeps the drawable's 762 × 527 proportions.
private val LogoWidth = 64.dp
private val LogoHeight = 44.dp
