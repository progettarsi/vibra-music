/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.player

import android.content.Context
import android.os.Build
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.vibra.canvas.CanvasArtwork
import app.vibra.canvas.CanvasClipCache
import app.vibra.canvas.CanvasEnabledKey
import app.vibra.canvas.CanvasRepository
import app.vibra.canvas.toCanvasQuery
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.rememberPreference

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  CANVAS VIDEO — a short looping clip dissolving in over the cover
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  Three independent layers (see app.vibra.canvas): discovery finds a clip for the track,
 *  this file plays it, and the cover (FlyingCover) only hosts the surface. The still cover
 *  is always underneath and is the permanent fallback.
 *
 *  Unlike ArtworkVideo (a music video that follows the audio) a Canvas is a decorative loop:
 *  silent, no audio focus, no sync with the playback position. Its player is built only while
 *  the player sheet is open, pauses while the queue/lyrics/another app cover it, and is
 *  released a little after the sheet closes. The cover dissolves into it only after the
 *  decoder has really rendered a frame; any failure leaves the cover exactly as it was.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/**
 * The Canvas for [metadata]'s track while [enabled], or null. The lookup starts at once and
 * independently of the cover (Coil) and of the audio; whatever the repository already knows is
 * returned at the first composition. A new track cancels the previous lookup, and a result is
 * only handed out for the media id it was found for.
 */
@Composable
fun rememberCanvasArtwork(
    metadata: MediaMetadata,
    enabled: Boolean,
    repository: CanvasRepository = CanvasRepository.shared,
): CanvasArtwork? {
    val query =
        remember(metadata.id, metadata.title, metadata.artists, metadata.album?.title, metadata.isPodcast, metadata.isMusicVideo) {
            metadata.toCanvasQuery()
        }
    // The Advanced-settings switch: off means no lookup and no request at all.
    val allowed by rememberPreference(CanvasEnabledKey, defaultValue = true)
    val identified = allowed && query != null
    var found by remember(metadata.id) { mutableStateOf(query?.let { repository.cached(it)?.artwork }) }
    LaunchedEffect(query, identified, enabled) {
        if (!identified || query == null) {
            found = null
            return@LaunchedEffect
        }
        // Closed player: keep what is known (it is ready when the player reopens), search nothing.
        if (!enabled) return@LaunchedEffect
        // Skipping through tracks must not fire a lookup per track.
        if (repository.cached(query) == null) delay(LookupDebounceMillis)
        found = repository.canvasFor(query)
    }
    return found?.takeIf { identified && it.mediaId == metadata.id }
}

/**
 * What the cover may show for [mediaId]: the Canvas, only if it was found for this very track,
 * ambient motion is allowed, and the track has no music video (which keeps its own surface).
 */
internal fun canvasForSurface(
    canvas: CanvasArtwork?,
    mediaId: String,
    hasMusicVideo: Boolean,
    ambientAllowed: Boolean,
): CanvasArtwork? = canvas?.takeIf { ambientAllowed && !hasMusicVideo && it.mediaId == mediaId }

internal enum class CanvasStage { Idle, Preparing, Ready, Visible, Failed }

/**
 * The life of one Canvas on screen: Idle → Preparing → Ready (a frame was really rendered) →
 * Visible (the dissolve is done). Failure tries the alternate rendition once, then gives up for
 * good. Plain Kotlin, so the rules are tested without a device.
 */
internal class CanvasPresentation(private val urls: List<String>) {
    var stage = CanvasStage.Idle
        private set
    private var index = 0

    /** The rendition being played. */
    val url: String get() = urls[index]

    /** A player is built only when asked for, and only once. */
    fun prepare(requested: Boolean): Boolean {
        if (!requested || stage != CanvasStage.Idle) return false
        stage = CanvasStage.Preparing
        return true
    }

    /** The decoder rendered a frame; ExoPlayer merely being READY does not count. */
    fun firstFrame(): Boolean {
        if (stage != CanvasStage.Preparing) return false
        stage = CanvasStage.Ready
        return true
    }

    fun revealed() {
        if (stage == CanvasStage.Ready) stage = CanvasStage.Visible
    }

    /** True when the alternate rendition should now be tried. */
    fun failed(): Boolean {
        if (stage == CanvasStage.Idle || stage == CanvasStage.Failed) return false
        if (index < urls.lastIndex) {
            index++
            stage = CanvasStage.Preparing
            return true
        }
        stage = CanvasStage.Failed
        return false
    }

    /** The player was released while the sheet is closed; a failed Canvas stays failed. */
    fun park() {
        if (stage != CanvasStage.Failed) {
            stage = CanvasStage.Idle
            index = 0
        }
    }

    val showing: Boolean get() = stage == CanvasStage.Ready || stage == CanvasStage.Visible
}

/** A reveal jumps instead of dissolving when animations are off or the cover is not on screen yet. */
internal fun revealSnaps(animationsDisabled: Boolean, target: Float, surfaceVisibility: Float): Boolean =
    animationsDisabled || (target > 0f && surfaceVisibility <= 0.001f)

/** One Canvas clip played into one surface (see the file header). */
@Stable
internal class CanvasPlayback(
    private val context: Context,
    canvas: CanvasArtwork,
    portrait: Boolean = false,
) {
    private val presentation = CanvasPresentation(canvas.playbackUrls(portrait))

    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var stage by mutableStateOf(CanvasStage.Idle)
        private set
    var aspectRatio by mutableStateOf(1f)
        private set

    /** 0 → 1 while the Canvas dissolves in over the cover. */
    val reveal = Animatable(0f)

    fun prepare(requested: Boolean) {
        if (!presentation.prepare(requested)) return
        stage = presentation.stage
        player = build()
    }

    fun revealed() {
        presentation.revealed()
        stage = presentation.stage
    }

    /** The sheet has been closed for a while: free the decoder; the cover is all that shows. */
    fun park() {
        release()
        presentation.park()
        stage = presentation.stage
    }

    fun release() {
        val video = player ?: return
        player = null
        video.release()
    }

    private val listener =
        object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (presentation.firstFrame()) stage = presentation.stage
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val video = player ?: return
                if (presentation.failed()) {
                    stage = presentation.stage
                    video.setMediaItem(MediaItem.fromUri(presentation.url))
                    video.prepare()
                } else {
                    stage = presentation.stage
                    release()
                }
            }
        }

    @OptIn(UnstableApi::class)
    private fun build(): ExoPlayer =
        ExoPlayer
            .Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(CanvasClipCache.dataSourceFactory(context)))
            .setLoadControl(
                DefaultLoadControl
                    .Builder()
                    .setBufferDurationsMs(MinBufferMillis, MaxBufferMillis, StartBufferMillis, StartBufferMillis)
                    .build(),
            ).build()
            .apply {
                // Decoration only: no sound, no audio track, no audio focus (the default).
                volume = 0f
                trackSelectionParameters =
                    trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = false
                addListener(listener)
                setMediaItem(MediaItem.fromUri(presentation.url))
                prepare()
            }
}

/**
 * [canvas] playing over the cover image, centre-cropped to the layer's box. [prepareWhen] says
 * when its player may exist, [active] when it may run, [visibility] fades it with the sheet.
 * Transparent until a frame has been rendered; if anything fails it stays transparent.
 *
 * With [portrait] the vertical rendition fills the whole box (the player screen, behind the
 * controls) and a soft scrim keeps the controls readable over it; otherwise it is the cover's.
 */
@Composable
internal fun CanvasSurface(
    canvas: CanvasArtwork,
    active: () -> Boolean,
    prepareWhen: () -> Boolean,
    visibility: () -> Float,
    modifier: Modifier = Modifier,
    portrait: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val playback = remember(canvas, portrait) { CanvasPlayback(context, canvas, portrait) }
    DisposableEffect(playback) { onDispose(playback::release) }
    val currentActive by rememberUpdatedState(active)
    val currentPrepare by rememberUpdatedState(prepareWhen)
    val currentVisibility by rememberUpdatedState(visibility)

    LaunchedEffect(playback) {
        snapshotFlow { currentPrepare() }.collectLatest { wanted ->
            if (wanted) {
                playback.prepare(true)
            } else {
                delay(ParkDelayMillis)
                playback.park()
            }
        }
    }
    // Paused whenever the cover is not settled in front: no decoding for a hidden surface.
    LaunchedEffect(playback) {
        snapshotFlow { currentActive() && playback.player != null }.collect { running ->
            playback.player?.playWhenReady = running
        }
    }
    val shown = playback.stage == CanvasStage.Ready || playback.stage == CanvasStage.Visible
    val animationsDisabled = LocalAnimationsDisabled.current
    LaunchedEffect(playback, shown) {
        val target = if (shown) 1f else 0f
        if (revealSnaps(animationsDisabled, target, currentVisibility())) {
            playback.reveal.snapTo(target)
        } else {
            playback.reveal.animateTo(target, tween(RevealMillis, easing = FastOutSlowInEasing))
        }
        if (shown) playback.revealed()
    }
    val video = playback.player ?: return
    key(video) {
        // The video, recorded once so the blurred copy below can draw the very same frames.
        val frames = rememberGraphicsLayer()
        val blurPx = with(LocalDensity.current) { PortraitBlur.toPx() }
        Box(modifier.clipToBounds().graphicsLayer { alpha = playback.reveal.value * currentVisibility() }) {
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    if (portrait) frames.record { this@drawWithContent.drawContent() }
                    drawContent()
                },
            ) {
                AndroidView(
                    factory = { viewContext -> TextureView(viewContext).also(video::setVideoTextureView) },
                    onRelease = video::clearVideoTextureView,
                    modifier = Modifier.fillMaxSize().canvasCentreCrop(playback.aspectRatio),
                )
            }
            if (portrait) {
                // The lower part carries the title and the controls. The picture there blurs
                // progressively (from nothing, higher up, to PortraitBlur) and darkens a little,
                // as the cover's own backdrop does behind them.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Box(
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                                renderEffect = BlurEffect(blurPx, blurPx)
                            }.drawWithContent {
                                drawLayer(frames)
                                drawRect(
                                    Brush.verticalGradient(
                                        PortraitBlurStart to Color.Transparent,
                                        PortraitBlurFull to Color.Black,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    )
                }
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                PortraitBlurStart to Color.Transparent,
                                PortraitBlurFull to Color.Black.copy(alpha = PortraitScrimAlpha),
                                1f to Color.Black.copy(alpha = PortraitScrimAlpha),
                            ),
                        )
                    },
                )
            }
        }
    }
}

/** Measures the content to cover the incoming box at [aspectRatio], centred (the box clips it). */
private fun Modifier.canvasCentreCrop(aspectRatio: Float): Modifier =
    layout { measurable, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val (contentWidth, contentHeight) =
            if (width.toFloat() / height.coerceAtLeast(1) < aspectRatio) {
                (height * aspectRatio).roundToInt() to height
            } else {
                width to (width / aspectRatio).roundToInt()
            }
        val placeable = measurable.measure(Constraints.fixed(contentWidth, contentHeight))
        layout(width, height) { placeable.place((width - contentWidth) / 2, (height - contentHeight) / 2) }
    }

/** The cover dissolving into the Canvas, once its first frame is on the surface. */
private const val RevealMillis = 450

/** Under a full-screen Canvas: the blur and the darkening ramp up between these heights of the screen. */
private const val PortraitBlurStart = 0.52f
private const val PortraitBlurFull = 0.80f
private val PortraitBlur = 14.dp

/** A light darkening for the controls; the blur does most of the work. */
private const val PortraitScrimAlpha = 0.32f

/** How long a closed sheet keeps the decoder, so reopening soon finds the Canvas ready and it can grow with the cover. */
private const val ParkDelayMillis = 15_000L

/** An uncached lookup waits this long, so skipping through the queue does not search every track. */
private const val LookupDebounceMillis = 250L

private const val MinBufferMillis = 2_000
private const val MaxBufferMillis = 8_000
private const val StartBufferMillis = 500
