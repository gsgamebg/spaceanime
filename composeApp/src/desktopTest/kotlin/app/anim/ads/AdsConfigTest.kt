package app.anim.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
