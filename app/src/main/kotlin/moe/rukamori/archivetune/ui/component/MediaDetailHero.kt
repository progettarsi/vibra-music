/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Vibra: re-skinned to the Figma "albumScreen" (31:2288) / "playlistScreen" (31:1981)
 * header — glowing shaped cover, centred title/subtitle, and the action row
 * [ Play ][ ● ][ ● ][ ● ]. The public API is unchanged so all detail screens (album,
 * playlists, artist, podcasts…) adopt the design without behavioural changes.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibra.ui.components.ArtworkGlow
import app.vibra.ui.components.VibraArtwork
import app.vibra.ui.components.VibraPrimaryButton
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.theme.VibraDimens
import app.vibra.ui.theme.VibraShapes
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight

@Composable
public fun MediaDetailHero(
    title: String,
    thumbnailUrl: String?,
    @DrawableRes fallbackIcon: Int,
    systemBarsTopPadding: Dp,
    isAdded: Boolean,
    @StringRes addContentDescription: Int,
    @StringRes removeContentDescription: Int,
    onShuffle: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    onToggleAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: AnnotatedString? = null,
    metadata: String? = null,
    description: String? = null,
    additionalPrimaryActions: (@Composable RowScope.(Color) -> Unit)? = null,
    isToggleAddEnabled: Boolean = true,
) {
    val menuState = LocalMenuState.current
    // Albums keep a rounded square (Figma 33:203); collections get the expressive pentagon
    // used by the playlist screen's "Shape Set".
    val coverShape: Shape = if (fallbackIcon == R.drawable.album) VibraShapes.AlbumCover else VibraShapes.Pentagon

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = systemBarsTopPadding + AppBarHeight - 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Cover + coloured glow (Figma "glow": blurred copy at reduced opacity).
        Box(
            modifier =
                Modifier
                    .widthIn(max = MediaDetailCoverMaxSize)
                    .fillMaxWidth(MediaDetailCoverWidthFraction)
                    .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            ArtworkGlow(url = thumbnailUrl, modifier = Modifier.fillMaxSize())
            VibraArtwork(
                url = thumbnailUrl,
                shape = coverShape,
                fallbackIcon = fallbackIcon,
                sizePx = MediaDetailHeroArtworkSizePx,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier =
                Modifier
                    .widthIn(max = MediaDetailContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = VibraDimens.Gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .padding(top = 16.dp)
                        .semantics { heading() },
            )

            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            description?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            metadata?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            MediaDetailPrimaryActions(
                isAdded = isAdded,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                contrastingColor = MaterialTheme.colorScheme.primary,
                addContentDescription = addContentDescription,
                removeContentDescription = removeContentDescription,
                onShuffle = onShuffle,
                onPlay = onPlay,
                onToggleAdd =
                    remember(isAdded, menuState, onToggleAdd, removeContentDescription, title) {
                        onToggleAdd?.let { toggleAdd ->
                            if (isAdded) {
                                {
                                    menuState.showDialog {
                                        MediaDetailRemovalConfirmationDialog(
                                            title = title,
                                            removeContentDescription = removeContentDescription,
                                            onDismiss = menuState::dismissDialog,
                                            onConfirm = {
                                                menuState.dismissDialog()
                                                toggleAdd()
                                            },
                                        )
                                    }
                                }
                            } else {
                                toggleAdd
                            }
                        }
                    },
                additionalActions = additionalPrimaryActions,
                isToggleAddEnabled = isToggleAddEnabled,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun MediaDetailRemovalConfirmationDialog(
    title: String,
    @StringRes removeContentDescription: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = {
            Text(text = stringResource(removeContentDescription))
        },
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(text = stringResource(removeContentDescription))
            }
        },
    ) {
        Text(
            text = stringResource(R.string.remove_from_library_confirm, title),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * The Figma action row: a large "Play" pill followed by round primary icon buttons
 * (shuffle, add-to-library, then any screen-specific actions such as download).
 * Scrolls horizontally on very narrow screens instead of clipping.
 *
 * [contentColor]/[contrastingColor] are kept for API compatibility; Vibra always uses the
 * primary/on-primary pair from the design.
 */
@Composable
public fun MediaDetailPrimaryActions(
    isAdded: Boolean,
    contentColor: Color,
    contrastingColor: Color,
    @StringRes addContentDescription: Int,
    @StringRes removeContentDescription: Int,
    onShuffle: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    onToggleAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    additionalActions: (@Composable RowScope.(Color) -> Unit)? = null,
    isToggleAddEnabled: Boolean = true,
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val playLabelMinWidth = MediaDetailPlayLabelMinWidth
    val gap = MediaDetailActionSpacing

    // Figma: [ Play ][ ● ][ ● ][ ● ] spanning the content width. Screens add a variable
    // number of actions, so the round buttons are measured first and the Play pill takes
    // the remaining width — dropping its label (icon-only pill) when the label would not
    // fit. Nothing is ever clipped or pushed off-screen.
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = MediaDetailContentMaxWidth),
    ) {
    val viewportPx = constraints.maxWidth
    // Scrolls only in the rare case that even an icon-only Play pill cannot fit.
    SubcomposeLayout(modifier = Modifier.horizontalScroll(rememberScrollState())) { constraints ->
        val gapPx = gap.roundToPx()
        val actionPlaceables =
            subcompose(MediaDetailActionSlot.Actions) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    onShuffle?.let { shuffle ->
                        MediaDetailIconAction(
                            icon = R.drawable.shuffle,
                            contentDescription = R.string.shuffle,
                            contentColor = onPrimary,
                            onClick = shuffle,
                        )
                    }
                    onToggleAdd?.let { toggleAdd ->
                        MediaDetailIconAction(
                            icon = if (isAdded) R.drawable.done else R.drawable.add,
                            contentDescription = if (isAdded) removeContentDescription else addContentDescription,
                            contentColor = onPrimary,
                            onClick = toggleAdd,
                            enabled = isToggleAddEnabled,
                        )
                    }
                    // Figma albumScreen: never more than four controls. Screen-specific
                    // actions (download, share, radio, edit…) fold into "⋯", where the
                    // very same MediaDetailAction calls render as labelled menu rows.
                    additionalActions?.let { extra ->
                        MediaDetailOverflowAction(actions = extra, onPrimary = onPrimary)
                    }
                }
            }.map { it.measure(Constraints()) }
        val actionsWidth = actionPlaceables.sumOf { it.width }
        val playWidth =
            (viewportPx - actionsWidth - if (actionsWidth > 0) gapPx else 0)
                .coerceAtLeast(MediaDetailPlayCompactWidth.roundToPx())
        val compactPlay = playWidth < playLabelMinWidth.roundToPx()
        val playPlaceables =
            if (onPlay == null) {
                emptyList()
            } else {
                subcompose(MediaDetailActionSlot.Play) {
                    VibraPrimaryButton(
                        text = if (compactPlay) "" else stringResource(R.string.play),
                        icon = R.drawable.play,
                        onClick = onPlay,
                        contentDescription = stringResource(R.string.play),
                    )
                }.map { it.measure(Constraints(minWidth = playWidth, maxWidth = playWidth)) }
            }
        val height = (actionPlaceables + playPlaceables).maxOfOrNull { it.height } ?: 0
        val contentWidth = playPlaceables.sumOf { it.width + gapPx } + actionsWidth
        val width = maxOf(viewportPx, contentWidth)
        layout(width, height) {
            var x = 0
            playPlaceables.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width + gapPx
            }
            if (playPlaceables.isEmpty()) x = ((viewportPx - actionsWidth) / 2).coerceAtLeast(0)
            actionPlaceables.forEach { it.placeRelative(x, (height - it.height) / 2) }
        }
    }
    }
}

private enum class MediaDetailActionSlot { Play, Actions }

/** "⋯" button whose menu hosts the screen's extra actions as labelled rows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaDetailOverflowAction(
    actions: @Composable RowScope.(Color) -> Unit,
    onPrimary: Color,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MediaDetailIconAction(
            icon = R.drawable.more_horiz,
            contentDescription = R.string.vibra_more,
            contentColor = onPrimary,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            CompositionLocalProvider(LocalMediaDetailOverflow provides MediaDetailOverflow { expanded = false }) {
                // FlowRow with one item per line gives the RowScope the actions expect
                // while stacking them vertically, like menu rows.
                FlowRow(maxItemsInEachRow = 1) {
                    actions(MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** Present while actions render inside the overflow menu; dismisses it on selection. */
private class MediaDetailOverflow(
    val dismiss: () -> Unit,
)

private val LocalMediaDetailOverflow = staticCompositionLocalOf<MediaDetailOverflow?> { null }

/**
 * Round primary icon action (Figma "Icon button", 50 dp). Destructive actions use the
 * error container so they are distinguishable by more than position.
 */
@Composable
public fun MediaDetailAction(
    @StringRes contentDescription: Int,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val actionDescription = stringResource(contentDescription)
    val haptics = rememberVibraHaptics()
    val overflow = LocalMediaDetailOverflow.current
    if (overflow != null) {
        DropdownMenuItem(
            text = { Text(actionDescription, style = MaterialTheme.typography.bodyLarge) },
            leadingIcon = {
                CompositionLocalProvider(
                    LocalContentColor provides
                        if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                ) { content() }
            },
            enabled = enabled,
            onClick = {
                haptics.press()
                overflow.dismiss()
                onClick()
            },
            modifier = modifier.widthIn(min = 200.dp),
        )
        return
    }
    val colors =
        if (isDestructive) {
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }

    FilledIconButton(
        onClick = {
            haptics.press()
            onClick()
        },
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        colors = colors,
        modifier =
            modifier
                .size(MediaDetailActionSize)
                .semantics { this.contentDescription = actionDescription },
    ) {
        content()
    }
}

@Composable
public fun MediaDetailIconAction(
    @DrawableRes icon: Int,
    @StringRes contentDescription: Int,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
) {
    MediaDetailAction(
        contentDescription = contentDescription,
        contentColor = contentColor,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        isDestructive = isDestructive,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
    }
}

private const val MediaDetailHeroArtworkSizePx = 1080
private const val MediaDetailCoverWidthFraction = 0.78f
private val MediaDetailCoverMaxSize = 360.dp
private val MediaDetailContentMaxWidth = 720.dp
private val MediaDetailActionSpacing = 8.dp
private val MediaDetailPlayLabelMinWidth = 120.dp
private val MediaDetailPlayCompactWidth = 64.dp
private val MediaDetailActionSize = VibraDimens.ActionButton
