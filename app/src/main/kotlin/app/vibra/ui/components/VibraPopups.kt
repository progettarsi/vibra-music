/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.vibra.ui.components

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.PopupProperties
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.motion.VibraButton as Button
import app.vibra.ui.motion.VibraFilledTonalButton as FilledTonalButton
import app.vibra.ui.theme.VibraShapes
import java.util.WeakHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  POPUPS — the one look for everything that appears above a page
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  The engine came with a dozen ways of drawing a sheet (four background colours, three
 *  corner radii, grey/purple/default grabbers), plain Material dialogs with underlined
 *  fields and text-only OK/Cancel, and square system dropdowns. Every popup now goes
 *  through these primitives:
 *
 *   • VibraBottomSheet   – the sheet: 32 dp top corners like the Figma sheets, one step
 *                          above the page (surfaceContainerLow), a deep scrim so the
 *                          page steps back, the same grabber everywhere. Short content wraps;
 *                          long content opens halfway and expands with a gesture.
 *   • VibraDialog        – the centred card: springs in from 92 % while it fades in, so
 *                          it grows out of the dimmed page instead of popping.
 *   • VibraDialogButton  – pill actions (tonal to dismiss, filled to confirm) instead of
 *                          bare text, like the Play pill and the settings "Allow" pill.
 *   • vibraTextFieldColors – a filled, rounded field without the underline.
 *   • VibraDropdownMenu  – the anchored menu, with the rounded card of the sheets.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/** Scrim behind sheets and dialogs: dark enough that the page clearly steps back. */
private val PopupScrim = Color.Black.copy(alpha = 0.5f)

/**
 * Vibra's modal bottom sheet. Same parameters as [ModalBottomSheet] minus the styling, which is
 * fixed so every sheet in the app looks and moves the same way. Content below about 60% wraps;
 * longer sheets open halfway and unlock the full viewport on an upward gesture or Expand action.
 */
@Composable
fun VibraBottomSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
  sheetGesturesEnabled: Boolean = true,
  containerColor: Color = MaterialTheme.colorScheme.background,
  contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
  properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
  content: @Composable ColumnScope.() -> Unit,
) {
  val density = LocalDensity.current
  val windowHeight = LocalWindowInfo.current.containerSize.height
  val windowHeightDp = with(density) { windowHeight.toDp() }
  val requestedInsets = contentWindowInsets()
  val bottomInset =
    with(density) {
      minOf(requestedInsets.getBottom(density), WindowInsets.navigationBars.getBottom(density))
        .toDp()
    }
  val navigationBarPx = WindowInsets.navigationBars.getBottom(density)
  var measuredHeight by remember { mutableIntStateOf(0) }
  var expansionUnlocked by remember { mutableStateOf(false) }
  var hasBeenActive by remember { mutableStateOf(false) }
  var previouslyActive by remember { mutableStateOf(false) }
  val openingLimitReached = sheetOpeningLimitReached(measuredHeight, windowHeight)
  // Only the shown/hidden flip matters; reading the inset directly would recompose every open
  // sheet on each frame of the keyboard animation.
  val imeInsets = WindowInsets.ime
  val imeVisible by
    remember(imeInsets, density) { derivedStateOf { imeInsets.getBottom(density) > 0 } }
  var previousIme by remember { mutableStateOf(imeVisible) }
  // Only a keyboard opened after the sheet is shown can expand it. A keyboard
  // inherited from the underlying screen never bypasses the opening-height limit.
  val currentDismiss by rememberUpdatedState(onDismissRequest)
  val dismissed = remember { booleanArrayOf(false) }
  PopupLayer { active ->
    // Safety net: Material removes a sheet only through onDismissRequest, after its hide
    // animation completes. If that animation is interrupted (content changing height while a
    // full-height sheet closes), the sheet can end up off screen still composed, and its window
    // keeps the scrim and swallows every touch. A sheet that was shown and has come to rest
    // off screen is dismissed here instead.
    LaunchedEffect(sheetState, active) {
      if (!active) return@LaunchedEffect
      var shown = false
      snapshotFlow {
          val offset = runCatching { sheetState.requireOffset() }.getOrNull()
          val offScreen =
            sheetState.currentValue == SheetValue.Hidden ||
              (offset != null && offset >= windowHeight * 0.98f)
          offScreen to (sheetState.targetValue == SheetValue.Hidden || offScreen)
        }
        .distinctUntilChanged()
        .collectLatest { (offScreen, leaving) ->
          if (!offScreen) {
            shown = true
            dismissed[0] = false
          } else if (shown && leaving) {
            delay(300) // Material's own dismissal normally lands first.
            if (!dismissed[0]) {
              dismissed[0] = true
              currentDismiss()
            }
          }
        }
    }
    LaunchedEffect(imeVisible, active) {
      if (active && imeVisible && !previousIme && sheetState.currentValue != SheetValue.Hidden) {
        expansionUnlocked = true
        sheetState.expand()
      }
      previousIme = imeVisible
    }
    LaunchedEffect(active, measuredHeight, openingLimitReached, expansionUnlocked, sheetState) {
      val restoringParent =
        active &&
          hasBeenActive &&
          !previouslyActive &&
          (sheetState.currentValue != SheetValue.Hidden ||
            sheetState.targetValue != SheetValue.Hidden)
      if (active) hasBeenActive = true
      previouslyActive = active
      if (restoringParent) {
        expansionUnlocked = true
        return@LaunchedEffect
      }
      if (active && measuredHeight > 0 && !expansionUnlocked) {
        // Late content can create the initial anchors after Material's automatic show.
        // Open that hidden sheet, without restarting an already targeted spring or a dismissal.
        when (
          sheetOpeningTarget(sheetState.currentValue, sheetState.targetValue, openingLimitReached)
        ) {
          SheetValue.PartiallyExpanded -> sheetState.partialExpand()
          SheetValue.Expanded -> sheetState.expand()
          else -> Unit
        }
      }
    }
    // Accessibility's Expand action has no scroll gesture; it must unlock the same full height.
    LaunchedEffect(active, openingLimitReached, expansionUnlocked, sheetState) {
      if (active && openingLimitReached && !expansionUnlocked)
        snapshotFlow { sheetState.currentValue to sheetState.targetValue }
          .collect { (current, target) ->
            if (current == SheetValue.PartiallyExpanded && target == SheetValue.Expanded)
              expansionUnlocked = true
          }
    }
    val expansionScroll =
      remember(active, openingLimitReached) {
        object : NestedScrollConnection {
          override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (
              active &&
                openingLimitReached &&
                source == NestedScrollSource.UserInput &&
                available.y < 0f
            )
              expansionUnlocked = true
            return Offset.Zero
          }
        }
      }
    val themeMotion = MaterialTheme.motionScheme
    val sheetMotion = remember(themeMotion) { SheetMotionScheme(themeMotion) }
    val textStyle = LocalTextStyle.current
    WithMotionScheme(sheetMotion, textStyle) {
      ModalBottomSheet(
        onDismissRequest = {
          if (active) {
            dismissed[0] = true
            onDismissRequest()
          }
        },
        modifier =
          modifier.drawWithContent {
            drawContent()
            // This modifier precedes Material's offset, so this band uses window coordinates.
            if (active && !imeVisible && navigationBarPx > 0)
              drawRect(
                containerColor,
                topLeft = Offset(0f, (windowHeight - navigationBarPx).toFloat()),
                size = Size(size.width, navigationBarPx.toFloat()),
              )
          },
        sheetState = sheetState,
        sheetMaxWidth = 560.dp,
        sheetGesturesEnabled = sheetGesturesEnabled && active,
        shape = VibraShapes.SheetTop,
        containerColor = if (active) containerColor else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        scrimColor = if (active) PopupScrim else Color.Transparent,
        dragHandle = {
          WithMotionScheme(themeMotion, textStyle) {
            Box(
              Modifier.fillMaxWidth()
                .graphicsLayer { alpha = if (active) 1f else 0f }
                .semantics { if (!active) invisibleToUser() }
                .pointerInput(active, openingLimitReached) {
                  if (active && openingLimitReached)
                    awaitEachGesture {
                      val down =
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                      var travel = 0f
                      var pressed = true
                      while (pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        travel += change.position.y - change.previousPosition.y
                        if (travel < -viewConfiguration.touchSlop) expansionUnlocked = true
                        pressed = change.pressed
                      }
                    }
                },
              contentAlignment = Alignment.Center,
            ) {
              VibraSheetHandle()
            }
          }
        },
        contentWindowInsets = { requestedInsets },
        properties = properties,
        content = {
          WithMotionScheme(themeMotion, textStyle) {
            PopupWindowInput(active)
            // Measure at the final available height from the start. Native partial/expanded
            // anchors control the opening; changing constraints during a drag can desynchronise
            // hit testing.
            val bodyLimit = (windowHeightDp - 32.dp) - MenuTokens.SheetHandleHeight - bottomInset
            Column(
              Modifier.fillMaxWidth()
                .heightIn(max = bodyLimit.coerceAtLeast(0.dp))
                .onSizeChanged {
                  measuredHeight =
                    it.height +
                      with(density) { (MenuTokens.SheetHandleHeight + bottomInset).roundToPx() }
                }
                .nestedScroll(expansionScroll)
                .graphicsLayer { alpha = if (active) 1f else 0f }
                .semantics { if (!active) invisibleToUser() }
            ) {
              content()
            }
          }
        },
      )
    }
  }
}

/**
 * The theme's spatial spring, stopping within half a pixel. The sheet animates its offset in pixels
 * with this spring; the default 0.01 threshold made the expressive spring creep through its last
 * pixel for about half a second after opening, drawing frames that changed nothing on screen.
 */
private class SheetMotionScheme(private val theme: MotionScheme) : MotionScheme by theme {
  override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
    theme.defaultSpatialSpec<T>().let { if (it is SpringSpec<T>) HalfPixelSpring(it) else it }
}

private class HalfPixelSpring<T>(private val spring: SpringSpec<T>) : FiniteAnimationSpec<T> {
  override fun <V : AnimationVector> vectorize(
    converter: TwoWayConverter<T, V>
  ): VectorizedFiniteAnimationSpec<V> =
    if (converter === Float.VectorConverter) {
      @Suppress("UNCHECKED_CAST")
      spring(spring.dampingRatio, spring.stiffness, 0.5f as T).vectorize(converter)
    } else spring.vectorize(converter)
}

/** Material reads the motion scheme from the theme; sheet content keeps the app's own. */
@Composable
private fun WithMotionScheme(
  motionScheme: MotionScheme,
  textStyle: TextStyle,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = MaterialTheme.colorScheme,
    motionScheme = motionScheme,
    shapes = MaterialTheme.shapes,
    typography = MaterialTheme.typography,
  ) {
    CompositionLocalProvider(LocalTextStyle provides textStyle, content = content)
  }
}

internal fun sheetOpeningTarget(
  current: SheetValue,
  target: SheetValue,
  limitReached: Boolean,
): SheetValue? {
  if (target == SheetValue.Hidden && current != SheetValue.Hidden) return null
  val opening = if (limitReached) SheetValue.PartiallyExpanded else SheetValue.Expanded
  return opening.takeUnless { target == it }
}

/** The grabber at the top of every sheet. */
@Composable
fun VibraSheetHandle(modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .padding(top = 14.dp, bottom = 20.dp)
        .size(width = 32.dp, height = 4.dp)
        .background(
          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
          VibraShapes.Pill,
        )
  )
}

/**
 * A centred dialog card. The window dims the page; the card itself springs from 92 % to full size
 * (spatial spring, a hint of overshoot) while it fades in on the effects spring. With animations
 * disabled the motion scheme is instant, so it simply appears.
 *
 * [content] is laid out inside the card's padding; size it with `heightIn`/`weight` as usual — the
 * card never grows past the window minus its margins.
 */
@Composable
fun VibraDialog(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  contentPadding: PaddingValues = PaddingValues(24.dp),
  groupContent: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  val outsideDismissAllowed =
    androidx.compose.runtime.rememberUpdatedState(properties.dismissOnClickOutside)
  // SheetState keys include this callback. Keep its identity stable while busy/properties change.
  val confirmDismiss = remember {
    { value: SheetValue -> value != SheetValue.Hidden || outsideDismissAllowed.value }
  }
  val state =
    rememberModalBottomSheetState(
      skipPartiallyExpanded = false,
      confirmValueChange = confirmDismiss,
    )
  VibraBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = state,
    properties =
      ModalBottomSheetProperties(
        securePolicy = properties.securePolicy,
        shouldDismissOnBackPress = properties.dismissOnBackPress,
        shouldDismissOnClickOutside = properties.dismissOnClickOutside,
      ),
  ) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
      if (groupContent)
        Surface(shape = VibraShapes.Card, color = MaterialTheme.colorScheme.surfaceContainer) {
          Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
        }
      else Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
  }
}

/**
 * Tallest [VibraDialog] content that still opens whole, below the sheets' opening limit. Content
 * capped to it never needs the sheet to expand, so a list inside can be the only thing scrolling.
 */
@Composable
fun vibraDialogNaturalMaxHeight(): Dp {
  val density = LocalDensity.current
  val window = LocalWindowInfo.current.containerSize.height
  val navigationBar = WindowInsets.navigationBars.getBottom(density)
  return with(density) {
    (window * MenuTokens.NaturalSheetOpeningLimit - navigationBar).toDp() -
      MenuTokens.SheetHandleHeight -
      20.dp - // VibraDialog's bottom padding
      2.dp
  }
}

/**
 * Pill action for dialogs and sheets. [primary] is the confirming action (filled in the primary
 * colour, or in the error colour when [destructive]); the others are tonal so the choice reads at a
 * glance. Same slot shape as a Material button, so it drops in where a `TextButton` was.
 */
@Composable
fun VibraDialogButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  primary: Boolean = false,
  destructive: Boolean = false,
  shapes: ButtonShapes = ButtonDefaults.shapes(),
  content: @Composable RowScope.() -> Unit,
) {
  val haptics = rememberVibraHaptics()
  val click = {
    haptics.press()
    onClick()
  }
  val padding = PaddingValues(horizontal = 22.dp)
  val label: @Composable RowScope.() -> Unit = {
    ProvideTextStyle(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)) {
      content()
    }
  }
  if (primary) {
    Button(
      onClick = click,
      modifier = modifier.then(LocalDialogActionModifier.current).heightIn(min = 52.dp),
      enabled = enabled,
      shapes = shapes,
      colors =
        if (destructive) {
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          )
        } else {
          ButtonDefaults.buttonColors()
        },
      contentPadding = padding,
      content = label,
    )
  } else {
    FilledTonalButton(
      onClick = click,
      modifier = modifier.then(LocalDialogActionModifier.current).heightIn(min = 52.dp),
      enabled = enabled,
      shapes = shapes,
      colors =
        ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          contentColor = MaterialTheme.colorScheme.onSurface,
        ),
      contentPadding = padding,
      content = label,
    )
  }
}

/** Shape of the filled text fields used in dialogs and sheets. */
val VibraFieldShape: Shape = RoundedCornerShape(20.dp)

/** Bordered name fields on the playlist form reference; labels sit above the field. */
@Composable
fun vibraOutlinedTextFieldColors() =
  androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
  )

/** Filled, underline-free field colours (pair with [VibraFieldShape]). */
@Composable
fun vibraTextFieldColors(): TextFieldColors =
  TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
    errorContainerColor = MaterialTheme.colorScheme.errorContainer,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
  )

/**
 * Anchored dropdown with the rounded card of Vibra's popups. Same parameters as [DropdownMenu]
 * minus the styling.
 */
@Composable
fun VibraDropdownMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  offset: DpOffset = DpOffset(0.dp, 0.dp),
  scrollState: ScrollState = rememberScrollState(),
  properties: PopupProperties = PopupProperties(focusable = true),
  content: @Composable ColumnScope.() -> Unit,
) {
  DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    modifier = modifier.widthIn(min = 196.dp),
    offset = offset,
    scrollState = scrollState,
    properties = properties,
    shape = VibraShapes.Menu,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation = 0.dp,
    shadowElevation = 6.dp,
    content = content,
  )
}

/** Spacing between pill actions in a dialog's button row. */
val VibraDialogButtonSpacing: Arrangement.Horizontal = Arrangement.spacedBy(10.dp, Alignment.End)

// The registry belongs to an activity window, never to the whole process. Nested
// windows inherit their owner; siblings at the same depth are ordered by registration.
private class PopupRegistry {
  val layers = mutableStateListOf<Pair<Any, Int>>()

  fun top(): Any? = activePopupToken(layers)
}

private val LocalPopupDepth = staticCompositionLocalOf { 0 }
private val LocalPopupRegistry = staticCompositionLocalOf<PopupRegistry?> { null }
private val PopupOwners = WeakHashMap<android.view.View, PopupRegistry>()

/** Invisible retained windows must also release native input and focus to the active window. */
@Composable
private fun PopupWindowInput(active: Boolean) {
  val view = LocalView.current
  val window =
    (view.parent as? DialogWindowProvider)?.window ?: (view as? DialogWindowProvider)?.window
  val mask =
    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
      android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
  val original = remember(window) { window?.attributes?.flags?.and(mask) ?: 0 }
  // WindowManager work belongs to ownership changes, never to content recompositions.
  // setFlags dispatches attribute updates even when the requested flags have not changed.
  DisposableEffect(window, active) {
    val requested = if (active) original else mask
    if (window != null && window.attributes.flags.and(mask) != requested)
      window.setFlags(requested, mask)
    onDispose {}
  }
  DisposableEffect(window) {
    onDispose {
      if (window != null && window.attributes.flags.and(mask) != original)
        window.setFlags(original, mask)
    }
  }
}

@Composable
private fun PopupLayer(content: @Composable (Boolean) -> Unit) {
  val root = LocalView.current.rootView
  val inherited = LocalPopupRegistry.current
  val registry =
    remember(root, inherited) { inherited ?: PopupOwners.getOrPut(root) { PopupRegistry() } }
  val depth = LocalPopupDepth.current + 1
  val token = remember { Any() }
  DisposableEffect(token, registry, depth) {
    val layer = token to depth
    registry.layers.add(layer)
    onDispose { registry.layers.remove(layer) }
  }
  val active = registry.top() === token
  CompositionLocalProvider(LocalPopupDepth provides depth, LocalPopupRegistry provides registry) {
    content(active)
  }
}
