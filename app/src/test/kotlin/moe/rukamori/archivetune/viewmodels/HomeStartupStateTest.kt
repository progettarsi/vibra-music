/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package moe.rukamori.archivetune.viewmodels

import moe.rukamori.archivetune.home.*
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.db.entities.*
import org.junit.Assert.*
import org.junit.Test

class HomeStartupStateTest {
    private fun state(content: Boolean, complete: Boolean, loading: Boolean, error: Int? = null): HomeScreenState {
        val local = HomeLocalContent(if (content) listOf(Song(SongEntity("id", "Song"), emptyList())) else emptyList(), emptyList(), emptyList(), emptyList())
        val remote = HomeRemoteContent(null, null, null, emptyList(), emptyList(), "", null)
        return HomeStateInputs(HomeContent(local, remote, null), HomePresentationPreferences(
            false, QuickPicksDisplayMode.CARD, QuickPicks.QUICK_PICKS, true), loading, complete, error).toScreenState(false, false)
    }

    @Test fun localContentIsUsableWhileRemoteWorkIsStillLoading() {
        assertTrue(state(true, false, true) is HomeScreenState.Success)
    }

    @Test fun failedRefreshKeepsUsableContent() {
        assertTrue(state(true, true, false, 1) is HomeScreenState.Success)
    }

    @Test fun emptyLoadingErrorAndEmptyCompletionRemainDistinct() {
        assertEquals(HomeScreenState.Loading, state(false, false, true))
        assertTrue(state(false, true, false, 1) is HomeScreenState.Error)
        assertEquals(HomeScreenState.Empty, state(false, true, false))
    }
}
