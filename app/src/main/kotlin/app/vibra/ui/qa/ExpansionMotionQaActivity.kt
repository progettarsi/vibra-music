/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import androidx.navigation.compose.composable as qaComposable
import app.vibra.ui.components.*
import app.vibra.ui.motion.*
import app.vibra.ui.shell.*
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme

/** Fixed local artwork and production components, with the production page-transition wiring. */
class ExpansionMotionQaActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      ArchiveTuneTheme(
        darkTheme = true,
        disableAnimations = intent.getBooleanExtra("reduced", false),
      ) {
        val nav = rememberNavController()
        val motion = remember { PageMotion() }
        motion.window = LocalWindowInfo.current.containerSize
        motion.maxOriginCornerPx = 84f
        val activeSession = motion.expansion
        LaunchedEffect(activeSession) {
            if (activeSession != null) {
                android.util.Log.i("ExpansionQA", "session-start")
                repeat(120) { frame ->
                    withFrameNanos { }
                    if (frame == 4 || frame == 20) android.util.Log.i("ExpansionQA", "frame=$frame p=${activeSession.progress()} layer=${activeSession.layer?.size} pairs=${activeSession.parts.size}")
                    if (activeSession.parts.isNotEmpty()) {
                        android.util.Log.i("ExpansionQA", "paired=${activeSession.parts.size}")
                        return@LaunchedEffect
                    }
                }
            }
        }
        var offset by remember { mutableStateOf(Offset.Zero) }
        CompositionLocalProvider(LocalPageMotion provides motion) {
          Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onPlaced {
              offset = it.positionInWindow()
            }
          ) {
            NavHost(
              navController = nav,
              startDestination = "cards",
              enterTransition = {
                motion.forward(initialState, targetState)
                EnterTransition.None
              },
              exitTransition = {
                motion.forward(initialState, targetState)
                ExitTransition.None
              },
              popEnterTransition = {
                motion.pop(initialState, targetState, false)
                EnterTransition.None
              },
              popExitTransition = {
                motion.pop(initialState, targetState, false)
                ExitTransition.None
              },
              predictivePopEnterTransition = {
                motion.pop(initialState, targetState, true)
                EnterTransition.None
              },
              predictivePopExitTransition = {
                motion.pop(initialState, targetState, true)
                ExitTransition.None
              },
            ) {
              qaComposable("cards") { entry ->
                PageFrame(entry) {
                  Column(
                    Modifier.statusBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                  ) {
                    Text("Expansion QA", color = MaterialTheme.colorScheme.onSurface)
                    repeat(2) {
                      VibraCard(
                        onClick = { nav.navigate("detail") },
                        modifier = Modifier.width(220.dp),
                      ) {
                        QaCover(Modifier.size(220.dp), RoundedCornerShape(24.dp))
                        Text(
                          "Local album",
                          modifier =
                            Modifier.expansionElement("Local album", "title").padding(16.dp),
                          color = MaterialTheme.colorScheme.onSurface,
                        )
                      }
                    }
                  }
                }
              }
              qaComposable("detail") { entry ->
                PageFrame(entry) {
                  Column(
                    Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                  ) {
                    Button(onClick = { nav.popBackStack() }) { Text("Back to cards") }
                    QaCover(Modifier.size(300.dp), CircleShape)
                    Text(
                      "Local album",
                      style = MaterialTheme.typography.headlineLarge,
                      color = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.expansionElement("Local album", "title"),
                    )
                    Text("Fixed offline fixture", color = MaterialTheme.colorScheme.onSurface)
                  }
                }
              }
            }
            ExpansionOverlay(motion.expansion, { offset })
          }
        }
      }
    }
  }
}

@Composable
private fun QaCover(modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
  VibraArtwork("file:///android_asset/player-flight-cover.png", shape, modifier)
}
