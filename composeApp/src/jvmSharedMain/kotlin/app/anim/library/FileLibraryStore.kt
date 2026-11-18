package app.anim.library

import app.anim.player.PlaybackState
import app.anim.sources.AnimeResult
import app.anim.sources.Episode
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

/** Single writer; atomic replacement keeps the last valid library intact on interruption. */
class FileLibraryStore(private val file: File) : LibraryStore {
    private val writer = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "anim-library").apply { isDaemon = true } }
    private var readable = true
    private val mutable = MutableStateFlow(load())
    override val state = mutable.asStateFlow()

    private fun load(): LibrarySnapshot {
        if (!file.exists()) return LibrarySnapshot()
        return try {
            require(file.length() <= 4 * 1024 * 1024) { "Library too large" }
            LibraryCodec.decode(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            readable = false
            LibrarySnapshot(error = "Não foi possível ler a biblioteca. O arquivo original foi preservado; novas gravações estão suspensas.")
        }
    }

    @Synchronized private fun change(transform: (LibrarySnapshot) -> LibrarySnapshot) {
        if (!readable) return
        val next = transform(mutable.value).copy(error = null)
        if (next == mutable.value) return
        mutable.value = next
        writer.execute {
            try {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, file.name + ".tmp")
                val bytes = LibraryCodec.encode(next).toByteArray(Charsets.UTF_8)
                temp.outputStream().use { output -> output.write(bytes); output.fd.sync() }
                try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            } catch (_: Exception) {
                synchronized(this) { mutable.value = mutable.value.copy(error = "Não foi possível salvar a biblioteca no dispositivo. Suas alterações continuam nesta sessão.") }
            }
        }
    }

    override fun toggleFavorite(anime: AnimeResult) = change { snapshot ->
        snapshot.copy(favorites = if (snapshot.isFavorite(anime)) snapshot.favorites.filterNot { it.sameAnime(anime) } else snapshot.favorites + anime)
    }

    override fun record(context: EpisodeContext, playback: PlaybackState) {
        // Do not replace a valid saved position with initial loading state or a failed stream.
        if (!playback.positionReady || playback.durationMs <= 0 || playback.positionMs <= 0 || playback.error != null) return
        val duration = playback.durationMs
        val position = playback.positionMs.coerceIn(0, duration)
        val completed = playback.ended || position.toDouble() / duration >= 0.95
        change { snapshot ->
            val previous = snapshot.progressFor(context)
            if (previous != null && previous.positionMs == position && previous.durationMs == duration && previous.completed == completed) return@change snapshot
            snapshot.copy(progress = (listOf(WatchProgress(context, position, duration, completed, System.currentTimeMillis())) + snapshot.progress.filterNot { it.context.sameEpisode(context) }).take(1000))
        }
    }

    override fun markWatched(context: EpisodeContext, watched: Boolean) = change { snapshot ->
        val old = snapshot.progressFor(context)
        val duration = old?.durationMs ?: 0
        snapshot.copy(progress = (listOf(WatchProgress(context, if (watched) duration else 0, duration, watched, System.currentTimeMillis())) + snapshot.progress.filterNot { it.context.sameEpisode(context) }).take(1000))
    }

    override suspend fun flush() = withContext(Dispatchers.IO) { writer.submit {}.get(10, TimeUnit.SECONDS); Unit }
    fun close() { writer.shutdown(); writer.awaitTermination(10, TimeUnit.SECONDS) }
}

internal object LibraryCodec {
    fun encode(snapshot: LibrarySnapshot): String = buildJsonObject {
        put("version", 1)
        put("favorites", JsonArray(snapshot.favorites.map(::anime)))
        put("progress", JsonArray(snapshot.progress.map { progress -> buildJsonObject {
            put("anime", anime(progress.context.anime))
            put("episode", buildJsonObject {
                put("id", progress.context.episode.id); put("number", progress.context.episode.number)
                put("title", progress.context.episode.title); put("url", progress.context.episode.url)
            })
            put("positionMs", progress.positionMs); put("durationMs", progress.durationMs)
            put("completed", progress.completed); put("updatedAt", progress.updatedAt)
        } }))
    }.toString()

    fun decode(text: String): LibrarySnapshot {
        val root = Json.parseToJsonElement(text).jsonObject
        require(root.getValue("version").jsonPrimitive.int == 1)
        val favorites = root.getValue("favorites").jsonArray.map { readAnime(it.jsonObject) }
        val progress = root.getValue("progress").jsonArray.map {
            val item = it.jsonObject
            val e = item.getValue("episode").jsonObject
            val episode = Episode(e.string("id"), e.getValue("number").jsonPrimitive.double, e.string("title"), e.string("url"))
            val duration = item.getValue("durationMs").jsonPrimitive.long.coerceAtLeast(0)
            WatchProgress(EpisodeContext(readAnime(item.getValue("anime").jsonObject), episode), item.getValue("positionMs").jsonPrimitive.long.coerceIn(0, duration), duration, item.getValue("completed").jsonPrimitive.boolean, item.getValue("updatedAt").jsonPrimitive.long)
        }
        return LibrarySnapshot(favorites, progress.sortedByDescending { it.updatedAt }.take(1000))
    }
    private fun anime(anime: AnimeResult) = buildJsonObject {
        put("sourceId", anime.sourceId); put("id", anime.id); put("title", anime.title)
        put("url", anime.url); put("dubbed", anime.dubbed); put("image", anime.image)
    }
    private fun readAnime(value: JsonObject) = AnimeResult(value.string("sourceId"), value.string("id"), value.string("title"), value.string("url"), value.getValue("dubbed").jsonPrimitive.boolean, value["image"]?.jsonPrimitive?.content.orEmpty())
    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
}
