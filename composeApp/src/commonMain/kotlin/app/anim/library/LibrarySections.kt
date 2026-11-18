package app.anim.library

import app.anim.sources.AnimeResult

private data class AnimeIdentity(val sourceId: String, val contentId: String, val dubbed: Boolean)

sealed interface LibraryEntry {
    data class Anime(
        val anime: AnimeResult,
        val progress: WatchProgress?,
        val favorite: Boolean,
    ) : LibraryEntry

    data class Manga(val manga: MangaReference) : LibraryEntry
}

data class LibrarySections(
    val all: List<LibraryEntry>,
    val continuing: List<WatchProgress>,
    val favorites: List<LibraryEntry>,
) {
    companion object {
        fun project(snapshot: LibrarySnapshot, followedManga: List<MangaReference>): LibrarySections {
            val continuing = snapshot.progress
                .filter { !it.completed && it.positionMs > 0 }
                .sortedByDescending { it.updatedAt }
            val favoriteAnime = snapshot.favorites.distinctBy { it.identity() }
            val progressByAnime = continuing
                .groupBy { it.context.anime.identity() }
                .mapValues { (_, entries) -> entries.first() }

            val animeByKey = linkedMapOf<AnimeIdentity, AnimeResult>()
            favoriteAnime.forEach { anime -> animeByKey[anime.identity()] = anime }
            continuing.forEach { progress ->
                val anime = progress.context.anime
                if (anime.identity() !in animeByKey) animeByKey[anime.identity()] = anime
            }

            val animeEntries = animeByKey.map { (key, anime) ->
                LibraryEntry.Anime(anime, progressByAnime[key], favoriteAnime.any { it.identity() == key })
            }
            val mangaEntries = followedManga.distinctBy { it.sourceId to it.contentId }
                .map { LibraryEntry.Manga(it) }
            val favoriteEntries = animeEntries.filter { it.favorite } + mangaEntries

            return LibrarySections(
                all = animeEntries + mangaEntries,
                continuing = continuing,
                favorites = favoriteEntries,
            )
        }
    }
}

private fun AnimeResult.identity() = AnimeIdentity(sourceId, id, dubbed)
