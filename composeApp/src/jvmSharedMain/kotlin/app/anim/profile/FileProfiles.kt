package app.anim.profile

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

class FileProfiles(private val file: File) : Profiles {
    private var readable = true
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(load())
    override val state = mutable.asStateFlow()
    private fun load(): ProfileState {
        if (!file.exists()) return ProfileState()
        return try {
            require(file.length() <= 16384)
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            val version = root.getValue("version").jsonPrimitive.int
            require(version in 1..2)
            val list = root.getValue("profiles").jsonArray.map {
                val p = it.jsonObject
                val name = p.getValue("name").jsonPrimitive.content
                LocalProfile(
                    id = p.getValue("id").jsonPrimitive.content,
                    name = name,
                    color = p.getValue("color").jsonPrimitive.int,
                    username = p["username"]?.jsonPrimitive?.content?.takeIf { version >= 2 } ?: defaultProfileUsername(name),
                    bio = p["bio"]?.jsonPrimitive?.content?.takeIf { version >= 2 }.orEmpty(),
                    reduceMotion = p["reduceMotion"]?.jsonPrimitive?.boolean ?: true,
                )
            }
            require(list.size in 1..6 && list.map { it.id }.distinct().size == list.size)
            require(list.any { it.id == "default" })
            require(list.all {
                validProfileId(it.id) &&
                    it.name.isNotBlank() &&
                    it.name.length <= 24 &&
                    it.name.none(Char::isISOControl) &&
                    it.color in 0..3 &&
                    validProfileUsername(it.username) &&
                    it.bio.length <= 140 && it.bio.none(Char::isISOControl)
            })
            val active = root.getValue("activeId").jsonPrimitive.content
            require(list.any { it.id == active })
            ProfileState(list, active)
        } catch (_: Exception) {
            readable = false
            ProfileState(error = "Não foi possível ler os perfis. O arquivo foi preservado; alterações estão suspensas.")
        }
    }
    private suspend fun change(transform: (ProfileState) -> ProfileState) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!readable) return@withLock
            try {
                val next = transform(mutable.value).copy(error = null)
                val text = buildJsonObject {
                    put("version", 2)
                    put("activeId", next.activeId)
                    put("profiles", JsonArray(next.profiles.map { p ->
                        buildJsonObject {
                            put("id", p.id)
                            put("name", p.name)
                            put("color", p.color)
                            put("username", p.username)
                            put("bio", p.bio)
                            put("reduceMotion", p.reduceMotion)
                        }
                    }))
                }.toString()
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, file.name + ".tmp")
                temp.outputStream().use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
                try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                mutable.value = next
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(error = if (e is IllegalArgumentException) e.message else "Não foi possível salvar o perfil. Tente novamente.")
            }
        }
    }
    override suspend fun create(name: String, color: Int, username: String, bio: String) = change {
        require(it.profiles.size < 6) { "Você pode ter até seis perfis neste dispositivo." }
        require(color in 0..3)
        val cleanName = sanitizeProfileName(name)
        val cleanUsername = sanitizeProfileUsername(username, cleanName)
        val cleanBio = sanitizeProfileBio(bio)
        require(it.profiles.none { profile -> profile.username == cleanUsername }) { "Esse nome de usuário já está em uso neste dispositivo." }
        val p = LocalProfile(UUID.randomUUID().toString().replace("-", ""), cleanName, color, cleanUsername, cleanBio)
        it.copy(profiles = it.profiles + p, activeId = p.id)
    }
    override suspend fun select(id: String) = change {
        require(it.profiles.any { p -> p.id == id }) { "Perfil não encontrado." }
        it.copy(activeId = id)
    }
    override suspend fun setReducedMotion(value: Boolean) = change { state ->
        state.copy(profiles = state.profiles.map { if (it.id == state.activeId) it.copy(reduceMotion = value) else it })
    }
    override suspend fun rename(name: String, color: Int, username: String, bio: String) = change {
        val cleaned = sanitizeProfileName(name)
        val cleanUsername = sanitizeProfileUsername(username, cleaned)
        val cleanBio = sanitizeProfileBio(bio)
        require(color in 0..3)
        require(it.profiles.none { profile -> profile.id != it.activeId && profile.username == cleanUsername }) {
            "Esse nome de usuário já está em uso neste dispositivo."
        }
        it.copy(profiles = it.profiles.map { p ->
            if (p.id == it.activeId) p.copy(name = cleaned, color = color, username = cleanUsername, bio = cleanBio) else p
        })
    }
}
