/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.home.HomeUiState
import moe.rukamori.archivetune.network.NetworkMonitor
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.SongItem
import androidx.media3.exoplayer.offline.Download

/** Random bag survives UI recomposition and returning from another page. */
internal class ForYouBag {
    private val played = mutableSetOf<String>()
    private var last: String? = null
    fun choose(ids: List<String>, current: String?, first: String?): String? {
        val all = ids.distinct()
        if (all.isEmpty()) return null
        var eligible = all.filterNot { it == first || it == current || it == last }
        if (eligible.isEmpty()) eligible = all.filterNot { it == current || it == last }
        if (eligible.isEmpty()) eligible = all
        played.retainAll(all.toSet())
        var fresh = eligible.filterNot { it in played }
        if (fresh.isEmpty()) { played.clear(); fresh = eligible }
        return fresh.random().also { played += it; last = it }
    }
}

@HiltViewModel
class ForYouViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val downloads: DownloadUtil,
    network: NetworkMonitor,
) : ViewModel() {
    val online = network.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val hasDownloads = downloads.downloads.map { rows -> rows.values.any { it.state == Download.STATE_COMPLETED } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val bag = ForYouBag()
    private var selecting = false

    fun play(ui: HomeUiState?, player: PlayerConnection?, unavailable: () -> Unit) {
        if (player == null || selecting) return
        selecting = true
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val offline = !online.value
                val candidates = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val blocked = database.getBlockedArtistIds().toSet()
                    if (offline) {
                        val completed = downloads.downloads.value.filterValues { it.state == Download.STATE_COMPLETED }.keys
                        database.downloadedSongsList().filter { it.id in completed && !it.song.isPodcast && it.artists.none { a -> a.id in blocked } }.map { it.toMediaMetadata() }
                    } else {
                        val local = ui?.quickPicks.orEmpty().filter { !it.song.isPodcast && it.artists.none { a -> a.id in blocked } }.map { it.toMediaMetadata() }
                        val remote = (ui?.remoteQuickPicks?.items.orEmpty() + ui?.homePage?.sections.orEmpty().flatMap { it.items } + ui?.similarRecommendations.orEmpty().flatMap { it.items })
                            .filterIsInstance<SongItem>().filter { it.artists.none { a -> a.id in blocked } }.map { it.toMediaMetadata() }
                        (local + remote).distinctBy { it.id }.ifEmpty {
                            val completed = downloads.downloads.value.filterValues { it.state == Download.STATE_COMPLETED }.keys
                            database.downloadedSongsList().filter { it.id in completed && !it.song.isPodcast && it.artists.none { a -> a.id in blocked } }.map { it.toMediaMetadata() }
                        }
                    }
                }
                val first = ui?.quickPicks?.firstOrNull()?.id ?: (ui?.remoteQuickPicks?.items?.filterIsInstance<SongItem>()?.firstOrNull()?.id)
                val chosen = bag.choose(candidates.map { it.id }, player.mediaMetadata.value?.id, first)
                if (chosen == null) unavailable() else {
                    val ordered = candidates.shuffled().sortedBy { if (it.id == chosen) 0 else 1 }
                    player.playQueue(ListQueue(title = "For You", items = ordered.map { it.toMediaItem() }))
                }
            } finally { selecting = false }
        }
    }
}
