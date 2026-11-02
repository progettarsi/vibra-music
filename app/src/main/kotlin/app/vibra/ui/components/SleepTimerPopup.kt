/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import kotlin.math.pow
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

/** Outer corner of the panel: its cards' radius plus the padding around them, so they nest. */
val SleepTimerPanelRadius = 34.dp

/**
 * The surface between the button ([from]) and the panel ([to]) at progress [p]. It widens a
 * little ahead of growing taller, so it unfolds along an arc rather than scaling as a box; past
 * 1 (the opening overshoot) both axes extend together.
 */
internal fun timerSurfaceRect(from: Rect, to: Rect, p: Float): Rect {
  val x = p.coerceIn(0f, 1.1f)
  val y = if (x < 1f) x.pow(1.35f) else x
  return Rect(
    androidx.compose.ui.util.lerp(from.left, to.left, x),
    androidx.compose.ui.util.lerp(from.top, to.top, y),
    androidx.compose.ui.util.lerp(from.right, to.right, x),
    androidx.compose.ui.util.lerp(from.bottom, to.bottom, y),
  )
}

private val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

/** Darkness of the veil behind the open panel. */
private const val SleepTimerScrim = 0.32f

/**
 * One interruptible surface grows from / returns to the measured player button, carrying the
 * panel's content with it (moved and scaled, as the profile page grows out of the dock avatar).
 * [anchor] places the panel when it opens; [liveAnchor] is read while drawing, so a close always
 * lands on the button's current shape (a pill once a timer runs) without state writes per frame.
 */
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
  liveAnchor: () -> Rect = { anchor },
  progress: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
) {
  var mounted by remember { mutableStateOf(false) }
  // Opening settles with a small overshoot, so the panel lands with some weight; closing is
  // critically damped, so it never passes through the button.
  val animationsOff = Liquid.container<Float>() is SnapSpec<*>
  LaunchedEffect(expanded) {
    if (expanded) {
      mounted = true
      progress.animateTo(1f, if (animationsOff) snap() else spring(0.74f, 420f))
    } else {
      progress.animateTo(0f, if (animationsOff) snap() else spring(1f, 520f))
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
  val currentLiveAnchor by rememberUpdatedState(liveAnchor)
  fun currentAnchor(): Rect = currentLiveAnchor().takeUnless { it.isEmpty } ?: anchor
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
            if (currentAnchor().contains(point)) currentAnchorClick() else currentDismiss()
          }
        }
        .drawBehind {
          val p = progress.value.coerceIn(0f, 1f)
          // A light veil darkens the screen behind the panel so it stands out.
          drawRect(Color.Black, alpha = SleepTimerScrim * p)
          val from = currentAnchor()
          val rect = timerSurfaceRect(from, target, progress.value)
          val radius =
            androidx.compose.ui.util.lerp(from.height / 2, SleepTimerPanelRadius.toPx(), p)
          // A soft shadow builds up as the panel lifts off the button.
          if (p > 0.01f)
            drawIntoCanvas { canvas ->
              shadowPaint.color = android.graphics.Color.TRANSPARENT
              shadowPaint.setShadowLayer(
                28.dp.toPx() * p,
                0f,
                10.dp.toPx() * p,
                Color.Black.copy(alpha = 0.45f * p).toArgb(),
              )
              canvas.nativeCanvas.drawRoundRect(
                rect.left, rect.top, rect.right, rect.bottom, radius, radius, shadowPaint,
              )
            }
          // Fades over the real button at both ends, so a close ends on the button itself.
          drawRoundRect(
            androidx.compose.ui.graphics.lerp(anchorColor, panelColor, p),
            rect.topLeft,
            rect.size,
            CornerRadius(radius),
            alpha = (p / 0.12f).coerceIn(0f, 1f),
          )
        }
    ) {
      Icon(
        painterResource(R.drawable.bedtime),
        null,
        tint = anchorContentColor,
        modifier =
          Modifier.size(20.dp).graphicsLayer {
            val p = progress.value.coerceIn(0f, 1f)
            val rect = timerSurfaceRect(currentAnchor(), target, progress.value)
            // The button's icon sits 11 dp in from its leading edge, round or pill.
            translationX = rect.left + 11.dp.toPx()
            translationY = rect.center.y - 10.dp.toPx()
            alpha = (p / 0.12f).coerceIn(0f, 1f) * (1f - (p - 0.12f) / 0.2f).coerceIn(0f, 1f)
          },
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
              if (currentAnchor().contains(point + currentTarget.topLeft)) currentAnchorClick()
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
                  if (currentAnchor().contains(last + currentTarget.topLeft)) currentAnchorClick()
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
            val rect = timerSurfaceRect(currentAnchor(), target, progress.value)
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
          .graphicsLayer {
            // The content rides the surface's corner by the button and grows in from 85 %, so it
            // arrives with the panel instead of zooming up from a few pixels.
            val p = progress.value
            val rect = timerSurfaceRect(currentAnchor(), target, p)
            val reveal = ((p - 0.18f) / 0.5f).coerceIn(0f, 1f)
            val scale = androidx.compose.ui.util.lerp(0.85f, 1f, reveal)
            transformOrigin = TransformOrigin(1f, 1f)
            scaleX = scale
            scaleY = scale
            translationX = rect.right - target.right
            translationY = rect.bottom - target.bottom
            alpha = reveal
          }
          .verticalScroll(rememberScrollState())
      ) {
        SleepTimerPanel(onConfirm, onEndOfSong)
      }
    }
  }
}
