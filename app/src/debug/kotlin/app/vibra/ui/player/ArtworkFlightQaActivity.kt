/*
 * Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.ui.player

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import app.vibra.ui.motion.VibraTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.vibra.ui.components.VibraPlayButton
import app.vibra.ui.dock.MiniPlayerGeometry
import app.vibra.ui.motion.*
import app.vibra.ui.theme.VibraDimens
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.playback.stream.ArtworkVideoStream
import org.json.JSONObject

/** Solo debug: geometria misurata sul player reale, stessi renderer e sorgente locale. */
@androidx.annotation.OptIn(UnstableApi::class)
class ArtworkFlightQaActivity : ComponentActivity() {
  private var command by mutableStateOf(Intent())
  private var serial by mutableIntStateOf(0)
  private lateinit var geometry: Intent

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    geometry = Intent(intent)
    command = intent
    setContent { MaterialTheme { Fixture(geometry, command, serial) } }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    command = intent
    serial++
  }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun Fixture(geometry: Intent, command: Intent, serial: Int) {
  val mode = geometry.getStringExtra("mode") ?: "ready"
  val context = LocalContext.current
  val density = LocalDensity.current
  val sheet = rememberLiquidSheetState(geometry.getFloatExtra("progress", 1f))
  val scope = rememberCoroutineScope()
  val current = remember { CurrentArtworkVideo() }
  val source = remember {
    { _: ArtworkVideoStream ->
      ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
        .createMediaSource(
          MediaItem.fromUri(
            if (mode == "fail") "asset:///missing-fixture.mp4"
            else "asset:///player-flight-local.mp4"
          )
        )
    }
  }
  val stream = remember {
    ArtworkVideoStream(
      "flight-fixture",
      "asset:///player-flight-local.mp4",
      emptyMap(),
      "video/mp4",
      1080,
      2400,
      Long.MAX_VALUE,
    )
  }
  val master = remember {
    ExoPlayer.Builder(context).build().apply {
      volume = 0f
      setMediaItem(MediaItem.Builder().setMediaId(stream.mediaId).setUri(stream.url).build())
      repeatMode = Player.REPEAT_MODE_ONE
      prepare()
      if (mode != "poses") play()
    }
  }
  DisposableEffect(master) { onDispose(master::release) }
  var playing by remember { mutableStateOf(mode != "poses") }
  val spin = rememberPlaybackSpin(playing)
  val flight = remember { PlayerArtworkFlight() }
  val frame = { flight.sample(sheet.progress, spin.degrees) }
  val content = rememberGraphicsLayer()
  val cover = remember {
    context.assets.open("player-flight-cover.png").use {
      BitmapFactory.decodeStream(it).asImageBitmap()
    }
  }
  val baker = rememberBackdropBaker()
  val url = "file:///android_asset/player-flight-cover.png"
  var offered by remember { mutableStateOf(mode != "late") }
  LaunchedEffect(mode) {
    if (mode == "late") {
      delay(2000)
      offered = true
    }
  }
  LaunchedEffect(serial) {
    when (command.getStringExtra("action")) {
      "open" -> sheet.expand()
      "close" -> sheet.collapse()
      else -> if (command.hasExtra("progress")) sheet.snapTo(command.getFloatExtra("progress", 1f))
    }
    if (command.hasExtra("progress")) {
      val target = command.getFloatExtra("progress", 1f)
      snapshotFlow { kotlin.math.abs(sheet.progress - target) < 0.00001f }.first { it }
      withFrameNanos {}
      withFrameNanos {}
      File(context.filesDir, "qa-pose-ready.json")
        .writeText(
          JSONObject()
            .put("token", command.getIntExtra("token", -1))
            .put("p", sheet.progress)
            .toString()
        )
    }
    delay(650)
    val f = frame()
    val v = current.playback?.player
    Log.i(
      "FlightQa",
      "CHECK p=${sheet.progress} video=${f.videoOpacity} id=${v?.let(System::identityHashCode)} pos=${v?.currentPosition} playing=${v?.isPlaying}",
    )
  }
  LaunchedEffect(current.playback) {
    val v = current.playback
    if (v != null)
      Log.i("FlightQa", "READY p=${sheet.progress} id=${v.player?.let(System::identityHashCode)}")
  }
  val radius = rememberScreenCornerRadius()
  CompositionLocalProvider(LocalPlaybackSpin provides spin) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Green)) {
      val viewport = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
      // Questi valori vengono letti dai bounds UI del player reale dal runner ADB.
      val pill =
        Rect(
          geometry.getFloatExtra("pillLeft", 224f),
          geometry.getFloatExtra("pillTop", 2137f),
          geometry.getFloatExtra("pillRight", 856f),
          geometry.getFloatExtra("pillBottom", 2284f),
        )
      val slot =
        Rect(
          0f,
          geometry.getFloatExtra("slotTop", 269f),
          viewport.width,
          geometry.getFloatExtra("slotTop", 269f) + viewport.width,
        )
      val cookie =
        with(density) {
          MiniPlayerGeometry.cookieIn(
            pill,
            MiniPlayerGeometry.CookieInset.toPx(),
            MiniPlayerGeometry.CookieSize.toPx(),
          )
        }
      val container = { playerContainerRect(pill, viewport, sheet.progress) }
      val shape = {
        PlayerContainerShape(
          container(),
          with(density) {
            androidx.compose.ui.unit.lerp(VibraDimens.DockHeight / 2, radius, sheet.progress).toPx()
          },
        )
      }
      LaunchedEffect(slot, viewport) {
        baker.bake(
          slot,
          IntSize(viewport.width.toInt(), viewport.height.toInt()),
          listOf(url),
          slot.bottom + slot.height * .06f,
        )
      }
      Box(
        Modifier.fillMaxSize()
          .graphicsLayer {
            clip = true
            this.shape = shape()
          }
          .background(Color.Magenta)
      ) {
        ArtworkFlightScene(slot, cookie, content, frame, container) {
          CoverExtension(url, baker, { 1f })
          LiveCoverExtension(
            current,
            content,
            url,
            slot,
            { 1f },
            videoMix = { frame().videoOpacity * (current.playback?.reveal?.value ?: 0f) },
          )
          Box(
            Modifier.layout { measurable, _ ->
                val child =
                  measurable.measure(Constraints.fixed(slot.width.toInt(), slot.height.toInt()))
                layout(child.width, child.height) {
                  child.place(slot.left.toInt(), slot.top.toInt())
                }
              }
              .drawWithContent {
                content.record(IntSize(slot.width.toInt(), slot.height.toInt())) {
                  this@drawWithContent.drawContent()
                }
              }
          ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
              drawImage(cover, dstSize = IntSize(slot.width.toInt(), slot.height.toInt()))
            }
            if (mode != "poses" && offered && sheet.isOpenOrOpening)
              ArtworkVideoSurface(
                stream,
                master,
                active = { frame().videoOpacity > .001f },
                visibility = { frame().videoOpacity },
                current = current,
                source = source,
                prepareWhen = { sheet.isOpenOrOpening },
              )
          }
          UnifiedCoverField(
            content,
            slot,
            slot.bottom + slot.height * .06f,
            rememberCoverCrop(url),
            { 1f },
          )
        }
        VibraPlayButton(
          playing,
          false,
          {},
          artworkUrl = url,
          size = MiniPlayerGeometry.CookieSize,
          modifier =
            Modifier.offset { IntOffset(cookie.left.toInt(), cookie.top.toInt()) }
              .graphicsLayer { alpha = if (artworkInMiniPlayer(sheet.progress)) 1f else 0f },
        )
      }
      if (mode != "poses")
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp)) {
          Row {
            for (p in listOf(0f, .25f, .5f, .75f, 1f)) TextButton(onClick = { sheet.snapTo(p) }) {
              Text("${(p*100).toInt()}%")
            }
          }
          Row {
            TextButton(onClick = { sheet.collapse() }) { Text("Close") }
            TextButton(onClick = { sheet.expand() }) { Text("Open") }
            TextButton(
              onClick = {
                playing = !playing
                master.playWhenReady = playing
              }
            ) {
              Text("Play/pause")
            }
          }
        }
    }
  }
}
