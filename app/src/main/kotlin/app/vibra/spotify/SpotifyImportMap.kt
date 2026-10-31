/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.spotify

import androidx.room.Entity

/** Persistent provenance; source IDs are namespaced by the Spotify account. */
@Entity(tableName = "spotify_import", primaryKeys = ["accountId", "kind", "sourceId"])
data class SpotifyImportMap(
    val accountId: String,
    val kind: String,
    val sourceId: String,
    val localId: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
