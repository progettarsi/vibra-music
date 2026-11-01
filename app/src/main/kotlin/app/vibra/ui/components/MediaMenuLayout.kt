/*
 * Vibra (2026) — media menu presentation preferences.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import moe.rukamori.archivetune.utils.rememberPreference

val CompactMediaMenusKey = booleanPreferencesKey("compactMediaMenus")
enum class MediaMenuLayout { Expanded, Compact }

@Composable
fun rememberMediaMenuLayout(): MediaMenuLayout {
    val compact by rememberPreference(CompactMediaMenusKey, false)
    return if (compact) MediaMenuLayout.Compact else MediaMenuLayout.Expanded
}
