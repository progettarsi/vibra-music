/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.vibra.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSpeed
import app.vibra.ui.theme.VibraShapes
import kotlinx.coroutines.launch

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
 *                          page steps back, the same grabber everywhere. Opens straight
 *                          to its content height on the theme's spatial spring.
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
 * Vibra's modal bottom sheet. Same parameters as [ModalBottomSheet] minus the styling,
 * which is fixed so every sheet in the app looks and moves the same way. By default it
 * skips the half-open state: a sheet opens to the height of what it shows (long content
 * scrolls inside it).
 */
@Composable
fun VibraBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    sheetGesturesEnabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = VibraShapes.SheetTop,
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        scrimColor = PopupScrim,
        dragHandle = { VibraSheetHandle() },
        contentWindowInsets = contentWindowInsets,
        properties = properties,
        content = content,
    )
}

/** The grabber at the top of every sheet. */
@Composable
fun VibraSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(top = 12.dp, bottom = 10.dp)
                .size(width = 36.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), VibraShapes.Pill),
    )
}

/**
 * A centred dialog card. The window dims the page; the card itself springs from 92 % to
 * full size (spatial spring, a hint of overshoot) while it fades in on the effects
 * spring. With animations disabled the motion scheme is instant, so it simply appears.
 *
 * [content] is laid out inside the card's padding; size it with `heightIn`/`weight` as
 * usual — the card never grows past the window minus its margins.
 */
@Composable
fun VibraDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        val scale = remember { Animatable(DialogEnterScale) }
        val alpha = remember { Animatable(0f) }
        val grow = Liquid.spatial<Float>(LiquidSpeed.Default)
        val fade = Liquid.effects<Float>(LiquidSpeed.Fast)
        LaunchedEffect(Unit) {
            launch { scale.animateTo(1f, grow) }
            alpha.animateTo(1f, fade)
        }

        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .widthIn(max = 560.dp)
                        .heightIn(max = maxHeight)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            this.alpha = alpha.value
                        },
                shape = VibraShapes.Dialog,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = modifier.padding(contentPadding),
                    content = content,
                )
            }
        }
    }
}

private const val DialogEnterScale = 0.92f

/**
 * Pill action for dialogs and sheets. [primary] is the confirming action (filled in the
 * primary colour, or in the error colour when [destructive]); the others are tonal so the
 * choice reads at a glance. Same slot shape as a Material button, so it drops in where a
 * `TextButton` was.
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
        ProvideTextStyle(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)) { content() }
    }
    if (primary) {
        Button(
            onClick = click,
            modifier = modifier.heightIn(min = 48.dp),
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
            modifier = modifier.heightIn(min = 48.dp),
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
 * Anchored dropdown with the rounded card of Vibra's popups. Same parameters as
 * [DropdownMenu] minus the styling.
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
