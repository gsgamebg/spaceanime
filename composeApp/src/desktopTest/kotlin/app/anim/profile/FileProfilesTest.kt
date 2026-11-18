package app.anim.profile

import app.anim.library.*
import app.anim.sources.AnimeResult
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FileProfilesTest {
    @Test fun autoPlayNextDefaultsForLegacyProfilesAndPersistsWithoutChangingOtherFields() = runBlocking {
        listOf(
            """{"version":1,"profiles":[{"id":"default","name":"Gabriel","color":0}],"activeId":"default"}""",
            """{"version":2,"profiles":[{"id":"default","name":"Gabriel","color":0,"username":"gabi.space","bio":"Animes","reduceMotion":false}],"activeId":"default"}""",
        ).forEachIndexed { index, json ->
            val file = File(root(), "profiles.json").apply { writeText(json) }
            val store = FileProfiles(file)
            assertFalse(store.state.value.active.autoPlayNext)
            if (index == 1) {
                assertEquals("gabi.space", store.state.value.active.username)
                assertEquals("Animes", store.state.value.active.bio)
                assertFalse(store.state.value.active.reduceMotion)
            }

            store.setAutoPlayNext(true)

            val restored = FileProfiles(file).state.value.active
            assertTrue(restored.autoPlayNext)
            if (index == 1) {
                assertEquals("gabi.space", restored.username)
                assertEquals("Animes", restored.bio)
                assertFalse(restored.reduceMotion)
            }
        }
    }

    @Test fun olderProfilesMigrateAndMotionPreferencePersists() = runBlocking {
        val file = File(root(), "profiles.json")
        file.writeText("""{"version":1,"profiles":[{"id":"default","name":"Gabriel","color":0}],"activeId":"default"}""")
        val store = FileProfiles(file)
        assertEquals("gabriel", store.state.value.active.username)
        assertTrue(store.state.value.active.reduceMotion)
        store.rename("Gabriel", 1, "gabriel.space", "Animes e histórias")
        store.setReducedMotion(false)
        val restored = FileProfiles(file).state.value.active
        assertEquals("Animes e histórias", restored.bio)
        assertEquals("gabriel.space", restored.username)
        assertFalse(restored.reduceMotion)
    }
    private fun root() = Files.createTempDirectory(File("build").apply { mkdirs() }.toPath(), "profiles-").toFile()
    @Test fun profilesSurviveReopeningWithActiveSelectionAndRenaming() = runBlocking {
        val file = File(root(), "profiles.json")
        val profiles = FileProfiles(file)
        profiles.create("Gabriel", 2)
        val id = profiles.state.value.activeId
        assertNotEquals("default", id)
        profiles.rename("Gabi", 1)
        val reopened = FileProfiles(file)
        assertEquals(id, reopened.state.value.activeId)
        assertEquals("Gabi", reopened.state.value.active.name)
        reopened.select("default")
        assertEquals("default", FileProfiles(file).state.value.activeId)
    }
    @Test fun corruptFileIsPreservedAndProfilePathsAreRestricted() = runBlocking {
        val file = File(root(), "profiles.json").apply { writeText("invalid") }
        val store = FileProfiles(file)
        store.create("Teste", 0)
        assertNotNull(store.state.value.error)
        assertEquals("invalid", file.readText())
        assertFalse(validProfileId("../default"))
        assertFalse(validProfileId("C:/temp"))
        assertTrue(validProfileId("default"))
    }
    @Test fun profileLimitAndInvalidNamesDoNotChangeSelection() = runBlocking {
        val store = FileProfiles(File(root(), "profiles.json"))
        store.create(" ", 0)
        assertEquals(1, store.state.value.profiles.size)
        repeat(5) { store.create("Perfil $it", it % 4) }
        val last = store.state.value.activeId
        store.create("Extra", 0)
        assertEquals(6, store.state.value.profiles.size)
        assertEquals(last, store.state.value.activeId)
        store.select("not-a-profile")
        assertEquals(last, store.state.value.activeId)
    }
    @Test fun legacyLibraryRemainsSeparateFromNewProfile() = runBlocking {
        val root = root()
        val legacy = FileLibraryStore(File(root, "library.json"))
        val other = FileLibraryStore(File(root, "profiles/0123456789abcdef0123456789abcdef/library.json"))
        try {
            legacy.toggleFavorite(AnimeResult("anitube", "one", "Favorito antigo", "https://www.anitube.zip/video/1", false))
            legacy.flush(); other.flush()
            assertTrue(other.state.value.favorites.isEmpty())
            assertEquals(1, legacy.state.value.favorites.size)
        } finally { legacy.close(); other.close() }
    }
}
