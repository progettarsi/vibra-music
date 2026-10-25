/*
 * Vibra (2026) - GPL-3.0 License.
 * Derived from ArchiveTune c Rukamori (github.com/rukamori).
 */
package app.vibra.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TargetPackage = "vibra.music.com.benchmark"

/** Prerequisite: complete optional onboarding and seed a playable queue in the benchmark app. */
private fun MacrobenchmarkScope.homePlayerSearchJourney() {
    pressHome()
    startActivityAndWait()
    val player = device.wait(Until.findObject(By.desc(Pattern.compile("Open player|Apri il player"))), 15_000)
    checkNotNull(player) { "Seed the benchmark app queue and finish onboarding before collecting profiles" }
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(width / 2, height * 3 / 4, width / 2, height / 3, 30)
    device.waitForIdle()
    player.click()
    device.waitForIdle()
    device.pressBack()
    val search = device.wait(Until.findObject(By.desc(Pattern.compile("Search|Cerca"))), 10_000)
    checkNotNull(search) { "Search action was not reachable after closing the player" }
    search.click()
    val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000)
    checkNotNull(field) { "Search field did not open" }
    field.text = "Miles Davis"
    device.pressEnter()
    device.waitForIdle()
}

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun startupHomePlayerSearch() {
        assumeTrue(BuildConfig.GENERATE_PROFILE)
        rule.collect(packageName = TargetPackage, includeInStartupProfile = false) {
            homePlayerSearchJourney()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun coldWithoutProfile() = measure(CompilationMode.None())
    @Test fun coldWithProfile() = measure(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measure(compilation: CompilationMode) {
        assumeFalse(BuildConfig.GENERATE_PROFILE)
        rule.measureRepeated(
            packageName = TargetPackage,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.COLD,
            iterations = 10,
            setupBlock = { pressHome() },
        ) { startActivityAndWait() }
    }
}
