/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.motion.liquidPress
import app.vibra.ui.motion.nudge
import app.vibra.ui.motion.rememberLiquidNudge
import app.vibra.ui.motion.rememberPlaybackSpin
import app.vibra.ui.theme.VibraDimens
import app.vibra.ui.theme.VibraShapes
import moe.rukamori.archivetune.R

/**
 * The About page's identity card: the VIBRA wave and name on a field of the theme's
 * primary → tertiary colours, over a large cookie that slowly turns like the play button
 * (the record that is always playing). A tap gives the card a little liquid squeeze.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VibraAboutHero(
    version: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberVibraHaptics()
    val squeeze = rememberLiquidNudge()
    val spin = rememberPlaybackSpin(isPlaying = true)
    val interactionSource = remember { MutableInteractionSource() }
    val cookie = MaterialShapes.Cookie12Sided.toShape()
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .padding(horizontal = VibraDimens.Gutter)
                    .fillMaxWidth()
                    .height(HeroHeight)
                    .liquidPress(interactionSource, pressedScale = 0.97f)
                    .nudge(squeeze, squash = 0.04f)
                    .clip(VibraShapes.Card)
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
                    .clickable(interactionSource = interactionSource, indication = null, role = Role.Image) {
                        haptics.toggle(on = true)
                        squeeze.kick()
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize(0.92f)
                    .aspectRatio(1f)
                    .graphicsLayer { rotationZ = spin.degrees }
                    .clip(cookie)
                    .background(scheme.onPrimary.copy(alpha = 0.10f)),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                VibraLogoMark(
                    state = null,
                    color = scheme.onPrimary,
                    modifier =
                        Modifier.graphicsLayer {
                            scaleX = LogoScale
                            scaleY = LogoScale
                        },
                )
                Spacer(Modifier.height(36.dp))
                Text(text = "Vibra", style = MaterialTheme.typography.displayMedium, color = scheme.onPrimary)
                Text(
                    text = stringResource(R.string.vibra_about_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onPrimary.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        Text(
            text = version,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSecondaryContainer,
            modifier =
                Modifier
                    .padding(top = 16.dp)
                    .clip(CircleShape)
                    .background(scheme.secondaryContainer)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

private val HeroHeight = 320.dp
private const val LogoScale = 2.2f
