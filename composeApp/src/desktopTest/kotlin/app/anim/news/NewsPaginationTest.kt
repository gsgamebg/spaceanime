package app.anim.news
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
class NewsPaginationTest {
    @Test fun acceptsCursorAndSearchOffset() {
        assertEquals("YWJj==", nextNewsCursor("""{"meta":{"nextCursor":"YWJj=="}}"""))
        assertEquals("offset:40", nextNewsCursor("""{"meta":{"hasMore":true,"offset":20,"limit":20}}"""))
        assertNull(nextNewsCursor("""{"meta":{"hasMore":false,"offset":0,"limit":20}}"""))
    }
    @Test fun onlyAllowKnownHttpsArticleLinks() {
        assertNull(safeNewsLink("https://www.jbox.com.br.evil.example/x"))
        assertNull(safeNewsLink("http://www.jbox.com.br/x"))
        assertEquals("https://www.jbox.com.br/a",safeNewsLink("https://www.jbox.com.br/a"))
    }
}
