package app.anim.library

import app.anim.player.PlaybackState
import app.anim.sources.AnimeResult
import app.anim.sources.Episode
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class FileLibraryStoreTest {
    private fun location(): File = Files.createTempDirectory(File("build").apply { mkdirs() }.toPath(), "anim-library-test-").resolve("library.json").toFile()
    private val anime = AnimeResult("anitube", "video/10", "Teste", "https://www.anitube.zip/video/10", false)
    private val episode = Episode("video/11", 1.0, "Episódio 1", "https://www.anitube.zip/video/11")
    private val context = EpisodeContext(anime, episode)

    @Test fun favoritesAndProgressSurviveReopening() = runBlocking {
        val file = location()
        val store = FileLibraryStore(file)
        store.toggleFavorite(anime)
        store.record(context, PlaybackState(positionMs = 20000, durationMs = 60000))
        store.flush(); store.close()
        val reopened = FileLibraryStore(file)
        try {
            assertTrue(reopened.state.value.isFavorite(anime))
            assertEquals(20000L, reopened.state.value.progressFor(context)?.resumePosition())
            assertFalse(file.readText().contains("headers"))
        } finally { reopened.close() }
    }

    @Test fun sourceAndAudioVersionsNeverShareProgress() = runBlocking {
        val store = FileLibraryStore(location())
        try {
            store.record(context, PlaybackState(positionMs = 10000, durationMs = 60000))
            store.record(context.copy(anime = anime.copy(dubbed = true)), PlaybackState(positionMs = 20000, durationMs = 60000))
            store.record(context.copy(anime = anime.copy(sourceId = "animesdigital")), PlaybackState(positionMs = 30000, durationMs = 60000))
            assertEquals(3, store.state.value.progress.size)
            assertEquals(10000L, store.state.value.progressFor(context)?.positionMs)
            store.flush()
        } finally { store.close() }
    }

    @Test fun loadingFailedAndUnrestoredStatesDoNotEraseProgress() = runBlocking {
        val store = FileLibraryStore(location())
        try {
            store.record(context, PlaybackState(positionMs = 20000, durationMs = 60000))
            store.record(context, PlaybackState())
            store.record(context, PlaybackState(positionMs = 1000, durationMs = 60000, positionReady = false))
            store.record(context, PlaybackState(positionMs = 1000, durationMs = 60000, error = "failed"))
            assertEquals(20000L, store.state.value.progressFor(context)?.positionMs)
            store.flush()
        } finally { store.close() }
    }

    @Test fun completionAndManualUnwatchHaveExplicitResumeSemantics() = runBlocking {
        val store = FileLibraryStore(location())
        try {
            store.record(context, PlaybackState(positionMs = 59000, durationMs = 60000))
            assertTrue(store.state.value.progressFor(context)!!.completed)
            assertEquals(0L, store.state.value.progressFor(context)!!.resumePosition())
            store.markWatched(context, false)
            assertFalse(store.state.value.progressFor(context)!!.completed)
            assertEquals(0L, store.state.value.progressFor(context)!!.positionMs)
            store.flush()
        } finally { store.close() }
    }

    @Test fun corruptOrFutureLibraryIsPreservedInsteadOfOverwritten() = runBlocking {
        for (content in listOf("{broken", """{"version":999,"favorites":[],"progress":[]}""")) {
            val file = location().apply { writeText(content) }
            val store = FileLibraryStore(file)
            try {
                assertNotNull(store.state.value.error)
                store.toggleFavorite(anime)
                store.flush()
                assertEquals(content, file.readText())
            } finally { store.close() }
        }
    }

    @Test fun repeatedWritesLeaveOnlyValidLatestSnapshot() = runBlocking {
        val file = location()
        val store = FileLibraryStore(file)
        try {
            repeat(25) { store.record(context, PlaybackState(positionMs = (it + 1) * 1000L, durationMs = 60000)) }
            store.flush()
            assertEquals(25000L, LibraryCodec.decode(file.readText()).progressFor(context)?.positionMs)
            assertFalse(File(file.parentFile, file.name + ".tmp").exists())
        } finally { store.close() }
    }
}
