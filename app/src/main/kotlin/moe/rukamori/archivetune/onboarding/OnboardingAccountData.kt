/*
 * Vibra (2026) — GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package moe.rukamori.archivetune.onboarding

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.spotify.ImportedArtist
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject

/** Public profile data; cookies and tokens stay in the session handler. */
data class OnboardingAccountProfile(val name: String, val avatarUrl: String?)

/** Screen-independent API, usable from any ViewModel. */
class OnboardingAccountData @Inject constructor(@ApplicationContext private val context: Context) {
    private suspend fun loggedIn() = hasYouTubeLoginCookie(context.dataStore.data.first()[InnerTubeCookieKey].orEmpty())

    /** Uses the same browse and all continuations as the subscriptions sync. */
    suspend fun youTubeLibraryArtists(): List<ImportedArtist> = withContext(Dispatchers.IO) {
        if (!loggedIn()) return@withContext emptyList()
        YouTube.library("FEmusic_library_corpus_artists").completed().getOrThrow().items
            .filterIsInstance<ArtistItem>().distinctBy { it.id }
            .map { ImportedArtist(it.id, it.title, it.thumbnail) }
    }

    /** Same accountInfo AccountSettings uses; no identity is inferred from the cookie. */
    suspend fun accountProfile(): OnboardingAccountProfile? = withContext(Dispatchers.IO) {
        if (!loggedIn()) return@withContext null
        val info = YouTube.accountInfo().getOrThrow()
        OnboardingAccountProfile(info.name, info.thumbnailUrl)
    }
}
