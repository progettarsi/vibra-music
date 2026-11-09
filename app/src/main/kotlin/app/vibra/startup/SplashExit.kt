package app.vibra.startup

import android.app.Activity
import android.os.Build
import android.view.animation.PathInterpolator

/**
 * Android 12+ splash exit: the liquid logo keeps moving while it grows a little and the
 * splash fades into the first frame of the app.
 */
object SplashExit {
    private const val DURATION_MS = 260L
    private val emphasized = PathInterpolator(0.2f, 0f, 0f, 1f)

    fun install(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        activity.splashScreen.setOnExitAnimationListener { view ->
            view.iconView
                ?.animate()
                ?.scaleX(1.12f)
                ?.scaleY(1.12f)
                ?.setDuration(DURATION_MS)
                ?.setInterpolator(emphasized)
                ?.start()
            view
                .animate()
                .alpha(0f)
                .setDuration(DURATION_MS)
                .setInterpolator(emphasized)
                .withEndAction { view.remove() }
                .start()
        }
    }
}
