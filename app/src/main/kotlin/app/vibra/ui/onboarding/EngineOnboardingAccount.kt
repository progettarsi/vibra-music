/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.onboarding

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import moe.rukamori.archivetune.onboarding.OnboardingAccountData
import moe.rukamori.archivetune.spotify.ImportedArtist
import moe.rukamori.archivetune.spotify.SpotifyOnboardingImport
import javax.inject.Inject

/**
 * [OnboardingAccount] over the engine: the YouTube Music profile and library artists
 * (OnboardingAccountData), Spotify's followed artists mapped to YouTube Music artists, and
 * following (SpotifyOnboardingImport.followArtists — a local bookmark plus a YouTube Music
 * subscription when signed in, whatever the source).
 */
class EngineOnboardingAccount
    @Inject
    constructor(
        private val youTube: OnboardingAccountData,
        private val spotify: SpotifyOnboardingImport,
        private val sync: app.vibra.spotify.SpotifySyncRepository,
        private val library: moe.rukamori.archivetune.spotify.SpotifyLibraryRepository,
    ) : OnboardingAccount {
        private var importedSpotifyArtists = emptySet<String>()
        override suspend fun connections() = (youTube.accountProfile() != null) to library.restoreSession().isAuthenticated

        override suspend fun youTubeProfile(): Pair<String?, String?> = youTube.accountProfile()?.let { it.name to it.avatarUrl } ?: (null to null)

        override suspend fun youTubeArtists(): List<OnboardingArtist> = youTube.youTubeLibraryArtists().map { it.toOnboarding() }

        override suspend fun spotifyArtists(): List<OnboardingArtist> {
            val artists = spotify.importFollowedArtists().getOrThrow()
            importedSpotifyArtists = artists.map { it.browseId }.toSet()
            return artists.map { it.toOnboarding() }
        }

        override fun importSpotifyPlaylists() = sync.sync()

        override suspend fun follow(artists: List<OnboardingArtist>) {
            spotify.followArtists(artists.map { it.id }.filterNot { it in importedSpotifyArtists })
        }

        private fun ImportedArtist.toOnboarding() = OnboardingArtist(id = browseId, name = name, thumbnailUrl = thumbnailUrl)
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingAccountModule {
    @Binds
    abstract fun bindOnboardingAccount(impl: EngineOnboardingAccount): OnboardingAccount
}
