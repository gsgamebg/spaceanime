package app.anim.sources
import kotlin.test.*
class EpisodeGapTest {
    @Test fun reportsMissingWholeEpisodesAndIgnoresSpecials() {
        val list = listOf(1.0, 2.0, 2.5, 4.0).map { Episode("e$it", it, "Episódio $it", "https://example.com/$it") }
        assertEquals(listOf(3), missingEpisodeNumbers(list))
        assertTrue(missingEpisodeNumbers(emptyList()).isEmpty())
    }
}
