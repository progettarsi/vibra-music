/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.material3.MaterialShapes
import app.vibra.ui.components.VibraArtwork
import app.vibra.ui.motion.LiquidMorphShape
import moe.rukamori.archivetune.R

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  AVATAR FLIGHT — the profile picture is one object, not two
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  The profile page grows out of the dock's avatar button and shrinks back into it
 *  (PageMotion, Origin style). Without help the big avatar would shrink together with the
 *  page and fade under its veil, while the dock's own small avatar faded in separately: the
 *  picture "appeared twice".
 *
 *  Instead, while the page is in that transition, the avatar leaves the page and is drawn by
 *  the shell above everything ([AvatarFlightOverlay]): it travels between its place on the
 *  profile and the dock button, its outline morphing from the profile shape to the dock's
 *  circle. Both the page's avatar and the dock's avatar are hidden meanwhile, so at the end
 *  of a close the flying picture simply *becomes* the dock's avatar.
 *
 *  Everything runs in the draw phase (graphicsLayer reading the page progress): no
 *  recomposition per frame.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/** What the profile page lends the shell for a flight. */
@Immutable
internal data class AvatarFlightSource(
    val url: String,
    val shape: RoundedPolygon,
    /** The page's transition progress while it is opening/closing into the dock, else null. */
    val progress: () -> Float?,
    /** The dock button's window bounds (the page's origin). */
    val target: () -> Rect?,
)

/** Shared between the profile page (source), the dock (hides its avatar) and the overlay. */
@Stable
class AvatarFlight {
    internal var source by mutableStateOf<AvatarFlightSource?>(null)

    /** Where the avatar sits on the profile page at rest, in window coordinates. */
    internal var rest by mutableStateOf<Rect?>(null)

    /** True while the avatar is in the air (read in draw/layer blocks only). */
    fun flying(): Boolean = currentFlight() != null

    internal fun currentFlight(): Triple<AvatarFlightSource, Rect, Float>? {
        val source = source ?: return null
        val progress = source.progress() ?: return null
        val rest = rest ?: return null
        return Triple(source, rest, progress)
    }
}

/** The shell's [AvatarFlight]; null outside the Vibra shell. */
val LocalAvatarFlight = staticCompositionLocalOf<AvatarFlight?> { null }

/**
 * Marks the profile page's avatar as the flying one: it reports where it rests and hides itself
 * while the shell draws it in flight. Without a picture ([url] null) nothing flies (the dock
 * shows a plain icon then, there is nothing to hand over).
 */
@Composable
fun Modifier.avatarFlightSource(
    url: String?,
    shape: RoundedPolygon,
): Modifier {
    val flight = LocalAvatarFlight.current
    val page = LocalPageFlight.current
    if (flight == null || page == null || url == null) return this
    DisposableEffect(flight, page, url, shape) {
        val source = AvatarFlightSource(url, shape, progress = page::progress, target = page::originBounds)
        flight.source = source
        onDispose { if (flight.source === source) flight.source = null }
    }
    return this
        .onGloballyPositioned { coordinates ->
            // Only the resting layout counts: mid-transition the page is scaled and moved.
            if (page.progress() == null) flight.rest = page.restBoundsInWindow(coordinates)
        }.graphicsLayer { alpha = if (flight.flying()) 0f else 1f }
}

/** Draws the avatar in flight, above pages and dock. Place it in the shell's root Box. */
@Composable
internal fun AvatarFlightOverlay(
    flight: AvatarFlight,
    shellOffset: () -> androidx.compose.ui.geometry.Offset,
) {
    val source = flight.source ?: return
    val density = LocalDensity.current
    val morph = remember(source.shape) { Morph(source.shape, MaterialShapes.Circle) }
    // The picture is laid out once at its resting size and only transformed per frame.
    val restSize = flight.rest?.size ?: return
    with(density) {
        Box(
            modifier =
                Modifier
                    .size(restSize.width.toDp(), restSize.height.toDp())
                    .graphicsLayer {
                        val (_, rest, progress) = flight.currentFlight() ?: run {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        // The origin is the whole dock button; the picture in it is smaller and centred.
                        val target = source.target()?.let { button ->
                            val side = DockAvatarSize.toPx().coerceAtMost(button.minDimension)
                            Rect(center = button.center, radius = side / 2f)
                        }
                        if (target == null) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        alpha = 1f
                        val t = progress.coerceIn(0f, 1f)
                        val left = target.left + (rest.left - target.left) * t
                        val top = target.top + (rest.top - target.top) * t
                        val width = target.width + (rest.width - target.width) * t
                        transformOrigin = TransformOrigin(0f, 0f)
                        // Window coordinates → the shell's own (it may not start at the window's corner).
                        val offset = shellOffset()
                        translationX = left - offset.x
                        translationY = top - offset.y
                        scaleX = width / rest.width
                        scaleY = scaleX
                        // Profile shape at rest, the dock's circle when landed.
                        shape = LiquidMorphShape(morph, 1f - t)
                        clip = true
                    },
        ) {
            VibraArtwork(
                url = source.url,
                shape = androidx.compose.ui.graphics.RectangleShape,
                sizePx = 720,
                fallbackIcon = R.drawable.person,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Size of the avatar inside the dock's leading button (see VibraDock's DockLeadingButton). */
val DockAvatarSize = 44.dp
