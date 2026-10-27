/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.shell

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.rememberScreenCornerRadius
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoute

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  PAGE MOTION — one rule for every screen change
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  A page always goes back the way it came:
 *
 *   • ORIGIN  opened by tapping something on the page beneath (a button, a dock button, a
 *             card, a cover, a row): the page grows out of that element (container
 *             transform) and, going back, shrinks into it again.
 *   • PUSH    opened any other way (code, a menu sheet, the player, a deep link), or its
 *             origin is gone (another page beneath it now, a rotated window): it comes in
 *             from the right and goes back to the right, dissolving.
 *   • FADE    search results take the place of the search overlay, so they appear and
 *             dissolve where they are (the search itself keeps its dock morph).
 *
 *  The NavHost plays no transition of its own: each page draws its pose from its own
 *  enter/exit transition ([PageFrame]), so the page on top and the one beneath share one
 *  progress value on one spring (Liquid.container: a whole page never bounces). The
 *  predictive back gesture scrubs the very same pose: the page follows the finger, carries
 *  on when released and settles back when the gesture is cancelled. The back button plays
 *  it on the spring.
 *
 *  The origin needs nothing from the screens: the shell notes where the last tap landed,
 *  and when a navigation follows it at once, the smallest clickable element under that
 *  point (read from the semantics tree) is where the new page comes from. Taps in the
 *  player or in the search overlay don't count: those surfaces close as the page opens.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/** How a page enters and leaves; see the file header. */
internal enum class PageStyle { Origin, Push, Fade }

/**
 * The element a page grew out of: [bounds] in window coordinates, on the page [hostId],
 * in a window of size [window] (a rotation invalidates it).
 */
@Immutable
internal data class PageOrigin(
    val bounds: Rect,
    val cornerPx: Float,
    val hostId: String,
    val window: IntSize,
)

/** A page's part in the transition it last joined: the page that moves, or the one beneath. */
@Immutable
internal data class PageRole(
    val top: Boolean,
    val style: PageStyle,
    val origin: PageOrigin?,
    val predictive: Boolean,
)

/**
 * The shell's record of where pages came from, shared by the NavHost transitions (which
 * decide each page's [PageRole]) and every [PageFrame] (which draws it).
 *
 * Roles are decided while the NavHost composes a transition and read when the pages draw,
 * so they are plain fields, not snapshot state: the animated progress already redraws
 * each frame.
 */
@Stable
class PageMotion {
    /** Size of the window the pages fill; origins from another size are dropped. */
    internal var window: IntSize = IntSize.Zero

    /** Largest corner radius given to an origin (a row or card, not a pill). */
    internal var maxOriginCornerPx: Float = 0f

    /** The window's semantics, to find what a tap landed on. */
    internal var semantics: () -> SemanticsOwner? = { null }

    private var pressPosition: Offset? = null
    private var pressExcluded = false
    private var releaseUptime = 0L

    private val origins = HashMap<String, PageOrigin>()
    private val roles = HashMap<String, PageRole>()
    private val watched = HashSet<String>()
    private var lastTransition: String? = null

    /** A finger went down at [position] (window coordinates); [excluded] if on an overlay. */
    internal fun onPress(
        position: Offset,
        excluded: Boolean,
    ) {
        pressPosition = position
        pressExcluded = excluded
        releaseUptime = Long.MAX_VALUE
    }

    /** The last finger went up. */
    internal fun onRelease(uptimeMillis: Long) {
        releaseUptime = uptimeMillis
    }

    internal fun role(entry: String): PageRole? = roles[entry]

    /** [to] is opened on top of [from]. */
    internal fun forward(
        from: NavBackStackEntry,
        to: NavBackStackEntry,
    ) {
        if (!begin("forward", from, to)) return
        val origin = if (to.isSearchResults) null else takeTapOrigin(hostId = from.id)
        if (origin != null) origins[to.id] = origin else origins.remove(to.id)
        val style =
            when {
                to.isSearchResults -> PageStyle.Fade
                origin != null -> PageStyle.Origin
                else -> PageStyle.Push
            }
        assign(top = to, under = from, style = style, origin = origin, predictive = false)
    }

    /** [from] is closed, revealing [to]; [predictive] while a back gesture drives it. */
    internal fun pop(
        from: NavBackStackEntry,
        to: NavBackStackEntry,
        predictive: Boolean,
    ) {
        if (!begin(if (predictive) "gesture" else "back", from, to)) return
        // Only into the element it came from, and only if that element is still there:
        // the same page beneath, in a window of the same size.
        val origin = origins[from.id]?.takeIf { it.hostId == to.id && it.window == window }
        val style =
            when {
                from.isSearchResults -> PageStyle.Fade
                origin != null -> PageStyle.Origin
                else -> PageStyle.Push
            }
        assign(top = from, under = to, style = style, origin = origin, predictive = predictive)
    }

    /** Forgets [entry] once it leaves the back stack for good. */
    internal fun watch(entry: NavBackStackEntry) {
        if (!watched.add(entry.id)) return
        entry.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    origins.remove(entry.id)
                    roles.remove(entry.id)
                    watched.remove(entry.id)
                }
            },
        )
    }

    /** The NavHost asks for the enter and the exit of one transition: decide it once. */
    private fun begin(
        kind: String,
        from: NavBackStackEntry,
        to: NavBackStackEntry,
    ): Boolean {
        val key = "$kind:${from.id}>${to.id}"
        if (key == lastTransition) return false
        lastTransition = key
        return true
    }

    private fun assign(
        top: NavBackStackEntry,
        under: NavBackStackEntry,
        style: PageStyle,
        origin: PageOrigin?,
        predictive: Boolean,
    ) {
        roles[top.id] = PageRole(top = true, style = style, origin = origin, predictive = predictive)
        roles[under.id] = PageRole(top = false, style = style, origin = origin, predictive = predictive)
    }

    /**
     * The element the last tap landed on, if this navigation is that tap's doing: it
     * follows the release within [TapToNavigationMillis] and was not on an overlay. One tap
     * opens at most one page.
     */
    private fun takeTapOrigin(hostId: String): PageOrigin? {
        val position = pressPosition ?: return null
        pressPosition = null
        if (pressExcluded) return null
        val sinceRelease = SystemClock.uptimeMillis() - releaseUptime
        if (sinceRelease !in 0..TapToNavigationMillis) return null
        val bounds = semantics()?.clickableBoundsAt(position) ?: return null
        // A clickable covering most of the window is a page, not an element to return to.
        if (bounds.width * bounds.height > window.width * window.height * MaxOriginArea) return null
        return PageOrigin(
            bounds = bounds,
            cornerPx = minOf(bounds.minDimension / 2f, maxOriginCornerPx),
            hostId = hostId,
            window = window,
        )
    }

    private val NavBackStackEntry.isSearchResults get() = destination.route == OnlineSearchResultRoute
}

/** Shell-wide [PageMotion]; null outside the Vibra shell (pages then never animate). */
val LocalPageMotion = staticCompositionLocalOf<PageMotion?> { null }

/**
 * What a page tells its own content about the transition it is in, so one element of the page
 * can fly on its own between the page and the element the page grew out of (a shared element,
 * e.g. the profile avatar landing in the dock's avatar button).
 *
 * Everything here is read in the draw phase: [progress] reads the page's animated value, so a
 * layer that reads it redraws every frame without recomposing anything.
 */
@Stable
class PageFlight internal constructor(
    private val entryId: String,
    private val motion: PageMotion?,
    private val shown: () -> Float,
    private val frame: FrameGeometry,
) {
    /** Layout coordinates of the page's content root (set by [PageFrame]). */
    internal var contentCoordinates: LayoutCoordinates? = null

    /**
     * The page's pose progress while it grows out of / shrinks into an origin element as the page
     * on top (1 = at rest, 0 = folded into the origin); null in any other situation (at rest,
     * pushed, faded, or the page beneath).
     */
    fun progress(): Float? {
        val role = motion?.role(entry = entryId) ?: return null
        if (!role.top || role.style != PageStyle.Origin || role.origin == null) return null
        val progress = PagePose.progress(shown(), role.predictive)
        return progress.takeIf { it < 1f }
    }

    /** Window bounds of the element the page grew out of, while [progress] is not null. */
    fun originBounds(): Rect? = motion?.role(entry = entryId)?.origin?.bounds

    /**
     * Where [coordinates] (something inside this page) sits in the window when the page is at
     * rest — i.e. ignoring the page's own transition pose, which scales and moves the content
     * while it opens or closes.
     */
    fun restBoundsInWindow(coordinates: LayoutCoordinates): Rect? {
        val root = contentCoordinates?.takeIf { it.isAttached } ?: return null
        if (!coordinates.isAttached) return null
        return root.localBoundingBoxOf(coordinates, clipBounds = false).translate(frame.windowOffset)
    }
}

/** The [PageFlight] of the page this content belongs to; null outside a Vibra page. */
val LocalPageFlight = staticCompositionLocalOf<PageFlight?> { null }

/**
 * Bounds (window coordinates) of the innermost clickable element under [point]. Later
 * siblings are visited later, so the element drawn on top wins, as it would for touches.
 */
private fun SemanticsOwner.clickableBoundsAt(point: Offset): Rect? {
    var hit: Rect? = null

    fun visit(node: SemanticsNode) {
        val bounds = node.boundsInWindow
        if (SemanticsActions.OnClick in node.config && bounds.contains(point)) hit = bounds
        node.children.forEach(::visit)
    }
    visit(rootSemanticsNode)
    return hit
}

/**
 * A destination's frame: the opaque page plus the pose of the transition it is in.
 * Two layers: the outer one is the page's container (position, clip, opacity), the inner
 * one its content (scaled to fit the container while it grows out of an origin).
 */
@Composable
internal fun AnimatedVisibilityScope.PageFrame(
    entry: NavBackStackEntry,
    content: @Composable () -> Unit,
) {
    val motion = LocalPageMotion.current
    val springSpec = Liquid.container<Float>()
    val gestureSpec: FiniteAnimationSpec<Float> = if (LocalAnimationsDisabled.current) snap() else GestureSpec
    // 1 = the page at rest, 0 = not there (still to come in, or gone).
    val shown =
        transition.animateFloat(
            transitionSpec = { if (motion?.role(entry.id)?.predictive == true) gestureSpec else springSpec },
            label = "page",
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    val screenCornerPx = with(LocalDensity.current) { rememberScreenCornerRadius().toPx() }
    val frame = remember { FrameGeometry() }
    val flight = remember(entry, motion) { PageFlight(entry.id, motion, { shown.value }, frame) }
    val background = MaterialTheme.colorScheme.background
    DisposableEffect(entry, motion) {
        motion?.watch(entry)
        onDispose {}
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onPlaced { frame.windowOffset = it.positionInWindow() }
                .graphicsLayer {
                    val role = motion?.role(entry.id) ?: return@graphicsLayer
                    poseContainer(role, PagePose.progress(shown.value, role.predictive), frame.windowOffset, screenCornerPx)
                }.background(background)
                .drawWithContent {
                    drawContent()
                    val role = motion?.role(entry.id) ?: return@drawWithContent
                    val progress = PagePose.progress(shown.value, role.predictive)
                    if (role.top) {
                        // A page growing out of an origin shows its content through the
                        // container's own colour: a veil over the content, which looks the
                        // same as fading the content in and needs no offscreen buffer.
                        if (role.style == PageStyle.Origin && progress < 1f) {
                            val veil = 1f - PagePose.contentAlpha(progress)
                            if (veil > 0f) drawRect(background, alpha = veil)
                        }
                    } else if (role.style != PageStyle.Fade) {
                        val scrim = PagePose.scrim(1f - progress)
                        if (scrim > 0f) drawRect(Color.Black, alpha = scrim)
                    }
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val role = motion?.role(entry.id) ?: return@graphicsLayer
                        if (role.top && role.style == PageStyle.Origin && role.origin != null) {
                            poseOriginContent(role.origin, PagePose.progress(shown.value, role.predictive), frame.windowOffset)
                        }
                    }.onPlaced { flight.contentCoordinates = it },
        ) {
            CompositionLocalProvider(LocalPageFlight provides flight, content = content)
        }
    }
}

/** Where the frame sits in the window (origins are in window coordinates). */
internal class FrameGeometry {
    var windowOffset: Offset = Offset.Zero
}

private fun GraphicsLayerScope.poseContainer(
    role: PageRole,
    progress: Float,
    windowOffset: Offset,
    screenCornerPx: Float,
) {
    if (progress >= 1f) return
    val origin = role.origin
    when {
        // The page beneath: still under an origin page, drifting left under a pushed one,
        // fading through (out first, so the two never overlap) under search results.
        !role.top ->
            when (role.style) {
                PageStyle.Push -> translationX = -PagePose.underTravel(1f - progress) * size.width
                PageStyle.Fade -> alpha = PagePose.fadeOutAlpha(1f - progress)
                PageStyle.Origin -> Unit
            }

        role.style == PageStyle.Origin && origin != null -> {
            val rect = PagePose.originRect(origin.bounds.translate(-windowOffset), size, progress)
            shape = RoundedRectClip(rect, PagePose.lerp(origin.cornerPx, screenCornerPx, progress))
            clip = true
            alpha = PagePose.containerAlpha(progress)
        }

        role.style == PageStyle.Fade -> {
            alpha = PagePose.fadeInAlpha(progress)
            translationY = (1f - progress) * PagePose.FadeDrop * size.height
        }

        else -> {
            val out = 1f - progress
            translationX = out * PagePose.PushTravel * size.width
            scaleX = 1f - out * PagePose.PushShrink
            scaleY = scaleX
            alpha = PagePose.pushAlpha(progress)
            shape = RoundedCornerShape(screenCornerPx)
            clip = true
        }
    }
}

/** The content of a page growing out of [origin], scaled to cover the container (it fades under the veil). */
private fun GraphicsLayerScope.poseOriginContent(
    origin: PageOrigin,
    progress: Float,
    windowOffset: Offset,
) {
    if (progress >= 1f) return
    val rect = PagePose.originRect(origin.bounds.translate(-windowOffset), size, progress)
    val scale = PagePose.coverScale(rect.size, size)
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = scale
    scaleY = scale
    translationX = rect.left + (rect.width - size.width * scale) / 2f
    translationY = rect.top
}

/** Clips a layer to [rect] (layer coordinates) with rounded corners. */
private class RoundedRectClip(
    private val rect: Rect,
    private val cornerPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline =
        if (rect.width <= 0f || rect.height <= 0f) {
            RectangleShape.createOutline(Size.Zero, layoutDirection, density)
        } else {
            Outline.Rounded(RoundRect(rect, CornerRadius(cornerPx)))
        }
}

/**
 * The geometry of the three page styles, as pure functions of the page's progress
 * (0 = away, 1 = at rest), so they can be tested and shared by both pages of a transition.
 */
internal object PagePose {
    /** Distance (fraction of the width) a pushed page travels from/to the right. */
    const val PushTravel = 0.25f

    /** How much a pushed page shrinks at the far end of its travel. */
    const val PushShrink = 0.06f

    /**
     * A pushed page dissolves between these two points of its travel: solid for most of
     * it, gone just before the end, so it is never a ghost over the page beneath for long.
     */
    const val PushFadeStart = 0.1f
    const val PushFadeEnd = 0.4f

    /** Drift (fraction of the width) of the page beneath a pushed one. */
    const val UnderTravel = 0.06f

    /** Darkening of the page beneath when fully covered. */
    const val MaxScrim = 0.25f

    /** Opening share over which an origin's container turns opaque. */
    const val ContainerFadeSpan = 0.12f

    /** Opening share over which the content fades in once the container is there. */
    const val ContentFadeSpan = 0.40f

    /** Sink (fraction of the height) of search results as they dissolve. */
    const val FadeDrop = 1f / 40f

    /** Share of a fade-through in which the page beneath is gone and the new one not yet in. */
    const val FadeThrough = 0.3f

    /**
     * The pose's progress for a transition progress [shown]. A back gesture drives the
     * transition linearly with the finger; [GestureEasing] makes the page answer at once
     * and land softly when released.
     */
    fun progress(
        shown: Float,
        predictive: Boolean,
    ): Float = if (predictive) 1f - GestureEasing.transform((1f - shown).coerceIn(0f, 1f)) else shown

    fun lerp(
        start: Float,
        stop: Float,
        fraction: Float,
    ): Float = start + (stop - start) * fraction

    /** The container of a page between its [origin] (0) and the full [page] (1). */
    fun originRect(
        origin: Rect,
        page: Size,
        progress: Float,
    ): Rect =
        Rect(
            left = lerp(origin.left, 0f, progress),
            top = lerp(origin.top, 0f, progress),
            right = lerp(origin.right, page.width, progress),
            bottom = lerp(origin.bottom, page.height, progress),
        )

    /** Uniform scale at which the full [page] covers [container] (no gaps, no stretching). */
    fun coverScale(
        container: Size,
        page: Size,
    ): Float = maxOf(container.width / page.width.coerceAtLeast(1f), container.height / page.height.coerceAtLeast(1f))

    fun containerAlpha(progress: Float): Float = (progress / ContainerFadeSpan).coerceIn(0f, 1f)

    fun contentAlpha(progress: Float): Float = ((progress - ContainerFadeSpan) / ContentFadeSpan).coerceIn(0f, 1f)

    fun pushAlpha(progress: Float): Float = ((progress - PushFadeStart) / (PushFadeEnd - PushFadeStart)).coerceIn(0f, 1f)

    fun fadeInAlpha(progress: Float): Float = ((progress - FadeThrough) / (1f - FadeThrough)).coerceIn(0f, 1f)

    fun fadeOutAlpha(covered: Float): Float = (1f - covered / FadeThrough).coerceIn(0f, 1f)

    fun underTravel(covered: Float): Float = covered * UnderTravel

    fun scrim(covered: Float): Float = covered.coerceIn(0f, 1f) * MaxScrim
}

/** How long a back gesture's whole travel is worth: the time the release has left to land. */
private const val GestureMillis = 450

/** Close to the finger while swiping (a little ahead of it), soft at the end. */
private val GestureEasing = CubicBezierEasing(0.3f, 0.38f, 0.6f, 1f)

/** Under a back gesture the transition is scrubbed linearly; [PagePose.progress] shapes it. */
private val GestureSpec: FiniteAnimationSpec<Float> = tween(GestureMillis, easing = LinearEasing)

/** A navigation later than this after the tap is not the tap's doing (e.g. a menu item). */
private const val TapToNavigationMillis = 600L

/** Share of the window above which a tapped element is not treated as an origin. */
private const val MaxOriginArea = 0.6f
