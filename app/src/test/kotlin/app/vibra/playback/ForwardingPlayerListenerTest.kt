/* Vibra (2026) - GPL-3.0. */
package app.vibra.playback

import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test

class ForwardingPlayerListenerTest {
    @Test
    fun playbackAndTransportStateReachTheAppListener() {
        val received = mutableListOf<String>()
        val target =
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    received += "state:$state"
                }

                override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                    received += "ready:$ready:$reason"
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    received += "playing:$playing"
                }

                override fun onVolumeChanged(volume: Float) {
                    received += "volume:$volume"
                }

                override fun onIsLoadingChanged(loading: Boolean) {
                    received += "loading:$loading"
                }
            }
        val proxy = ForwardingPlayerListener(target)
        proxy.onPlaybackStateChanged(Player.STATE_READY)
        proxy.onPlayWhenReadyChanged(true, 1)
        proxy.onIsPlayingChanged(true)
        proxy.onVolumeChanged(.5f)
        proxy.onIsLoadingChanged(false)
        assertEquals(
            listOf("state:3", "ready:true:1", "playing:true", "volume:0.5", "loading:false"),
            received,
        )
    }

    @Test
    fun everyMedia3DefaultCallbackHasAnExplicitBridge() {
        fun java.lang.reflect.Method.key() =
            name + parameterTypes.joinToString(prefix = "(", postfix = ")") { it.name }
        val required = Player.Listener::class.java.declaredMethods.map { it.key() }.toSet()
        val implemented =
            ForwardingPlayerListener::class.java.declaredMethods.map { it.key() }.toSet()
        assertEquals(emptySet<String>(), required - implemented)
    }
}
