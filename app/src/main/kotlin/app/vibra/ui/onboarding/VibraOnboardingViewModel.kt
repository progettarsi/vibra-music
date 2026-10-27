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
 *   • Account: YouTube Music sign-in first (it fills name and picture by itself), Spotify
 *     only to import artists, or no account at all.
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

/** Where the account data came from (decides the Artists step's source). */
enum class AccountSource { None, YouTube, Spotify }

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
    val source: AccountSource = AccountSource.None,
    val loadingAccount: Boolean = false,
    val accountName: String? = null,
    val accountAvatarUrl: String? = null,
    val artists: List<OnboardingArtist> = emptyList(),
    val selectedArtists: Set<String> = emptySet(),
) {
    /** Screens this run will show (Artists only with an account), for the progress bar. */
    val steps: List<OnboardingStep>
        get() = OnboardingStep.entries.filter { it != OnboardingStep.Artists || source != AccountSource.None }
}

/**
 * Account data the onboarding reads: YouTube Music profile and artists, Spotify artists,
 * following. Implemented over the engine by [EngineOnboardingAccount].
 */
interface OnboardingAccount {
    /** Name and picture of the signed-in YouTube Music account (null if not available). */
    suspend fun youTubeProfile(): Pair<String?, String?>

    suspend fun youTubeArtists(): List<OnboardingArtist>

    suspend fun spotifyArtists(): List<OnboardingArtist>

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

        fun next() = move(+1)

        fun back() = move(-1)

        private fun move(delta: Int) {
            mutableState.update { s ->
                val steps = s.steps
                val index = (steps.indexOf(s.step) + delta).coerceIn(0, steps.lastIndex)
                s.copy(step = steps[index], back = delta < 0)
            }
        }

        /** Continue without an account: straight to Style. */
        fun skipAccount() {
            mutableState.update { it.copy(source = AccountSource.None, step = OnboardingStep.Style, back = false) }
        }

        /** The YouTube Music sign-in finished: load the profile and the library's artists. */
        fun onYouTubeSignedIn() = load(AccountSource.YouTube)

        /** The Spotify sign-in finished: import its followed artists. */
        fun onSpotifySignedIn() = load(AccountSource.Spotify)

        private fun load(source: AccountSource) {
            mutableState.update { it.copy(source = source, loadingAccount = true, step = OnboardingStep.Artists, back = false) }
            viewModelScope.launch {
                val profile = if (source == AccountSource.YouTube) runCatching { account.youTubeProfile() }.getOrNull() else null
                val artists =
                    runCatching {
                        if (source == AccountSource.YouTube) account.youTubeArtists() else account.spotifyArtists()
                    }.getOrDefault(emptyList())
                mutableState.update {
                    it.copy(
                        loadingAccount = false,
                        accountName = profile?.first ?: it.accountName,
                        accountAvatarUrl = profile?.second ?: it.accountAvatarUrl,
                        artists = artists,
                        // Everything found is preselected: the account already says you like them.
                        selectedArtists = artists.mapTo(HashSet()) { a -> a.id },
                    )
                }
            }
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
