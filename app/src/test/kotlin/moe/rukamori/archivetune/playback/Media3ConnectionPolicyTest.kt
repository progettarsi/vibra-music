/*
 * Vibra (2026) — Media3 command access and library ownership regressions.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package moe.rukamori.archivetune.playback

import org.junit.Assert.*
import org.junit.Test

class Media3ConnectionPolicyTest {
    @Test fun autoControllerGetsFullMedia3Defaults() {
        assertTrue(controllerGetsFullCommands(false, true))
        assertTrue(controllerMayChangeLibrary(false, false, true, true))
        assertFalse(controllerMayChangeLibrary(false, false, true, false))
    }
    @Test fun untrustedNonCarControllerKeepsRestrictedBuilderDefaults() {
        assertFalse(controllerGetsFullCommands(false, false))
        assertFalse(controllerMayChangeLibrary(false, false, false, true))
    }
    @Test fun trustedControllerGetsFullMedia3Defaults() {
        assertTrue(controllerGetsFullCommands(true, false))
        assertTrue(controllerMayChangeLibrary(false, true, false, false))
    }
}
