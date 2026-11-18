package app.anim.sources

data class RecentRelease(
    val title: String,
    val number: Double,
    val episodeTitle: String,
    val image: String,
    val dubbed: Boolean,
    val candidates: List<RecentEpisode>
)

expect fun normalizeReleaseTitle(title: String): String

fun groupRecentEpisodes(items: List<RecentEpisode>): List<RecentRelease> {
    data class Key(val title: String, val number: Double, val dubbed: Boolean, val uniqueIndex: Int? = null)

    val grouped = linkedMapOf<Key, MutableList<RecentEpisode>>()
    items.forEachIndexed { index, item ->
        val title = normalizeReleaseTitle(item.title)
        val number = item.episode.number
        val canGroup = title.isNotBlank() && number > 0.0 && !number.isNaN() && !number.isInfinite()
        val key = if (canGroup) Key(title, number, item.dubbed) else Key("", 0.0, item.dubbed, index)
        grouped.getOrPut(key) { mutableListOf() }.add(item)
    }
    return grouped.values.map { candidates ->
        val first = candidates.first()
        RecentRelease(
            title = first.title,
            number = first.episode.number,
            episodeTitle = first.episode.title,
            image = candidates.firstOrNull { it.image.isNotBlank() }?.image.orEmpty(),
            dubbed = first.dubbed,
            candidates = candidates.toList()
        )
    }
}
