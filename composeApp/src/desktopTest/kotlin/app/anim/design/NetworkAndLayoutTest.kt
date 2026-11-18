package app.anim.design

import app.anim.sources.allowedSourceAddress
import kotlin.test.*

class NetworkAndLayoutTest {
    @Test fun artworkRejectsFilesCleartextCredentialsAndUntrustedHosts() {
        assertNotNull(safeArtworkUrl("https://www.anitube.zip/poster.jpg"))
        assertNotNull(safeArtworkUrl("https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/test.jpg"))
        assertNull(safeArtworkUrl("https://s4.anilist.co.evil.test/test.jpg"))
        for (url in listOf("file:///C:/secret.txt", "http://www.anitube.zip/a.jpg", "https://www.anitube.zip.evil.test/a.jpg", "https://127.0.0.1/a.jpg", "https://u:p@www.anitube.zip/a.jpg", "https://www.anitube.zip:8443/a.jpg")) assertNull(safeArtworkUrl(url), url)
    }
    @Test fun metadataRequestsStayOnApprovedHttpsOrigins() {
        assertTrue(allowedSourceAddress("https://www.blogger.com/video.g?token=test"))
        assertTrue(allowedSourceAddress("https://animesdigital.org/"))
        for (url in listOf("https://example.com/", "http://www.anitube.zip/", "https://user@www.blogger.com/", "https://www.blogger.com:1234/", "https://localhost/")) assertFalse(allowedSourceAddress(url))
    }
    @Test fun posterGridFitsPhoneTabletAndDesktopWidths() {
        assertEquals(1, posterColumnCount(280f))
        assertEquals(2, posterColumnCount(360f))
        assertEquals(3, posterColumnCount(768f))
        assertEquals(4, posterColumnCount(1024f))
        assertEquals(5, posterColumnCount(1600f))
        for (width in listOf(280f, 360f, 768f, 1024f, 1600f)) assertTrue((width - (posterColumnCount(width)-1)*14) / posterColumnCount(width) >= 140)
    }
}
