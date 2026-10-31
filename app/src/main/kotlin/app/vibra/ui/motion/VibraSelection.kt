/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.motion
import androidx.compose.foundation.selection.toggleable as foundationToggleable
import androidx.compose.foundation.selection.selectable as foundationSelectable
import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role

fun Modifier.toggleable(value: Boolean, enabled: Boolean = true, role: Role? = null, onValueChange: (Boolean) -> Unit): Modifier = composed {
    val bounce = rememberTapBounce()
    then(if (enabled) Modifier.tapBounce(bounce) else Modifier).foundationToggleable(value = value, enabled = enabled, role = role, onValueChange = onValueChange)
}
fun Modifier.toggleable(value: Boolean, interactionSource: MutableInteractionSource?, indication: Indication?, enabled: Boolean = true, role: Role? = null, onValueChange: (Boolean) -> Unit): Modifier = composed {
    val bounce = rememberTapBounce()
    then(if (enabled) Modifier.tapBounce(bounce) else Modifier).foundationToggleable(value, interactionSource, indication, enabled, role, onValueChange)
}
fun Modifier.selectable(selected: Boolean, enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier = composed {
    val bounce = rememberTapBounce()
    then(if (enabled) Modifier.tapBounce(bounce) else Modifier).foundationSelectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
}
fun Modifier.selectable(selected: Boolean, interactionSource: MutableInteractionSource?, indication: Indication?, enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier = composed {
    val bounce = rememberTapBounce()
    then(if (enabled) Modifier.tapBounce(bounce) else Modifier).foundationSelectable(selected, interactionSource, indication, enabled, role, onClick)
}
