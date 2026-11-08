/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.interactiveSurface
import kotlin.math.sin
import moe.rukamori.archivetune.R

/**
 * Shared repeat/shuffle response. The disc and glyph compress as one surface; a small turn
 * follows the state transition and settles upright. Repeat's arrows stay in place while its
 * centre mark dissolves, including when a transition is interrupted by another tap.
 */
@Composable
internal fun PlayerModeButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    repeatOne: Boolean = false,
) {
    val activation = animateFloatAsState(
        if (active) 1f else 0f,
        animationSpec = Liquid.effects(),
        label = "playerModeActivation",
    )
    val one = animateFloatAsState(
        if (repeatOne) 1f else 0f,
        animationSpec = Liquid.effects(),
        label = "playerRepeatOne",
    )
    val tint by animateColorAsState(
        if (active) Color.Black else Color.White.copy(alpha = 0.6f),
        animationSpec = Liquid.effects(),
        label = "playerModeInk",
    )
    Box(
        modifier
            .size(48.dp)
            .interactiveSurface(shape = CircleShape, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                selected = active
            }
            .drawBehind {
                val progress = activation.value.coerceIn(0f, 1f)
                drawCircle(
                    Color.White,
                    radius = 20.dp.toPx() * (0.88f + 0.12f * progress),
                    alpha = progress,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).graphicsLayer {
            val arc = sin(activation.value.coerceIn(0f, 1f) * Math.PI).toFloat()
            val mark = sin(one.value.coerceIn(0f, 1f) * Math.PI).toFloat()
            rotationZ = (if (icon == R.drawable.shuffle) -8f else 8f) * arc
            scaleX = 1f - 0.06f * maxOf(arc, mark)
            scaleY = scaleX
        }) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
            if (icon == R.drawable.repeat) {
                Icon(
                    painterResource(R.drawable.vibra_repeat_one_mark),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp).graphicsLayer {
                        alpha = one.value
                        scaleX = 0.8f + 0.2f * one.value
                        scaleY = scaleX
                    },
                )
            }
        }
    }
}
