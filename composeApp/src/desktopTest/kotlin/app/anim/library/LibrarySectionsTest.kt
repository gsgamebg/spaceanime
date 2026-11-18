package app.anim.library

import app.anim.sources.AnimeResult
import app.anim.sources.Episode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LibrarySectionsTest {
    private val firstAnime = AnimeResult("anitube", "series/1", "Anime", "https://example.test/1", false)
    private val episode = Episode("episode/1", 1.0, "Episódio 1", "https://example.test/episode/1")

    @Test
    fun combinesFavoriteAndUnfinishedProgressWithoutDuplicateAnime() {
        val context = EpisodeContext(firstAnime, episode)
        val snapshot = LibrarySnapshot(
            favorites = listOf(firstAnime),
            progress = listOf(
                WatchProgress(context, 20_000, 60_000, false, 1),
                WatchProgress(context.copy(episode = episode.copy(id = "episode/2")), 30_000, 60_000, false, 2),
            ),
        )

        val sections = LibrarySections.project(snapshot, emptyList())
        val animeEntries = sections.all.filterIsInstance<LibraryEntry.Anime>()

        assertEquals(1, animeEntries.count { it.anime.sameAnime(firstAnime) })
        assertEquals(30_000, animeEntries.single().progress?.positionMs)
        assertEquals(2, sections.continuing.size)
        assertEquals(1, sections.favorites.size)
    }

    @Test
    fun preservesSourceAndAudioIdentityAndExcludesCompletedOrZeroPositionFromContinue() {
        val base = EpisodeContext(firstAnime, episode)
        val snapshot = LibrarySnapshot(
            favorites = listOf(firstAnime),
            progress = listOf(
                WatchProgress(base, 0, 60_000, false, 1),
                WatchProgress(base.copy(anime = firstAnime.copy(dubbed = true)), 12_000, 60_000, false, 2),
                WatchProgress(base.copy(anime = firstAnime.copy(sourceId = "animesdigital")), 15_000, 60_000, false, 3),
                WatchProgress(base.copy(episode = episode.copy(id = "episode/4")), 59_000, 60_000, true, 4),
            ),
        )

        val sections = LibrarySections.project(snapshot, emptyList())

        assertEquals(3, sections.all.filterIsInstance<LibraryEntry.Anime>().size)
        assertEquals(2, sections.continuing.size)
        assertEquals(1, sections.favorites.size)
    }

    @Test
    fun includesFollowedMangaInAllAndFavoritesOnly() {
        val manga = MangaReference("mangadex", "manga/1", "Mangá", "https://example.test/cover.jpg")
        val sections = LibrarySections.project(LibrarySnapshot(), listOf(manga))

        assertEquals(1, sections.all.size)
        assertEquals(1, sections.favorites.size)
        assertIs<LibraryEntry.Manga>(sections.all.single())
        assertEquals(emptyList(), sections.continuing)
    }
}
