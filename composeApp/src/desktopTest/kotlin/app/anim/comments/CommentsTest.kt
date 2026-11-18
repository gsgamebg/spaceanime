package app.anim.comments

import app.anim.sources.AnimeResult
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class CommentsTest {
    private val anime = AnimeResult("anitube", "video/1", "Teste", "https://www.anitube.zip/video/1", false)
    private fun file() = Files.createTempDirectory(File("build").apply { mkdirs() }.toPath(), "comments-").resolve("comments.json").toFile()
    @Test fun commentsPersistAndStayIsolatedByProfileAndAnime() = runBlocking {
        val file = file(); val store = FileComments(file)
        store.add(anime, "Gabriel", "Gostei do episódio", true)
        val reopened = FileComments(file)
        assertTrue(reopened.state.value.items.single().spoiler)
        assertEquals(anime.commentKey(), reopened.state.value.items.single().animeKey)
        assertNotEquals(anime.commentKey(), anime.copy(dubbed = true).commentKey())
        assertTrue(FileComments(file()).state.value.items.isEmpty())
        reopened.remove(reopened.state.value.items.single().id)
        assertTrue(FileComments(file).state.value.items.isEmpty())
    }
    @Test fun invalidContentAndCorruptFilesDoNotOverwriteData() = runBlocking {
        val file = file(); val store = FileComments(file)
        store.add(anime, "Gabriel", " ", false); assertTrue(store.state.value.items.isEmpty())
        store.add(anime, "Gabriel", "x".repeat(1001), false); assertTrue(store.state.value.items.isEmpty())
        file.writeText("invalid")
        FileComments(file).add(anime, "Gabriel", "Teste", false)
        assertEquals("invalid", file.readText())
    }
}
