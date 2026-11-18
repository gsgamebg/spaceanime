package app.anim.manga

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class YomuAdapterTest {
    @Test fun searchPreservesSourceAndRejectsUnsafeSlugs() {
        val json = Json.parseToJsonElement("""{"series":[{"slug":"one-piece","title":"One Piece","cover":"https://yomu.com.br/a.jpg"},{"slug":"../other","title":"Inválido"}],"hasNextPage":true}""").jsonObject
        val result = parseYomuSearch(json)
        assertEquals(listOf("one-piece"), result.items.map { it.id })
        assertEquals("yomu", result.items.single().source)
        assertTrue(result.more)
    }
    @Test fun chaptersKeepDecimalNumbersAndRejectInvalidNumbers() {
        val json = Json.parseToJsonElement("""{"chapters":[{"id":"a","number":1.5},{"id":"b","number":2.0,"title":"Encontro"},{"number":"../../x"}]}""").jsonObject
        val result = parseYomuChapters(json, "one-piece")
        assertEquals(listOf("1.5", "2"), result.map { it.id })
        assertEquals("one-piece", result.first().mangaId)
        assertEquals("Encontro", result.last().label)
    }
}
