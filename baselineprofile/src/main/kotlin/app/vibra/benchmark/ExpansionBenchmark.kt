/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run paired baseline/candidate APKs on the same device; do not screen-record measurements. */
@RunWith(AndroidJUnit4::class)
class ExpansionBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    @Test fun localCardRoundTrip() = rule.measureRepeated(
        packageName = "vibra.music.com.benchmark",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        iterations = 20,
        setupBlock = {
            startActivityAndWait(Intent().setClassName(packageName, "app.vibra.ui.ExpansionMotionQaActivity"))
        },
    ) {
        val card = device.wait(Until.findObject(By.text("Local album")), 5_000)
        checkNotNull(card).click()
        checkNotNull(device.wait(Until.findObject(By.text("Back to cards")), 5_000))
        device.waitForIdle()
        device.pressBack()
        checkNotNull(device.wait(Until.findObject(By.text("Expansion QA")), 5_000))
        device.waitForIdle()
    }
}
