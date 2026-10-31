package app.vibra.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.network.currentValidatedNetworkStatusFlow
import org.junit.Assert.*
import org.junit.Test

class CurrentNetworkSnapshotTest {
    @Test fun firstUsesTheNetworkAtCollectionRatherThanObjectCreation() = runBlocking {
        var networks = emptySet<String>()
        val online = currentValidatedNetworkStatusFlow(current = { networks }) { AutoCloseable {} }
        networks = setOf("wifi")
        assertTrue(online.first())
    }
    @Test fun repeatedShortLivedChecksFollowLossAndReturnOfNetwork() = runBlocking {
        var networks = setOf("wifi")
        val online = currentValidatedNetworkStatusFlow(current = { networks }) { AutoCloseable {} }
        assertTrue(online.first())
        networks = emptySet()
        assertFalse(online.first())
        networks = setOf("mobile")
        assertTrue(online.first())
    }
}
