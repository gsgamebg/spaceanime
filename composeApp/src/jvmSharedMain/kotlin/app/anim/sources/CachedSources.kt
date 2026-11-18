package app.anim.sources

/** Small in-memory metadata cache. Signed playback URLs are deliberately never cached. */
internal class CachedSources(private val delegate: SourceRepository, private val now: () -> Long = { System.nanoTime() / 1_000_000 }) : SourceRepository {
    private data class Entry<T>(val value: T, val expires: Long)
    private val titleAliases = AniListAliases()
    override suspend fun alternateTitles(query: String) = titleAliases.lookup(query)
    private val searches = LinkedHashMap<Triple<String, String, Boolean>, Entry<List<AnimeResult>>>()
    private val episodes = LinkedHashMap<Pair<String, String>, Entry<List<Episode>>>()
    override suspend fun search(sourceId: String, query: String, dubbed: Boolean): List<AnimeResult> {
        val key = Triple(sourceId, query.trim().lowercase(), dubbed)
        synchronized(searches) { searches[key]?.takeIf { it.expires > now() }?.let { return it.value } }
        val result = delegate.search(sourceId, query, dubbed)
        synchronized(searches) { searches[key] = Entry(result, now() + 120_000); if (searches.size > 20) searches.remove(searches.keys.first()) }
        return result
    }
    override suspend fun searchAll(sourceId: String, query: String) = delegate.searchAll(sourceId, query)
    override suspend fun searchAllPage(sourceId: String, query: String, page: Int) =
        delegate.searchAllPage(sourceId, query, page)
    private var trendingCache: Entry<List<AnimeResult>>? = null
    override suspend fun genre(slug: String, page: Int) = delegate.genre(slug, page)
    override suspend fun genrePageResults(slug: String, page: Int) =
        delegate.genrePageResults(slug, page)
    override suspend fun trending(): List<AnimeResult> {
        synchronized(this) { trendingCache?.takeIf { it.expires > now() }?.let { return it.value } }
        val titles = AniListTrending().fetch()
        val result = matchTrending(titles, delegate)
        if (result.isNotEmpty()) synchronized(this) { trendingCache = Entry(result, now() + 300_000) }
        return result
    }
    override suspend fun highResolutionCover(anime: AnimeResult) = titleAliases.cover(anime.title)
    override suspend fun synopsis(anime: AnimeResult) = delegate.synopsis(anime)
    override suspend fun episodes(anime: AnimeResult): List<Episode> {
        val key = anime.sourceId to anime.url
        synchronized(episodes) { episodes[key]?.takeIf { it.expires > now() }?.let { return it.value } }
        val result = delegate.episodes(anime)
        synchronized(episodes) { episodes[key] = Entry(result, now() + 120_000); if (episodes.size > 12) episodes.remove(episodes.keys.first()) }
        return result
    }
    override suspend fun streams(anime: AnimeResult, episode: Episode) = delegate.streams(anime, episode)
    override suspend fun discover(sourceId: String) = delegate.discover(sourceId)
    override suspend fun animeForRecent(item: RecentEpisode) = delegate.animeForRecent(item)
}

