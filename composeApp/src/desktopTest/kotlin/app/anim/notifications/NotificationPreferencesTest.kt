package app.anim.notifications

import app.anim.library.MangaReference
import app.anim.sources.AnimeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPreferencesTest {
    @Test
    fun categoryControlsAreEnabledWhenIdleAndDisabledWhileSaving() {
        assertTrue(notificationPreferencesControlsEnabled(isBusy = false))
        assertFalse(notificationPreferencesControlsEnabled(isBusy = true))
    }

    @Test
    fun preferencesAreOffByDefaultAndValidateDigestFields() {
        val defaults = NotificationPreferences()

        assertFalse(defaults.anime)
        assertFalse(defaults.manga)
        assertFalse(defaults.announcements)
        assertEquals("20:00", defaults.digestTime)
        assertEquals("America/Sao_Paulo", defaults.timeZone)
        assertTrue(defaults.isValid())
        assertFalse(defaults.copy(digestTime = "24:00").isValid())
        assertFalse(defaults.copy(timeZone = "Bad Zone").isValid())
    }

    @Test
    fun subscriptionProjectionIncludesOnlyEnabledFollowedIdentifiers() {
        val favorites = listOf(
            AnimeResult("anitube", "video/42", "Série", "https://www.anitube.zip/video/42", true),
            AnimeResult("animesdigital", "anime/a/example", "Outra", "https://animesdigital.org/anime/a/example", false),
        )
        val followed = listOf(
            MangaReference("dex", "c0ffee00-cafe-4000-8000-000000000001", "Mangá Dex", ""),
            MangaReference("yomu", "example-slug", "Mangá Yomu", ""),
        )

        val subscriptions = projectNotificationSubscriptions(
            favorites,
            followed,
            NotificationPreferences(anime = true, manga = true),
        )

        assertEquals(
            listOf(
                NotificationSubscription("anime", "anitube", "video/42", "dubbed"),
                NotificationSubscription("anime", "animesdigital", "anime/a/example", "subtitled"),
                NotificationSubscription("manga", "dex", "c0ffee00-cafe-4000-8000-000000000001", "none"),
                NotificationSubscription("manga", "yomu", "example-slug", "none"),
            ),
            subscriptions,
        )
        assertEquals(emptyList(), projectNotificationSubscriptions(favorites, followed, NotificationPreferences()))
    }
}
