/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.startup

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.OnboardingCompletedKey
import moe.rukamori.archivetune.utils.dataStore

/** Shell-only setup for an isolated benchmark package, including unrooted physical phones. */
class StartupBenchmarkSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Preparing benchmark" })
        val experiment = intent.getStringExtra("experiment") ?: "combined"
        val request = intent.getIntExtra("request", 0)
        if (experiment !in setOf("baseline", "progressive", "cache", "deferred", "combined")) {
            finish()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                File(filesDir, "startup-experiment").writeText(experiment)
                if (intent.getBooleanExtra("bootstrap", false)) {
                    dataStore.edit { it[OnboardingCompletedKey] = true }
                    val tone = File(filesDir, "startup-tone.wav")
                    if (!tone.exists()) writeTone(tone)
                }
                Log.i("StartupFixture", "ready experiment=$experiment request=$request")
            } catch (error: Exception) {
                Log.e("StartupFixture", "setup_failed request=$request type=${error.javaClass.simpleName}")
            } finally {
                withContext(Dispatchers.Main) { finish() }
            }
        }
    }

    private fun writeTone(file: File) {
        val rate = 22_050
        val size = rate * 60 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(size + 36).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(size).array()
        val period = ByteBuffer.allocate(2_205 * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(2_205) { i -> period.putShort((1_200 * kotlin.math.sin(2 * Math.PI * 440 * i / rate)).toInt().toShort()) }
        file.outputStream().use { output ->
            output.write(header)
            repeat(600) { output.write(period.array()) }
        }
    }
}
