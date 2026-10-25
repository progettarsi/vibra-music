/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.androidauto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.car.app.connection.CarConnection
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Observer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAutoSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()

    val configuration: Flow<AndroidAutoConfiguration> = kotlinx.coroutines.flow.flowOf(AndroidAutoConfiguration())

    val connectionStatus: Flow<AndroidAutoConnectionStatus> = callbackFlow {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            trySend(AndroidAutoConnectionStatus.NATIVE)
            close()
            return@callbackFlow
        }
        val connection = CarConnection(context)
        val observer = Observer<Int> { type -> trySend(type.toConnectionStatus()) }
        connection.type.observeForever(observer)
        awaitClose { connection.type.removeObserver(observer) }
    }.distinctUntilChanged()

    val networkState: Flow<AndroidAutoNetworkState> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(AndroidAutoNetworkState())
            close()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentNetworkState())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.toAndroidAutoNetworkState())
            }

            override fun onLost(network: Network) {
                trySend(currentNetworkState())
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(currentNetworkState())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun currentConfiguration(): AndroidAutoConfiguration = AndroidAutoConfiguration()

    fun currentNetworkState(): AndroidAutoNetworkState {
        val manager = connectivityManager ?: return AndroidAutoNetworkState()
        val network = manager.activeNetwork ?: return AndroidAutoNetworkState()
        val capabilities = manager.getNetworkCapabilities(network) ?: return AndroidAutoNetworkState()
        return capabilities.toAndroidAutoNetworkState()
    }

    fun hasLocalAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        },
    ) == PackageManager.PERMISSION_GRANTED

    private fun Int.toConnectionStatus(): AndroidAutoConnectionStatus = when (this) {
        CarConnection.CONNECTION_TYPE_PROJECTION -> AndroidAutoConnectionStatus.PROJECTION
        CarConnection.CONNECTION_TYPE_NATIVE -> AndroidAutoConnectionStatus.NATIVE
        else -> AndroidAutoConnectionStatus.DISCONNECTED
    }
}

private fun NetworkCapabilities.toAndroidAutoNetworkState(): AndroidAutoNetworkState = AndroidAutoNetworkState(
    online = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    metered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
)
