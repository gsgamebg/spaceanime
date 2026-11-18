package app.anim.sources

import kotlin.test.*

class RecentReleaseGroupingTest {
    private fun item(
        source: String,
        title: String,
        number: Double,
        dubbed: Boolean = false,
        id: String = "$source/$number"
    ) = RecentEpisode(
        source,
        title,
        Episode(id, number, "Episódio ${number.toInt()}", "https://$source.example/$id"),
        "",
        dubbed
    )

    @Test fun mergesEquivalentCrossServerEpisodesAndRetainsBothCandidates() {
        val grouped = groupRecentEpisodes(
            listOf(
                item("anitube", "Café! Série", 12.0),
                item("animesdigital", "Cafe Serie", 12.0)
            )
        )

        assertEquals(1, grouped.size)
        assertEquals(setOf("anitube", "animesdigital"), grouped.single().candidates.map { it.sourceId }.toSet())
        assertEquals(12.0, grouped.single().number)
    }

    @Test fun keepsDifferentTitlesEpisodesAndAudioVariantsSeparate() {
        val grouped = groupRecentEpisodes(
            listOf(
                item("anitube", "Série A", 1.0),
                item("animesdigital", "Série A 2", 1.0),
                item("anitube", "Série A", 2.0),
                item("animesdigital", "Série A", 1.0, dubbed = true)
            )
        )

        assertEquals(4, grouped.size)
        assertEquals(setOf(false, true), grouped.map { it.dubbed }.toSet())
    }

    @Test fun doesNotMergeItemsWithMissingTitlesOrUnusableEpisodeNumbers() {
        val grouped = groupRecentEpisodes(
            listOf(
                item("anitube", "", 4.0),
                item("animesdigital", "", 4.0),
                item("anitube", "Série B", 0.0),
                item("animesdigital", "Série B", 0.0)
            )
        )

        assertEquals(4, grouped.size)
        assertTrue(grouped.all { it.candidates.size == 1 })
    }
}
