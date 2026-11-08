/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vibra.ui.theme.VibraShapes
import moe.rukamori.archivetune.R

/** Compact title card shared by the reference's control panels. */
@Composable
fun ControlSheetHeader(
  title: String,
  subtitle: String,
  icon: Int,
  shape: Shape = VibraShapes.Card,
  trailing: @Composable () -> Unit = {},
) {
  MenuCard(shape = shape) {
    // Like the compact media header: one margin all round, the tile concentric in the corner.
    Row(
      Modifier.padding(MenuTokens.CompactHeaderInset),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(concentric(MenuTokens.CardRadius, MenuTokens.CompactHeaderInset)),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
          Icon(painterResource(icon), null, Modifier.size(26.dp))
        }
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank())
          Text(
            subtitle,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
      }
      trailing()
    }
  }
}

/** A reference-style slider with accessible decrement/increment targets. */
@Composable
fun ControlSliderRow(
  value: Float,
  onValueChange: (Float) -> Unit,
  valueRange: ClosedFloatingPointRange<Float>,
  onDecrease: () -> Unit,
  onIncrease: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  steps: Int = 0,
  onValueChangeFinished: (() -> Unit)? = null,
  label: String = "",
  valueLabel: String? = null,
) {
  Row(
    modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    FilledTonalIconButton(
      onClick = onDecrease,
      enabled = enabled && value > valueRange.start,
      modifier = Modifier.size(40.dp),
    ) {
      Icon(painterResource(R.drawable.remove), stringResource(R.string.vibra_menu_decrease))
    }
    ReferenceSlider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      steps = steps,
      enabled = enabled,
      onValueChangeFinished = onValueChangeFinished,
      label = label,
      valueLabel = valueLabel,
      modifier = Modifier.weight(1f),
    )
    FilledTonalIconButton(
      onClick = onIncrease,
      enabled = enabled && value < valueRange.endInclusive,
      modifier = Modifier.size(40.dp),
    ) {
      Icon(painterResource(R.drawable.add), stringResource(R.string.vibra_menu_increase))
    }
  }
}

/** Two joined choices, matching the semitone / multiplier control in the reference. */
@Composable
fun ControlModeChoice(label: String, selected: Boolean, first: Boolean, onClick: () -> Unit) {
  val shape =
    app.vibra.ui.theme.adaptiveRoundedCornerShape(
      topStart = if (first) 16.dp else 4.dp,
      bottomStart = if (first) 16.dp else 4.dp,
      topEnd = if (first) 4.dp else 16.dp,
      bottomEnd = if (first) 4.dp else 16.dp,
    )
  Surface(
    onClick = onClick,
    modifier =
      Modifier.semantics {
        role = Role.RadioButton
        this.selected = selected
      },
    shape = shape,
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor =
      if (selected) MaterialTheme.colorScheme.onPrimaryContainer
      else MaterialTheme.colorScheme.onSurface,
  ) {
    Text(label, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
  }
}
