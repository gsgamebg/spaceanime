package app.anim.manga

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class MangaCatalogTest {
    @Test fun initialCatalogUsesMangaDexPopularOrderingAndRemoteOffsets() {
        val first = mangaDexBrowseUrl(0)
        val second = mangaDexBrowseUrl(1)

        assertEquals("desc", first.queryParameter("order[followedCount]"))
        assertEquals("0", first.queryParameter("offset"))
        assertEquals("24", second.queryParameter("offset"))
        assertEquals(null, first.queryParameter("title"))
        assertEquals("pt-br", first.queryParameter("availableTranslatedLanguage[]"))
    }

    @Test fun catalogParserReturnsCardsAndStopsWhenRemoteTotalIsExhausted() {
        val root = Json.parseToJsonElement(
            """{"total":25,"data":[{"id":"856efb1c-b2f2-4ea4-8e48-53b01efc2323","attributes":{"title":{"pt-br":"Obra em português","en":"English title"}},"relationships":[{"type":"cover_art","attributes":{"fileName":"cover.jpg"}}]}]}"""
        ).jsonObject

        val first = parseDexMangaResults(root, page = 0)
        val last = parseDexMangaResults(root, page = 1)

        assertEquals("Obra em português", first.items.single().title)
        assertEquals("https://uploads.mangadex.org/covers/856efb1c-b2f2-4ea4-8e48-53b01efc2323/cover.jpg.256.jpg", first.items.single().cover)
        assertTrue(first.more)
        assertFalse(last.more)
    }
}
