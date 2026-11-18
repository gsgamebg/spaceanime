package app.anim.update

import kotlin.test.*

class UpdatesTest {
    private val origin = "https://space.example/updates.json"
    private fun manifest(url: String = "https://space.example/downloads/space.apk", code: Int = APP_VERSION_CODE + 1, hash: String = "a".repeat(64), schema: Int = 1) = """{"schemaVersion":$schema,"releases":{"android":{"version":"0.9.0","versionCode":$code,"url":"$url","sha256":"$hash","notes":"Novo layout"}}}"""
    @Test fun newerVersionIsOfferedAndCurrentOrOlderIsNot() {
        assertEquals("0.9.0", parseRelease(manifest(), origin, "android")!!.version)
        assertNull(parseRelease(manifest(code = APP_VERSION_CODE), origin, "android"))
        assertNull(parseRelease(manifest(code = 1), origin, "android"))
    }
    @Test fun untrustedOrMalformedDownloadsAreRejected() {
        for (url in listOf("http://space.example/a.apk", "https://evil.example/a.apk", "https://space.example:444/a.apk", "https://user:pass@space.example/a.apk", "https://space.example/a.exe", "javascript:alert(1)")) {
            assertFails { parseRelease(manifest(url), origin, "android") }
        }
        assertFails { parseRelease(manifest(hash = "bad"), origin, "android") }
        assertFails { parseRelease(manifest(schema = 2), origin, "android") }
    }

    @Test fun failedOrRegressingChecksDoNotRemoveAnAlreadyRequiredRelease() {
        val required = AppRelease("0.8.0", APP_VERSION_CODE + 4, "https://space.example/a.apk", "a".repeat(64), "")
        val older = required.copy(version = "0.7.20", code = APP_VERSION_CODE + 1)

        assertEquals(required, retainHighestRelease(required, null))
        assertEquals(required, retainHighestRelease(required, older))
        assertEquals(required.copy(code = required.code + 1), retainHighestRelease(required, required.copy(code = required.code + 1)))
        assertEquals(required, retainHighestRelease(null, required))
    }

    @Test fun startupGateUsesOnlyAStoredHigherBuild() {
        val required = AppRelease("0.9.0", APP_VERSION_CODE + 1, "https://spaceanime.onrender.com/app.apk", "a".repeat(64), "")

        assertNull(requiredReleaseForStartup(APP_VERSION_CODE, null))
        assertNull(requiredReleaseForStartup(APP_VERSION_CODE, required.copy(code = APP_VERSION_CODE)))
        assertNull(requiredReleaseForStartup(APP_VERSION_CODE, required.copy(code = APP_VERSION_CODE - 1)))
        assertEquals(required, requiredReleaseForStartup(APP_VERSION_CODE, required))
    }

    @Test fun newRequirementsNeverRegress() {
        val required = AppRelease("0.9.0", APP_VERSION_CODE + 3, "https://spaceanime.onrender.com/app.apk", "a".repeat(64), "")
        val lower = required.copy(version = "0.8.0", code = APP_VERSION_CODE + 1)

        assertEquals(required, updateRequirementAfterCheck(APP_VERSION_CODE, required, null))
        assertEquals(required, updateRequirementAfterCheck(APP_VERSION_CODE, required, lower))
        assertEquals(required, updateRequirementAfterCheck(APP_VERSION_CODE, required, required.copy(code = required.code)))
        assertEquals(required.copy(code = required.code + 1), updateRequirementAfterCheck(APP_VERSION_CODE, required, required.copy(code = required.code + 1)))
        assertNull(updateRequirementAfterCheck(APP_VERSION_CODE, required.copy(code = APP_VERSION_CODE), null))
        assertEquals(lower, updateRequirementAfterCheck(APP_VERSION_CODE, required.copy(code = APP_VERSION_CODE), lower))
    }
}
