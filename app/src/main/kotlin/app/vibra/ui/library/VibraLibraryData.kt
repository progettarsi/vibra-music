/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vibra.ui.profile.StatsPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import moe.rukamori.archivetune.db.MusicDatabase
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  PAGE DATA — the library and listening rows behind the profile, library and stats
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  These pages used to read the database straight from composition. A page leaves
 *  composition when another one opens on top of it, so coming back restarted every flow
 *  empty: for a few frames the list had only its first rows, Compose clamped the restored
 *  scroll position to the top, and the rows (and the position) came back a moment later —
 *  the page jumped up and then down again in the middle of the back transition.
 *
 *  A ViewModel lives as long as its page is in the back stack and its StateFlows keep the
 *  last rows, so a page that comes back is complete in its very first frame and its list
 *  restores its own position. (Room keeps them current while the page is shown.)
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/** Library and this-week listening for the profile and library pages. */
@HiltViewModel
class VibraLibraryViewModel
    @Inject
    constructor(
        database: MusicDatabase,
    ) : ViewModel() {
        private val weekAgo = daysAgoMillis(7)

        val playlists = database.playlistsByUpdatedDateAsc().retainIn(this, emptyList())
        val likedSongs = database.likedSongsByCreateDateAsc().retainIn(this, emptyList())
        val likedCount = database.likedSongsCount().retainIn(this, 0)
        val onRepeat = database.mostPlayedSongs(weekAgo, limit = 8).retainIn(this, emptyList())
        val albums = database.albumsByPlayTimeAsc().retainIn(this, emptyList())
        val artists = database.artistsByPlayTimeAsc().retainIn(this, emptyList())

        /** The week's most played artist (a list of at most one). */
        val weekTopArtist = database.mostPlayedArtists(weekAgo, limit = 1).retainIn(this, emptyList())
        val weekSongs = database.mostPlayedSongsStats(weekAgo, limit = 500).retainIn(this, emptyList())
    }

/** Listening stats for the selected [StatsPeriod]. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VibraStatsViewModel
    @Inject
    constructor(
        database: MusicDatabase,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        val period: StateFlow<StatsPeriod> = savedState.getStateFlow(PeriodKey, StatsPeriod.Week)

        private val from = period.map { it.days?.let(::daysAgoMillis) ?: 0L }

        val artists = from.flatMapLatest { database.mostPlayedArtists(it, limit = 8) }.retainIn(this, emptyList())
        val songs = from.flatMapLatest { database.mostPlayedSongs(it, limit = 10) }.retainIn(this, emptyList())
        val songStats = from.flatMapLatest { database.mostPlayedSongsStats(it, limit = 1000) }.retainIn(this, emptyList())
        val albums = from.flatMapLatest { database.mostPlayedAlbums(it, limit = 6) }.retainIn(this, emptyList())

        fun select(period: StatsPeriod) {
            savedState[PeriodKey] = period
        }

        private companion object {
            const val PeriodKey = "period"
        }
    }

/** The engine stores event timestamps as LocalDateTime-at-UTC epoch millis. */
private fun daysAgoMillis(days: Long): Long = LocalDateTime.now().minusDays(days).toInstant(ZoneOffset.UTC).toEpochMilli()

/**
 * Keeps the flow's latest value for as long as [owner] lives; the database is only
 * queried while someone collects (plus a grace period across quick round trips).
 */
private fun <T> Flow<T>.retainIn(
    owner: ViewModel,
    initial: T,
): StateFlow<T> = stateIn(owner.viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), initial)

private const val StopTimeoutMillis = 5_000L
