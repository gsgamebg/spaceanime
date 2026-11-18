package app.anim.notifications
import kotlin.test.*
class PlaybackNotifierTest {
    @Test fun notifyOnlyNearEndOfKnownLongEpisode() {
        assertFalse(shouldNotifyNearEnd(0, 0))
        assertFalse(shouldNotifyNearEnd(50_000, 100_000))
        assertFalse(shouldNotifyNearEnd(1_000_000, 1_400_000))
        assertTrue(shouldNotifyNearEnd(1_280_000, 1_400_000))
        assertFalse(shouldNotifyNearEnd(1_400_000, 1_400_000))
    }
}
