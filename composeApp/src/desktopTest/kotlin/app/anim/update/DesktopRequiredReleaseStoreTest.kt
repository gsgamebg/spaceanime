package app.anim.update

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DesktopRequiredReleaseStoreTest {
    private val release = AppRelease(
        "0.9.0",
        APP_VERSION_CODE + 1,
        "https://spaceanime.onrender.com/downloads/space.apk",
        "a".repeat(64),
        "Atualização necessária",
    )

    @Test fun requiredReleaseSurvivesReopening() = runBlocking {
        val file = File(root(), "required-release.json")
        DesktopRequiredReleaseStore(file).save(release)

        assertEquals(release, DesktopRequiredReleaseStore(file).load())
    }

    @Test fun corruptOrOversizedStateIsRejected() = runBlocking {
        val corrupt = File(root(), "corrupt.json").apply { writeText("{") }
        assertFails { DesktopRequiredReleaseStore(corrupt).load() }
        assertEquals("{", corrupt.readText())

        val oversized = File(root(), "oversized.json").apply { writeText("x".repeat(8193)) }
        assertFails { DesktopRequiredReleaseStore(oversized).load() }
        assertEquals(8193, oversized.length().toInt())
    }

    @Test fun clearRemovesTheStoredRequirement() = runBlocking {
        val file = File(root(), "required-release.json")
        val store = DesktopRequiredReleaseStore(file)
        store.save(release)
        store.clear()

        assertNull(store.load())
    }

    @Test fun saveFailureIsReported() = runBlocking {
        val parentFile = File(root(), "not-a-directory").apply { writeText("block") }
        val store = DesktopRequiredReleaseStore(File(parentFile, "required-release.json"))

        assertFailsWith<Exception> { store.save(release) }
        Unit
    }

    private fun root(): File =
        Files.createTempDirectory(File("build").apply { mkdirs() }.toPath(), "required-release-").toFile()
}
