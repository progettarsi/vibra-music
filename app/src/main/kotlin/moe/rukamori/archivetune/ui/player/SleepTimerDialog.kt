/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

// Vibra: independent radii follow the global rounding style.
import app.vibra.ui.theme.adaptiveRoundedCornerShape

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vibra.ui.components.*
import app.vibra.ui.haptics.rememberVibraHaptics
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R

/** The panel's blocks in reading order; the opening reveals each one on its own. */
enum class SleepTimerBlock {
  Header,
  Readout,
  Slider,
  Presets,
  Actions,
}

/**
 * The sleep timer's controls, laid out on one surface (the caller draws it): the chosen time,
 * written once in large type with the clock time it stops at, the slider, four presets and the
 * two ways to start. [block] decorates each [SleepTimerBlock] (the opening moves them);
 * [icon] decorates the header's moon (the opening flies it in from the button).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerPanel(
  onConfirm: (Int) -> Unit,
  onEndOfSong: () -> Unit,
  modifier: Modifier = Modifier,
  block: (SleepTimerBlock) -> Modifier = { Modifier },
  icon: Modifier = Modifier,
) {
  var minutes by remember { mutableIntStateOf(30) }
  val haptics = rememberVibraHaptics()
  // One tick for every 5-minute step the value crosses.
  val setMinutes: (Float) -> Unit = {
    val next = ((it / 5).roundToInt() * 5).coerceIn(5, 120)
    if (next != minutes) {
      minutes = next
      haptics.tick()
    }
  }
  val context = LocalContext.current
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(15_000)
      now = System.currentTimeMillis()
    }
  }
  val stopsAt =
    remember(now, minutes) {
      DateFormat.getTimeFormat(context).format(Date(now + minutes * 60_000L))
    }
  val minutesLabel = stringResource(R.string.vibra_menu_minutes_short, minutes)
  val colors = MaterialTheme.colorScheme
  // Large text: presets two per row and the actions one above the other, as the home's cards stack.
  val largeText = LocalDensity.current.fontScale >= 1.3f
  // A short window (a phone held sideways): the time and its stop on one line, the header closer.
  val short = LocalConfiguration.current.screenHeightDp < 480

  // 20 dp around and between groups, 10 dp inside a group (the app's card rhythm).
  Column(modifier.padding(SleepTimerPadding)) {
    Row(
      block(SleepTimerBlock.Header),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MenuTokens.ExpandedGap),
    ) {
      // As tall as the pill buttons below: both hug a corner of the panel (see SleepTimerOverlay).
      Surface(shape = CircleShape, color = colors.secondaryContainer) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
          Icon(
            painterResource(R.drawable.bedtime),
            null,
            icon.size(24.dp),
            tint = colors.onSecondaryContainer,
          )
        }
      }
      Text(
        stringResource(R.string.sleep_timer),
        fontSize = MenuTokens.ExpandedTitle,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
      )
    }
    Spacer(Modifier.height(if (short) MenuTokens.ExpandedGap else SleepTimerPadding))
    val readout: @Composable (Modifier) -> Unit = { textModifier ->
      Text(
        minutesLabel,
        textModifier,
        style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.SemiBold,
        color = colors.primary,
      )
      Text(
        stringResource(R.string.vibra_timer_stops_at, stopsAt),
        textModifier,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
      )
    }
    if (short) {
      Row(
        block(SleepTimerBlock.Readout),
        horizontalArrangement = Arrangement.spacedBy(MenuTokens.ExpandedGap),
      ) {
        readout(Modifier.alignByBaseline())
      }
    } else {
      Column(block(SleepTimerBlock.Readout)) { readout(Modifier) }
    }
    Spacer(Modifier.height(MenuTokens.ExpandedGap))
    ControlSliderRow(
      minutes.toFloat(),
      setMinutes,
      5f..120f,
      onDecrease = { setMinutes((minutes - 5).toFloat()) },
      onIncrease = { setMinutes((minutes + 5).toFloat()) },
      modifier = block(SleepTimerBlock.Slider),
      steps = 22,
      label = stringResource(R.string.vibra_menu_duration),
      valueLabel = minutesLabel,
    )
    Spacer(Modifier.height(MenuTokens.ExpandedGap))
    // Four even presets; with very large text they wrap to two rows.
    FlowRow(
      block(SleepTimerBlock.Presets).fillMaxWidth().semantics { selectableGroup() },
      horizontalArrangement = Arrangement.spacedBy(MenuTokens.ExpandedGap),
      verticalArrangement = Arrangement.spacedBy(MenuTokens.ExpandedGap),
      maxItemsInEachRow = if (largeText) 2 else 4,
    ) {
      listOf(15, 30, 45, 60).forEach { preset ->
        val selected = minutes == preset
        Surface(
          onClick = { setMinutes(preset.toFloat()) },
          modifier =
            Modifier.weight(1f).semantics {
              role = Role.RadioButton
              this.selected = selected
            },
          shape = if (selected) adaptiveRoundedCornerShape(12.dp) else CircleShape,
          color = if (selected) colors.primaryContainer else colors.surfaceContainerHighest,
          contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        ) {
          Box(
            Modifier.heightIn(min = 40.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              stringResource(R.string.vibra_menu_minutes_short, preset),
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
            )
          }
        }
      }
    }
    Spacer(Modifier.height(SleepTimerPadding))
    val endOfSong: @Composable (Modifier) -> Unit = { buttonModifier ->
      VibraDialogButton(onClick = onEndOfSong, modifier = buttonModifier) {
        Icon(painterResource(R.drawable.music_note), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.end_of_song), textAlign = TextAlign.Center)
      }
    }
    val start: @Composable (Modifier) -> Unit = { buttonModifier ->
      VibraDialogButton(onClick = { onConfirm(minutes) }, primary = true, modifier = buttonModifier) {
        Text(stringResource(R.string.vibra_menu_start_timer), textAlign = TextAlign.Center)
      }
    }
    if (largeText) {
      // Stacked: the main action first.
      Column(
        block(SleepTimerBlock.Actions).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MenuTokens.ExpandedGap),
      ) {
        start(Modifier.fillMaxWidth())
        endOfSong(Modifier.fillMaxWidth())
      }
    } else {
      Row(
        block(SleepTimerBlock.Actions).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MenuTokens.ExpandedGap),
      ) {
        endOfSong(Modifier.weight(1f))
        start(Modifier.weight(1f))
      }
    }
  }
}

/** Inner margin of the panel and gap between its groups (VibraDimens.Gutter). */
private val SleepTimerPadding = 20.dp
