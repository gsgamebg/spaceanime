package app.anim.player

data class EpisodeMarkerKey(
    val sourceId: String,
    val contentId: String,
    val episodeId: String,
)

interface EpisodeMarkersClient {
    suspend fun load(key: EpisodeMarkerKey): EpisodeMarkers?
}

class EpisodeMarkersLoadException internal constructor(cause: Throwable? = null) :
    Exception("Episode markers are unavailable.", cause)

expect fun createEpisodeMarkersClient(): EpisodeMarkersClient

internal fun EpisodeMarkerKey.isValid(): Boolean =
    sourceId in setOf("anitube", "animesdigital") &&
        validMarkerIdentifier(contentId) &&
        validMarkerIdentifier(episodeId)

private fun validMarkerIdentifier(value: String): Boolean =
    value.length in 1..220 && Regex("^[A-Za-z0-9._:/-]+$").matches(value)
