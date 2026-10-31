/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.vibra.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import app.vibra.ui.motion.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import app.vibra.ui.motion.VibraButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import app.vibra.ui.motion.VibraFilledIconButton as FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSpeed
import app.vibra.ui.motion.LocalPlaybackSpin
import app.vibra.ui.motion.liquidPress
import app.vibra.ui.motion.nudge
import app.vibra.ui.motion.rememberLiquidMorphShape
import app.vibra.ui.motion.rememberLiquidNudge
import app.vibra.ui.motion.rememberPlaybackSpin
import app.vibra.ui.theme.VibraDimens
import moe.rukamori.archivetune.R

/**
 * The cookie-shaped play/pause control (Figma "Shape Set — 12-sided cookie").
 *
 * Behaviour:
 *  - Paused: a still 12-sided cookie with a play glyph.
 *  - Playing: the cookie slowly turns like a record (shared [PlaybackSpin]); pausing
 *    freezes it where it is instead of snapping back.
 *  - Buffering: an indeterminate arc runs along the cookie's own outline
 *    ([shapeTracingLoader]) while the glyph stays visible.
 *  - Press: the surface squishes and the outline briefly morphs to a softer 9-sided
 *    cookie (M3 Expressive press shape-morph).
 *  - [artworkUrl]: the cookie is filled with the cover (mini-player), dimmed so the glyph
 *    stays legible. Only the silhouette spins; the cover itself stays upright.
 */
@Composable
fun VibraPlayButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = VibraDimens.PlayerPlayButton,
    artworkUrl: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val haptics = rememberVibraHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val spin = LocalPlaybackSpin.current ?: rememberPlaybackSpin(isPlaying)
    val pressed by interactionSource.collectIsPressedAsState()
    val shape =
        rememberLiquidMorphShape(
            target = if (pressed) MaterialShapes.Cookie9Sided else MaterialShapes.Cookie12Sided,
            speed = LiquidSpeed.Fast,
        )
    val loader = rememberShapeTracingLoaderState(active = isBuffering)
    val bounce = rememberLiquidNudge()
    val onArtwork = artworkUrl != null
    val glyphColor = if (onArtwork) Color.White else contentColor
    val description = stringResource(if (isPlaying) R.string.pause else R.string.play)

    Box(
        modifier =
            modifier
                .size(size)
                .liquidPress(interactionSource, pressedScale = 0.9f)
                .graphicsLayer { rotationZ = spin.degrees }
                .clip(shape)
                .background(containerColor)
                .shapeTracingLoader(
                    state = loader,
                    shape = shape,
                    color = if (onArtwork) Color.White else contentColor,
                    strokeWidth = if (size < 60.dp) 2.5.dp else 4.dp,
                ).clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                ) {
                    haptics.toggle(on = !isPlaying)
                    bounce.kick()
                    onClick()
                }.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Counter-rotation: only the silhouette turns, the cover and glyph stay upright.
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = -spin.degrees },
            contentAlignment = Alignment.Center,
        ) {
            if (onArtwork) {
                VibraArtwork(
                    url = artworkUrl,
                    shape = RectangleShape,
                    sizePx = SmallArtworkPx,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.32f)),
                )
            }
            val glyphEnter = Liquid.spatial<Float>(LiquidSpeed.Fast)
            val glyphExit = Liquid.effects<Float>(LiquidSpeed.Fast)
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    scaleIn(glyphEnter, initialScale = 0.4f) togetherWith scaleOut(glyphExit, targetScale = 0.4f)
                },
                label = "playGlyph",
            ) { playing ->
                Icon(
                    painter = painterResource(if (playing) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = glyphColor,
                    modifier = Modifier.size(size * 0.42f),
                )
            }
        }
    }
}

/**
 * Round filled icon button in the primary colour (Figma "Icon button", 50 dp circles
 * next to the Play pill and the player's add button). Uses M3 Expressive press shapes,
 * so the circle subtly squares off while pressed.
 */
@Composable
fun VibraFilledIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = VibraDimens.ActionButton,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    haptic: Boolean = true,
) {
    val haptics = rememberVibraHaptics()
    FilledIconButton(
        onClick = {
            if (haptic) haptics.press()
            onClick()
        },
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
        modifier = modifier.size(size),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
        )
    }
}




/**
 * Large primary pill with a leading icon (Figma "Button — Play", 120 × 66). With an empty
 * [text] it renders icon-only (used when a row has no room for the label);
 * [contentDescription] then labels it for TalkBack.
 */
@Composable
fun VibraPrimaryButton(
    text: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = VibraDimens.PrimaryActionHeight,
    contentDescription: String? = null,
) {
    val haptics = rememberVibraHaptics()
    Button(
        onClick = {
            haptics.press()
            onClick()
        },
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        contentPadding = PaddingValues(horizontal = if (text.isEmpty()) 16.dp else 24.dp),
        modifier =
            modifier
                .heightIn(min = height)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/**
 * Plain glyph button without container (player close / more / skip). Large touch target,
 * press squish and haptic tick.
 */
@Composable
fun VibraGlyphButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 28.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    val haptics = rememberVibraHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .size(size)
                .liquidPress(interactionSource, pressedScale = 0.82f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = false),
                    enabled = enabled,
                    role = Role.Button,
                ) {
                    haptics.press()
                    onClick()
                }.semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(iconSize),
        )
    }
}

