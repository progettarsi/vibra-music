/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vibra.ui.components.*
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R

/** Reference controls, independent of the popup's window and transition lifecycle. */
@Composable
fun SleepTimerPanel(onConfirm: (Int) -> Unit, onEndOfSong: () -> Unit) {
  var minutes by remember { mutableIntStateOf(30) }
  val setMinutes: (Float) -> Unit = { minutes = ((it / 5).roundToInt() * 5).coerceIn(5, 120) }
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    ControlSheetHeader(
      stringResource(R.string.sleep_timer),
      stringResource(R.string.vibra_menu_minutes_short, minutes),
      R.drawable.bedtime,
    )
    MenuCard {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(stringResource(R.string.vibra_menu_duration), modifier = Modifier.weight(1f))
          Text(
            stringResource(R.string.vibra_menu_minutes_short, minutes),
            color = MaterialTheme.colorScheme.primary,
          )
        }
        ControlSliderRow(
          minutes.toFloat(),
          setMinutes,
          5f..120f,
          onDecrease = { setMinutes((minutes - 5).toFloat()) },
          onIncrease = { setMinutes((minutes + 5).toFloat()) },
          steps = 22,
          label = stringResource(R.string.vibra_menu_duration),
          valueLabel = stringResource(R.string.vibra_menu_minutes_short, minutes),
        )
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 48.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("5", style = MaterialTheme.typography.labelSmall)
          Text("120", style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(15, 30, 45, 60).forEach { preset ->
            Surface(
              onClick = { minutes = preset },
              modifier = Modifier.weight(1f),
              shape =
                if (minutes == preset) androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                else androidx.compose.foundation.shape.CircleShape,
              color =
                if (minutes == preset) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
              contentColor =
                if (minutes == preset) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            ) {
              Box(
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  stringResource(R.string.vibra_menu_minutes_short, preset),
                  fontSize = 12.sp,
                  maxLines = 1,
                )
              }
            }
          }
        }
      }
    }
    MenuCard {
      MediaMenuRow(
        headlineContent = { Text(stringResource(R.string.end_of_song)) },
        leadingContent = { Icon(painterResource(R.drawable.music_note), null) },
        modifier = Modifier.clickable { onEndOfSong() },
      )
    }
    VibraDialogButton(
      onClick = { onConfirm(minutes) },
      primary = true,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(stringResource(R.string.vibra_menu_start_timer))
    }
  }
}
