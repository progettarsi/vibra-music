/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Rounded track and narrow thumb from the control-sheet reference, with native gestures and
 * semantics.
 */
@Composable
fun ReferenceSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  steps: Int = 0,
  onValueChangeFinished: (() -> Unit)? = null,
  label: String = "",
  valueLabel: String? = null,
) {
  var draft by remember { mutableFloatStateOf(value) }
  var dragging by remember { mutableStateOf(false) }
  // Unsnapped finger position: stepping each small drag delta from the snapped value would round
  // back to the same step, so a slow drag never moved the thumb.
  var dragPosition by remember { mutableFloatStateOf(value) }
  var width by remember { mutableIntStateOf(1) }
  val change by rememberUpdatedState(onValueChange)
  val finish by rememberUpdatedState(onValueChangeFinished)
  LaunchedEffect(value, dragging) { if (!dragging) draft = value }
  val minimum = valueRange.start
  val maximum = valueRange.endInclusive
  val update: (Float) -> Unit = { raw ->
    val clamped = raw.coerceIn(minimum, maximum)
    val next =
      if (steps > 0)
        minimum +
          (((clamped - minimum) / (maximum - minimum) * (steps + 1)).roundToInt().toFloat() /
            (steps + 1)) * (maximum - minimum)
      else clamped
    if (next != draft) {
      draft = next
      change(next)
    }
  }
  val primary = MaterialTheme.colorScheme.primary
  val track = MaterialTheme.colorScheme.secondaryContainer
  Canvas(
    modifier
      .fillMaxWidth()
      .height(40.dp)
      .onSizeChanged { width = it.width }
      .semantics {
        if (label.isNotBlank()) contentDescription = label
        if (valueLabel != null) stateDescription = valueLabel
        progressBarRangeInfo = ProgressBarRangeInfo(draft, valueRange, steps)
        if (!enabled) disabled()
        setProgress {
          if (enabled) {
            update(it)
            finish?.invoke()
            true
          } else false
        }
      }
      .draggable(
        orientation = Orientation.Horizontal,
        enabled = enabled,
        state =
          rememberDraggableState { delta ->
            dragPosition =
              (dragPosition + delta / width.coerceAtLeast(1) * (maximum - minimum)).coerceIn(
                minimum,
                maximum,
              )
            update(dragPosition)
          },
        onDragStarted = { start ->
          dragging = true
          dragPosition = minimum + start.x / width.coerceAtLeast(1) * (maximum - minimum)
          update(dragPosition)
        },
        onDragStopped = {
          dragging = false
          finish?.invoke()
        },
      )
      .pointerInput(enabled, minimum, maximum, steps) {
        if (enabled)
          detectTapGestures { point ->
            update(minimum + point.x / size.width.coerceAtLeast(1) * (maximum - minimum))
            finish?.invoke()
          }
      }
  ) {
    val alpha = if (enabled) 1f else 0.38f
    val fraction = ((draft - minimum) / (maximum - minimum)).coerceIn(0f, 1f)
    val thumb = 4.dp.toPx()
    val x = thumb / 2 + (size.width - thumb) * fraction
    val gap = 6.dp.toPx()
    val height = 16.dp.toPx()
    val y = (size.height - height) / 2
    if (x > gap)
      drawRoundRect(
        primary.copy(alpha = alpha),
        Offset(0f, y),
        Size(x - gap, height),
        CornerRadius(height / 2),
      )
    if (x + gap < size.width)
      drawRoundRect(
        track.copy(alpha = alpha),
        Offset(x + gap, y),
        Size(size.width - x - gap, height),
        CornerRadius(height / 2),
      )
    drawRoundRect(
      primary.copy(alpha = alpha),
      Offset(x - thumb / 2, (size.height - 32.dp.toPx()) / 2),
      Size(thumb, 32.dp.toPx()),
      CornerRadius(thumb / 2),
    )
  }
}
