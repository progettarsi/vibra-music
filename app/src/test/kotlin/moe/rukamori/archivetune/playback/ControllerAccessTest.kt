/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package moe.rukamori.archivetune.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerAccessTest {
    private fun allowed(
        own: Boolean = false,
        trusted: Boolean = false,
        car: Boolean = false,
        verified: Boolean = false,
    ) = controllerMayChangeLibrary(own, trusted, car, verified)

    @Test fun anUnknownAppCannotChangeTheLibrary() = assertFalse(allowed(verified = true))

    @Test fun theAppAndSystemTrustedControllersCan() {
        assertTrue(allowed(own = true))
        assertTrue(allowed(trusted = true))
    }

    @Test fun aCarNeedsItsPackageVerified() {
        assertTrue(allowed(car = true, verified = true))
        assertFalse(allowed(car = true, verified = false))
    }
}
