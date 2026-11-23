package app.anim.player

import kotlin.test.*

class PlayerBufferingTest {
    @Test fun hlsDetectionIgnoresQueryAndFragment() {
        assertTrue(isHlsUrl("https://cdn.example/stream/index.m3u8"))
        assertTrue(isHlsUrl("https://cdn.example/index.M3U8?token=a#x"))
        assertFalse(isHlsUrl("https://video.example/file.mp4?x=.m3u8"))
    }

    @Test fun desktopOptionsCarryHeadersCacheAndResumePoint() {
        val source = PlaybackSource("t", "https://cdn.example/index.m3u8", headers = mapOf("Referer" to "https://a.example/"), startPositionMs = 90_500)
        assertEquals(
            listOf(":http-referrer=https://a.example/", ":network-caching=${PlayerBuffering.DESKTOP_NETWORK_CACHE_MS}", ":start-time=90.5"),
            desktopMediaOptions(source),
        )
        assertFalse(desktopMediaOptions(source.copy(startPositionMs = 0)).any { it.startsWith(":start-time") })
    }

    @Test fun startsFasterThanExoPlayerDefaults() {
        assertTrue(PlayerBuffering.START_BUFFER_MS < 2_500)
        assertTrue(PlayerBuffering.REBUFFER_MS < 5_000)
        assertTrue(PlayerBuffering.MIN_BUFFER_MS >= PlayerBuffering.REBUFFER_MS)
    }
}
