/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.library

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Playlists pinned to the top of the library, in their pinned order (one id per line). A plain
 * preference: pinning is a view choice, not playlist data, so the database is left as it is.
 */
val PinnedPlaylistsKey = stringPreferencesKey("vibraPinnedPlaylists")

fun decodePinnedPlaylists(raw: String): List<String> = raw.lines().filter(String::isNotBlank).distinct()

fun encodePinnedPlaylists(ids: List<String>): String = ids.distinct().joinToString("\n")

/** [id] pinned (at the top of the pinned ones) or, if it already was, unpinned. */
fun togglePinnedPlaylist(pinned: List<String>, id: String): List<String> = if (id in pinned) pinned - id else listOf(id) + pinned

/** The pinned items first, in their pinned order, then the others in their current order. */
fun <T> List<T>.pinnedFirst(
    pinned: List<String>,
    id: (T) -> String,
): List<T> {
    if (pinned.isEmpty()) return this
    val rank = pinned.withIndex().associate { (index, value) -> value to index }
    val (top, rest) = partition { id(it) in rank }
    return top.sortedBy { rank.getValue(id(it)) } + rest
}

/** Pinned ids after a drag reordered the visible list [visibleOrder]; ids not shown keep their place at the end. */
fun reorderedPinned(
    pinned: List<String>,
    visibleOrder: List<String>,
): List<String> {
    val shown = visibleOrder.filter { it in pinned }
    return shown + pinned.filter { it !in shown }
}
