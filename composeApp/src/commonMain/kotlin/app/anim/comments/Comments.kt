package app.anim.comments

import androidx.compose.runtime.Composable
import app.anim.sources.AnimeResult
import kotlinx.coroutines.flow.StateFlow

data class LocalComment(val id: String, val animeKey: String, val author: String, val text: String, val spoiler: Boolean, val createdAt: Long)
data class CommentsState(val items: List<LocalComment> = emptyList(), val error: String? = null)
fun AnimeResult.commentKey(): String = "$sourceId|$id|$dubbed"
interface CommentsStore {
    val state: StateFlow<CommentsState>
    suspend fun add(anime: AnimeResult, author: String, text: String, spoiler: Boolean)
    suspend fun remove(id: String)
}
@Composable expect fun rememberComments(profileId: String): CommentsStore
