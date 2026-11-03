/*
 * Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.ui.player

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Estrazione OFFLINE dei filmati ADB, mai invocata dal renderer dell'app. */
class FlightFrameExtractorActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val name = intent.getStringExtra("name") ?: "frames"
    require(name.matches(Regex("[a-zA-Z0-9_-]+")))
    lifecycleScope.launch(Dispatchers.IO) {
      val out = File(filesDir, "offline-$name").apply { mkdirs() }
      try {
        MediaMetadataRetriever().use { reader ->
          reader.setDataSource(File(filesDir, "qa-input.mp4").absolutePath)
          val count =
            reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)!!.toInt()
          for (i in 0 until count) {
            val frame = reader.getFrameAtIndex(i)!!
            val small =
              Bitmap.createScaledBitmap(
                frame,
                540,
                (frame.height * 540f / frame.width).toInt(),
                true,
              )
            File(out, "%05d.jpg".format(i)).outputStream().use {
              small.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            if (small !== frame) small.recycle()
            frame.recycle()
          }
          File(out, "done.json")
            .writeText(JSONObject().put("frames", count).put("mode", "offline-indexed").toString())
        }
      } catch (e: Exception) {
        File(out, "error.txt").writeText(e.javaClass.simpleName + ":" + e.message)
      }
      withContext(Dispatchers.Main) { finish() }
    }
  }
}
