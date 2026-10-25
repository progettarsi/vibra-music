/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.androidauto

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class AndroidAutoSettingsUseCases @Inject constructor(
    private val repository: AndroidAutoSettingsRepository,
) {
    val configuration: Flow<AndroidAutoConfiguration> = repository.configuration
    val networkState: Flow<AndroidAutoNetworkState> = repository.networkState

    fun observeSettings(): Flow<AndroidAutoSettingsSnapshot> = combine(
        repository.configuration,
        repository.connectionStatus,
    ) { configuration, connectionStatus ->
        AndroidAutoSettingsSnapshot(
            configuration = configuration,
            connectionStatus = connectionStatus,
            hasLocalAudioPermission = repository.hasLocalAudioPermission(),
        )
    }

    fun currentConfiguration(): AndroidAutoConfiguration = repository.currentConfiguration()
    fun hasLocalAudioPermission(): Boolean = repository.hasLocalAudioPermission()

    fun isOnlinePlaybackAllowed(configuration: AndroidAutoConfiguration): Boolean {
        val network = repository.currentNetworkState()
        return network.online && (configuration.meteredPlayback || !network.metered)
    }

    fun isRemoteArtworkAllowed(configuration: AndroidAutoConfiguration): Boolean {
        val network = repository.currentNetworkState()
        return network.online && (configuration.meteredArtwork || !network.metered)
    }

}
