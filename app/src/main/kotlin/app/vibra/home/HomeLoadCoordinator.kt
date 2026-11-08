/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Equivalent requests share work; a new account/context cancels the previous request. */
internal class HomeLoadCoordinator(private val scope: CoroutineScope) {
    private val lock = Mutex()
    private var key: String? = null
    private var request: Deferred<Unit>? = null

    suspend fun run(context: String, block: suspend () -> Unit) {
        val shared = lock.withLock {
            val active = request
            if (key == context && active != null && !active.isCompleted) active else {
                active?.cancel()
                key = context
                scope.async { block() }.also { request = it }
            }
        }
        shared.await()
    }
}
