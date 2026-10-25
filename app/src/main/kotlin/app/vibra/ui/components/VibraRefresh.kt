/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package app.vibra.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSpeed
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.LocalAnimationsDisabled

/**
 * Pull-to-refresh where the VIBRA wave itself is the indicator (no stock spinner).
 *
 *  - Pulling lowers the whole screen (iOS-style), the logo a little more than the rest, and
 *    the logo's fill *drains away* to the right in proportion to the pull; a haptic marks
 *    the point of no return.
 *  - Release: the screen drops into the refreshing position on a bouncy spring (a small
 *    dip below it), and a bright band of fill flows through the wave in a loop.
 *  - Done: the wave fills back up from the left, the screen springs home overshooting a
 *    touch upwards, and a haptic says it is finished.
 *
 * [content] receives the modifier that applies the downward travel (put it on the root of
 * the scrolling content) and the [RefreshLogoState] to hand to [VibraLogoMark].
 */
@Composable
fun VibraPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(contentModifier: Modifier, logo: RefreshLogoState) -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptics = rememberVibraHaptics()
    val animationsDisabled = LocalAnimationsDisabled.current

    // Refill after a refresh completes.
    val refill = remember { Animatable(1f) }
    val refillSpec = Liquid.effects<Float>(LiquidSpeed.Slow)
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            refill.snapTo(0f)
        } else if (refill.value < 1f) {
            refill.animateTo(1f, refillSpec)
        }
    }

    // How far the screen is lowered (0 = home, 1 = refreshing position). It follows the
    // finger while pulling and runs its own bouncy springs on release and on completion
    // (the stock indicator's motion is a plain, non-elastic one).
    val travel = remember { Animatable(0f) }
    val bounce: AnimationSpec<Float> =
        if (animationsDisabled) snap() else spring(dampingRatio = BounceDamping, stiffness = Spring.StiffnessMediumLow)
    var following by remember { mutableStateOf(true) }
    // The logo has a bounce of its own on top of the screen's: it drops (and springs back
    // up past its rest) a beat after the screen, so it reads as its own object.
    val logoDrop = remember { Animatable(0f) }
    val logoSpring: AnimationSpec<Float> =
        if (animationsDisabled) snap() else spring(dampingRatio = LogoBounceDamping, stiffness = Spring.StiffnessLow)
    LaunchedEffect(isRefreshing) {
        logoDrop.animateTo(if (isRefreshing) 1f else 0f, logoSpring)
    }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            following = false
            travel.animateTo(1f, bounce)
        } else if (!following) {
            haptics.confirm()
            travel.animateTo(0f, bounce)
            // The stock state is still easing back on its own; take over the finger again
            // only once it is home, so the screen never jumps to its leftover distance.
            snapshotFlow { state.distanceFraction }.first { it <= 0.001f }
            following = true
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction }.collect { fraction ->
            if (following) travel.snapTo(fraction.coerceIn(0f, MaxPull))
        }
    }
    val flow =
        if (isRefreshing && !animationsDisabled) {
            rememberInfiniteTransition(label = "refreshFlow")
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
                    label = "refreshFlowPhase",
                )
        } else {
            null
        }
    val logo =
        RefreshLogoState(
            isRefreshing = isRefreshing,
            pull = { state.distanceFraction.coerceIn(0f, 1f) },
            refill = { refill.value },
            flow = flow?.let { phase -> { phase.value } },
            travel = { travel.value },
            drop = { logoDrop.value },
        )

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            haptics.threshold()
            onRefresh()
        },
        state = state,
        modifier = modifier.fillMaxSize(),
        indicator = {},
    ) {
        content(
            Modifier.graphicsLayer { translationY = travel.value * RefreshTravel.toPx() },
            logo,
        )
    }
}

/** Frame-time inputs of the animated logo (read in draw only). */
class RefreshLogoState(
    val isRefreshing: Boolean,
    val pull: () -> Float,
    val refill: () -> Float,
    val flow: (() -> Float)?,
    /** The screen's lowering (may dip below 0 or above 1 while bouncing). */
    val travel: () -> Float,
    /** The logo's own bounce: 0 at rest, 1 while refreshing (overshoots on both ends). */
    val drop: () -> Float,
)

/** The VIBRA wave; with a [state] it drains, flows and refills with the refresh. */
@Composable
fun VibraLogoMark(
    state: RefreshLogoState?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    val logo = remember { logoPath() }
    Canvas(
        modifier =
            modifier
                .size(LogoWidth, LogoHeight)
                .graphicsLayer {
                    // The logo leads the pull: it drops a little further and grows slightly.
                    val travel = state?.travel?.invoke() ?: 0f
                    val drop = state?.drop?.invoke() ?: 0f
                    translationY = travel.coerceAtLeast(0f) * LogoLead.toPx() + drop * LogoBounce.toPx()
                    val scale = 1f + 0.12f * travel.coerceIn(0f, 1f) + 0.1f * drop
                    scaleX = scale
                    scaleY = scale
                },
    ) {
        val path = logo.scaledTo(size)
        if (state == null) {
            drawPath(path, color)
            return@Canvas
        }
        val flow = state.flow
        val refill = state.refill()
        val pull = state.pull()
        if (flow != null || refill < 1f || pull > 0f) drawPath(path, color.copy(alpha = 0.18f))
        when {
            flow != null -> {
                // A band of fill travelling through the wave.
                val band = 0.42f
                val start = (flow() * (1f + band) - band) * size.width
                clipRect(left = start, right = start + band * size.width) { drawPath(path, color) }
            }
            state.isRefreshing -> drawPath(path, color.copy(alpha = 0.6f))
            refill < 1f -> clipRect(right = size.width * refill) { drawPath(path, color) }
            // Pulling: the fill drains away to the right as the pull grows.
            else -> clipRect(left = size.width * pull) { drawPath(path, color) }
        }
    }
}

/** The VIBRA wave outline (same path as res/drawable/ic_vibra_logo.xml), in viewport units. */
private fun logoPath(): Path = PathParser().parsePathString(LogoPathData).toPath()

private fun Path.scaledTo(size: Size): Path {
    val copy = Path().apply { addPath(this@scaledTo) }
    copy.transform(
        Matrix().apply {
            scale(size.width / LogoViewportWidth, size.height / LogoViewportHeight)
            translate(-LogoViewportX, -LogoViewportY)
        },
    )
    return copy
}

private val RefreshTravel = 88.dp
/** Extra drop of the logo, on top of the screen's own travel, at the refreshing position. */
private val LogoLead = 14.dp

/** How far the logo's own bounce carries it (it overshoots about a third beyond). */
private val LogoBounce = 18.dp

/** Damping of the release/finish springs: visibly elastic, still calm. */
private const val BounceDamping = 0.55f

/** The logo bounces more than the screen does. */
private const val LogoBounceDamping = 0.38f

/** Furthest the screen follows an over-pull (fraction of the refreshing position). */
private const val MaxPull = 1.4f

private val LogoWidth = 51.dp
private val LogoHeight = 35.dp
private const val LogoViewportWidth = 762f
private const val LogoViewportHeight = 527f
private const val LogoViewportX = 159f
private const val LogoViewportY = 276f

private const val LogoPathData =
    "M377.585 365.486C403.957 251.422 577.57 245.957 606.958 358.86C618.098 401.44 629.046 444.077 639.802 486.769" +
        "C640.991 504.577 671.676 513.367 683.677 493.927C688.156 486.677 692.642 479.431 697.134 472.188" +
        "C729.59 411.132 829.096 430.322 837.772 493.363C838.887 502.365 844.87 510.35 854.38 513.962" +
        "C863.104 517.271 873.311 516.213 881.642 509.92C885.03 507.193 888.42 504.468 891.81 501.744" +
        "C895.37 498.886 899.88 497.345 904.481 497.606C909.082 497.867 913.377 499.885 916.449 503.249" +
        "C919.521 506.612 921.144 511.074 920.99 515.682C920.837 520.291 918.896 524.647 915.732 527.937" +
        "C912.716 531.071 909.699 534.204 906.68 537.334C891.233 553.328 866.08 560.902 843.423 555.65" +
        "C818.92 550.689 796.772 530.329 790.035 505.052C783.781 484.901 754.63 486.771 750.71 501.998" +
        "C746.931 509.637 743.145 517.274 739.353 524.906C708.34 595.189 588.478 587.04 567.642 507.807" +
        "C553.794 466.017 540.139 424.169 526.676 382.267C521.847 369.184 511.115 363.472 494.805 364.369" +
        "C478.511 366.194 469.07 374.795 466.741 386.866C451.59 454.091 434.516 521.086 413.64 587.672" +
        "C403.607 619.551 391.848 651.075 377.624 682.032C353.608 738.17 300.701 781.718 245.526 795.103" +
        "C222.52 801.012 200.372 803.314 175.876 801.268C171.571 800.625 167.391 798.802 164.233 795.697" +
        "C161.075 792.594 159.2 788.518 159.015 784.237C158.831 779.956 160.347 775.734 163.227 772.372" +
        "C166.106 769.005 170.115 766.829 174.349 765.818C190.721 760.605 209.021 752.194 223.899 742.615" +
        "C261.021 719.091 280.2 697.814 297.215 657.308C314.795 615.456 325.1 582.095 330.556 560.259" +
        "C349.524 484.344 360.919 430.802 377.585 365.486Z"
