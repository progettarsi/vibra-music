/*
 * Vibra (2026) — GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.VibraPowerSaveAutoKey
import moe.rukamori.archivetune.constants.VibraPowerSaveThresholdKey
import moe.rukamori.archivetune.constants.VibraPowerSaveModeKey
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

/** Effective level: OFF also when the battery is unknown or charging. */
enum class PowerSaveLevel { OFF, NORMAL, MAX }
enum class PowerSaveMode { NORMAL, MAX }

/** Shared access for code without Hilt. The controller is initialised by the Application. */
object PowerSave {
    internal val mutableLevel = MutableStateFlow(PowerSaveLevel.OFF)
    val level: StateFlow<PowerSaveLevel> = mutableLevel.asStateFlow()
    internal val mutableCharging = MutableStateFlow(false)
    val charging: StateFlow<Boolean> = mutableCharging.asStateFlow()
}

internal fun powerSaveLevel(auto: Boolean, percent: Int, charging: Boolean, threshold: Int, mode: PowerSaveMode): PowerSaveLevel =
    if (!auto || charging || percent !in 0..threshold) PowerSaveLevel.OFF
    else if (mode == PowerSaveMode.MAX) PowerSaveLevel.MAX else PowerSaveLevel.NORMAL

/** One sticky receiver for the app's whole life; the battery is never polled. */
@Singleton
class PowerSaveController @Inject constructor(@ApplicationContext context: Context) {
    val level: StateFlow<PowerSaveLevel> = PowerSave.level
    val charging: StateFlow<Boolean> = PowerSave.charging
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val battery = MutableStateFlow(-1 to false)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            PowerSave.mutableCharging.value = charging
            battery.value = (if (raw >= 0 && scale > 0) (raw * 100 / scale).coerceIn(0, 100) else -1) to charging
        }
    }

    init {
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.let { receiver.onReceive(context, it) }
        scope.launch {
            val preferences = context.dataStore.data.catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            combine(preferences, battery) { prefs, (percent, charging) ->
                val threshold = (prefs[VibraPowerSaveThresholdKey] ?: 20).takeIf { it in listOf(10, 15, 20, 30, 50) } ?: 20
                val mode = PowerSaveMode.entries.firstOrNull { it.name == prefs[VibraPowerSaveModeKey] } ?: PowerSaveMode.NORMAL
                powerSaveLevel(prefs[VibraPowerSaveAutoKey] ?: false, percent, charging, threshold, mode)
            }.distinctUntilChanged().collect { PowerSave.mutableLevel.value = it }
        }
    }
}
