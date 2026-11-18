package app.anim.profile

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

data class LocalProfile(
    val id: String,
    val name: String,
    val color: Int = 0,
    val username: String = defaultProfileUsername(name),
    val bio: String = "",
    val reduceMotion: Boolean = true,
    val autoPlayNext: Boolean = false,
)

data class ProfileState(
    val profiles: List<LocalProfile> = listOf(LocalProfile("default", "Explorador")),
    val activeId: String = "default",
    val error: String? = null,
) {
    val active get() = profiles.first { it.id == activeId }
}

interface Profiles {
    val state: StateFlow<ProfileState>
    suspend fun create(
        name: String,
        color: Int,
        username: String = defaultProfileUsername(name),
        bio: String = "",
    )
    suspend fun select(id: String)
    suspend fun setReducedMotion(value: Boolean)
    suspend fun setAutoPlayNext(value: Boolean)
    suspend fun rename(
        name: String,
        color: Int,
        username: String = defaultProfileUsername(name),
        bio: String = "",
    )
}

@Composable expect fun rememberProfiles(): Profiles

fun validProfileId(id: String): Boolean = id == "default" || Regex("[a-f0-9]{32}").matches(id)

fun validProfileUsername(value: String): Boolean =
    value.length in 3..24 && Regex("[a-z0-9._]+").matches(value)

fun defaultProfileUsername(name: String): String {
    val base = name.lowercase()
        .replace(Regex("[^a-z0-9]+"), ".")
        .trim('.')
        .ifBlank { "spaceanime" }
        .take(18)
        .trim('.')
        .ifBlank { "spaceanime" }
    return if (base.length >= 3) base else (base + "anime").take(3)
}

fun sanitizeProfileName(value: String): String =
    value.trim().also {
        require(it.length in 1..24 && it.none(Char::isISOControl)) { "Use um nome de 1 a 24 caracteres." }
    }

fun sanitizeProfileUsername(value: String, fallbackName: String): String {
    val raw = value.trim().lowercase().replace(Regex("[^a-z0-9._]+"), ".").replace(Regex("\\.{2,}"), ".").trim('.')
    val candidate = raw.ifBlank { defaultProfileUsername(fallbackName) }.take(24).trim('.')
    require(validProfileUsername(candidate)) { "Use um usuário de 3 a 24 caracteres com letras, números, ponto ou sublinhado." }
    return candidate
}

fun sanitizeProfileBio(value: String): String =
    value.trim().replace(Regex("\\s+"), " ").also {
        require(it.length <= 140 && it.none(Char::isISOControl)) { "A bio deve ter até 140 caracteres." }
    }
