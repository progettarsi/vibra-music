/*
 * Vibra (2026) — display style and window clipping are separate geometries. GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.ui.theme

import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Physical positions, not start/end: RTL must not mirror a display's asymmetry. */
@Immutable
data class WindowCornerRadii(
    val topLeft: Dp = 0.dp,
    val topRight: Dp = 0.dp,
    val bottomRight: Dp = 0.dp,
    val bottomLeft: Dp = 0.dp,
) {
    fun shape(from: Dp = 0.dp, progress: Float = 1f) = AbsoluteRoundedCornerShape(
        lerp(from, topLeft, progress), lerp(from, topRight, progress),
        lerp(from, bottomRight, progress), lerp(from, bottomLeft, progress),
    )

    fun pixels(density: Density) = with(density) {
        WindowCornerPixels(topLeft.toPx(), topRight.toPx(), bottomRight.toPx(), bottomLeft.toPx())
    }
}

@Immutable
data class WindowCornerPixels(val topLeft: Float, val topRight: Float, val bottomRight: Float, val bottomLeft: Float) {
    fun roundRect(rect: Rect, from: Float = 0f, progress: Float = 1f): RoundRect {
        // Clamp together so small animated containers never acquire overlapping corner arcs.
        val limit = minOf(rect.width, rect.height).coerceAtLeast(0f) / 2f
        fun corner(to: Float) = CornerRadius((from + (to - from) * progress).coerceIn(0f, limit))
        return RoundRect(rect.left, rect.top, rect.right, rect.bottom,
            corner(topLeft), corner(topRight), corner(bottomRight), corner(bottomLeft))
    }
}

@Immutable
internal data class DeviceCorners(
    val displayRadiiPx: List<Int?> = emptyList(),
    val window: WindowCornerRadii = WindowCornerRadii(),
)

val LocalWindowCorners = compositionLocalOf { WindowCornerRadii() }

/** Event driven: no frame polling and no listeners installed on Compose's own view. */
@Composable
internal fun rememberDeviceCorners(): DeviceCorners {
    if (Build.VERSION.SDK_INT < 31) return DeviceCorners()
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var corners by remember(view, density.density) { mutableStateOf(readDeviceCorners(view, density)) }
    DisposableEffect(view, density.density, configuration, lifecycle) {
        val contentRoot = view.rootView.findViewById<View>(android.R.id.content)
        fun refresh() { corners = readDeviceCorners(view, density) }
        fun refreshWindow(insets: WindowInsets?) {
            val window = readWindowCorners(insets, density)
            if (window != corners.window) corners = corners.copy(window = window)
        }
        val attach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                refresh()
                if (view.rootWindowInsets == null) contentRoot?.requestApplyInsets()
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        val layout = View.OnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (l != oldL || t != oldT || r != oldR || b != oldB) refreshWindow(view.rootWindowInsets)
        }
        val manager = view.context.getSystemService(DisplayManager::class.java)
        val displays = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) { refresh() }
            override fun onDisplayRemoved(id: Int) { refresh() }
            override fun onDisplayChanged(id: Int) { refresh() }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        // The Activity's content root forwards untouched insets to the Compose child.
        // IME/layout updates only affect window geometry; querying the physical display here
        // would do redundant work during those updates.
        contentRoot?.setOnApplyWindowInsetsListener { _, insets ->
            refreshWindow(insets)
            insets
        }
        view.addOnAttachStateChangeListener(attach)
        view.addOnLayoutChangeListener(layout)
        manager?.registerDisplayListener(displays, Handler(Looper.getMainLooper()))
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) refresh()
        lifecycle.addObserver(observer) // Also refreshes immediately if already resumed.
        if (view.isAttachedToWindow && view.rootWindowInsets == null) contentRoot?.requestApplyInsets()
        onDispose {
            contentRoot?.setOnApplyWindowInsetsListener(null)
            view.removeOnAttachStateChangeListener(attach)
            view.removeOnLayoutChangeListener(layout)
            manager?.unregisterDisplayListener(displays)
            lifecycle.removeObserver(observer)
        }
    }
    return corners
}

private val CornerPositions = intArrayOf(
    RoundedCorner.POSITION_TOP_LEFT,
    RoundedCorner.POSITION_TOP_RIGHT,
    RoundedCorner.POSITION_BOTTOM_RIGHT,
    RoundedCorner.POSITION_BOTTOM_LEFT,
)

@androidx.annotation.RequiresApi(31)
private fun readDeviceCorners(view: View, density: Density): DeviceCorners {
    val display = view.display
    val radii = display?.let { CornerPositions.map { position -> it.getRoundedCorner(position)?.radius } }.orEmpty()
    return DeviceCorners(radii, readWindowCorners(view.rootWindowInsets, density))
}

@androidx.annotation.RequiresApi(31)
private fun readWindowCorners(insets: WindowInsets?, density: Density): WindowCornerRadii {
    if (insets == null) return WindowCornerRadii()
    fun at(position: Int) = with(density) { (insets.getRoundedCorner(position)?.radius ?: 0).toDp() }
    return WindowCornerRadii(at(CornerPositions[0]), at(CornerPositions[1]), at(CornerPositions[2]), at(CornerPositions[3]))
}
