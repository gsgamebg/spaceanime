package app.anim.library

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

data class MangaReference(
    val sourceId: String,
    val contentId: String,
    val title: String,
    val coverUrl: String
)

data class MangaFollowSnapshot(
    val followed: List<MangaReference> = emptyList(),
    val error: String? = null
) {
    fun isFollowing(reference: MangaReference): Boolean =
        followed.any { it.sourceId == reference.sourceId && it.contentId == reference.contentId }
}

interface MangaFollowStore {
    val state: StateFlow<MangaFollowSnapshot>
    fun toggleFollow(reference: MangaReference)
    suspend fun flush()
}

@Composable
expect fun rememberMangaFollowStore(profileId: String = "default"): MangaFollowStore
