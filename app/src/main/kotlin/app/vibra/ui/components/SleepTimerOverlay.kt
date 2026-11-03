/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.lerp
import androidx.core.graphics.ColorUtils
import app.vibra.ui.motion.Liquid
import app.vibra.ui.theme.VibraDimens
import com.materialkolor.PaletteStyle
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.SleepTimerBlock
import moe.rukamori.archivetune.ui.player.SleepTimerPanel

/**
 * Where the open panel sits: as wide as the player's content (at most the measured [panel]), its
 * right edge at the end of the button's row and its bottom on the button's, so it grows out of
 * the button upwards. A panel taller than the room above moves down and, past the safe area, is
 * capped (its content scrolls).
 */
fun timerPanelBounds(
  button: Rect,
  panel: Size,
  viewport: Size,
  gutter: Float,
  topInset: Float,
  bottomInset: Float,
  endInset: Float = 0f,
): Rect {
  val width = panel.width.coerceAtMost((viewport.width - endInset - gutter * 2).coerceAtLeast(1f))
  val height =
    panel.height.coerceAtMost(
      (viewport.height - topInset - bottomInset - gutter * 2).coerceAtLeast(1f)
    )
  val right = viewport.width - endInset - gutter
  val left = (right - width).coerceAtLeast(gutter)
  val top =
    (button.bottom - height).coerceIn(
      topInset + gutter,
      (viewport.height - bottomInset - gutter - height).coerceAtLeast(topInset + gutter),
    )
  return Rect(left, top, left + width, top + height)
}

/** The surface between the button and the panel at progress [p]: every edge moves together, never past either end. */
internal fun timerContainerRect(button: Rect, panel: Rect, p: Float): Rect =
  androidx.compose.ui.geometry.lerp(button, panel, p.coerceIn(0f, 1f))

/**
 * Where the panel's content is drawn inside the moving [container]: scaled as a whole (never
 * stretched) to fit inside it and centred, so it grows with the surface and is never cut. The
 * scale is the result's width over the [panel]'s.
 */
internal fun timerContentBounds(container: Rect, panel: Size): Rect {
  val scale =
    minOf(
        container.width / panel.width.coerceAtLeast(1f),
        container.height / panel.height.coerceAtLeast(1f),
      )
      .coerceIn(0f, 1f)
  val size = Size(panel.width * scale, panel.height * scale)
  return Rect(container.center - Offset(size.width / 2f, size.height / 2f), size)
}

/**
 * How far block [order] (0 = nearest the button) has appeared at progress [p]. The blocks closest
 * to the button come first, so the content unfolds out of it; closing reverses the order by itself.
 */
internal fun timerBlockReveal(order: Int, p: Float): Float {
  val x = ((p - (RevealStart + order * RevealStagger)) / RevealSpan).coerceIn(0f, 1f)
  return x * x * (3f - 2f * x)
}

/**
 * The sleep timer panel's state: whether it is open, the progress of the surface between the button
 * (0) and the panel (1), and the button it grows from. The button and the layer it is drawn in
 * register their layout here; they are read while drawing, never observed.
 */
@Stable
class SleepTimerPanelState {
  var isOpen by mutableStateOf(false)
    private set

  val progress = Animatable(0f)

  /** The button's own layout. */
  var button: LayoutCoordinates? = null

  /** The layer the button is drawn in, inside its transformations (the player's receding interface). */
  var buttonLayer: LayoutCoordinates? = null

  /** The button's current colours, so the surface leaves and lands on it exactly. */
  var buttonColor: Color = Color.Transparent
  var buttonContentColor: Color = Color.White

  fun toggle() {
    isOpen = !isOpen
  }

  fun close() {
    isOpen = false
  }

  /** The button where it stands with nothing receding, in its layer's (= the overlay's) space. */
  fun buttonRect(): Rect? {
    val layer = buttonLayer?.takeIf { it.isAttached } ?: return null
    val node = button?.takeIf { it.isAttached } ?: return null
    return layer.localBoundingBoxOf(node, clipBounds = false)
  }
}

/** Where the panel landed and where its header moon is, written in layout and read while drawing. */
private class TimerGeometry {
  var target = Rect.Zero
  var panel: LayoutCoordinates? = null
  var icon: LayoutCoordinates? = null
}

/**
 * The sleep timer as part of the player, like the queue: the button itself grows into the panel
 * and shrinks back into it. One progress drives everything, as when the player opens: the surface
 * moves its edges from the button's to the panel's, the moon flies to the header, the content
 * grows out of the button and its blocks appear one after another. Colours come from the cover
 * ([seed]), like the player's other controls. Fill the player with [modifier]; it draws nothing
 * while closed.
 */
@Composable
fun SleepTimerOverlay(
  state: SleepTimerPanelState,
  seed: Color,
  onConfirm: (Int) -> Unit,
  onEndOfSong: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Critically damped like the player's own container, in both directions: no overshoot.
  val spec = Liquid.container<Float>()
  LaunchedEffect(state.isOpen) { state.progress.animateTo(if (state.isOpen) 1f else 0f, spec) }
  val visible by remember(state) { derivedStateOf { state.isOpen || state.progress.value > 0f } }
  if (!visible) return
  val scope = rememberCoroutineScope()
  PredictiveBackHandler(enabled = state.isOpen) { events ->
    try {
      events.collect { state.progress.snapTo(1f - it.progress * BackPreviewTravel) }
      state.close()
    } catch (cancelled: CancellationException) {
      scope.launch { state.progress.animateTo(1f, spec) }
      throw cancelled
    }
  }
  // Computed only while the panel is on screen (it costs a few milliseconds per colour). A grey
  // cover gives a neutral panel: a scheme grown from grey would pick an unrelated blue.
  val scheme =
    remember(seed) {
      val hsl = FloatArray(3).also { ColorUtils.colorToHSL(seed.toArgb(), it) }
      com.materialkolor.dynamicColorScheme(
        seedColor = seed,
        isDark = true,
        style = if (hsl[1] < GreySeedSaturation) PaletteStyle.Monochrome else PaletteStyle.TonalSpot,
      )
    }
  MaterialTheme(colorScheme = scheme) { TimerSurface(state, onConfirm, onEndOfSong, modifier) }
}

@Composable
private fun TimerSurface(
  state: SleepTimerPanelState,
  onConfirm: (Int) -> Unit,
  onEndOfSong: () -> Unit,
  modifier: Modifier,
) {
  val density = LocalDensity.current
  val geometry = remember { TimerGeometry() }
  val surface = MaterialTheme.colorScheme.surfaceContainerHigh
  val moonColor = MaterialTheme.colorScheme.onSecondaryContainer
  val moon = painterResource(R.drawable.bedtime)
  val title = stringResource(R.string.sleep_timer)
  val open = state.isOpen
  val gutter = with(density) { VibraDimens.Gutter.toPx() }
  val corner = with(density) { PanelCorner.toPx() }
  val maxWidth = with(density) { PanelMaxWidth.toPx() }
  val topInset = WindowInsets.statusBars.getTop(density).toFloat()
  val bottomInset = WindowInsets.navigationBars.getBottom(density).toFloat()
  val endInset = WindowInsets.navigationBars.getRight(density, LayoutDirection.Ltr).toFloat()

  fun progress() = state.progress.value.coerceIn(0f, 1f)
  fun from() = state.buttonRect() ?: geometry.target
  fun radius(from: Rect, p: Float) = lerp(from.height / 2f, corner, p)

  Box(
    modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        // Outside the panel: a tap closes it; on the button it toggles, as the button itself does.
        detectTapGestures { point ->
          val button = state.buttonRect()
          when {
            button != null && button.contains(point) -> state.toggle()
            state.isOpen && !geometry.target.contains(point) -> state.close()
          }
        }
      }
      .drawWithContent {
        val p = progress()
        val from = from()
        val rect = timerContainerRect(from, geometry.target, p)
        val radius = radius(from, p)
        // The button's colour at first, so the surface leaves and lands on the button itself.
        drawRoundRect(
          lerp(state.buttonColor, surface, (p / SurfaceTintEnd).coerceIn(0f, 1f)),
          rect.topLeft,
          rect.size,
          CornerRadius(radius),
        )
        drawContent()
        // The moon, flown from the button to the header (where the panel's own is hidden).
        val iconSize = MoonSize.toPx()
        val start = Offset(from.left + ButtonMoonInset.toPx() + ButtonMoon.toPx() / 2f, from.center.y)
        val content = timerContentBounds(rect, geometry.target.size)
        val contentScale = content.width / geometry.target.width.coerceAtLeast(1f)
        val end = moonLanding(geometry, content, contentScale) ?: start
        val center = androidx.compose.ui.geometry.lerp(start, end, p)
        val scale = lerp(ButtonMoon.toPx() / iconSize, contentScale, p)
        val clip = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radius))) }
        clipPath(clip) {
          translate(center.x - iconSize * scale / 2f, center.y - iconSize * scale / 2f) {
            with(moon) {
              draw(
                Size(iconSize * scale, iconSize * scale),
                colorFilter = ColorFilter.tint(lerp(state.buttonContentColor, moonColor, p)),
              )
            }
          }
        }
      }
  ) {
    Box(
      Modifier.layout { measurable, constraints ->
          val width =
            minOf(constraints.maxWidth - endInset - gutter * 2, maxWidth).roundToInt().coerceAtLeast(1)
          val height =
            (constraints.maxHeight - topInset - bottomInset - gutter * 2).roundToInt().coerceAtLeast(1)
          val placeable = measurable.measure(Constraints(width, width, 0, height))
          val viewport = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
          val target =
            timerPanelBounds(
              state.buttonRect() ?: Rect(Offset.Zero, viewport),
              Size(placeable.width.toFloat(), placeable.height.toFloat()),
              viewport,
              gutter,
              topInset,
              bottomInset,
              endInset,
            )
          geometry.target = target
          layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(target.left.roundToInt(), target.top.roundToInt())
          }
        }
        .graphicsLayer {
          // Nothing shows outside the surface while it moves.
          val p = progress()
          val from = from()
          val target = geometry.target
          shape = RoundedRectShape(timerContainerRect(from, target, p).translate(-target.topLeft), radius(from, p))
          clip = true
        }
        .graphicsLayer {
          // The whole content follows the surface, as the player's content follows its container.
          val target = geometry.target
          val content = timerContentBounds(timerContainerRect(from(), target, progress()), target.size)
          val scale = content.width / target.width.coerceAtLeast(1f)
          scaleX = scale
          scaleY = scale
          transformOrigin = TransformOrigin(0f, 0f)
          translationX = content.left - target.left
          translationY = content.top - target.top
        }
        .onPlaced { geometry.panel = it }
        .semantics {
          paneTitle = title
          if (!open) invisibleToUser()
          dismiss {
            state.close()
            true
          }
        }
        .then(
          // While it closes, the panel no longer takes touches (a tap reaches the overlay instead).
          if (open) Modifier
          else
            Modifier.pointerInput(Unit) {
              awaitEachGesture {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
              }
            }
        )
        .verticalScroll(rememberScrollState())
    ) {
      CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        SleepTimerPanel(
          onConfirm = onConfirm,
          onEndOfSong = onEndOfSong,
          block = { block ->
            val order = SleepTimerBlock.entries.size - 1 - block.ordinal
            Modifier.graphicsLayer {
              val shown = timerBlockReveal(order, progress())
              alpha = shown
              translationY = (1f - shown) * BlockRise.toPx()
            }
          },
          icon = Modifier.onPlaced { geometry.icon = it }.graphicsLayer { alpha = 0f },
        )
      }
    }
  }
}

/**
 * Where the header moon is drawn right now, or null before layout: its place in the content (with
 * the header's rise, which the layout coordinates include) scaled into [content].
 */
private fun moonLanding(geometry: TimerGeometry, content: Rect, scale: Float): Offset? {
  val panel = geometry.panel?.takeIf { it.isAttached } ?: return null
  val icon = geometry.icon?.takeIf { it.isAttached } ?: return null
  return content.topLeft + panel.localBoundingBoxOf(icon, clipBounds = false).center * scale
}

/** A rounded rectangle at [bounds] inside the layer (the moving surface, in the panel's own space). */
private class RoundedRectShape(private val bounds: Rect, private val radius: Float) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
    Outline.Rounded(RoundRect(bounds, CornerRadius(radius)))
}

/**
 * Outer corner of the open panel: concentric with the pill buttons in its bottom corners and the
 * moon's disc in its top one (radius 26 dp, 20 dp in from the edges).
 */
private val PanelCorner = 46.dp

/** Widest panel, for landscape and tablets. */
private val PanelMaxWidth = 420.dp

/** The button's moon: 20 dp, 11 dp in from its leading edge (round or pill). */
private val ButtonMoon = 20.dp
private val ButtonMoonInset = 11.dp

/** The header's moon. */
private val MoonSize = 24.dp

/** How far below its place a block starts. */
private val BlockRise = 12.dp

/** The surface takes the panel's colour over this first part of the progress. */
private const val SurfaceTintEnd = 0.3f

private const val RevealStart = 0.15f
private const val RevealStagger = 0.07f
private const val RevealSpan = 0.4f

/** Below this saturation the cover's accent is grey and the panel stays neutral. */
private const val GreySeedSaturation = 0.15f

/** How far a predictive back swipe previews the close (as the player's sheets do). */
private const val BackPreviewTravel = 0.18f
