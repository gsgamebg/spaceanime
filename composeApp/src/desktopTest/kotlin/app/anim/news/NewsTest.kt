package app.anim.news
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
class NewsTest {
    @Test fun safeRssBodyIsAvailableInsideApp() {
        val xml = """<rss xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel><item><title>Notícia</title><link>https://www.jbox.com.br/anime/</link><description><![CDATA[<p>Resumo</p>]]></description><content:encoded><![CDATA[<p>Texto completo.</p><script>alert(1)</script>]]></content:encoded><pubDate>Hoje</pubDate></item><item><title>Falso</title><link>https://jbox.com.br.evil.test/a</link></item></channel></rss>"""
        val items = parseNews(xml)
        assertEquals(1, items.size)
        assertEquals("Resumo", items.single().summary)
        assertEquals("Texto completo.", items.single().body)
    }
    @Test fun apiListRejectsInvalidSlugs() {
        val json = """{"data":[{"title":"Anime","slug":"anime-novo","excerpt":"Resumo","date":"2026-11-02"},{"title":"Inválido","slug":"../other"}]}"""
        val items = parseApiList(json)
        assertEquals(1, items.size)
        assertEquals("anime-novo", items.single().slug)
        assertTrue(items.single().url.isBlank())
    }
}
