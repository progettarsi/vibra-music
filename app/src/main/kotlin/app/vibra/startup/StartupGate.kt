/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.startup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Optional work yields until content is rendered, with a bounded service-only fallback. */
internal class StartupGate(private val timeoutMs: Long = 5_000) {
    private val frame = CompletableDeferred<Unit>()
    private val content = CompletableDeferred<Unit>()
    private val work = Mutex()
    fun firstFrame() = frame.complete(Unit)
    fun homeVisible() = content.complete(Unit)

    suspend fun run(block: suspend () -> Unit) {
        withTimeoutOrNull(timeoutMs) { frame.await(); content.await() }
        work.withLock { block() }
    }
}
