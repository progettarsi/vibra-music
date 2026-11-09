/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.motion

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/*
 * Tiles whose only visible surface is the artwork (a cover with a loose title under it: home and
 * profile covers, library and browse grids). The whole tile stays the touch target, but the press
 * highlight is painted only over the pixels the artwork draws, so it follows its exact outline
 * (rounded, circular, a playlist's custom shape) instead of the tile's reachable area.
 */

/** Draws [press] over the artwork drawn before it in the same box, clipped to its pixels. */
@Composable
fun BoxScope.ArtworkPressHighlight(press: InteractionSource) {
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer { blendMode = BlendMode.SrcAtop }
            .indication(press, ripple()),
    )
}

/**
 * The artwork container of a tile with [ArtworkPressHighlight]: isolates its pixels while a
 * highlight is visible (press, plus the ripple's fade-out), so the highlight only tints them.
 */
fun Modifier.artworkPressLayer(press: InteractionSource): Modifier = composed {
    val active = remember { mutableStateOf(false) }
    LaunchedEffect(press) {
        press.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> active.value = true
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    delay(RippleFadeOutMillis)
                    active.value = false
                }
            }
        }
    }
    graphicsLayer {
        compositingStrategy = if (active.value) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
}

/**
 * Mirrors presses on this element into [press] without consuming them, for tiles whose click
 * modifier comes from the caller. Like a click in a scrolling list, the press shows after the tap
 * timeout and a finger that starts scrolling cancels it.
 */
fun Modifier.artworkPressRelay(press: MutableInteractionSource): Modifier =
    pointerInput(press) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            val interaction = PressInteraction.Press(down.position)
            var outcome: PressOutcome = PressOutcome.Held
            withTimeoutOrNull(TapTimeoutMillis) { outcome = awaitPressEnd(down, slop) }
            when (outcome) {
                PressOutcome.Moved -> return@awaitEachGesture
                PressOutcome.Lifted -> {
                    press.tryEmit(interaction)
                    press.tryEmit(PressInteraction.Release(interaction))
                }
                PressOutcome.Held -> {
                    press.tryEmit(interaction)
                    var end: PressOutcome = PressOutcome.Moved
                    try {
                        end = awaitPressEnd(down, slop)
                    } finally {
                        press.tryEmit(
                            if (end == PressOutcome.Lifted) PressInteraction.Release(interaction)
                            else PressInteraction.Cancel(interaction),
                        )
                    }
                }
            }
        }
    }

/**
 * Runs [content] — a tile whose click modifier the caller built — without the default highlight on
 * the whole tile; [inner] restores it for the tile's children (their own buttons keep theirs).
 */
@Composable
fun WithoutTileIndication(content: @Composable (inner: @Composable (@Composable () -> Unit) -> Unit) -> Unit) {
    val outer = LocalIndication.current
    CompositionLocalProvider(LocalIndication provides NoIndication) {
        content { children -> CompositionLocalProvider(LocalIndication provides outer, content = children) }
    }
}

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -1
}

/** Longer than the ripple's fade-out, so the isolation never ends while it is still visible. */
private const val RippleFadeOutMillis = 450L
