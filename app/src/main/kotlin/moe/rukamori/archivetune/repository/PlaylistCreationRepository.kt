/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.repository

import androidx.datastore.preferences.core.edit
import app.vibra.ui.components.playlistCoverStyleKey
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

data class PlaylistCreationAvailability(
    val isSignedIn: Boolean,
    val isSyncEnabled: Boolean,
)

@Singleton
class PlaylistCreationRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val libraryActions: app.vibra.library.LibraryActions,
    ) {
        suspend fun getAvailability(): PlaylistCreationAvailability =
            withContext(Dispatchers.IO) {
                val preferences = context.dataStore.data.first()
                val isSignedIn = hasYouTubeLoginCookie(preferences[InnerTubeCookieKey].orEmpty())
                PlaylistCreationAvailability(
                    isSignedIn = isSignedIn,
                    isSyncEnabled =
                        isSignedIn &&
                            (preferences[YtmSyncKey] ?: true),
                )
            }

        suspend fun createPlaylist(
            name: String,
            syncRequested: Boolean,
            coverStyle: String,
        ): String = withContext(Dispatchers.IO) {
            val id = libraryActions.createPlaylist(name, syncRequested).getOrThrow()
            val style = app.vibra.ui.components.parsePlaylistCoverStyle(coverStyle).name
            context.dataStore.edit { it[playlistCoverStyleKey(id)] = style }
            id
        }
    }
