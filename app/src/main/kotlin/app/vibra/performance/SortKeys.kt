/* Vibra (2026) — GPL-3.0. Derived from ArchiveTune © Rukamori. */
package app.vibra.performance

import java.text.Collator

/** The key is evaluated once per row, not twice for every comparator invocation. */
fun <T> List<T>.sortedByArtistKey(collator: Collator, key: (T) -> String): List<T> =
    map { it to key(it) }.sortedWith(compareBy(collator) { it.second }).map { it.first }
