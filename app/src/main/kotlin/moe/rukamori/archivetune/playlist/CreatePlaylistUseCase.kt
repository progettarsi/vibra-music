/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playlist

import android.net.Uri
import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.repository.PlaylistCreationRepository
import timber.log.Timber
import javax.inject.Inject

data class CreatePlaylistOptions(
    val isSignedIn: Boolean,
    val isSyncEnabled: Boolean,
)

class GetCreatePlaylistOptionsUseCase
    @Inject
    constructor(
        private val repository: PlaylistCreationRepository,
    ) {
        suspend operator fun invoke(): CreatePlaylistOptions {
            val availability = repository.getAvailability()
            return CreatePlaylistOptions(
                isSignedIn = availability.isSignedIn,
                isSyncEnabled = availability.isSyncEnabled,
            )
        }
    }

class CreatePlaylistUseCase
    @Inject
    constructor(
        private val repository: PlaylistCreationRepository,
        private val updateCover: UpdatePlaylistCoverUseCase,
    ) {
        suspend operator fun invoke(
            name: String,
            syncRequested: Boolean,
            coverStyle: String = "Pentagon",
            coverUri: Uri? = null,
        ) {
            val normalizedName = name.trim()
            require(normalizedName.isNotEmpty())

            val playlistId = repository.createPlaylist(normalizedName, syncRequested, coverStyle)
            // The playlist exists either way; an image that cannot be read only leaves the default cover.
            if (coverUri != null) {
                try {
                    updateCover(playlistId, coverUri)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Timber.w(error, "Playlist cover could not be applied after creation")
                }
            }
        }
    }
