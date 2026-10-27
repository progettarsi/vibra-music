/*
 * Vibra (2026) — GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package moe.rukamori.archivetune.downloads

/** An empty playlist never implies consent to download future songs. */
internal fun shouldAutoDownloadPlaylist(existingIds: List<String>, completedIds: Set<String>): Boolean =
    existingIds.isNotEmpty() && existingIds.all { it in completedIds }

/** Snapshot taken before the change; a batch stays valid while its downloads start. */
data class PlaylistDownloadSnapshot(val existingIds: Set<String>, val fullyDownloaded: Boolean)
