/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.vibra.ui.components.VibraDialog
import app.vibra.ui.components.VibraDialogButton
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.theme.VibraShapes
import moe.rukamori.archivetune.R
import kotlin.math.roundToInt

/**
 * Sleep timer dialog: pick a duration in minutes or stop at the end of the current song.
 *
 * Vibra: the duration is the headline of the card, with one-tap presets above a slider
 * that ticks a haptic on every 5-minute step; the actions are pills.
 */
@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onEndOfSong: () -> Unit,
    initialValue: Float = 30f,
) {
    val haptics = rememberVibraHaptics()
    var minutes by remember { mutableIntStateOf(initialValue.roundToInt().coerceIn(MinMinutes, MaxMinutes)) }

    VibraDialog(onDismissRequest = onDismiss) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.bedtime),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.sleep_timer),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            text = pluralStringResource(R.plurals.minute, minutes, minutes),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Presets.forEach { preset ->
                val selected = preset == minutes
                Surface(
                    onClick = {
                        haptics.tick()
                        minutes = preset
                    },
                    shape = VibraShapes.Pill,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = preset.toString(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Slider(
            value = minutes.toFloat(),
            onValueChange = { value ->
                val stepped = value.roundToInt()
                if (stepped != minutes) {
                    haptics.tick()
                    minutes = stepped
                }
            },
            valueRange = MinMinutes.toFloat()..MaxMinutes.toFloat(),
            steps = (MaxMinutes - MinMinutes) / StepMinutes - 1,
            // 23 tick dots crowd the track; the haptic tick already marks each step.
            colors =
                SliderDefaults.colors(
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        VibraDialogButton(onClick = onEndOfSong, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.end_of_song))
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VibraDialogButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(android.R.string.cancel))
            }
            VibraDialogButton(onClick = { onConfirm(minutes) }, primary = true, modifier = Modifier.weight(1f)) {
                Text(stringResource(android.R.string.ok))
            }
        }
    }
}

private const val MinMinutes = 5
private const val MaxMinutes = 120
private const val StepMinutes = 5
private val Presets = listOf(15, 30, 45, 60)
