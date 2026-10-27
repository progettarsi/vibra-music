/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)

package app.vibra.ui.motion

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalAnimationsDisabled

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  LIQUID MOTION — Vibra's universal physics layer
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  Design rules (M3 Expressive "Motion physics"):
 *   1. Everything moves on springs, never on fixed-duration tweens. A spring can be
 *      interrupted at any time and keeps its velocity, so a gesture can hand over to an
 *      animation (and vice versa) without a visible seam.
 *   2. Two spring families:
 *        • SPATIAL  – position, size, corner radius, shape morphs. May overshoot a little,
 *                     which is what makes surfaces feel elastic/"liquid".
 *        • EFFECTS  – colour, alpha, blur. Critically damped: no overshoot.
 *      Each family comes in FAST / DEFAULT / SLOW, picked by how far the element travels.
 *   3. The springs come from MaterialTheme.motionScheme, which ArchiveTuneTheme sets to
 *      MotionScheme.expressive() — or to an instant scheme when the user disables
 *      animations. System "remove animations" (animator scale 0) is honoured by Compose
 *      itself. So there is exactly ONE switch for reduced motion, and every Liquid API
 *      respects it automatically.
 *
 *  Building blocks in this package:
 *   • Liquid.*                  – typed access to the spring tokens.
 *   • LiquidScope / liquid()    – drop-in "layout morphing": wrap any Row/Box/Column in
 *                                 LiquidScope { … } and tag children with Modifier.liquid();
 *                                 whenever their size or position changes (a sibling
 *                                 appears, spacing changes, a slot expands) they spring to
 *                                 the new bounds instead of jumping.
 *   • GooSurface (LiquidGoo.kt) – renders the background pills of tagged children as one
 *                                 metaball surface, so neighbours visibly melt together and
 *                                 pinch apart while they move.
 *   • liquidPress()             – elastic press feedback ("squish") for any clickable.
 *   • LiquidSheetState          – gesture + spring driven 0..1 progress used by the player.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/** How far/fast an element travels; selects the matching spring token. */
enum class LiquidSpeed { Fast, Default, Slow }

/** Typed access to the M3 Expressive spring tokens of the current theme. */
object Liquid {
    @Composable
    fun <T> spatial(speed: LiquidSpeed = LiquidSpeed.Default): FiniteAnimationSpec<T> =
        MaterialTheme.motionScheme.spatial(speed)

    @Composable
    fun <T> effects(speed: LiquidSpeed = LiquidSpeed.Default): FiniteAnimationSpec<T> =
        MaterialTheme.motionScheme.effects(speed)

    /**
     * Spring for large container transforms (player, profile, queue): the slow spatial
     * stiffness without its overshoot. A full-screen surface that overshoots reads as a
     * "bounce" of everything inside it, so containers settle critically damped.
     */
    @Composable
    fun <T> container(): FiniteAnimationSpec<T> =
        if (LocalAnimationsDisabled.current) snap() else spring(dampingRatio = 1f, stiffness = ContainerStiffness)
}

private const val ContainerStiffness = 300f

fun <T> MotionScheme.spatial(speed: LiquidSpeed): FiniteAnimationSpec<T> =
    when (speed) {
        LiquidSpeed.Fast -> fastSpatialSpec()
        LiquidSpeed.Default -> defaultSpatialSpec()
        LiquidSpeed.Slow -> slowSpatialSpec()
    }

fun <T> MotionScheme.effects(speed: LiquidSpeed): FiniteAnimationSpec<T> =
    when (speed) {
        LiquidSpeed.Fast -> fastEffectsSpec()
        LiquidSpeed.Default -> defaultEffectsSpec()
        LiquidSpeed.Slow -> slowEffectsSpec()
    }

/**
 * Scope in which layout changes are animated with spatial springs.
 *
 * Internally this is a Compose [LookaheadScope]: the layout is first resolved at its final
 * state ("lookahead"), then every child tagged with [liquid] animates from where it was to
 * where it will be. That makes the morph universal — callers simply change their layout
 * (weights, spacing, visibility) and the physics happens for free.
 */
@Stable
class LiquidScope internal constructor(
    private val lookaheadScope: LookaheadScope,
    private val motionScheme: MotionScheme,
) {
    private val transforms =
        LiquidSpeed.entries.associateWith { speed ->
            BoundsTransform { _: Rect, target: Rect ->
                // Expressive spatial springs overshoot slightly (~1.5 %). That is the
                // liquid "settle", but a surface collapsing to zero size would overshoot
                // into a *negative* size, which layout rejects. Collapsing bounds therefore
                // use the critically-damped effects spring of the same speed.
                if (target.width < 1f || target.height < 1f) {
                    motionScheme.effects<Rect>(speed)
                } else {
                    motionScheme.spatial<Rect>(speed)
                }
            }
        }

    /** Springs this element's size and position whenever its layout changes. */
    fun Modifier.liquid(speed: LiquidSpeed = LiquidSpeed.Default): Modifier =
        this.animateBounds(
            lookaheadScope = lookaheadScope,
            boundsTransform = transforms.getValue(speed),
        )
}

/** Creates a [LiquidScope] for [content]; see the file header for the design rationale. */
@Composable
fun LiquidScope(content: @Composable LiquidScope.() -> Unit) {
    val motionScheme = MaterialTheme.motionScheme
    LookaheadScope {
        val scope = remember(this, motionScheme) { LiquidScope(this, motionScheme) }
        scope.content()
    }
}

/**
 * Elastic press feedback: the element compresses while pressed and springs back with a
 * slight overshoot on release (M3 Expressive "squish"). Works for any clickable that
 * shares [interactionSource].
 */
@Composable
fun Modifier.liquidPress(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.92f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale =
        animateFloatAsState(
            targetValue = if (pressed) pressedScale else 1f,
            // Pressing is a short, direct movement → fast spatial spring. Releasing uses the
            // default spatial spring, whose expressive damping (0.8) lets the surface overshoot
            // and settle like a liquid drop.
            animationSpec = Liquid.spatial(if (pressed) LiquidSpeed.Fast else LiquidSpeed.Default),
            label = "liquidPress",
        )
    // Read in the layer block, not here: each animation frame then only redraws the layer
    // instead of recomposing the pressed element (and everything it contains).
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * A one-shot elastic kick for feedback that a tap alone would not show (a quick tap can
 * release before [liquidPress] has visibly squished). [kick] pushes the value to 1 on a
 * fast spatial spring and lets it fall back to 0 on the default one, whose overshoot gives
 * the little rebound. Map it to any transform with [nudge].
 */
@Stable
class LiquidNudge internal constructor(
    private val scope: CoroutineScope,
    private val out: FiniteAnimationSpec<Float>,
    private val back: FiniteAnimationSpec<Float>,
) {
    internal val value = Animatable(0f)

    fun kick() {
        scope.launch {
            value.animateTo(1f, out)
            value.animateTo(0f, back)
        }
    }

    /**
     * Holds the kick at its peak while a finger stays down: the element squishes on the fast
     * spring and waits there. [release] lets it spring back. A new call interrupts the running one
     * (Animatable cancels the previous animation), so press/release never fight.
     */
    fun press() {
        scope.launch { value.animateTo(1f, out) }
    }

    /** The bounce-back of [press], on the default spring whose overshoot gives the rebound. */
    fun release() {
        scope.launch { value.animateTo(0f, back) }
    }
}

@Composable
fun rememberLiquidNudge(): LiquidNudge {
    val scope = rememberCoroutineScope()
    val out = Liquid.spatial<Float>(LiquidSpeed.Fast)
    val back = Liquid.spatial<Float>(LiquidSpeed.Default)
    return remember(scope, out, back) { LiquidNudge(scope, out, back) }
}

/**
 * Applies [nudge]: at the peak of a kick the element is scaled by `1 - squash` and moved
 * by [shift] (e.g. a skip button leaning towards the side it skips to).
 */
fun Modifier.nudge(
    nudge: LiquidNudge,
    squash: Float = 0f,
    shift: Dp = 0.dp,
): Modifier =
    graphicsLayer {
        val v = nudge.value.value
        val scale = 1f - squash * v
        scaleX = scale
        scaleY = scale
        translationX = shift.toPx() * v
    }
