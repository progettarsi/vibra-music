/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import app.vibra.ui.motion.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import app.vibra.ui.motion.VibraFilledTonalIconButton as FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.motion.liquidPress
import app.vibra.ui.theme.VibraShapes

// Vibra: a quick action is a tonal circle with its label underneath (like the round icon
// buttons on album and player pages), not a grey tile. The circle squishes on its spring
// when pressed and ticks a haptic, so the grid reads as part of Vibra.
@Composable
fun NewActionButton(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val containerColor = if (backgroundColor.isSpecified) backgroundColor else MaterialTheme.colorScheme.secondaryContainer
    val iconColor = if (contentColor.isSpecified) contentColor else MaterialTheme.colorScheme.onSecondaryContainer
    val haptics = rememberVibraHaptics()
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier =
            modifier
                .clip(VibraShapes.SheetGroup)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                ) {
                    haptics.press()
                    onClick()
                }.alpha(if (enabled) 1f else 0.38f)
                .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(ActionCircle)
                    .liquidPress(interactionSource, pressedScale = 0.86f)
                    .clip(CircleShape)
                    .background(containerColor)
                    .indication(interactionSource, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides iconColor) {
                icon()
            }
        }

        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private val ActionCircle = 56.dp

@Composable
fun NewMenuItem(
    headlineContent: @Composable () -> Unit,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        ListItem(
            headlineContent = headlineContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            modifier = Modifier.padding(horizontal = 4.dp),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            tonalElevation = 0.dp,
        )
    }

    if (onClick == null) {
        Box(modifier = modifier.fillMaxWidth()) {
            content()
        }
    } else {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            shape = VibraShapes.SheetGroup,
            color = Color.Transparent,
        ) {
            content()
        }
    }
}

@Composable
fun NewMenuSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

// Vibra: every cell has the same width, and a last row with fewer actions is centred
// instead of leaving an empty slot at its end.
@Composable
fun NewActionGrid(
    actions: List<NewAction>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    if (actions.isEmpty()) return

    val columnCount = columns.coerceAtLeast(1)
    val rows = actions.chunked(columnCount)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = 4.dp
        val cellWidth = (maxWidth - gap * (columnCount - 1)) / columnCount
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                ) {
                    row.forEach { action ->
                        NewActionButton(
                            icon = action.icon,
                            text = action.text,
                            onClick = action.onClick,
                            modifier = Modifier.width(cellWidth),
                            enabled = action.enabled,
                            backgroundColor = action.backgroundColor,
                            contentColor = action.contentColor,
                        )
                    }
                }
            }
        }
    }
}

data class NewAction(
    val icon: @Composable () -> Unit,
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val backgroundColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
)

@Composable
fun NewMenuContent(
    headerContent: @Composable (() -> Unit)? = null,
    actionGrid: @Composable (() -> Unit)? = null,
    menuItems: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        headerContent?.invoke()
        actionGrid?.invoke()

        if (actionGrid != null && menuItems != null) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        menuItems?.invoke()
    }
}

@Composable
fun NewIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val containerColor = if (backgroundColor.isSpecified) backgroundColor else MaterialTheme.colorScheme.surfaceContainerHigh
    val iconContentColor = if (contentColor.isSpecified) contentColor else MaterialTheme.colorScheme.onSurfaceVariant

    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        colors =
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = containerColor,
                contentColor = iconContentColor,
            ),
    ) {
        icon()
    }
}

@Composable
fun NewMenuContainer(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
    ) {
        content()
    }
}

@Composable
fun MenuSurfaceSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Vibra: one step above the sheet (surfaceContainerLow), with the sheet-group radius.
    Surface(
        shape = VibraShapes.SheetGroup,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}
