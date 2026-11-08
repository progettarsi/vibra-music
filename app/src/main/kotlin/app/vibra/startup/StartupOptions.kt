/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.startup

import android.content.Context
import java.io.File
import moe.rukamori.archivetune.BuildConfig

/** Private benchmark controls let every A/B run use the exact same compiled APK. */
object StartupOptions {
    private var experiment: String? = null
    val label: String get() = experiment ?: "build_default"
    val progressive: Boolean get() = experiment?.let { it != "baseline" } ?: BuildConfig.STARTUP_PROGRESSIVE
    val cache: Boolean get() = experiment?.let { it == "cache" || it == "combined" } ?: BuildConfig.STARTUP_CACHE
    val deferred: Boolean get() = experiment?.let { it == "deferred" || it == "combined" } ?: BuildConfig.STARTUP_DEFERRED

    fun configure(context: Context) {
        // No file read or runtime override is available in production builds.
        if (BuildConfig.BUILD_TYPE != "benchmark" && BuildConfig.BUILD_TYPE != "nonMinifiedBenchmark") return
        val file = File(context.filesDir, "startup-experiment")
        experiment = runCatching {
            if (file.length() in 1..32) file.readText().trim() else null
        }.getOrNull()?.takeIf { it in setOf("baseline", "progressive", "cache", "deferred", "combined") }
    }
}
