/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.motion

import androidx.compose.runtime.Composable
import app.vibra.ui.theme.LocalWindowCorners
import app.vibra.ui.theme.WindowCornerRadii

/** Four physical window corners, independent of the user's rounding mode. */
@Composable
fun rememberScreenCorners(): WindowCornerRadii = LocalWindowCorners.current
