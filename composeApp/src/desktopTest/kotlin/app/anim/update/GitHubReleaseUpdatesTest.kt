package app.anim.update

import kotlin.test.*

class GitHubReleaseUpdatesTest {
    private val sha = "b".repeat(64)
    private fun json(
        tag: String = "v0.8.0",
        name: String = "SpaceAnime-0.8.0-${APP_VERSION_CODE + 1}-android.apk",
        url: String = "${RELEASE_DOWNLOAD_PREFIX}$tag/$name",
        digest: String? = "sha256:$sha",
        prerelease: Boolean = false,
    ) = """{"tag_name":"$tag","draft":false,"prerelease":$prerelease,"body":"Correções",
        "assets":[{"name":"$name","browser_download_url":"$url"${digest?.let { ",\"digest\":\"$it\"" } ?: ""}}]}"""

    @Test fun newerReleaseIsParsedFromAssetName() {
        val release = parseGitHubRelease(json(), "android")!!
        assertEquals("0.8.0", release.version)
        assertEquals(APP_VERSION_CODE + 1, release.code)
        assertEquals(sha, release.sha256)
        assertEquals("Correções", release.notes)
        assertTrue(release.isValidForPersistence())
    }

    @Test fun currentOrOlderBuildAndOtherPlatformAreIgnored() {
        assertNull(parseGitHubRelease(json(name = "SpaceAnime-0.8.0-$APP_VERSION_CODE-android.apk"), "android"))
        assertNull(parseGitHubRelease(json(), "windows"))
        assertNull(parseGitHubRelease(json(prerelease = true), "android"))
    }

    @Test fun releaseWithoutDigestOrOutsideTheRepositoryIsRejected() {
        assertFails { parseGitHubRelease(json(digest = null), "android") }
        assertFails { parseGitHubRelease(json(digest = "sha1:abc"), "android") }
        assertFails { parseGitHubRelease(json(url = "https://github.com/evil/repo/releases/download/v0.8.0/x.apk"), "android") }
        assertFails { parseGitHubRelease(json(tag = "latest"), "android") }
    }

    @Test fun downloadsOnlyFollowGitHubAssetHosts() {
        assertTrue(allowedReleaseHost("https://github.com/gsgamebg/spaceanime/releases/download/v1/a.apk"))
        assertTrue(allowedReleaseHost("https://release-assets.githubusercontent.com/x"))
        assertFalse(allowedReleaseHost("http://github.com/x"))
        assertFalse(allowedReleaseHost("https://evil.example/x"))
    }

    @Test fun storedRequirementMustPointAtOfficialReleases() {
        val ok = AppRelease("0.8.0", APP_VERSION_CODE + 1, "${RELEASE_DOWNLOAD_PREFIX}v0.8.0/SpaceAnime-0.8.0-39-android.apk", sha, "")
        assertTrue(ok.isValidStoredRelease())
        assertFalse(ok.copy(url = "https://spaceanime.onrender.com/downloads/39-android.apk").isValidStoredRelease())
    }
}
