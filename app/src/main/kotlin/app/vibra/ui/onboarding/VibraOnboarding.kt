/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.onboarding

import app.vibra.ui.motion.ambientMotionAllowed
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.vibra.ui.motion.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibra.ui.components.VibraArtwork
import app.vibra.ui.components.VibraGlyphButton
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSpeed
import app.vibra.ui.motion.liquidPress
import app.vibra.ui.motion.rememberLiquidMorphShape
import app.vibra.ui.profile.ProfileColors
import app.vibra.ui.profile.ProfileShape
import app.vibra.ui.profile.rememberProfileStyle
import app.vibra.ui.theme.VibraDimens
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import kotlin.math.PI
import kotlin.math.sin

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  VIBRA ONBOARDING — one stage, five scenes (state in VibraOnboardingViewModel)
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  The screens are not five pages pushed in turn but one stage whose pieces move:
 *
 *   • THE HERO, a large shape in the theme's primary colour. On the welcome it carries the
 *     logo; on Style it *becomes you*: it shows your picture and morphs into the shape you
 *     pick, in the colour you pick (the whole theme follows live); on Ready it turns into the
 *     player's play cookie.
 *   • FOUR PASTEL SHAPES (flower, gem, clover, sunny — the profile shapes) that gather around
 *     the hero on the welcome and on Ready and step aside, small and faint, while you read.
 *   • THE PRIMARY BUTTON, always at the bottom: it is one button for the whole flow; its label
 *     slides to the next step's action and its colour turns to the theme's primary for the
 *     actions that matter (sign in, enter).
 *
 *  Only the words change by sliding sideways (forwards/backwards), each line a beat after
 *  the previous one. Every movement is a spring from the Liquid tokens; positions are read
 *  in graphics layers, so moving the stage never recomposes it. With animations off
 *  (LocalAnimationsDisabled) everything simply jumps to its place.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/** Shows the onboarding. [onFinished] is called once the user enters the app. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VibraOnboarding(
    viewModel: VibraOnboardingViewModel,
    onSignInYouTube: () -> Unit,
    onSignInSpotify: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val style = rememberProfileStyle()
    val context = LocalContext.current
    val haptics = rememberVibraHaptics()
    // Android 13+: the only permission asked, as the user enters (the result doesn't matter).
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFinished() }
    val enter = {
        val needsAsk =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else onFinished()
    }

    val animationsOff = LocalAnimationsDisabled.current
    // Springs read from the theme here: transition lambdas can't read composition locals.
    val slideSpec = Liquid.spatial<IntOffset>()
    val fadeSpec = Liquid.effects<Float>()
    val fadeFast = Liquid.effects<Float>(LiquidSpeed.Fast)

    BackHandler(enabled = state.step != OnboardingStep.Welcome, onBack = viewModel::back)

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val stageHeight = maxHeight
        val hero = HeroPoses.getValue(state.step)
        // The words start below the hero (or below the top bar when the hero steps away).
        val contentTop = if (hero.alpha > 0f) stageHeight * hero.y + hero.size / 2 + 20.dp else TopBarHeight + 24.dp

        Stage(step = state.step, avatarUrl = state.accountAvatarUrl, profileShape = style.shape.polygon)

        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = VibraDimens.Gutter)) {
            TopBar(state = state, onBack = viewModel::back, onSkip = viewModel::next)
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    if (animationsOff) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        val direction = if (viewModel.state.value.back) -1 else 1
                        (slideInHorizontally(slideSpec) { it / 5 * direction } + fadeIn(fadeSpec)) togetherWith
                            (slideOutHorizontally(slideSpec) { -it / 5 * direction } + fadeOut(fadeFast))
                    }.using(SizeTransform(clip = false))
                },
                contentAlignment = Alignment.TopStart,
                label = "onboardingStep",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { step ->
                Column(Modifier.fillMaxSize().padding(top = (contentTop - TopBarHeight).coerceAtLeast(0.dp))) {
                    when (step) {
                        OnboardingStep.Welcome -> WelcomeScene()
                        OnboardingStep.Account ->
                            AccountScene(
                                state = state,
                                onYouTube = {
                                    haptics.press()
                                    onSignInYouTube()
                                },
                                onSpotify = {
                                    haptics.press()
                                    onSignInSpotify()
                                },
                            )
                        OnboardingStep.Artists -> ArtistsScene(state = state, onToggle = viewModel::toggleArtist)
                        OnboardingStep.Style -> StyleScene(name = state.accountName, selectedColor = style.color, selectedShape = style.shape, onColor = style.setColor, onShape = style.setShape)
                        OnboardingStep.Ready -> ReadyScene(name = state.accountName)
                    }
                }
            }
            Actions(
                state = state,
                onPrimary = {
                    haptics.press()
                    when (state.step) {
                        OnboardingStep.Welcome, OnboardingStep.Account, OnboardingStep.Style -> viewModel.next()
                        OnboardingStep.Artists -> viewModel.followSelected()
                        OnboardingStep.Ready -> enter()
                    }
                },
            )
        }
    }
}

// ── Stage: hero + pastel shapes ─────────────────────────────────────────────────────

/** Where the hero sits on a step: centre ([x], [y] as shares of the stage), size, opacity. */
@Immutable
private data class HeroPose(
    val x: Float,
    val y: Float,
    val size: Dp,
    val alpha: Float,
)

private val HeroPoses =
    mapOf(
        OnboardingStep.Welcome to HeroPose(0.5f, 0.27f, 200.dp, 1f),
        OnboardingStep.Account to HeroPose(0.5f, 0.2f, 128.dp, 1f),
        OnboardingStep.Artists to HeroPose(0.5f, 0.06f, 48.dp, 0f),
        OnboardingStep.Style to HeroPose(0.5f, 0.2f, 160.dp, 1f),
        OnboardingStep.Ready to HeroPose(0.5f, 0.3f, 184.dp, 1f),
    )

/** A pastel shape's pose: centre as shares of the stage, size, rotation, opacity. */
@Immutable
private data class ShapePose(
    val x: Float,
    val y: Float,
    val size: Dp,
    val rotation: Float,
    val alpha: Float,
)

/**
 * The four pastel shapes (colours from the onboarding design) and their pose per step. Every
 * pose keeps clear of the top bar and of the words below the hero (checked on a 1080 × 2400
 * screen): the shapes frame the scene, they never sit on text or buttons.
 */
private class PastelShape(
    val polygon: RoundedPolygon,
    val color: Color,
    val poses: Map<OnboardingStep, ShapePose>,
)

private val PastelShapes =
    listOf(
        PastelShape(
            MaterialShapes.Flower,
            Color(0xFFEFB8C8),
            mapOf(
                OnboardingStep.Welcome to ShapePose(0.13f, 0.14f, 72.dp, -12f, 1f),
                OnboardingStep.Account to ShapePose(0.12f, 0.14f, 44.dp, 20f, 0.7f),
                OnboardingStep.Artists to ShapePose(-0.02f, 0.03f, 64.dp, 40f, 0.25f),
                OnboardingStep.Style to ShapePose(0.12f, 0.15f, 40.dp, 60f, 0.55f),
                OnboardingStep.Ready to ShapePose(0.18f, 0.18f, 64.dp, -24f, 1f),
            ),
        ),
        PastelShape(
            MaterialShapes.Gem,
            Color(0xFF9CD3C8),
            mapOf(
                OnboardingStep.Welcome to ShapePose(0.87f, 0.12f, 64.dp, 0f, 1f),
                OnboardingStep.Account to ShapePose(0.88f, 0.13f, 40.dp, 30f, 0.7f),
                OnboardingStep.Artists to ShapePose(1.02f, 0.06f, 56.dp, 60f, 0.25f),
                OnboardingStep.Style to ShapePose(0.88f, 0.14f, 36.dp, 45f, 0.55f),
                OnboardingStep.Ready to ShapePose(0.83f, 0.17f, 56.dp, 45f, 1f),
            ),
        ),
        PastelShape(
            MaterialShapes.Clover4Leaf,
            Color(0xFFA8C8FF),
            mapOf(
                OnboardingStep.Welcome to ShapePose(0.13f, 0.33f, 80.dp, 0f, 1f),
                OnboardingStep.Account to ShapePose(0.14f, 0.24f, 44.dp, 22f, 0.5f),
                OnboardingStep.Artists to ShapePose(-0.04f, 0.94f, 76.dp, 30f, 0.2f),
                OnboardingStep.Style to ShapePose(0.14f, 0.25f, 44.dp, 15f, 0.45f),
                OnboardingStep.Ready to ShapePose(0.15f, 0.34f, 64.dp, 10f, 1f),
            ),
        ),
        PastelShape(
            MaterialShapes.Sunny,
            Color(0xFFF3D27A),
            mapOf(
                OnboardingStep.Welcome to ShapePose(0.87f, 0.32f, 64.dp, 0f, 1f),
                OnboardingStep.Account to ShapePose(0.86f, 0.25f, 40.dp, 40f, 0.5f),
                OnboardingStep.Artists to ShapePose(1.03f, 0.92f, 68.dp, 70f, 0.2f),
                OnboardingStep.Style to ShapePose(0.86f, 0.26f, 40.dp, 30f, 0.45f),
                OnboardingStep.Ready to ShapePose(0.85f, 0.36f, 56.dp, 30f, 1f),
            ),
        ),
    )

/**
 * The shapes behind the words. Each pose value is its own spring (so a shape can travel and
 * turn at once), read in the draw phase; a slow idle sway keeps the stage alive.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Stage(
    step: OnboardingStep,
    avatarUrl: String?,
    profileShape: RoundedPolygon,
) {
    val animationsOff = !ambientMotionAllowed
    val sway = if (animationsOff) null else rememberInfiniteTransition(label = "sway").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(SwayMillis, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        PastelShapes.forEachIndexed { index, pastel ->
            val pose = pastel.poses.getValue(step)
            val x = animatePose(pose.x)
            val y = animatePose(pose.y)
            val size = animatePose(pose.size.value)
            val rotation = animatePose(pose.rotation)
            val alpha = animatePose(pose.alpha, effects = true)
            val shape = pastel.polygon.toShape()
            Box(
                Modifier
                    .size(MaxPastel)
                    .graphicsLayer {
                        val scale = size.value / MaxPastel.value
                        val phase = (sway?.value ?: 0f) + index * 1.7f
                        val drift = with(density) { SwayReach.toPx() } * sin(phase)
                        translationX = x.value * width - this.size.width / 2
                        translationY = y.value * height - this.size.height / 2 + drift
                        scaleX = scale
                        scaleY = scale
                        rotationZ = rotation.value + 6f * sin(phase * 0.5f)
                        this.alpha = alpha.value.coerceIn(0f, 1f)
                    }.clip(shape)
                    .background(pastel.color),
            )
        }
        Hero(step = step, avatarUrl = avatarUrl, profileShape = profileShape, stageWidth = width, stageHeight = height)
    }
}

/** The hero shape: logo → your picture in your shape → the play cookie. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Hero(
    step: OnboardingStep,
    avatarUrl: String?,
    profileShape: RoundedPolygon,
    stageWidth: Float,
    stageHeight: Float,
) {
    val pose = HeroPoses.getValue(step)
    val x = animatePose(pose.x)
    val y = animatePose(pose.y)
    val size = animatePose(pose.size.value)
    val alpha = animatePose(pose.alpha, effects = true)
    val target =
        when (step) {
            OnboardingStep.Style -> profileShape
            OnboardingStep.Ready -> MaterialShapes.Cookie12Sided
            else -> MaterialShapes.Cookie9Sided
        }
    val outline = rememberLiquidMorphShape(target = target, speed = LiquidSpeed.Slow)
    val fadeSpec = Liquid.effects<Float>()
    val fadeFast = Liquid.effects<Float>(LiquidSpeed.Fast)
    val scaleSpec = Liquid.spatial<Float>()
    Box(
        modifier =
            Modifier
                .size(MaxHero)
                .graphicsLayer {
                    val scale = size.value / MaxHero.value
                    translationX = x.value * stageWidth - this.size.width / 2
                    translationY = y.value * stageHeight - this.size.height / 2
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha.value.coerceIn(0f, 1f)
                }.clip(outline)
                .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        val glyph =
            when (step) {
                OnboardingStep.Style -> HeroGlyph.Avatar
                OnboardingStep.Ready -> HeroGlyph.Play
                else -> HeroGlyph.Logo
            }
        AnimatedContent(
            targetState = glyph,
            transitionSpec = { (fadeIn(fadeSpec) + scaleIn(scaleSpec, 0.7f)) togetherWith fadeOut(fadeFast) },
            label = "heroGlyph",
        ) { shown ->
            val onPrimary = MaterialTheme.colorScheme.onPrimary
            when (shown) {
                HeroGlyph.Logo -> Icon(painterResource(R.drawable.ic_vibra_logo), contentDescription = null, tint = onPrimary, modifier = Modifier.size(MaxHero * 0.5f))
                HeroGlyph.Play -> Icon(painterResource(R.drawable.play), contentDescription = null, tint = onPrimary, modifier = Modifier.size(MaxHero * 0.42f))
                HeroGlyph.Avatar ->
                    if (avatarUrl != null) {
                        VibraArtwork(url = avatarUrl, shape = androidx.compose.ui.graphics.RectangleShape, sizePx = 480, fallbackIcon = R.drawable.person, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(painterResource(R.drawable.person), contentDescription = null, tint = onPrimary, modifier = Modifier.size(MaxHero * 0.46f))
                    }
            }
        }
    }
}

private enum class HeroGlyph { Logo, Avatar, Play }

/** A spring towards [target] (spatial for geometry, effects for opacity), snapped when animations are off. */
@Composable
private fun animatePose(
    target: Float,
    effects: Boolean = false,
): State<Float> {
    val spec: FiniteAnimationSpec<Float> =
        when {
            LocalAnimationsDisabled.current -> snap()
            effects -> Liquid.effects(LiquidSpeed.Slow)
            else -> Liquid.spatial(LiquidSpeed.Slow)
        }
    return animateFloatAsState(target, spec, label = "pose")
}

// ── Chrome: top bar and actions ─────────────────────────────────────────────────────

/** Back, a segmented progress bar of the steps after the welcome, and Skip on Artists. */
@Composable
private fun TopBar(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val visible = state.step != OnboardingStep.Welcome
    val alpha = animatePose(if (visible) 1f else 0f, effects = true)
    val steps = state.steps.drop(1)
    val current = steps.indexOf(state.step)
    Row(
        modifier = Modifier.fillMaxWidth().height(TopBarHeight).graphicsLayer { this.alpha = alpha.value },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VibraGlyphButton(icon = R.drawable.arrow_back, contentDescription = stringResource(R.string.vibra_back), onClick = onBack, enabled = visible, size = 44.dp, iconSize = 22.dp)
        val description = stringResource(R.string.vibra_onb_step, current + 1, steps.size)
        Row(
            modifier = Modifier.weight(1f).semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val filled = MaterialTheme.colorScheme.primary
            val track = MaterialTheme.colorScheme.secondaryContainer
            steps.forEachIndexed { index, _ ->
                // Each segment fills from the left on a spring as the step is reached.
                val fill = animatePose(if (index <= current) 1f else 0f)
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .drawBehind {
                            val radius = CornerRadius(size.height / 2)
                            drawRoundRect(track, cornerRadius = radius)
                            val w = size.width * fill.value.coerceIn(0f, 1f)
                            if (w > 0f) drawRoundRect(filled, size = size.copy(width = w), cornerRadius = radius)
                        },
                )
            }
        }
        val skipVisible = state.step == OnboardingStep.Artists
        Text(
            text = stringResource(R.string.vibra_onb_skip),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .graphicsLayer { this.alpha = if (skipVisible) 1f else 0f }
                    .clip(CircleShape)
                    .clickable(enabled = skipVisible, role = Role.Button, onClick = onSkip)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
        )
    }
}

/** The bottom of the stage: the one primary button of the whole flow. */
@Composable
private fun Actions(
    state: OnboardingUiState,
    onPrimary: () -> Unit,
) {
    val label =
        when (state.step) {
            OnboardingStep.Welcome -> stringResource(R.string.vibra_onb_start)
            OnboardingStep.Account ->
                stringResource(if (state.anyConnected) R.string.vibra_onb_continue else R.string.vibra_onb_account_later)
            OnboardingStep.Artists -> {
                val n = state.selectedArtists.size
                when (n) {
                    0 -> stringResource(R.string.vibra_onb_continue)
                    1 -> stringResource(R.string.vibra_onb_artists_follow_one)
                    else -> stringResource(R.string.vibra_onb_artists_follow, n)
                }
            }
            OnboardingStep.Style -> stringResource(R.string.vibra_onb_continue)
            OnboardingStep.Ready -> stringResource(R.string.vibra_onb_ready_enter)
        }
    // A connected account and entering are the moments that matter: the button takes the theme colour.
    val emphasised = state.step == OnboardingStep.Account && state.anyConnected || state.step == OnboardingStep.Ready
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 8.dp)) {
        val container by animateColorAsState(
            if (emphasised) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            Liquid.effects(),
            label = "ctaContainer",
        )
        val content by animateColorAsState(
            if (emphasised) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface,
            Liquid.effects(),
            label = "ctaContent",
        )
        Pill(
            text = label,
            container = container,
            content = content,
            onClick = onPrimary,
            enabled = !(state.step == OnboardingStep.Artists && state.loadingAccount),
            trailing = if (state.step == OnboardingStep.Welcome) R.drawable.arrow_forward else null,
            animateLabel = true,
        )
    }
}

/**
 * A full-width 56 dp pill with the Vibra press squish. With [animateLabel] a new label slides
 * up into place, so the one button reads as changing its mind, not as a new button.
 */
@Composable
private fun Pill(
    text: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: Int? = null,
    animateLabel: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val slideSpec = Liquid.spatial<IntOffset>()
    val fadeSpec = Liquid.effects<Float>()
    val fadeFast = Liquid.effects<Float>(LiquidSpeed.Fast)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .liquidPress(interaction, pressedScale = 0.96f)
                .clip(CircleShape)
                .background(container)
                .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val label: @Composable (String) -> Unit = { value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(trailing), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (animateLabel) {
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    (slideInVertically(slideSpec) { it } + fadeIn(fadeSpec)) togetherWith
                        (slideOutVertically(slideSpec) { -it } + fadeOut(fadeFast))
                },
                label = "ctaLabel",
            ) { label(it) }
        } else {
            label(text)
        }
    }
}

// ── Scenes ──────────────────────────────────────────────────────────────────────────

/** Title + body pair used by every scene, entering line by line. */
@Composable
private fun AnimatedVisibilityScope.Heading(
    eyebrow: String? = null,
    title: String,
    body: String,
    centred: Boolean = false,
    large: Boolean = false,
) {
    val align = if (centred) TextAlign.Center else TextAlign.Start
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (eyebrow != null) {
            Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align, modifier = stagger(0))
        }
        Text(
            text = title,
            style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = align,
            modifier = stagger(1).semantics { heading() },
        )
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align, modifier = stagger(2))
    }
}

@Composable
private fun AnimatedVisibilityScope.WelcomeScene() {
    Heading(
        eyebrow = stringResource(R.string.vibra_onb_welcome_eyebrow),
        title = stringResource(R.string.vibra_onb_welcome_title),
        body = stringResource(R.string.vibra_onb_welcome_body),
        large = true,
    )
}

/** YouTube Music and Spotify side by side: both optional, both can be connected. */
@Composable
private fun AnimatedVisibilityScope.AccountScene(
    state: OnboardingUiState,
    onYouTube: () -> Unit,
    onSpotify: () -> Unit,
) {
    Heading(
        title = stringResource(R.string.vibra_onb_account_title),
        body = stringResource(R.string.vibra_onb_account_body),
        centred = true,
    )
    Spacer(Modifier.height(24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = stagger(3)) {
        ConnectionRow(
            icon = {
                Icon(
                    painterResource(R.drawable.library_music),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            },
            title = stringResource(R.string.vibra_onb_account_youtube),
            note = stringResource(R.string.vibra_onb_account_youtube_note),
            connected = state.youTubeConnected,
            loading = state.loadingYouTube,
            onClick = onYouTube,
        )
        ConnectionRow(
            icon = { Image(painterResource(R.drawable.spotify_icon), contentDescription = null, modifier = Modifier.size(28.dp)) },
            title = stringResource(R.string.vibra_onb_account_spotify),
            note = stringResource(R.string.vibra_onb_account_spotify_note),
            connected = state.spotifyConnected,
            loading = state.loadingSpotify,
            onClick = onSpotify,
        )
    }
}

/** What a [ConnectionRow] shows at its end. */
private enum class ConnectionStatus { Idle, Connected, Loading }

/**
 * One service: its mark, what connecting brings, and "Connect", which turns into a check once
 * connected (a loader while its data arrives). A connected row no longer reacts to taps.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectionRow(
    icon: @Composable () -> Unit,
    title: String,
    note: String,
    connected: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val connectedLabel = stringResource(R.string.vibra_onb_account_connected)
    val status =
        when {
            loading -> ConnectionStatus.Loading
            connected -> ConnectionStatus.Connected
            else -> ConnectionStatus.Idle
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .liquidPress(interaction, pressedScale = 0.97f)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(
                    interactionSource = interaction,
                    indication = androidx.compose.material3.ripple(),
                    enabled = !connected,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Springs read from the theme here: transition lambdas can't read composition locals.
        val fadeSpec = Liquid.effects<Float>()
        val fadeFast = Liquid.effects<Float>(LiquidSpeed.Fast)
        val popSpec = Liquid.spatial<Float>()
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                (fadeIn(fadeSpec) + scaleIn(popSpec, initialScale = 0.6f)) togetherWith fadeOut(fadeFast)
            },
            contentAlignment = Alignment.Center,
            label = "connection",
        ) { shown ->
            when (shown) {
                ConnectionStatus.Loading -> LoadingIndicator(modifier = Modifier.size(36.dp))
                ConnectionStatus.Connected ->
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .semantics { contentDescription = connectedLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.check), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                ConnectionStatus.Idle ->
                    Text(
                        text = stringResource(R.string.vibra_onb_account_connect),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
            }
        }
    }
}

/** Artists found in the account, preselected; a tap toggles one. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedVisibilityScope.ArtistsScene(
    state: OnboardingUiState,
    onToggle: (String) -> Unit,
) {
    Heading(title = stringResource(R.string.vibra_onb_artists_title), body = stringResource(R.string.vibra_onb_artists_body))
    Spacer(Modifier.height(20.dp))
    when {
        state.loadingAccount ->
            Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingIndicator(modifier = Modifier.size(64.dp))
                Text(stringResource(R.string.vibra_onb_account_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }

        state.artists.isEmpty() ->
            Text(stringResource(R.string.vibra_onb_artists_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = stagger(3))

        else ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().then(stagger(3)),
            ) {
                items(state.artists, key = { it.id }) { artist ->
                    ArtistChoice(artist = artist, selected = artist.id in state.selectedArtists, onClick = { onToggle(artist.id) })
                }
            }
    }
}

/** One artist: picture in a cookie that settles back and dims when deselected, with a check badge. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistChoice(
    artist: OnboardingArtist,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberVibraHaptics()
    val interaction = remember { MutableInteractionSource() }
    val emphasis = animatePose(if (selected) 1f else 0f)
    val cookie = MaterialShapes.Cookie9Sided.toShape()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .liquidPress(interaction, pressedScale = 0.92f)
                .clickable(interactionSource = interaction, indication = null, role = Role.Checkbox) {
                    haptics.toggle(on = !selected)
                    onClick()
                }.semantics { this.selected = selected },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            VibraArtwork(
                url = artist.thumbnailUrl,
                shape = cookie,
                sizePx = 240,
                fallbackIcon = R.drawable.person,
                modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        val e = emphasis.value
                        val s = 0.88f + 0.12f * e
                        scaleX = s
                        scaleY = s
                        alpha = 0.45f + 0.55f * e.coerceIn(0f, 1f)
                    },
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .graphicsLayer {
                            val e = emphasis.value
                            scaleX = e
                            scaleY = e
                        }.clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(3.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.check), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
        Text(artist.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Colour and shape (always asked; the current ones are preselected, so Continue keeps them). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedVisibilityScope.StyleScene(
    name: String?,
    selectedColor: Color,
    selectedShape: ProfileShape,
    onColor: (Color) -> Unit,
    onShape: (ProfileShape) -> Unit,
) {
    val haptics = rememberVibraHaptics()
    Heading(
        eyebrow = name?.let { stringResource(R.string.vibra_onb_ready_hello, it) },
        title = stringResource(R.string.vibra_onb_style_title),
        body = stringResource(R.string.vibra_onb_style_body),
        centred = true,
    )
    Spacer(Modifier.height(24.dp))
    SectionLabel(stringResource(R.string.vibra_onb_style_colour), stagger(3))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp), modifier = stagger(3)) {
        items(ProfileColors, key = { it.value.toLong() }) { colour ->
            val on = colour == selectedColor
            val ring = animatePose(if (on) 1f else 0f)
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .drawBehind {
                            // The ring grows in around the chosen swatch.
                            val r = ring.value.coerceIn(0f, 1f)
                            if (r > 0f) drawCircle(colour, radius = size.minDimension / 2, alpha = 0.5f * r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                        }.padding(6.dp)
                        .clip(CircleShape)
                        .background(colour)
                        .clickable(role = Role.RadioButton) {
                            haptics.toggle(on = true)
                            onColor(colour)
                        }.semantics { selected = on },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel(stringResource(R.string.vibra_onb_style_shape), stagger(4))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp), modifier = stagger(4)) {
        items(ProfileShape.entries, key = { it.name }) { shape ->
            val on = shape == selectedShape
            val emphasis = animatePose(if (on) 1f else 0f)
            val outline = shape.polygon.toShape()
            val label = stringResource(shape.label)
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.RadioButton) {
                            haptics.toggle(on = true)
                            onShape(shape)
                        }.semantics {
                            selected = on
                            contentDescription = label
                        },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            val e = emphasis.value
                            val s = 0.8f + 0.2f * e
                            scaleX = s
                            scaleY = s
                            rotationZ = -20f * (1f - e)
                        }.clip(outline)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(start = 4.dp))
}

@Composable
private fun AnimatedVisibilityScope.ReadyScene(name: String?) {
    Heading(
        eyebrow = name?.let { stringResource(R.string.vibra_onb_ready_hello, it) },
        title = stringResource(R.string.vibra_onb_ready_title),
        body = stringResource(R.string.vibra_onb_ready_body),
        centred = true,
        large = true,
    )
}

/**
 * Enters a scene line by line: each [index] rises 24 dp and fades in a beat ([StaggerMillis])
 * after the previous one, on an emphasised deceleration (one curve for every line).
 */
@Composable
private fun AnimatedVisibilityScope.stagger(index: Int): Modifier {
    if (LocalAnimationsDisabled.current) return Modifier
    val density = LocalDensity.current
    val rise = with(density) { StaggerRise.toPx() }
    val progress =
        transition.animateFloat(
            transitionSpec = { tween(StaggerDuration, delayMillis = StaggerMillis * index, easing = EmphasizedDecelerate) },
            label = "stagger",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
    return Modifier.graphicsLayer {
        // Exiting lines leave together with the scene (its own slide/fade), not in steps.
        val p = if (transition.targetState == EnterExitState.Visible) progress.value else 1f
        alpha = p
        translationY = (1f - p) * rise
    }
}

private val TopBarHeight = 56.dp
private val MaxHero = 200.dp
private val MaxPastel = 84.dp
private val SwayReach = 6.dp
private const val SwayMillis = 9_000
private const val StaggerMillis = 60
private const val StaggerDuration = 480
private val StaggerRise = 24.dp
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
