package app.anim.manga

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class MangaChapterTest {
    @Test fun chapterPageReportsUnavailableAndKeepsPagination() {
        val json = Json.parseToJsonElement("""{"total":101,"data":[{"id":"856efb1c-b2f2-4ea4-8e48-53b01efc2323","attributes":{"translatedLanguage":"pt-br","chapter":"1.1","pages":10,"externalUrl":null}},{"id":"d6cc17e6-d9d3-4a37-b43a-1abe81c86445","attributes":{"translatedLanguage":"pt-br","chapter":"2","pages":0}},{"id":"d6cc17e6-d9d3-4a37-b43a-1abe81c86445","attributes":{"translatedLanguage":"pt-br","chapter":"3","pages":5,"externalUrl":"https://example.org"}}]}""").jsonObject
        val result = parseDexChapters(json, 0)
        assertEquals(listOf("Capítulo 1.1"), result.items.map { it.label })
        assertEquals(2, result.unavailableChapters)
        assertTrue(result.more)
    }
}
