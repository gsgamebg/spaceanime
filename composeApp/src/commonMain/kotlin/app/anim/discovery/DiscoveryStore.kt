package app.anim.discovery

import androidx.compose.runtime.Composable
import app.anim.sources.*
import kotlinx.coroutines.flow.StateFlow

data class DiscoveryState(val pages: List<DiscoveryPage> = emptyList(), val loading: Boolean = false, val message: String? = null)
interface DiscoveryStore {
    val state: StateFlow<DiscoveryState>
    suspend fun refresh(force: Boolean = false)
}
@Composable expect fun rememberDiscovery(repository: SourceRepository): DiscoveryStore
expect fun discoveryTime(timestamp: Long): String
