/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.TapBounceHost
import app.vibra.ui.motion.TapBounceHostContent
import app.vibra.ui.motion.noTapBounce
import app.vibra.ui.motion.tapBounceInput
import app.vibra.ui.motion.tapBounceLayer
import app.vibra.ui.theme.VibraDimens
import app.vibra.ui.theme.LocalVibraRowShapes
import app.vibra.ui.theme.LocalVibraRadii
import app.vibra.ui.theme.VibraRowShapes

/**
 * How a song row paints its container (Figma "Canzone" component variants).
 */
enum class RowBackground {
    /** `BG=Default`: rounded surface-container card (playlists, queue). */
    Card,

    /** `BG=noBack`: no container (album track lists, rows inside home cards). */
    None,
}

/**
 * The Vibra song row — the single row implementation used everywhere: Vibra screens, the
 * queue, and (through `ListItem` in Items.kt) every list inherited from the engine.
 *
 *   [ thumb ]  Title (title-large, medium)                trailing (e.g. 3:00, ⋮)
 *              subtitle (label-medium, on-surface-variant)
 *
 * The click/long-click modifier from the caller is applied *inside* the rounded clip so
 * ripples follow the card shape. The active (now playing) row tints to the secondary
 * container with an effects spring. With [bounceOnTap] (song rows) a tap gives the row a light
 * bounce; taps on the trailing controls don't.
 */
@Composable
fun VibraRow(
    title: String,
    thumbnail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    interactionModifier: Modifier = Modifier,
    subtitle: (@Composable RowScope.() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false,
    background: RowBackground = RowBackground.Card,
    bounceOnTap: Boolean = false,
    containerInset: androidx.compose.ui.unit.Dp? = null,
) {
    val radii = LocalVibraRadii.current
    val nested = remember(radii, containerInset) {
        containerInset?.let { VibraRowShapes(radii.inset(radii.card, it)) }
    }
    val shapes = nested ?: radii.rowShapes
    val containerColor by animateColorAsState(
        targetValue =
            when {
                isActive -> MaterialTheme.colorScheme.secondaryContainer
                background == RowBackground.Card -> MaterialTheme.colorScheme.surfaceContainer
                else -> Color.Transparent
            },
        animationSpec = Liquid.effects(),
        label = "rowContainer",
    )
    val titleColor =
        if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val secondaryColor =
        if (isActive) {
            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    // The row owns the bounce of the caller's click modifier: it scales outside the rounded clip
    // and background, so card, ripple and content move as one. Trailing controls keep their own.
    TapBounceHost { bounce ->
        Row(
            modifier =
                modifier
                    .tapBounceLayer(bounce)
                    .clip(shapes.container)
                    .background(containerColor)
                    .then(if (bounceOnTap && bounce != null) Modifier.tapBounceInput(bounce) else Modifier)
                    .then(interactionModifier)
                    .heightIn(min = VibraDimens.RowHeight)
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TapBounceHostContent {
                Box(
                    modifier =
                        Modifier
                            .size(VibraDimens.RowThumbnail)
                            .clip(shapes.thumbnail),
                    contentAlignment = Alignment.Center,
                ) {
                    if (nested == null) thumbnail()
                    else CompositionLocalProvider(LocalVibraRowShapes provides nested, content = thumbnail)
                }

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        CompositionLocalProvider(LocalContentColor provides secondaryColor) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                content = subtitle,
                            )
                        }
                    }
                }

                CompositionLocalProvider(LocalContentColor provides secondaryColor) {
                    Row(
                        modifier = if (bounce != null) Modifier.noTapBounce(bounce) else Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                        content = trailing,
                    )
                }
            }
        }
    }
}

/** Plain subtitle text in the row's label style. */
@Composable
fun RowSubtitleText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalContentColor.current,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Duration on the trailing edge (Figma "3:00", secondary colour, title-medium). */
@Composable
fun RowDurationText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary,
        maxLines = 1,
        modifier = Modifier.padding(start = 8.dp),
    )
}
