/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.home

import kotlinx.coroutines.CancellationException

/** A cached value only stands in until real data arrives: it never counts as fresh and is never saved again. */
internal class CachedPlaceholder<T : Any> {
    @Volatile private var cached: T? = null

    fun mark(value: T): T = value.also { cached = it }

    /** The current value, unless it is still the one restored from the cache. */
    fun fresh(current: T?): T? = current?.takeUnless { it === cached }
}

/** Without a page, or with only the cached one, a returning connection must fetch the real feed. */
internal fun needsFreshFeed(hasPage: Boolean, showingCache: Boolean): Boolean = !hasPage || showingCache

/** Runs a step whose failure must not stop what follows, such as restoring an optional cache. */
internal suspend fun optionalStep(onFailure: (Exception) -> Unit, block: suspend () -> Unit) {
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        onFailure(failure)
    }
}
