package app.anim.notifications

import androidx.compose.runtime.Composable
import app.anim.auth.AccountState
import app.anim.library.MangaReference
import app.anim.sources.AnimeResult

data class NotificationPreferences(
    val anime: Boolean = false,
    val manga: Boolean = false,
    val announcements: Boolean = false,
    val digestTime: String = "20:00",
    val timeZone: String = "America/Sao_Paulo",
) {
    val enabled: Boolean get() = anime || manga || announcements

    fun isValid(): Boolean =
        Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$").matches(digestTime) &&
            timeZone.length in 1..100 &&
            (timeZone in setOf("UTC", "GMT") || Regex("^[A-Za-z][A-Za-z0-9_+-]*(?:/[A-Za-z0-9_+-]+)+$").matches(timeZone))
}

data class NotificationSubscription(
    val mediaType: String,
    val sourceId: String,
    val contentId: String,
    val audioVariant: String,
)

fun projectNotificationSubscriptions(
    animeFavorites: List<AnimeResult>,
    followedManga: List<MangaReference>,
    preferences: NotificationPreferences,
): List<NotificationSubscription> {
    require(preferences.isValid()) { "Preferências de notificação inválidas." }
    val anime = if (preferences.anime) animeFavorites.mapNotNull { item ->
        if (item.sourceId !in setOf("anitube", "animesdigital") || !validContentId(item.id)) return@mapNotNull null
        NotificationSubscription(
            mediaType = "anime",
            sourceId = item.sourceId,
            contentId = item.id,
            audioVariant = if (item.dubbed) "dubbed" else "subtitled",
        )
    } else emptyList()
    val manga = if (preferences.manga) followedManga.mapNotNull { item ->
        if (item.sourceId !in setOf("dex", "yomu") || !validContentId(item.contentId)) return@mapNotNull null
        NotificationSubscription("manga", item.sourceId, item.contentId, "none")
    } else emptyList()
    val subscriptions = (anime + manga).distinctBy {
        listOf(it.mediaType, it.sourceId, it.contentId, it.audioVariant).joinToString("\u0000")
    }
    require(subscriptions.size <= 500) { "A lista acompanhada excede o limite de notificações." }
    return subscriptions
}

private fun validContentId(value: String): Boolean =
    value.length in 1..180 && Regex("^[A-Za-z0-9._:/-]+$").matches(value)

@Composable
expect fun NotificationPreferencesPanel(
    accountState: AccountState,
    animeFavorites: List<AnimeResult>,
    followedManga: List<MangaReference>,
)
