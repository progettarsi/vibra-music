/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.motion

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable as foundationClickable
import androidx.compose.foundation.combinedClickable as foundationCombinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role

/**
 * Shared touch response, including inherited engine surfaces. The bounce sits where the call is:
 * inside a [TapBounceHost] surface it moves out to the host (outside its clip and background),
 * elsewhere it scales what follows. A surface that draws its own clip and background before the
 * click should use [interactiveSurface] instead.
 */
fun Modifier.clickable(enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null, onClick: () -> Unit): Modifier =
    sharedTapBounce(enabled)
        .foundationClickable(enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)

fun Modifier.clickable(interactionSource: MutableInteractionSource?, indication: Indication?, enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null, onClick: () -> Unit): Modifier =
    sharedTapBounce(enabled)
        .foundationClickable(interactionSource = interactionSource, indication = indication, enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)

fun Modifier.clickable(interactionSource: MutableInteractionSource?, enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null, onClick: () -> Unit): Modifier = composed {
    sharedTapBounce(enabled)
        .foundationClickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.combinedClickable(
    enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null,
    onLongClickLabel: String? = null, onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null, hapticFeedbackEnabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null, indication: Indication?,
    onClick: () -> Unit,
): Modifier =
    sharedTapBounce(enabled)
        .foundationCombinedClickable(interactionSource = interactionSource, indication = indication,
            enabled = enabled, onClickLabel = onClickLabel, role = role, onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick, onDoubleClick = onDoubleClick, hapticFeedbackEnabled = hapticFeedbackEnabled, onClick = onClick)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.combinedClickable(
    enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null,
    onLongClickLabel: String? = null, onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null, hapticFeedbackEnabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null, onClick: () -> Unit,
): Modifier = composed {
    sharedTapBounce(enabled)
        .foundationCombinedClickable(interactionSource = interactionSource, indication = LocalIndication.current,
            enabled = enabled, onClickLabel = onClickLabel, role = role, onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick, onDoubleClick = onDoubleClick, hapticFeedbackEnabled = hapticFeedbackEnabled, onClick = onClick)
}

/**
 * A custom surface that owns its touch feedback: one bounce outside everything it draws, then
 * [shape] as both background and ripple mask, then a bounded ripple that grows to the whole
 * shape. Glow, shadows and outer decorations go before this modifier, so they stay unmasked.
 */
fun Modifier.interactiveSurface(
    shape: Shape,
    containerColor: Color = Color.Transparent,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    surfaceLayers(shape, containerColor, enabled)
        .foundationClickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )
}

/** [interactiveSurface] for surfaces that also open something on a long press. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.interactiveLongSurface(
    shape: Shape,
    onLongClick: () -> Unit,
    containerColor: Color = Color.Transparent,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    surfaceLayers(shape, containerColor, enabled)
        .foundationCombinedClickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/** Bounce, then mask, then background: all three move together while pressed. */
private fun Modifier.surfaceLayers(shape: Shape, containerColor: Color, enabled: Boolean): Modifier =
    sharedTapBounce(enabled)
        .clip(shape)
        .then(if (containerColor != Color.Transparent) Modifier.background(containerColor) else Modifier)
