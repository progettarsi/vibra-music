/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.motion
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/** Feedback for Material controls whose internal click modifier is not exposed. */
fun Modifier.vibraControlFeedback(enabled: Boolean = true): Modifier = composed {
    if (enabled) tapBounce(rememberTapBounce()) else this
}
