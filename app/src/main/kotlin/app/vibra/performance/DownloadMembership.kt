/* Vibra (2026) — GPL-3.0. Derived from ArchiveTune © Rukamori. */
package app.vibra.performance

import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Progress bytes never invalidate library membership or its modification-date order. */
fun Flow<Map<String, Download>>.completedMembership(): Flow<Map<String, Long>> =
    map { downloads ->
            downloads
                .filterValues { it.state == Download.STATE_COMPLETED }
                .mapValues { it.value.updateTimeMs }
        }
        .distinctUntilChanged()
