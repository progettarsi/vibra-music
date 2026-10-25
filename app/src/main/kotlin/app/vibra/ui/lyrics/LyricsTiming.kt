/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.lyrics

/*
 * The pure rules behind the synced lyrics (no Compose, unit-tested): which line is being
 * sung, how bright and how soft each line is, how far a word is sung, and how the lines
 * below the active one queue up when the text moves.
 */

/** Index of the last line that has started at [position] ([starts] ascending), or -1 before the first. */
internal fun lineAt(
    starts: IntArray,
    position: Int,
): Int {
    var low = 0
    var high = starts.lastIndex
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (starts[mid] <= position) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

/**
 * The active line after [current] when the clock reads [position]: the line at [position],
 * except that a step back of less than [BackwardToleranceMs] is ignored. The playback clock
 * re-aligns with the player by a few milliseconds now and then; taking those steps would
 * switch a line that has just lit up off and on again.
 */
internal fun followLine(
    starts: IntArray,
    current: Int,
    position: Int,
): Int {
    val at = lineAt(starts, position)
    if (at >= current) return at
    return if (starts[current] - position < BackwardToleranceMs) current else at
}

/** How far a word spanning [start]‥[end] is sung at [position]: 0 before, 1 after, eased in between. */
internal fun wordFill(
    start: Int,
    end: Int,
    position: Int,
): Float {
    val span = (end - start).coerceAtLeast(1)
    val fill = ((position - start).toFloat() / span).coerceIn(0f, 1f)
    return fill * fill * (3f - 2f * fill)
}

/**
 * Brightness of a line by its distance from the active one. The active line's words that
 * are not sung yet keep the level the line had as the next one (distance 1), so a line that
 * becomes active never brightens or dims at once: only its words light up.
 */
internal fun lineDim(distance: Int): Float =
    when (distance) {
        0, 1 -> 0.55f
        2 -> 0.45f
        else -> 0.36f
    }

/** Blur (dp) of a line by its distance from the active one: the lines far away go soft. */
internal fun lineBlurDp(distance: Int): Float =
    when (distance) {
        0 -> 0f
        1 -> 1.2f
        2 -> 2.4f
        else -> 3.6f
    }

/** Distance of line [index] from the [active] one; before the first line, line 0 is the next one. */
internal fun lineDistance(
    index: Int,
    active: Int,
): Int = if (active < 0) index + 1 else kotlin.math.abs(index - active)

/**
 * How long line [index] waits before following a move towards line [target]. Moving
 * [forward] (the text goes up) the lines below the target go one after another; moving back
 * the lines above it do — always the side the text is pulled away from, so lines open up
 * and close again but never run into each other.
 */
internal fun staggerMillis(
    index: Int,
    target: Int,
    forward: Boolean,
): Long {
    val behind = if (forward) index - target else target - index
    return if (behind <= 0) 0L else minOf(behind, MaxStagger) * StaggerStepMillis
}

private const val MaxStagger = 6
private const val StaggerStepMillis = 45L

/** Longest wait of [staggerMillis]. */
internal const val MaxStaggerMillis = MaxStagger * StaggerStepMillis

internal const val BackwardToleranceMs = 250
