/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** A real vertical gesture target: no rotated layout or inverted touch coordinates. */
@Composable
fun EqualizerBandControl(
  value: Int,
  minimum: Int,
  maximum: Int,
  label: String,
  valueLabel: String,
  enabled: Boolean,
  onValueChange: (Int) -> Unit,
  onFinished: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var draft by remember { mutableFloatStateOf(value.toFloat()) }
  var dragging by remember { mutableStateOf(false) }
  var height by remember { mutableIntStateOf(1) }
  LaunchedEffect(value, dragging) { if (!dragging) draft = value.toFloat() }
  val change by rememberUpdatedState(onValueChange)
  val finish by rememberUpdatedState(onFinished)
  val range = minimum.toFloat()..maximum.toFloat()
  val update: (Float) -> Unit = {
    val next = it.coerceIn(range)
    val previousLevel = draft.roundToInt()
    draft = next
    // Fractional movement still draws smoothly, but the DSP accepts integer millibels.
    if (next.roundToInt() != previousLevel) change(next.roundToInt())
  }
  val primary = MaterialTheme.colorScheme.primary
  val track = MaterialTheme.colorScheme.secondaryContainer
  Column(
    modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(valueLabel, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
    Canvas(
      Modifier.width(48.dp)
        .height(120.dp)
        .onSizeChanged { height = it.height }
        .semantics {
          contentDescription = "$label Hz"
          stateDescription = "$valueLabel dB"
          progressBarRangeInfo = ProgressBarRangeInfo(draft, range)
          if (!enabled) disabled()
          setProgress {
            if (enabled) {
              update(it)
              finish()
              true
            } else false
          }
        }
        .draggable(
          orientation = Orientation.Vertical,
          enabled = enabled,
          state =
            rememberDraggableState { delta ->
              update(draft - delta * (maximum - minimum) / height.coerceAtLeast(1))
            },
          onDragStarted = {
            dragging = true
            draft = value.toFloat()
          },
          onDragStopped = {
            dragging = false
            finish()
          },
        )
        .pointerInput(enabled, minimum, maximum) {
          if (enabled)
            detectTapGestures { point ->
              update(maximum - point.y / size.height.coerceAtLeast(1) * (maximum - minimum))
              finish()
            }
        }
    ) {
      val alpha = if (enabled) 1f else 0.38f
      val width = 14.dp.toPx()
      val x = (size.width - width) / 2f
      val fraction = ((draft - minimum) / (maximum - minimum).coerceAtLeast(1)).coerceIn(0f, 1f)
      val y = size.height * (1f - fraction)
      val gap = 4.dp.toPx()
      if (y > gap)
        drawRoundRect(
          track.copy(alpha = alpha),
          Offset(x, 0f),
          Size(width, y - gap),
          CornerRadius(width / 2),
        )
      if (y + gap < size.height)
        drawRoundRect(
          primary.copy(alpha = alpha),
          Offset(x, y + gap),
          Size(width, size.height - y - gap),
          CornerRadius(width / 2),
        )
      drawRoundRect(
        primary.copy(alpha = alpha),
        Offset(
          (size.width - 26.dp.toPx()) / 2,
          (y - 2.dp.toPx()).coerceIn(0f, size.height - 4.dp.toPx()),
        ),
        Size(26.dp.toPx(), 4.dp.toPx()),
        CornerRadius(2.dp.toPx()),
      )
    }
    Text(label, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
  }
}
