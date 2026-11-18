package app.anim.comments

import app.anim.sources.AnimeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.util.UUID

class FileComments(private val file: File) : CommentsStore {
    private var readable = true
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(load())
    override val state = mutable.asStateFlow()
    private fun load(): CommentsState = try {
        if (!file.exists()) CommentsState() else {
            require(file.length() <= 1024 * 1024)
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            require(root.getValue("version").jsonPrimitive.int == 1)
            val list = root.getValue("items").jsonArray.map {
                val c = it.jsonObject
                fun string(key: String) = c.getValue(key).jsonPrimitive.content
                val comment = LocalComment(string("id"), string("animeKey"), string("author"), string("text"), c.getValue("spoiler").jsonPrimitive.boolean, c.getValue("createdAt").jsonPrimitive.long)
                require(comment.text.length in 1..1000 && comment.author.length <= 24)
                comment
            }
            require(list.size <= 200)
            CommentsState(list)
        }
    } catch (_: Exception) { readable = false; CommentsState(error = "Não foi possível ler os comentários locais. O arquivo foi preservado.") }
    private suspend fun change(transform: (List<LocalComment>) -> List<LocalComment>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!readable) return@withLock
            try {
                val items = transform(mutable.value.items)
                val json = buildJsonObject {
                    put("version", 1)
                    put("items", JsonArray(items.map { c -> buildJsonObject {
                        put("id", c.id); put("animeKey", c.animeKey); put("author", c.author); put("text", c.text); put("spoiler", c.spoiler); put("createdAt", c.createdAt)
                    } }))
                }
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.outputStream().use { it.write(json.toString().toByteArray(Charsets.UTF_8)); it.fd.sync() }
                try { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                catch (_: AtomicMoveNotSupportedException) { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                mutable.value = CommentsState(items)
            } catch (error: Exception) { mutable.value = mutable.value.copy(error = if (error is IllegalArgumentException) error.message else "Não foi possível salvar seu comentário.") }
        }
    }
    override suspend fun add(anime: AnimeResult, author: String, text: String, spoiler: Boolean) = change { list ->
        val clean = text.trim()
        require(clean.length in 1..1000) { "Escreva de 1 a 1.000 caracteres." }
        require(author.trim().length in 1..24)
        require(list.size < 200) { "Limite de 200 comentários locais atingido. Exclua um comentário para continuar." }
        require(list.count { it.animeKey == anime.commentKey() } < 50) { "Este anime já tem 50 comentários locais neste perfil." }
        listOf(LocalComment(UUID.randomUUID().toString(), anime.commentKey(), author.trim(), clean, spoiler, System.currentTimeMillis())) + list
    }
    override suspend fun remove(id: String) = change { it.filterNot { c -> c.id == id } }
}
