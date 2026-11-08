/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import moe.rukamori.archivetune.LocalAnimationsDisabled

/** Material 3 Expressive feedback for an operation with no measurable progress. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VibraLoadingIndicator(
    modifier: Modifier = Modifier,
    contained: Boolean = false,
    color: Color = if (contained) LoadingIndicatorDefaults.containedIndicatorColor else LoadingIndicatorDefaults.indicatorColor,
) {
    when {
        LocalAnimationsDisabled.current -> {
            val shape = MaterialShapes.Cookie9Sided.toShape()
            val container =
                if (contained) {
                    Modifier.background(LoadingIndicatorDefaults.containedContainerColor, LoadingIndicatorDefaults.containerShape)
                } else {
                    Modifier
                }
            Box(
                modifier =
                    modifier
                        .progressSemantics()
                        .size(LoadingIndicatorDefaults.ContainerWidth, LoadingIndicatorDefaults.ContainerHeight)
                        .then(container),
                contentAlignment = Alignment.Center,
            ) {
                val fraction = LoadingIndicatorDefaults.IndicatorSize / LoadingIndicatorDefaults.ContainerWidth
                Box(Modifier.fillMaxSize(fraction).background(color, shape))
            }
        }
        contained -> ContainedLoadingIndicator(modifier = modifier, indicatorColor = color)
        else -> LoadingIndicator(modifier = modifier, color = color)
    }
}
