/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package moe.rukamori.archivetune.playback

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class SleepTimerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val player =
        Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, _, _ -> null } as Player
    private var expired = 0
    private val timer = SleepTimer(scope, player) { expired++ }

    @After fun tearDown() = scope.cancel()

    @Test fun minutesAfterSongEndForgetsSongEnd() {
        timer.start(-1)
        timer.start(30)
        assertFalse(timer.pauseWhenSongEnd)
        assertTrue(timer.triggerTime > 0)
        timer.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        timer.onPlaybackStateChanged(Player.STATE_ENDED)
        assertEquals(0, expired)
    }

    @Test fun songEndAfterMinutesForgetsTheDeadline() {
        timer.start(30)
        timer.start(-1)
        assertTrue(timer.pauseWhenSongEnd)
        assertEquals(-1L, timer.triggerTime)
        timer.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        assertEquals(1, expired)
    }

    @Test fun clearEndsBothModes() {
        timer.start(-1)
        timer.clear()
        assertFalse(timer.isActive)
        timer.start(30)
        timer.clear()
        assertFalse(timer.isActive)
    }
}
