/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.motion

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.CancellationException

/**
 * Like detectHorizontalDragGestures, but once the touch slop is crossed the finger belongs
 * to this drag alone: every move is consumed, the purely vertical ones too.
 *
 * The stock detector consumes only moves with a horizontal component, so a finger held still
 * mid-drag (it always wobbles a little vertically) fed a vertical draggable around it: the
 * player sheet started to close under a half-swiped cover and sprang back on release.
 */
suspend fun PointerInputScope.detectOwnedHorizontalDragGestures(
    onDragStart: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onHorizontalDrag: (change: PointerInputChange, dragAmount: Float) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    var overSlop = 0f
    val start =
        awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            overSlop = over
        } ?: return@awaitEachGesture
    onDragStart(start.position)
    onHorizontalDrag(start, overSlop)
    var pointer = start.id
    try {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointer }
            // Gone, or taken by someone before us: the drag is cancelled.
            if (change == null || change.isConsumed) {
                onDragCancel()
                return@awaitEachGesture
            }
            if (change.changedToUpIgnoreConsumed()) {
                // Another finger still down carries the drag on.
                val other = event.changes.firstOrNull { it.pressed }
                event.changes.forEach { it.consume() }
                if (other == null) {
                    onDragEnd()
                    return@awaitEachGesture
                }
                pointer = other.id
                continue
            }
            val dx = change.positionChange().x
            if (dx != 0f) onHorizontalDrag(change, dx)
            event.changes.forEach { it.consume() }
        }
    } catch (cancelled: CancellationException) {
        onDragCancel()
        throw cancelled
    }
}
