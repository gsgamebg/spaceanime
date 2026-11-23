package app.anim.sources

import app.anim.player.PlaybackSource
import kotlin.test.*

class PreferredStreamTest {
    private fun option(label: String, url: String) = StreamOption(label, PlaybackSource("t", url))

    @Test fun prefersAdaptiveHlsThenHighestQuality() {
        val hls = option("Servidor principal · automático", "https://cdn.example/index.m3u8")
        val p720 = option("Blogger · 720p", "https://video.example/a")
        val p360 = option("Blogger · 360p", "https://video.example/b")
        assertEquals(hls, preferredStream(listOf(p360, p720, hls)))
        assertEquals(p720, preferredStream(listOf(p360, p720)))
        assertNull(preferredStream(emptyList()))
    }
}
