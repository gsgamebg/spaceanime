package app.anim.sources

import kotlinx.coroutines.*

private val aliases = listOf(
    listOf("attack on titan", "shingeki no kyojin", "o titã de ataque"),
    listOf("demon slayer", "kimetsu no yaiba", "lamina da destruicao de demonios"),
    listOf("my hero academia", "boku no hero academia"),
    listOf("fullmetal alchemist", "hagane no renkinjutsushi"),
    listOf("saint seiya", "knights of the zodiac", "cavaleiros do zodiaco"),
    listOf("bleach thousand year blood war", "bleach sennen kessen hen"),
)
fun searchTerms(query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.length < 2) return emptyList()
    val match = aliases.firstNotNullOfOrNull { group -> group.firstOrNull { trimmed.contains(it, ignoreCase = true) }?.let { group to it } }
    return (listOf(trimmed) + (match?.let { (group, alias) -> group.map { trimmed.replace(alias, it, ignoreCase = true) } } ?: emptyList())).distinctBy { it.lowercase() }.take(4)
}
data class UnifiedResult(
    val items: List<AnimeResult>,
    val sourceFailures: Set<String>,
    val terms: List<String>,
    val hasMore: Boolean = false
) {
    val failedSources: Int get() = sourceFailures.size
}
suspend fun unifiedSearch(repository: SourceRepository, query: String): UnifiedResult =
    unifiedSearchPage(repository, query, 1)

suspend fun unifiedSearchPage(repository: SourceRepository, query: String, page: Int): UnifiedResult = coroutineScope {
    require(page in 1..40) { "A página deve estar entre 1 e 40." }
    val known = searchTerms(query)
    val discovered = try { withTimeout(5_000) { repository.alternateTitles(query) } }
        catch (_: TimeoutCancellationException) { emptyList() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { emptyList() }
    val terms = (known + discovered).map { it.trim() }.filter { it.length in 2..120 }
        .distinctBy { it.lowercase() }.take(5)
    val sourceIds = listOf("anitube", "animesdigital")
    val results = sourceIds.map { source -> async {
        var successful = false
        var hasMore = false
        val items = mutableListOf<AnimeResult>()
        for (term in terms) {
            try {
                val result = withTimeout(30_000) { repository.searchAllPage(source, term, page) }
                items += result.items
                successful = true
                hasMore = hasMore || result.hasMore
            }
            catch (_: TimeoutCancellationException) { }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
        }
        Triple(successful, hasMore, items)
    } }.awaitAll()
    val failures = sourceIds.filterIndexed { index, _ -> !results[index].first }.toSet()
    UnifiedResult(
        items = results.flatMap { it.third }.distinctBy { "${it.sourceId}|${it.id}|${it.dubbed}" },
        sourceFailures = failures,
        terms = terms,
        hasMore = results.any { it.second }
    )
}
