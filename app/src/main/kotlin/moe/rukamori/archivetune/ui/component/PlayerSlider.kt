/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberControlledSliderState(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
): SliderState {
    val sliderState =
        remember(valueRange, steps) {
            SliderState(
                value = value.coerceIn(valueRange),
                steps = steps,
                trackRange = valueRange,
            )
        }
    LaunchedEffect(sliderState, value) {
        sliderState.value = value.coerceIn(valueRange)
    }
    return sliderState
}
