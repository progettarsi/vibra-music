/* Vibra (2026) — GPL-3.0. */
package app.vibra.performance

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Bounded reusable results; a shared computation lives only while at least one reader needs it. */
internal class SharedResultCache<K : Any, V : Any>(
    private val capacity: Int,
    parallelism: Int,
    private val scope: CoroutineScope,
) {
    private val slots = Semaphore(parallelism)
    private val values = LinkedHashMap<K, V>(capacity, 0.75f, true)

    private class Pending<V>(val work: Deferred<V?>, val generation: Long, var readers: Int = 0)

    private val pending = mutableMapOf<K, Pending<V>>()
    private var generation = 0L

    suspend fun get(key: K, compute: suspend () -> V?): V? {
        val request =
            synchronized(this) {
                values[key]?.let {
                    return it
                }
                pending
                    .getOrPut(key) {
                        Pending(
                            scope.async(start = CoroutineStart.LAZY) {
                                slots.withPermit { compute() }
                            },
                            generation,
                        )
                    }
                    .also { it.readers++ }
            }
        try {
            return request.work.await()?.also { result ->
                synchronized(this) {
                    if (request.generation == generation) {
                        values[key] = result
                        if (values.size > capacity) values.remove(values.keys.first())
                    }
                }
            }
        } finally {
            synchronized(this) {
                request.readers--
                if (request.readers == 0) {
                    request.work.cancel()
                    if (pending[key] === request) pending.remove(key)
                }
            }
        }
    }

    @Synchronized
    fun clear() {
        generation++
        values.clear()
    }
}
