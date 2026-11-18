package app.anim.discovery

import app.anim.sources.*
import app.anim.library.LibraryCodec
import app.anim.library.LibrarySnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

actual fun discoveryTime(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM · HH:mm"))
internal fun discoveryDue(last: Long, now: Long): Boolean = last <= 0 || now < last || now - last >= 86_400_000 ||
    Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).toLocalDate() != Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()

class FileDiscovery(private val file: File, private val repository: SourceRepository, private val now: () -> Long = System::currentTimeMillis) : DiscoveryStore {
    private val mutex = Mutex()
    private var retryAfter = 0L
    private val mutable = MutableStateFlow(DiscoveryState(load()))
    override val state = mutable.asStateFlow()
    private fun load(): List<DiscoveryPage> = try {
        if (!file.exists() || file.length() > 4 * 1024 * 1024) emptyList() else {
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            require(root.getValue("version").jsonPrimitive.int == 1)
            root.getValue("pages").jsonArray.map { element ->
                val page = element.jsonObject
                val source = page.getValue("sourceId").jsonPrimitive.content
                require(source in listOf("anitube", "animesdigital"))
                val library = LibraryCodec.decode(page.getValue("catalog").jsonPrimitive.content)
                val recent = page.getValue("recent").jsonArray.map {
                    val p = it.jsonObject
                    RecentEpisode(source, p.getValue("title").jsonPrimitive.content,
                        Episode(p.getValue("id").jsonPrimitive.content, p.getValue("number").jsonPrimitive.double, p.getValue("label").jsonPrimitive.content, p.getValue("url").jsonPrimitive.content),
                        p.getValue("image").jsonPrimitive.content, p.getValue("dubbed").jsonPrimitive.boolean)
                }.take(120)
                DiscoveryPage(source, recent, library.favorites.take(76), updatedAt = page.getValue("updatedAt").jsonPrimitive.long)
            }.take(2)
        }
    } catch (_: Exception) { emptyList() }

    override suspend fun refresh(force: Boolean) {
        if (!mutex.tryLock()) return
        try {
            val time = now()
            if (!force && time < retryAfter) return
            val old = mutable.value.pages
            val ids = listOf("anitube", "animesdigital").filter { id -> force || discoveryDue(old.firstOrNull { it.sourceId == id }?.updatedAt ?: 0, time) }
            if (ids.isEmpty()) return
            mutable.value = mutable.value.copy(loading = true, message = null)

            val fetched = coroutineScope {
                ids.map { id -> async {
                    try { withTimeout(35_000) { repository.discover(id) }.also { require(it.recent.isNotEmpty()) }.copy(updatedAt = time) }
                    catch (_: TimeoutCancellationException) { null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                } }.awaitAll()
            }
            val failed = fetched.any { it == null }
            val pages = listOf("anitube", "animesdigital").mapNotNull { id ->
                val previous = old.firstOrNull { it.sourceId == id }
                val latest = fetched.filterNotNull().firstOrNull { it.sourceId == id }
                if (latest == null) previous else latest.copy(recent = (latest.recent + previous?.recent.orEmpty()).distinctBy { it.episode.id }.take(120))
            }
            mutable.value = DiscoveryState(pages, message = if (failed) "Um servidor não respondeu. Mantivemos os últimos lançamentos disponíveis." else null)
            retryAfter = if (failed) time + 15 * 60_000 else 0
            if (fetched.any { it != null }) {
                try { withContext(Dispatchers.IO) { persist(pages) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutable.value = mutable.value.copy(message = "Lançamentos atualizados, mas o cache local não pôde ser salvo.") }
            }
        } finally { mutable.value = mutable.value.copy(loading = false); mutex.unlock() }
    }

    private fun persist(pages: List<DiscoveryPage>) {
        val text = buildJsonObject {
            put("version", 1)
            put("pages", JsonArray(pages.map { page -> buildJsonObject {
                put("sourceId", page.sourceId); put("updatedAt", page.updatedAt)
                put("catalog", LibraryCodec.encode(LibrarySnapshot(favorites = page.featured)))
                put("recent", JsonArray(page.recent.map { item -> buildJsonObject {
                    put("title", item.title); put("id", item.episode.id); put("number", item.episode.number)
                    put("label", item.episode.title); put("url", item.episode.url); put("image", item.image); put("dubbed", item.dubbed)
                } }))
            } }))
        }.toString()
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.outputStream().use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }
}

