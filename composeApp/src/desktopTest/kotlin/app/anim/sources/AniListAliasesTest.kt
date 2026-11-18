package app.anim.sources

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.*

class AniListAliasesTest {
    private val single = """{"data":{"Page":{"media":[{"id":1,"title":{"romaji":"Shingeki no Kyojin","english":"Attack on Titan"},"synonyms":["Ataque dos Titãs"]}]}}}"""
    @Test fun matchesTitlesIgnoringCaseAndAccents() {
        assertTrue(parseAniListAliases(single, "attack on titan").contains("Shingeki no Kyojin"))
        assertTrue(parseAniListAliases(single, "Ataque dos titas").contains("Attack on Titan"))
        assertTrue(parseAniListAliases(single, "Dragon Ball").isEmpty())
    }
    @Test fun coverRequiresAnUnambiguousExactTitle() {
        val json = """{"data":{"Page":{"media":[{"title":{"romaji":"One Piece","english":"One Piece"},"synonyms":[],"coverImage":{"extraLarge":"https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/test.jpg"}}]}}}"""
        assertEquals("https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/test.jpg", parseAniListMetadata(json, "One Piece").cover)
        assertNull(parseAniListMetadata(json, "One Piece 2").cover)
        val duplicate = json.replace("]}}}", ", {\"title\":{\"romaji\":\"One Piece\",\"english\":null},\"synonyms\":[],\"coverImage\":{\"extraLarge\":\"https://s4.anilist.co/other.jpg\"}}]}}}")
        assertNull(parseAniListMetadata(duplicate, "One Piece").cover)
    }
    @Test fun rejectsAmbiguousMatches() {
        val ambiguous = """{"data":{"Page":{"media":[{"title":{"romaji":"Mesmo Nome","english":"Primeira Série"},"synonyms":[]},{"title":{"romaji":"Mesmo Nome","english":"Segunda Série"},"synonyms":[]}]}}}"""
        assertTrue(parseAniListAliases(ambiguous, "Mesmo Nome").isEmpty())
    }
    @Test fun liveApiReturnsAlternateTitle() = runBlocking {
        assumeTrue(System.getenv("ANILIST_SMOKE") == "1")
        val aliases = AniListAliases()
        assertTrue(aliases.lookup("Attack on Titan").any { it.contains("Shingeki", true) })
        assertTrue(aliases.cover("One Piece")?.startsWith("https://s4.anilist.co/") == true)
    }
}
