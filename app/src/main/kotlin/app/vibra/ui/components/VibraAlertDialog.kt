/*
 * Vibra (2026) — adaptive dialog compatibility for inherited application flows.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

enum class DialogActionStyle {
  Confirm,
  Dismiss,
}

val LocalDialogActionStyle = compositionLocalOf<DialogActionStyle?> { null }
val LocalDialogActionModifier = compositionLocalOf<Modifier> { Modifier }

/** Keeps the engine's slots and callbacks while giving every alert the shared sheet. */
@Composable
fun VibraAlertDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  dismissButton: (@Composable () -> Unit)? = null,
  icon: (@Composable () -> Unit)? = null,
  title: (@Composable () -> Unit)? = null,
  text: (@Composable () -> Unit)? = null,
  shape: Shape = app.vibra.ui.theme.VibraShapes.Dialog,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
  iconContentColor: Color = MaterialTheme.colorScheme.primary,
  titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
  textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  tonalElevation: Dp = 0.dp,
  properties: DialogProperties = DialogProperties(),
) {
  VibraDialog(
    onDismissRequest,
    modifier,
    properties,
    contentPadding = PaddingValues(0.dp),
    groupContent = false,
  ) {
    MenuCard(Modifier.weight(1f, fill = false)) {
      Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        icon?.let {
          CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides iconContentColor
          ) {
            it()
          }
        }
        title?.let { ProvideTextStyle(MaterialTheme.typography.titleLarge) { it() } }
        text?.invoke()
      }
    }
    Row(
      Modifier.fillMaxWidth().padding(top = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (dismissButton != null)
        Box(Modifier.weight(1f)) {
          CompositionLocalProvider(LocalDialogActionStyle provides DialogActionStyle.Dismiss) {
            dismissButton()
          }
        }
      Box(Modifier.weight(1f)) {
        CompositionLocalProvider(LocalDialogActionStyle provides DialogActionStyle.Confirm) {
          confirmButton()
        }
      }
    }
  }
}

/** Compatibility for custom dialog bodies; platform file/auth/share windows are excluded. */
@Composable
fun VibraWindowDialog(
  onDismissRequest: () -> Unit,
  properties: DialogProperties = DialogProperties(),
  content: @Composable () -> Unit,
) {
  VibraDialog(
    onDismissRequest = onDismissRequest,
    properties = properties,
    contentPadding = PaddingValues(0.dp),
  ) {
    content()
  }
}

/** Custom alert bodies share the opening limit and expansion gesture too. */
@Composable
fun VibraBasicAlertDialog(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  properties: DialogProperties = DialogProperties(),
  content: @Composable () -> Unit,
) {
  VibraDialog(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    properties = properties,
    contentPadding = PaddingValues(0.dp),
  ) {
    content()
  }
}
