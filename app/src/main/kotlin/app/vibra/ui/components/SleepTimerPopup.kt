/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import app.vibra.ui.motion.Liquid
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.SleepTimerPanel

/** Geometry is shared by rendering and tests; an anchor near an edge never clips a panel. */
fun timerPopupBounds(
  anchor: Rect,
  panel: Size,
  viewport: Size,
  margin: Float,
  topInset: Float,
  bottomInset: Float,
): Rect {
  val width = panel.width.coerceAtMost((viewport.width - margin * 2).coerceAtLeast(1f))
  val height =
    panel.height.coerceAtMost(
      (viewport.height - topInset - bottomInset - margin * 2).coerceAtLeast(1f)
    )
  val left =
    (anchor.right - width).coerceIn(margin, (viewport.width - margin - width).coerceAtLeast(margin))
  val above = anchor.top - margin / 2 - height
  val below = anchor.bottom + margin / 2
  val top =
    (if (above >= topInset + margin) above else below).coerceIn(
      topInset + margin,
      (viewport.height - bottomInset - margin - height).coerceAtLeast(topInset + margin),
    )
  return Rect(left, top, left + width, top + height)
}

/** One interruptible surface grows from / returns to the measured player button. */
@Composable
fun SleepTimerPopup(
  expanded: Boolean,
  anchor: Rect,
  onDismiss: () -> Unit,
  onAnchorClick: () -> Unit,
  onConfirm: (Int) -> Unit,
  onEndOfSong: () -> Unit,
  anchorColor: Color = MaterialTheme.colorScheme.secondaryContainer,
  anchorContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
  val progress = remember { Animatable(0f) }
  var mounted by remember { mutableStateOf(false) }
  val motion = Liquid.container<Float>()
  LaunchedEffect(expanded) {
    if (expanded) {
      mounted = true
      progress.animateTo(1f, motion)
    } else {
      progress.animateTo(0f, motion)
      mounted = false
    }
  }
  if (!mounted || anchor.isEmpty) return
  val density = LocalDensity.current
  val window = LocalWindowInfo.current.containerSize
  val margin = with(density) { 20.dp.toPx() }
  val topInset = WindowInsets.statusBars.getTop(density).toFloat()
  val bottomInset = WindowInsets.navigationBars.getBottom(density).toFloat()
  var panelSize by remember { mutableStateOf(IntSize.Zero) }
  val viewport = Size(window.width.toFloat(), window.height.toFloat())
  val target =
    timerPopupBounds(
      anchor,
      Size(panelSize.width.toFloat(), panelSize.height.toFloat()),
      viewport,
      margin,
      topInset,
      bottomInset,
    )
  val panelColor = MaterialTheme.colorScheme.background
  val timerTitle = stringResource(R.string.sleep_timer)
  val currentDismiss by rememberUpdatedState(onDismiss)
  val currentAnchorClick by rememberUpdatedState(onAnchorClick)
  val currentAnchor by rememberUpdatedState(anchor)
  val currentTarget by rememberUpdatedState(target)
  Popup(
    popupPositionProvider =
      remember {
        object : PopupPositionProvider {
          override fun calculatePosition(
            anchorBounds: IntRect,
            windowSize: IntSize,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
          ) = IntOffset.Zero
        }
      },
    onDismissRequest = onDismiss,
    properties = PopupProperties(focusable = true, clippingEnabled = false),
  ) {
    Box(
      Modifier.requiredSize(
          with(density) { window.width.toDp() },
          with(density) { window.height.toDp() },
        )
        .pointerInput(Unit) {
          detectTapGestures { point ->
            if (currentAnchor.contains(point)) currentAnchorClick() else currentDismiss()
          }
        }
        .drawBehind {
          val p = progress.value.coerceIn(0f, 1f)
          val rect = lerp(anchor, target, p)
          val radius = androidx.compose.ui.util.lerp(anchor.height / 2, 32.dp.toPx(), p)
          drawRoundRect(
            androidx.compose.ui.graphics.lerp(anchorColor, panelColor, p),
            rect.topLeft,
            rect.size,
            CornerRadius(radius),
          )
        }
    ) {
      Icon(
        painterResource(R.drawable.bedtime),
        null,
        tint = anchorContentColor,
        modifier =
          Modifier.offset {
              IntOffset(
                (anchor.center.x - with(density) { 10.dp.toPx() }).roundToInt(),
                (anchor.center.y - with(density) { 10.dp.toPx() }).roundToInt(),
              )
            }
            .size(20.dp)
            .graphicsLayer { alpha = (1f - progress.value * 3).coerceIn(0f, 1f) },
      )
      Column(
        Modifier.width(minOf(350.dp, with(density) { (window.width - margin * 2).toDp() }))
          .heightIn(
            max = with(density) { (viewport.height - topInset - bottomInset - margin * 2).toDp() }
          )
          .onSizeChanged { panelSize = it }
          .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
          .pointerInput(Unit) {
            detectTapGestures { point ->
              if (currentAnchor.contains(point + currentTarget.topLeft)) currentAnchorClick()
            }
          }
          .pointerInput(expanded) {
            // Retain the closing surface, but stop its old controls from intercepting the anchor.
            if (!expanded)
              awaitEachGesture {
                val down =
                  awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down.consume()
                var last = down.position
                var moved = false
                var pressed = true
                while (pressed) {
                  val event = awaitPointerEvent(PointerEventPass.Initial)
                  val change = event.changes.firstOrNull { it.id == down.id } ?: break
                  last = change.position
                  moved =
                    moved || (last - down.position).getDistance() > viewConfiguration.touchSlop
                  pressed = change.pressed
                  event.changes.forEach { it.consume() }
                }
                if (!moved) {
                  if (currentAnchor.contains(last + currentTarget.topLeft)) currentAnchorClick()
                  else currentDismiss()
                }
              }
          }
          .semantics {
            if (!expanded) invisibleToUser()
            paneTitle = timerTitle
            dismiss {
              currentDismiss()
              true
            }
          }
          .drawWithContent {
            val p = progress.value.coerceIn(0f, 1f)
            val rect = lerp(anchor, target, p)
            val contentScope = this
            clipRect(
              rect.left - target.left,
              rect.top - target.top,
              rect.right - target.left,
              rect.bottom - target.top,
            ) {
              contentScope.drawContent()
            }
          }
          .graphicsLayer { alpha = ((progress.value - 0.45f) / 0.55f).coerceIn(0f, 1f) }
          .verticalScroll(rememberScrollState())
      ) {
        SleepTimerPanel(onConfirm, onEndOfSong)
      }
    }
  }
}
