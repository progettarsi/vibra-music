/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.datastore.preferences.core.booleanPreferencesKey
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.PI
import kotlin.math.sin

/**
 * Preference (Settings › Advanced): the other rows wobble while one is being dragged to a
 * new place. On by default.
 */
val ReorderWobbleKey = booleanPreferencesKey("vibraReorderWobble")

/**
 * The iOS "jiggle" while reordering, kept very light: the row rocks by a fraction of a
 * degree. [active] is true for the rows that are *not* being dragged while a drag is in
 * progress; [seed] (e.g. the row index) puts neighbours out of phase. It eases in and out,
 * and does nothing when the preference is off or animations are disabled.
 */
@Composable
fun Modifier.reorderWobble(
    active: Boolean,
    seed: Int,
): Modifier {
    val enabled by rememberPreference(ReorderWobbleKey, defaultValue = true)
    val animationsDisabled = !ambientMotionAllowed
    val strength by animateFloatAsState(
        targetValue = if (active && enabled && !animationsDisabled) 1f else 0f,
        animationSpec = Liquid.effects(),
        label = "reorderWobbleStrength",
    )
    if (strength == 0f) return this
    val cycle =
        rememberInfiniteTransition(label = "reorderWobble").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(WobblePeriodMillis, easing = LinearEasing)),
            label = "reorderWobbleCycle",
        )
    return graphicsLayer {
        val phase = (cycle.value + seed * 0.37f) * 2f * PI.toFloat()
        rotationZ = sin(phase) * WobbleDegrees * strength
    }
}

private const val WobblePeriodMillis = 300
private const val WobbleDegrees = 0.6f
