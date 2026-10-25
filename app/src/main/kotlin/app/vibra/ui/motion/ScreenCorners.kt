/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.motion

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The radius of the physical display's corners, so full-screen surfaces that grow out of
 * a smaller one (player, profile) end exactly on the screen's own curve instead of turning
 * square. Android 12+ reports it per device; older versions and square displays give 0 dp.
 */
@Composable
fun rememberScreenCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    // Insets become available once the view is attached; re-read when they change.
    val insetsKey = WindowInsets.systemBars.getTop(density)
    return remember(view, density, insetsKey) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@remember 0.dp
        val insets = view.rootWindowInsets ?: return@remember 0.dp
        val radiusPx =
            listOf(RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_BOTTOM_LEFT)
                .mapNotNull { insets.getRoundedCorner(it)?.radius }
                .minOrNull() ?: 0
        with(density) { radiusPx.toDp() }
    }
}
