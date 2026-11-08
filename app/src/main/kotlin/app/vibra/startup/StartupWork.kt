/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.startup

import android.os.SystemClock
import android.os.Trace
import timber.log.Timber

/** Process-wide startup milestones. Only optional work waits; playback never waits here. */
object StartupWork {
    private val started = SystemClock.elapsedRealtime()
    private val gate = StartupGate()

    fun firstFrame() {
        if (gate.firstFrame()) mark("first_frame")
    }

    fun homeVisible(source: String) {
        if (gate.homeVisible()) mark("home_visible_$source")
    }

    fun mark(name: String) {
        Trace.beginSection("Vibra.$name")
        Trace.endSection()
        Timber.tag("StartupMetrics").i("%s elapsedMs=%d", name, SystemClock.elapsedRealtime() - started)
    }

    suspend fun <T> trace(name: String, block: suspend () -> T): T {
        val start = SystemClock.elapsedRealtime()
        mark("${name}_start")
        return try { block() } finally {
            Timber.tag("StartupMetrics").i("%s durationMs=%d", name, SystemClock.elapsedRealtime() - start)
            mark("${name}_end")
        }
    }

    suspend fun optional(name: String, block: suspend () -> Unit) {
        gate.run { trace(name, block) }
    }
}
