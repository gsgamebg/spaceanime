package app.anim.player

import app.anim.sources.AnimeResult
import app.anim.sources.Episode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeQueueTest {
    private val anime = AnimeResult("anitube", "series/1", "Anime", "https://example.test/series/1", false)
    private val episodes = listOf(
        Episode("ep/1", 1.0, "Episódio 1", "https://example.test/ep/1"),
        Episode("ep/2", 2.0, "Episódio 2", "https://example.test/ep/2"),
        Episode("ep/3", 3.0, "Episódio 3", "https://example.test/ep/3"),
    )

    @Test
    fun providesAdjacentEpisodesWithoutCrossingQueueBoundaries() {
        val first = EpisodeQueue(anime, episodes, "ep/1")
        val middle = EpisodeQueue(anime, episodes, "ep/2")
        val last = EpisodeQueue(anime, episodes, "ep/3")

        assertNull(first.previous())
        assertEquals(episodes[1], first.next())
        assertEquals(episodes[0], middle.previous())
        assertEquals(episodes[2], middle.next())
        assertEquals(episodes[1], last.previous())
        assertNull(last.next())
    }

    @Test
    fun doesNotGuessWhenCurrentEpisodeIsMissingOrQueueIsEmpty() {
        assertNull(EpisodeQueue(anime, episodes, "missing").next())
        assertNull(EpisodeQueue(anime, emptyList(), "missing").previous())
    }

    @Test
    fun retainsSourceOrderRatherThanSortingByEpisodeNumber() {
        val sourceOrder = listOf(episodes[1], episodes[0])
        val queue = EpisodeQueue(anime, sourceOrder, "ep/2")

        assertEquals(episodes[0], queue.next())
    }

    @Test
    fun selectingAnAdjacentEpisodeReturnsANewQueueAtThatEpisode() {
        val queue = EpisodeQueue(anime, episodes, "ep/1")

        assertEquals("ep/2", queue.select(episodes[1])?.currentEpisodeId)
        assertEquals("ep/1", queue.currentEpisodeId)
        assertNull(queue.select(Episode("foreign", 9.0, "Fora da fila", "https://example.test/foreign")))
    }
}
