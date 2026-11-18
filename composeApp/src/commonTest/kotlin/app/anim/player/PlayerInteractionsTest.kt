package app.anim.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class PlayerInteractionsTest {
    @Test fun autoAdvanceRequiresPreferenceEndedStateAndNextEpisode() {
        assertTrue(shouldAutoAdvance(autoPlayNext = true, ended = true, hasNext = true))
        assertFalse(shouldAutoAdvance(autoPlayNext = false, ended = true, hasNext = true))
        assertFalse(shouldAutoAdvance(autoPlayNext = true, ended = false, hasNext = true))
        assertFalse(shouldAutoAdvance(autoPlayNext = true, ended = true, hasNext = false))
    }

    @Test fun markersOnlyApplyInsideRealSeekableDuration() {
        val intro = Segment(75_000, 165_000)
        assertFalse(intro.contains(74_999, 300_000))
        assertTrue(intro.contains(75_000, 300_000))
        assertTrue(intro.contains(164_999, 300_000))
        assertFalse(intro.contains(165_000, 300_000))
        assertFalse(intro.contains(80_000, 160_000))
        assertFalse(intro.contains(80_000, 0))
        assertFailsWith<IllegalArgumentException> { Segment(-1, 10) }
        assertFailsWith<IllegalArgumentException> { Segment(10, 10) }
    }
    @Test fun controlsAndGesturesRespectPlaybackAndMenus() {
        assertTrue(controlsMayHide(true, false, false, true, false, false, false))
        assertFalse(controlsMayHide(false, false, false, true, false, false, false))
        assertFalse(controlsMayHide(true, false, false, true, true, false, false))
        assertTrue(controlsMayHide(true, true, false, true, false, false, false))
        assertEquals(VideoTapAction.Reveal, videoTapAction(true, false, true, .5f))
        assertEquals(VideoTapAction.Toggle, videoTapAction(true, true, true, .5f))
        assertEquals(VideoTapAction.Hide, videoTapAction(true, true, true, .1f))
        assertEquals(VideoTapAction.Reveal, videoTapAction(true, true, false, .1f))
        assertEquals(VideoTapAction.Toggle, videoTapAction(false, false, true, .5f))
    }

    @Test fun desktopSideTapSeeksWhileTouchDevicesKeepTheirSingleTapBehavior() {
        assertEquals(-10_000L, directSeekForTap(false, .10f))
        assertEquals(10_000L, directSeekForTap(false, .90f))
        assertEquals(null, directSeekForTap(false, .50f))
        assertEquals(null, directSeekForTap(true, .10f))
        assertEquals(null, directSeekForTap(true, .90f))
    }
}


