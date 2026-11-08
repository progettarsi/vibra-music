/* Vibra (2026) — local-only rounding fixtures. GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui

import android.app.Activity
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import app.vibra.ui.components.*
import app.vibra.ui.theme.*
import java.io.File
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.screens.settings.AppearanceSettings
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import org.json.JSONObject

/** Same production theme/components; radius forcing is confined to debug source sets. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RoundingQaTheme(
  darkTheme: Boolean = true,
  disableAnimations: Boolean = false,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val activity =
    generateSequence(context) { (it as? ContextWrapper)?.baseContext }
      .filterIsInstance<Activity>()
      .firstOrNull()
  val forced = activity?.intent?.getIntExtra("radius", -1) ?: -1
  ArchiveTuneTheme(darkTheme = darkTheme, disableAnimations = disableAnimations) {
    if (forced in 24..40) {
      val radii = remember(forced) { VibraRadii(forced.dp) }
      val shapes = remember(radii) { radii.materialShapes() }
      CompositionLocalProvider(LocalVibraRadii provides radii) {
        MaterialExpressiveTheme(
          colorScheme = MaterialTheme.colorScheme,
          typography = MaterialTheme.typography,
          motionScheme = MaterialTheme.motionScheme,
          shapes = shapes,
          content = content,
        )
      }
    } else content()
  }
}

class RoundingQaActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    setContent {
      RoundingQaTheme(
        darkTheme = !intent.getBooleanExtra("light", false),
        disableAnimations = intent.getBooleanExtra("reduced", false),
      ) {
        if (intent.getStringExtra("mode") == "appearance") {
          CompositionLocalProvider(
            moe.rukamori.archivetune.LocalPlayerAwareWindowInsets provides WindowInsets.systemBars
          ) {
            AppearanceSettings(rememberNavController())
          }
        } else RoundingGallery()
      }
    }
  }
}

@Composable
private fun RoundingGallery() {
  val context = LocalContext.current
  val density = LocalDensity.current
  val radii = LocalVibraRadii.current
  val window = LocalWindowCorners.current
  val measurements = remember { mutableMapOf<String, JSONObject>() }
  fun measured(name: String) =
    Modifier.onGloballyPositioned { coordinates ->
      val bounds = coordinates.boundsInRoot()
      measurements[name] =
        JSONObject()
          .put("left", bounds.left)
          .put("top", bounds.top)
          .put("right", bounds.right)
          .put("bottom", bounds.bottom)
    }
  LaunchedEffect(radii, density, window) {
    delay(1200)
    val result =
      JSONObject()
        .put("baseDp", radii.base.value)
        .put("rowDp", radii.row.value)
        .put("thumbnailDp", radii.rowThumbnail.value)
        .put("sheetDp", radii.sheet.value)
        .put("density", density.density)
        .put("fontScale", density.fontScale)
        .put("windowCorners", window.toString())
        .put("bounds", JSONObject(measurements.toMap()))
    File(context.filesDir, "rounding-geometry.json").writeText(result.toString(2))
  }
  var field by remember { mutableStateOf("VIBRA") }
  Column(
    Modifier.fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .safeDrawingPadding()
      .verticalScroll(rememberScrollState())
      .padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      "Rounding · ${radii.base.value.toInt()} dp",
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Surface(
      shape = VibraShapes.Card,
      color = MaterialTheme.colorScheme.surfaceContainer,
      modifier = measured("card").fillMaxWidth(),
    ) {
      Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
          measured("cardArtwork")
            .size(80.dp)
            .background(Color(0xFFEDAA66), VibraShapes.InnerArtwork)
        )
        Text("Card + artwork\n8 dp inset", Modifier.padding(top = 14.dp))
      }
    }
    VibraRow(
      title = "Standalone row",
      subtitle = { RowSubtitleText("8 dp inset") },
      modifier = measured("row").fillMaxWidth(),
      thumbnail = {
        Box(
          measured("rowArtwork")
            .fillMaxSize()
            .background(Color(0xFF8FCED0), VibraShapes.RowThumbnail)
        )
      },
    )
    Surface(shape = VibraShapes.Card, color = MaterialTheme.colorScheme.surfaceContainer) {
      Column(Modifier.padding(10.dp)) {
        VibraRow(
          title = "Nested row",
          isActive = true,
          containerInset = 10.dp,
          thumbnail = {
            Box(Modifier.fillMaxSize().background(Color(0xFFDEACD8), VibraShapes.RowThumbnail))
          },
        )
      }
    }
    OutlinedTextField(
      value = field,
      onValueChange = { field = it },
      shape = VibraFieldShape,
      label = { Text("Text field") },
      modifier = Modifier.fillMaxWidth(),
    )
    MediaMenuHeader(
      title = "Production menu header",
      subtitle = "Uniform 16 dp inset",
      artworkUrl = null,
      layout = MediaMenuLayout.Expanded,
      onPlay = null,
      artworkShape = VibraShapes.AlbumCover,
    )
    PreferenceGroup(title = "Production settings") {
      item { PreferenceEntry(title = { Text("First row") }) }
      item { PreferenceEntry(title = { Text("Last row") }) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = {}) { Text("Pill") }
      Surface(shape = VibraShapes.Cookie9, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Box(Modifier.size(48.dp))
      }
    }
  }
}
