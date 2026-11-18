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

    @Test fun adsRequireFreshConsentAndCompletedPrivacyForm() {
        assertTrue(adsMayBeRequested(consentRefreshSucceeded = true, formCompleted = true, canRequestAds = true))
        assertFalse(adsMayBeRequested(consentRefreshSucceeded = false, formCompleted = true, canRequestAds = true))
        assertFalse(adsMayBeRequested(consentRefreshSucceeded = true, formCompleted = false, canRequestAds = true))
        assertFalse(adsMayBeRequested(consentRefreshSucceeded = true, formCompleted = true, canRequestAds = false))
    }
}
