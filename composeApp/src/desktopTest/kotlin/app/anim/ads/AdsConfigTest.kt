package app.anim.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdsConfigTest {
    @Test fun validPublicFlagsAreParsed() {
        assertEquals(
            AdsConfiguration(enabled = true, homeBanner = true, catalogBanner = false),
            parseAdsConfiguration(
                """{"schemaVersion":1,"ads":{"enabled":true,"homeBanner":true,"catalogBanner":false}}""",
            ),
        )
    }

    @Test fun invalidOrOversizedConfigurationStaysUnavailable() {
        val invalid = listOf(
            """{"schemaVersion":2,"ads":{"enabled":true,"homeBanner":true,"catalogBanner":true}}""",
            """{"schemaVersion":1,"ads":{"enabled":true,"homeBanner":"true","catalogBanner":true}}""",
            """{"schemaVersion":1,"ads":{"enabled":true,"homeBanner":true}}""",
            """{"schemaVersion":1,"ads":{"enabled":true,"homeBanner":true,"catalogBanner":true,"adminKey":"private"}}""",
            """{"schemaVersion":1,"ads":{"enabled":true,"homeBanner":true,"catalogBanner":true},"serviceKey":"private"}""",
            " ".repeat(4097),
        )

        invalid.forEach { assertNull(parseAdsConfiguration(it)) }
    }

    @Test fun schemaTwoCarriesProviderAndFullScreenFormats() {
        val parsed = parseAdsConfiguration(
            """{"schemaVersion":2,"ads":{"enabled":true,"provider":"startio","homeBanner":true,"catalogBanner":true,"interstitialEnabled":true,"interstitialEveryEpisodes":3,"rewardedEnabled":true,"rewardedPassMinutes":120}}""",
        )!!
        assertEquals(AdProvider.StartIo, parsed.provider)
        assertTrue(parsed.interstitials && parsed.rewarded)
        assertEquals(3, parsed.interstitialEveryEpisodes)
        assertNull(parseAdsConfiguration("""{"schemaVersion":2,"ads":{"enabled":true,"provider":"other","homeBanner":true,"catalogBanner":true,"interstitialEnabled":true,"interstitialEveryEpisodes":3,"rewardedEnabled":true,"rewardedPassMinutes":120}}"""))
        assertNull(parseAdsConfiguration("""{"schemaVersion":2,"ads":{"enabled":true,"provider":"startio","homeBanner":true,"catalogBanner":true,"interstitialEnabled":true,"interstitialEveryEpisodes":0,"rewardedEnabled":true,"rewardedPassMinutes":120}}"""))
        // AdMob has no interstitial/rewarded units configured, so those formats stay off.
        assertFalse(parsed.copy(provider = AdProvider.AdMob).interstitials)
    }

    @Test fun interstitialShowsEveryNthEpisodeNeverTheFirst() {
        assertEquals(listOf(3, 6, 9), (1..10).filter { interstitialDue(it, 3) })
        assertTrue((1..5).none { interstitialDue(it, 0) })
    }

    @Test fun adsRequireFreshConsentAndCompletedPrivacyForm() {
        assertTrue(adsMayBeRequested(consentRefreshSucceeded = true, formCompleted = true, canRequestAds = true))
        assertFalse(adsMayBeRequested(consentRefreshSucceeded = false, formCompleted = true, canRequestAds = true))
        assertFalse(adsMayBeRequested(consentRefreshSucceeded = true, formCompleted = false, canRequestAds = true))
        assertFalse(adsMayBeRequested(consentRefreshSucceeded = true, formCompleted = true, canRequestAds = false))
    }
}
