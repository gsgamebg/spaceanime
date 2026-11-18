package app.anim.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class PlaybackTest {
    @Test fun playbackHeadersPreserveReferrerAndRejectUnsupportedOrInjectedValues() {
        assertEquals("https://video.example/", PlaybackSource("Test", "https://example.org/a.m3u8", mapOf("Referer" to "https://video.example/")).headers["Referer"])
        assertFailsWith<IllegalArgumentException> { PlaybackSource("Test", "https://example.org/a", mapOf("Referer" to "a\r\nInjected: b")) }
        assertFailsWith<IllegalArgumentException> { PlaybackSource("Test", "https://example.org/a", mapOf("Cookie" to "session=abc")) }
    }
    @Test fun streamUrlsKeepSignedQueriesAndNormalizeScheme() {
        assertEquals("https://example.org/video.m3u8?token=a%2Fb&expires=123", normalizedStreamUrl(" HTTPS://example.org/video.m3u8?token=a%2Fb&expires=123 "))
    }
    @Test fun invalidOrNonHttpsInputsAreRejected() {
        listOf("", "https://", "file:///video.mp4", "http://example.org/a", "https:///a", "https://user:password@example.org/a", "https://example.org/a\nb").forEach { assertNull(normalizedStreamUrl(it), it) }
    }
    @Test fun seekPositionHandlesUnknownAndChangedDuration() {
        assertEquals(0, boundedPosition(-1000, 10000))
        assertEquals(10000, boundedPosition(50000, 10000))
        assertEquals(0, boundedPosition(1000, -1))
    }
    @Test fun restartOverridesSavedPositionWithoutChangingNormalResume() {
        val source = PlaybackSource("Episódio", "https://example.org/video")
        assertEquals(120_000L, startPosition(source, 120_000L))
        assertEquals(0L, startPosition(source.copy(startFromBeginning = true), 120_000L))
    }
    @Test fun timeFormattingHandlesLongVideos() {
        assertEquals("00:00", playbackTime(-1))
        assertEquals("01:05", playbackTime(65000))
        assertEquals("1:01:01", playbackTime(3661000))
    }
}
