/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  ONBOARDING — state of the first-run setup (UI in VibraOnboarding.kt)
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  Welcome → Account → [Artists] → Style → Ready
 *
 *   • Account: YouTube Music and Spotify side by side, both optional, both connectable (it
 *     is not a choice). YouTube Music fills name and picture by itself; Spotify brings its
 *     artists and copies its playlists into the library. With both connected the step moves
 *     on by itself.
 *   • Artists: only after a sign-in or an import, and skippable — it is the account part.
 *   • Style: always shown (colour and shape), but the defaults are already selected, so
 *     "Continue" alone is a valid choice.
 *
 *  The state lives in an activity-scoped ViewModel because the sign-in screens replace the
 *  onboarding while they are open: when they close, the onboarding resumes where it was.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/** The onboarding's screens, in order. */
enum class OnboardingStep { Welcome, Account, Artists, Style, Ready }

/** An artist offered in the Artists step. */
@Immutable
data class OnboardingArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
)

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    /** True when the last move went backwards (the screens slide the other way). */
    val back: Boolean = false,
    val youTubeConnected: Boolean = false,
    val spotifyConnected: Boolean = false,
    val loadingYouTube: Boolean = false,
    val loadingSpotify: Boolean = false,
    val accountName: String? = null,
    val accountAvatarUrl: String? = null,
    val artists: List<OnboardingArtist> = emptyList(),
    val selectedArtists: Set<String> = emptySet(),
) {
    val anyConnected: Boolean get() = youTubeConnected || spotifyConnected

    val loadingAccount: Boolean get() = loadingYouTube || loadingSpotify

    /** Screens this run will show (Artists only with an account), for the progress bar. */
    val steps: List<OnboardingStep>
        get() = OnboardingStep.entries.filter { it != OnboardingStep.Artists || anyConnected }
}

/**
 * Account data the onboarding reads: YouTube Music profile and artists, Spotify artists,
 * following. Implemented over the engine by [EngineOnboardingAccount].
 */
interface OnboardingAccount {
    suspend fun connections(): Pair<Boolean, Boolean> = false to false

    /** Name and picture of the signed-in YouTube Music account (null if not available). */
    suspend fun youTubeProfile(): Pair<String?, String?>

    suspend fun youTubeArtists(): List<OnboardingArtist>

    suspend fun spotifyArtists(): List<OnboardingArtist>

    /** Copies the Spotify playlists into the library; returns at once, the copy runs on its own. */
    fun importSpotifyPlaylists()

    suspend fun follow(artists: List<OnboardingArtist>)
}

@HiltViewModel
class VibraOnboardingViewModel
    @Inject
    constructor(
        private val account: OnboardingAccount,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(OnboardingUiState())
        val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                val connections = runCatching { account.connections() }.getOrDefault(false to false)
                if (connections.first) onYouTubeSignedIn()
                if (connections.second) onSpotifySignedIn()
            }
        }

        fun next() = move(+1)

        fun back() = move(-1)

        private fun move(delta: Int) {
            mutableState.update { s ->
                val steps = s.steps
                val index = (steps.indexOf(s.step) + delta).coerceIn(0, steps.lastIndex)
                s.copy(step = steps[index], back = delta < 0)
            }
        }

        /** The YouTube Music sign-in finished: load the profile and the library's artists. */
        fun onYouTubeSignedIn() {
            if (mutableState.value.youTubeConnected) return
            mutableState.update { it.copy(youTubeConnected = true, loadingYouTube = true, step = OnboardingStep.Account, back = false) }
            viewModelScope.launch {
                val profile = runCatching { account.youTubeProfile() }.getOrNull()
                val artists = runCatching { account.youTubeArtists() }.getOrDefault(emptyList())
                mutableState.update {
                    it.copy(
                        loadingYouTube = false,
                        accountName = profile?.first ?: it.accountName,
                        accountAvatarUrl = profile?.second ?: it.accountAvatarUrl,
                    ).withArtists(artists)
                }
            }
        }

        /** The Spotify sign-in finished: import its followed artists and copy its playlists. */
        fun onSpotifySignedIn() {
            if (mutableState.value.spotifyConnected) return
            mutableState.update { it.copy(spotifyConnected = true, loadingSpotify = true, step = OnboardingStep.Account, back = false) }
            account.importSpotifyPlaylists()
            viewModelScope.launch {
                val artists = runCatching { account.spotifyArtists() }.getOrDefault(emptyList())
                mutableState.update { it.copy(loadingSpotify = false).withArtists(artists) }
            }
        }

        /** Nothing left to connect on the Account step: go on to the artists. */
        private fun advanceWhenBothConnected() {
            val s = mutableState.value
            if (s.youTubeConnected && s.spotifyConnected && s.step == OnboardingStep.Account) next()
        }

        /** Adds [found] to the artists on offer; everything found is preselected (the account already says you like them). */
        private fun OnboardingUiState.withArtists(found: List<OnboardingArtist>): OnboardingUiState {
            val fresh = found.filter { a -> artists.none { it.id == a.id } }
            return copy(artists = artists + fresh, selectedArtists = selectedArtists + fresh.map { it.id })
        }

        fun toggleArtist(id: String) {
            mutableState.update { s ->
                s.copy(selectedArtists = if (id in s.selectedArtists) s.selectedArtists - id else s.selectedArtists + id)
            }
        }

        /** Follows the selected artists (in the background) and moves on. */
        fun followSelected() {
            val s = mutableState.value
            val chosen = s.artists.filter { it.id in s.selectedArtists }
            if (chosen.isNotEmpty()) viewModelScope.launch { runCatching { account.follow(chosen) } }
            next()
        }
    }
