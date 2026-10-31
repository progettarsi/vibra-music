/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.motion

import androidx.compose.foundation.clickable as foundationClickable
import androidx.compose.foundation.combinedClickable as foundationCombinedClickable
import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role

/** Shared touch response, including inherited engine surfaces. */
fun Modifier.clickable(enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null, onClick: () -> Unit): Modifier = composed {
    val feedback = rememberTapBounce()
    then(if (enabled) Modifier.tapBounce(feedback) else Modifier)
        .foundationClickable(enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
}

fun Modifier.clickable(interactionSource: MutableInteractionSource?, indication: Indication?, enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null, onClick: () -> Unit): Modifier = composed {
    val feedback = rememberTapBounce()
    then(if (enabled) Modifier.tapBounce(feedback) else Modifier)
        .foundationClickable(interactionSource = interactionSource, indication = indication, enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
}

fun Modifier.clickable(interactionSource: MutableInteractionSource?, enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null, onClick: () -> Unit): Modifier = composed {
    val bounce = rememberTapBounce()
    then(if (enabled) Modifier.tapBounce(bounce) else Modifier)
        .foundationClickable(interactionSource = interactionSource, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.combinedClickable(
    enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null,
    onLongClickLabel: String? = null, onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null, hapticFeedbackEnabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null, indication: Indication?,
    onClick: () -> Unit,
): Modifier = composed {
    val bounce = rememberTapBounce()
    then(if (enabled) Modifier.tapBounce(bounce) else Modifier)
        .foundationCombinedClickable(interactionSource = interactionSource, indication = indication,
            enabled = enabled, onClickLabel = onClickLabel, role = role, onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick, onDoubleClick = onDoubleClick, hapticFeedbackEnabled = hapticFeedbackEnabled, onClick = onClick)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.combinedClickable(
    enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null,
    onLongClickLabel: String? = null, onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null, hapticFeedbackEnabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null, onClick: () -> Unit,
): Modifier = composed {
    val bounce = rememberTapBounce()
    then(if (enabled) Modifier.tapBounce(bounce) else Modifier)
        .foundationCombinedClickable(interactionSource = interactionSource, indication = androidx.compose.foundation.LocalIndication.current,
            enabled = enabled, onClickLabel = onClickLabel, role = role, onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick, onDoubleClick = onDoubleClick, hapticFeedbackEnabled = hapticFeedbackEnabled, onClick = onClick)
}
