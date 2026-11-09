/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.motion.nudge
import app.vibra.ui.motion.rememberLiquidNudge
import app.vibra.ui.theme.VibraDimens
import app.vibra.ui.theme.VibraShapes
import app.vibra.update.VibraUpdates
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R

/**
 * The About page's identity: the VIBRA wave beside the name, and the tagline under it.
 * Plain text on the page background, so the page breathes around it.
 */
@Composable
fun VibraAboutHero(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val status by VibraUpdates.manualCheck.collectAsStateWithLifecycle()
    val checking = status == VibraUpdates.CheckStatus.Checking
    val statusText = stringResource(
        when (status) {
            VibraUpdates.CheckStatus.Idle -> R.string.vibra_update_check_hint
            VibraUpdates.CheckStatus.Checking -> R.string.vibra_update_checking
            VibraUpdates.CheckStatus.UpToDate -> R.string.vibra_update_current
            VibraUpdates.CheckStatus.Ready -> R.string.vibra_update_ready
            VibraUpdates.CheckStatus.Failed -> R.string.vibra_update_check_failed
            VibraUpdates.CheckStatus.Unavailable -> R.string.vibra_update_check_unavailable
        },
    )
    val loader = rememberShapeTracingLoaderState(checking && !LocalAnimationsDisabled.current)
    val haptics = rememberVibraHaptics()
    val squeeze = rememberLiquidNudge()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .nudge(squeeze, squash = 0.04f)
                .shapeTracingLoader(loader, VibraShapes.Card, scheme.primary)
                .semantics {
                    stateDescription = statusText
                    liveRegion = LiveRegionMode.Polite
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !checking,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.vibra_update_check_action),
                ) {
                    haptics.press()
                    squeeze.kick()
                    VibraUpdates.checkNow()
                }.padding(horizontal = VibraDimens.Gutter),
    ) {
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
