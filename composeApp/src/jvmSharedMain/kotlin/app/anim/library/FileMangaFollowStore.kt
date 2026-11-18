package app.anim.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal fun mangaFollowFile(root: File, profileId: String): File {
    require(app.anim.profile.validProfileId(profileId))
    return File(
        root,
        if (profileId == "default") "manga-follows.json" else "profiles/$profileId/manga-follows.json"
    )
}

class FileMangaFollowStore(private val file: File) : MangaFollowStore {
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "anim-manga-follows").apply { isDaemon = true }
    }
    private var readable = true
    private val mutable = MutableStateFlow(load())
    override val state = mutable.asStateFlow()

    private fun load(): MangaFollowSnapshot {
        if (!file.exists()) return MangaFollowSnapshot()
        return try {
            require(file.length() <= 1024 * 1024) { "Follow list too large" }
            val root = Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
            require(root.getValue("version").jsonPrimitive.int == 1)
            val followed = root.getValue("followed").jsonArray.map { value ->
                val item = value.jsonObject
                MangaReference(
                    sourceId = item.getValue("sourceId").jsonPrimitive.content,
                    contentId = item.getValue("contentId").jsonPrimitive.content,
                    title = item.getValue("title").jsonPrimitive.content,
                    coverUrl = item.getValue("coverUrl").jsonPrimitive.content
                ).also(::validate)
            }
            require(followed.size <= 500)
            require(followed.distinctBy { it.sourceId to it.contentId }.size == followed.size)
            MangaFollowSnapshot(followed)
        } catch (_: Exception) {
            readable = false
            MangaFollowSnapshot(error = "Não foi possível ler a lista de mangás. O arquivo original foi preservado; novas gravações estão suspensas.")
        }
    }

    @Synchronized
    override fun toggleFollow(reference: MangaReference) {
        if (!readable) return
        try {
            validate(reference)
            val current = mutable.value.followed
            val exists = current.any { it.sourceId == reference.sourceId && it.contentId == reference.contentId }
            if (!exists) require(current.size < 500) { "A lista de mangás atingiu o limite deste perfil." }
            val next = if (exists) current.filterNot {
                it.sourceId == reference.sourceId && it.contentId == reference.contentId
            } else current + reference
            mutable.value = MangaFollowSnapshot(followed = next)
            writer.execute { persist(next) }
        } catch (error: IllegalArgumentException) {
            mutable.value = mutable.value.copy(error = error.message ?: "Mangá inválido.")
        }
    }

    private fun persist(followed: List<MangaReference>) {
        try {
            file.parentFile?.mkdirs()
            val text = buildJsonObject {
                put("version", 1)
                put("followed", JsonArray(followed.map { item ->
                    buildJsonObject {
                        put("sourceId", item.sourceId)
                        put("contentId", item.contentId)
                        put("title", item.title)
                        put("coverUrl", item.coverUrl)
                    }
                }))
            }.toString()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.outputStream().use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) {
            synchronized(this) {
                mutable.value = mutable.value.copy(error = "Não foi possível salvar a lista de mangás neste dispositivo.")
            }
        }
    }

    override suspend fun flush() = withContext(Dispatchers.IO) {
        writer.submit {}.get(10, TimeUnit.SECONDS)
        Unit
    }

    fun close() {
        writer.shutdown()
        writer.awaitTermination(10, TimeUnit.SECONDS)
    }

    private fun validate(reference: MangaReference) {
        require(reference.sourceId in setOf("dex", "yomu"))
        require(reference.contentId.length in 1..180 && reference.contentId.none(Char::isISOControl))
        require(reference.title.length in 1..180 && reference.title.none(Char::isISOControl))
        require(reference.coverUrl.length <= 2048 && reference.coverUrl.none(Char::isISOControl))
    }
}
