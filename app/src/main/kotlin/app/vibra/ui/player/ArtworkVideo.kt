/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.Surface
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.ChunkedDataSource
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.stream.ArtworkVideoStream
import moe.rukamori.archivetune.playback.stream.ArtworkVideoStreams
import okhttp3.OkHttpClient

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  ARTWORK VIDEO — a music video playing in place of the cover
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  When the track is a music video the engine resolves, with its audio, a light
 *  video-only stream (ArtworkVideoStreams). The cover layer then plays it on top of the
 *  cover image: muted, centre-cropped like a cover, and following the audio player
 *  (play/pause, seeks, speed). Drift beyond DriftToleranceMillis is corrected by a seek
 *  that lands a little ahead of the audio and waits for it there (see align).
 *
 *  Cost is kept to the settled player: the video player is built only once the sheet is
 *  fully open, pauses while the queue, the lyrics or another app cover it, and is
 *  released when the sheet closes. Until its first frame, or if anything fails, the cover
 *  image underneath simply stays.
 *
 *  The backdrop follows the video through ArtworkVideoFrames: a few times a second a tiny
 *  frame is copied and turned into a low-resolution blurred backdrop off the main thread
 *  (LiveBackdrop), instead of blurring the live video every frame.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/**
 * The picture of [metadata]'s music video while [enabled], once the engine has resolved one
 * (else null). If the track's own resolution brought none (its audio came from the cache),
 * [request] asks for it after a moment.
 */
@Composable
fun rememberArtworkVideoStream(
    metadata: MediaMetadata,
    enabled: Boolean,
    request: (mediaId: String) -> Unit,
): ArtworkVideoStream? {
    if (!metadata.isMusicVideo || !enabled) return null
    val stream by remember(metadata.id) { ArtworkVideoStreams.stream(metadata.id) }.collectAsState(null)
    val current by rememberUpdatedState(stream)
    LaunchedEffect(metadata.id) {
        delay(RequestDelayMillis)
        if (current == null) request(metadata.id)
    }
    return stream
}

/** The current track's video surface, while it shows a picture — read by the live backdrop. */
@Stable
class ArtworkVideoFrames {
    internal var source by mutableStateOf<ArtworkVideoPlayback?>(null)
}

/** One video stream played into one surface (see the file header). */
@Stable
internal class ArtworkVideoPlayback(
    private val context: Context,
    val stream: ArtworkVideoStream,
) {
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var hasFrame by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    internal var surface: TextureView? = null

    // The video's buffers, read back for the live backdrop (see grab).
    private var copySource: Surface? = null

    // Wakes the sync loop early: the audio played, paused, jumped or changed speed, or the
    // video became ready after a seek.
    private val nudge = Channel<Unit>(Channel.CONFLATED)

    // How far ahead of the audio a seek lands (see align); grows when seeks take longer.
    private var seekLead = InitialSeekLeadMillis
    private var seeking = false

    /** Builds the player the first time [active] holds, then keeps it following [master]. */
    suspend fun follow(
        master: Player,
        active: () -> Boolean,
    ) {
        snapshotFlow(active).first { it }
        val video = build()
        player = video
        val listener =
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    if (events.containsAny(
                            Player.EVENT_IS_PLAYING_CHANGED,
                            Player.EVENT_POSITION_DISCONTINUITY,
                            Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                        )
                    ) {
                        nudge.trySend(Unit)
                    }
                }
            }
        master.addListener(listener)
        try {
            while (!failed) {
                val wait = align(video, master, active())
                withTimeoutOrNull(wait) { nudge.receive() }
            }
        } finally {
            master.removeListener(listener)
        }
    }

    /**
     * Copies the latest video frame into [into], scaled to its size; false without one. The
     * copy runs on the render thread and answers on a background one: unlike
     * TextureView.getBitmap it never waits on the main thread.
     */
    suspend fun grab(into: Bitmap): Boolean {
        val texture = surface?.surfaceTexture ?: return false
        if (!hasFrame || failed) return false
        val source = copySource ?: Surface(texture).also { copySource = it }
        return suspendCancellableCoroutine { continuation ->
            runCatching {
                PixelCopy.request(source, into, { result -> continuation.resume(result == PixelCopy.SUCCESS) }, FrameCopies.handler)
            }.onFailure { continuation.resume(false) }
        }
    }

    /** The surface is going away: forget the copy source made for it. */
    fun detach(view: TextureView) {
        if (surface !== view) return
        surface = null
        copySource?.release()
        copySource = null
    }

    fun release() {
        copySource?.release()
        copySource = null
        player?.release()
        player = null
    }

    /**
     * Brings the video to the audio's position and play state; returns how long until the
     * next check. A seek takes a moment (download, decode from a keyframe) while the audio
     * keeps going, so a playing seek lands [seekLead] ahead and the video waits there,
     * paused, for the audio to arrive — then both run together.
     */
    private fun align(
        video: ExoPlayer,
        master: Player,
        active: Boolean,
    ): Long {
        if (!active || master.currentMediaItem?.mediaId != stream.mediaId) {
            video.playWhenReady = false
            return SyncIntervalMillis
        }
        val speed = master.playbackParameters.speed
        if (video.playbackParameters.speed != speed) video.setPlaybackSpeed(speed)
        val target = master.currentPosition
        val playing = master.isPlaying
        val duration = video.duration
        // A video shorter than its audio rests on its last frame.
        if (duration != C.TIME_UNSET && target >= duration - DriftToleranceMillis) {
            video.playWhenReady = false
            return SyncIntervalMillis
        }
        if (video.playbackState == Player.STATE_BUFFERING) {
            video.playWhenReady = false
            return SyncIntervalMillis
        }
        val ahead = video.currentPosition - target
        if (seeking && ahead < -DriftToleranceMillis) seekLead = (seekLead * 3 / 2).coerceAtMost(MaxSeekLeadMillis)
        seeking = false
        val lead = if (playing) seekLead else 0L
        if (ahead < -DriftToleranceMillis || ahead > lead + DriftToleranceMillis) {
            video.playWhenReady = false
            video.seekTo(target + lead)
            seeking = playing
            return SyncIntervalMillis
        }
        if (playing && ahead > DriftToleranceMillis) {
            // Landed ahead: hold the frame until the audio gets there.
            video.playWhenReady = false
            return (ahead / speed).toLong()
        }
        video.playWhenReady = playing
        return SyncIntervalMillis
    }

    @OptIn(UnstableApi::class)
    private fun build(): ExoPlayer =
        ExoPlayer
            .Builder(context)
            .setLoadControl(
                DefaultLoadControl
                    .Builder()
                    .setBufferDurationsMs(MinBufferMillis, MaxBufferMillis, StartBufferMillis, StartBufferMillis)
                    .build(),
            ).build()
            .apply {
                volume = 0f
                addListener(
                    object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            hasFrame = true
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            nudge.trySend(Unit)
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            failed = true
                            ArtworkVideoStreams.markFailed(stream.mediaId)
                        }
                    },
                )
                surface?.let(::setVideoTextureView)
                setMediaSource(mediaSource(stream))
                prepare()
            }
}

/**
 * [stream] playing over the cover image, centre-cropped to the layer's box. [active] says
 * when it may play (it is built the first time it holds); [visibility] fades it with the
 * sheet; [frames], for the current track only, lets the backdrop follow it.
 */
@Composable
internal fun ArtworkVideoSurface(
    stream: ArtworkVideoStream,
    master: Player,
    active: () -> Boolean,
    visibility: () -> Float,
    frames: ArtworkVideoFrames?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val playback = remember(stream) { ArtworkVideoPlayback(context, stream) }
    DisposableEffect(playback) { onDispose(playback::release) }
    // The layer stays the same while it turns from current to fading: read the latest rule.
    val currentActive by rememberUpdatedState(active)
    LaunchedEffect(playback) { playback.follow(master) { currentActive() } }
    val shown = playback.hasFrame && !playback.failed
    if (frames != null) {
        DisposableEffect(frames, playback, shown) {
            if (shown) frames.source = playback
            onDispose { if (frames.source === playback) frames.source = null }
        }
    }
    val reveal = animateFloatAsState(if (shown) 1f else 0f, tween(RevealMillis), label = "artworkVideo")
    val video = playback.player ?: return
    AndroidView(
        factory = { viewContext ->
            TextureView(viewContext).also { view ->
                playback.surface = view
                video.setVideoTextureView(view)
            }
        },
        onRelease = { view ->
            video.clearVideoTextureView(view)
            playback.detach(view)
        },
        modifier =
            modifier
                .centreCrop(stream.aspectRatio)
                .graphicsLayer { alpha = reveal.value * visibility() },
    )
}

/** Measures the content to cover the incoming box at [aspectRatio], centred (the box clips it). */
private fun Modifier.centreCrop(aspectRatio: Float): Modifier =
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

@OptIn(UnstableApi::class)
private fun mediaSource(stream: ArtworkVideoStream): ProgressiveMediaSource {
    val http = OkHttpDataSource.Factory(VideoHttp.client).setDefaultRequestProperties(stream.requestHeaders)
    // googlevideo throttles long open-ended reads: the same chunked requests as the audio.
    val chunked = DataSource.Factory { ChunkedDataSource(http.createDataSource(), MusicService.CHUNK_LENGTH) }
    return ProgressiveMediaSource.Factory(chunked).createMediaSource(MediaItem.fromUri(stream.url))
}

/** The thread frame copies for the live backdrop answer on. */
private object FrameCopies {
    val handler: Handler by lazy { Handler(HandlerThread("VibraVideoFrames").apply { start() }.looper) }
}

private object VideoHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .proxy(YouTube.streamOkHttpProxy)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

/** How long the track's own resolution is given to bring the video before asking for it. */
private const val RequestDelayMillis = 1_500L

/** How often the video is checked against the audio (events also trigger a check). */
private const val SyncIntervalMillis = 500L

/** Drift between video and audio tolerated before a seek; below it lips still read in sync. */
private const val DriftToleranceMillis = 250L

/** How far ahead of a playing audio a seek first lands, and at most (see align). */
private const val InitialSeekLeadMillis = 600L
private const val MaxSeekLeadMillis = 3_000L

private const val MinBufferMillis = 4_000
private const val MaxBufferMillis = 15_000
private const val StartBufferMillis = 500

private const val RevealMillis = 450
