package app.anim.library

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileMangaFollowStoreTest {
    private fun location(): File =
        Files.createTempDirectory(File("build").apply { mkdirs() }.toPath(), "manga-follow-test-")
            .resolve("follows.json").toFile()

    private val dex = MangaReference("dex", "same-id", "Obra Dex", "https://uploads.mangadex.org/cover.jpg")
    private val yomu = MangaReference("yomu", "same-id", "Obra Yomu", "https://yomu.tauruus.com/cover.jpg")

    @Test fun profileIdsResolveToSeparateFollowFiles() {
        val root = location().parentFile
        val defaultFile = mangaFollowFile(root, "default")
        val profileFile = mangaFollowFile(root, "0123456789abcdef0123456789abcdef")

        assertEquals(File(root, "manga-follows.json"), defaultFile)
        assertEquals(File(root, "profiles/0123456789abcdef0123456789abcdef/manga-follows.json"), profileFile)
    }

    @Test fun followsPersistAcrossReopenAndKeepProviderIdentity() = runBlocking {
        val file = location()
        val store = FileMangaFollowStore(file)
        store.toggleFollow(dex)
        store.toggleFollow(yomu)
        store.flush()
        store.close()

        val reopened = FileMangaFollowStore(file)
        try {
            assertEquals(setOf("dex", "yomu"), reopened.state.value.followed.map { it.sourceId }.toSet())
            assertTrue(reopened.state.value.isFollowing(dex))
            assertTrue(reopened.state.value.isFollowing(yomu))
        } finally {
            reopened.close()
        }
    }

    @Test fun corruptFileIsPreservedAndFurtherWritesAreSuspended() = runBlocking {
        val file = location().apply { writeText("{broken") }
        val store = FileMangaFollowStore(file)
        try {
            assertNotNull(store.state.value.error)
            store.toggleFollow(dex)
            store.flush()
            assertEquals("{broken", file.readText())
        } finally {
            store.close()
        }
    }
}
