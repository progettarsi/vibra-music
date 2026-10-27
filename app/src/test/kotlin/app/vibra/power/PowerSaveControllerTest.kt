/*
 * Vibra (2026) — GPL-3.0 License.
 * Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.power

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerSaveControllerTest {
    @Test fun onlyActivatesAtThresholdWithoutCharging() {
        assertEquals(PowerSaveLevel.NORMAL, powerSaveLevel(true, 20, false, 20, PowerSaveMode.NORMAL))
        assertEquals(PowerSaveLevel.MAX, powerSaveLevel(true, 10, false, 20, PowerSaveMode.MAX))
        assertEquals(PowerSaveLevel.OFF, powerSaveLevel(true, 21, false, 20, PowerSaveMode.NORMAL))
        assertEquals(PowerSaveLevel.OFF, powerSaveLevel(true, 10, true, 20, PowerSaveMode.MAX))
        assertEquals(PowerSaveLevel.OFF, powerSaveLevel(false, 10, false, 20, PowerSaveMode.NORMAL))
        assertEquals(PowerSaveLevel.OFF, powerSaveLevel(true, -1, false, 20, PowerSaveMode.NORMAL))
    }
}
