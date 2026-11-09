/* Vibra (2026) - GPL-3.0. */
package app.vibra.playback

/** A contiguous projection that prioritizes the selected item and upcoming playback. */
internal fun boundedWindowRange(
    size: Int,
    current: Int,
    maxItems: Int,
    maxBytes: Int,
    overhead: Int,
    estimate: (Int) -> Int,
): IntRange {
    if (size == 0) return IntRange.EMPTY
    require(current in 0 until size && maxItems > 0)
    var first = current
    var last = current
    var bytes = overhead.toLong() + estimate(current).coerceAtLeast(0)
    // Preserve the existing Cast behavior for one individually oversized current item.
    if (bytes > maxBytes) return current..current
    while (last + 1 < size && last - first + 1 < maxItems) {
        val cost = estimate(last + 1).coerceAtLeast(0)
        if (bytes + cost > maxBytes) break
        bytes += cost
        last++
    }
    while (first > 0 && last - first + 1 < maxItems) {
        val cost = estimate(first - 1).coerceAtLeast(0)
        if (bytes + cost > maxBytes) break
        bytes += cost
        first--
    }
    return first..last
}
