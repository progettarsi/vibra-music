/*
 * Vibra (2026) — debug-only exercise of the real playback service and final-error dialog.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.playback

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.player.PlaybackErrorDialog
import timber.log.Timber

/** No arbitrary URLs, credentials, account changes or release entry point. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackFallbackQaActivity : ComponentActivity() {
  private var service by mutableStateOf<MusicService?>(null)
  private var observation: Job? = null
  private var scenarioJob: Job? = null
  private var visibleErrors by mutableIntStateOf(0)
  private var connection: PlayerConnection? = null
  private val binding =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        val music = (binder as MusicService.MusicBinder).service
        service = music
        connection =
          PlayerConnection(this@PlaybackFallbackQaActivity, binder, music.database, lifecycleScope)
        observation =
          lifecycleScope.launch {
            var lastErrorAttempt = -1L
            connection!!.playbackAttempt.collect { state ->
              if (state.error != null && state.id != lastErrorAttempt) {
                visibleErrors++
                lastErrorAttempt = state.id
              }
              Timber.tag(TAG)
                .i(
                  "attempt=${state.id} stage=${state.stage} phase=${state.phase} visibleErrors=$visibleErrors"
                )
            }
          }
        runScenario(intent)
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        service = null
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    bindService(Intent(this, MusicService::class.java), binding, BIND_AUTO_CREATE)
    setContent {
      MaterialTheme {
        Column(Modifier.padding(24.dp)) {
          Text("Playback fallback QA")
          Text("Visible errors: $visibleErrors")
          service?.let { music ->
            val state by music.playbackAttempts.state.collectAsState()
            Text("${state.phase} / ${state.stage}")
            if (state.isLoading) CircularProgressIndicator()
            state.error?.exception?.let { error ->
              PlaybackErrorDialog(
                error,
                false,
                music::retryPlayback,
                music.playbackAttempts::dismiss,
                {},
                onNext = if (music.player.hasNextMediaItem()) connection!!::seekToNext else null,
              )
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    runScenario(intent)
  }

  private fun runScenario(command: Intent) {
    val music = service ?: return
    scenarioJob?.cancel()
    scenarioJob =
      lifecycleScope.launch {
        music.queueRestoreCompleted.first { it }
        val scenario = command.getStringExtra("scenario") ?: "natural"
        if (scenario == "clear_faults") {
          music.playbackAttempts.configureDebugFailures(emptySet())
          return@launch
        }
        if (scenario in setOf("pause", "resume", "seek", "retry", "handoff")) {
          when (scenario) {
            "pause" -> music.player.pause()
            "resume" -> music.player.play()
            "seek" -> music.player.seekTo(command.getLongExtra("position", 90_000L))
            "retry" -> music.retryPlayback()
            "handoff" -> {
              music.player.stop()
              music.onPlayerError(
                androidx.media3.common.PlaybackException(
                  "Simulated interrupted stream",
                  null,
                  androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                )
              )
            }
          }
          return@launch
        }
        visibleErrors = 0
        music.playbackAttempts.configureDebugFailures(
          when (scenario) {
            "recover",
            "timeout" ->
              setOf(PlaybackStage.DIRECT, PlaybackStage.REFRESHED, PlaybackStage.ANONYMOUS)
            "recover_piped" ->
              setOf(
                PlaybackStage.DIRECT,
                PlaybackStage.REFRESHED,
                PlaybackStage.ANONYMOUS,
                PlaybackStage.INVIDIOUS,
              )
            "all_fail" -> PlaybackStage.entries.toSet()
            else -> emptySet()
          },
          timeout = scenario == "timeout",
        )
        val tracks =
          if (scenario == "failure")
            listOf(MediaMetadata("___________", "Unavailable QA track", emptyList(), 60))
          else
            listOf(
              MediaMetadata("dQw4w9WgXcQ", "Never Gonna Give You Up", emptyList(), 213),
              MediaMetadata("kJQP7kiw5Fk", "Despacito", emptyList(), 282),
              MediaMetadata("JE7OMw7yqjg", "Habibi", emptyList(), 185),
              MediaMetadata("nfWlot6h_JM", "Shake It Off", emptyList(), 242),
            )
        music.player.stop()
        music.player.setMediaItems(tracks.map { it.toMediaItem() })
        music.player.shuffleModeEnabled = false
        music.bypassPlaybackCacheForDebug(tracks.map { it.id })
        Timber.tag(TAG).i("scenario=$scenario START networkOnly=true")
        // Force a provider only in this debug fixture. All I/O still uses the production resolver.
        if (scenario == "invidious" || scenario == "piped") {
          val target = if (scenario == "invidious") PlaybackStage.INVIDIOUS else PlaybackStage.PIPED
          val attempt = music.playbackAttempts.current(tracks.first().id)!!
          while (music.playbackAttempts.state.value.stage != target) {
            music.playbackAttempts.advance(
              attempt.id,
              tracks.first().id,
              false,
              android.os.SystemClock.elapsedRealtime(),
            )
          }
        }
        music.player.repeatMode =
          if (scenario == "soak") Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        music.player.prepare()
        music.player.play()
        if (scenario == "switch") {
          delay(300)
          music.player.seekToNextMediaItem()
          music.player.prepare()
          music.player.play()
        }
        repeat(if (scenario == "soak") 60 else 3) { minute ->
          delay(60_000)
          Timber.tag(TAG)
            .i(
              "minute=${minute + 1} state=${music.player.playbackState} playing=${music.player.isPlaying} " +
                "position=${music.player.currentPosition} index=${music.player.currentMediaItemIndex} visibleErrors=$visibleErrors"
            )
        }
        Timber.tag(TAG).i("scenario=$scenario COMPLETE visibleErrors=$visibleErrors")
      }
  }

  override fun onDestroy() {
    observation?.cancel()
    scenarioJob?.cancel()
    connection?.dispose()
    unbindService(binding)
    super.onDestroy()
  }

  companion object {
    private const val TAG = "PlaybackFallbackQA"
  }
}
