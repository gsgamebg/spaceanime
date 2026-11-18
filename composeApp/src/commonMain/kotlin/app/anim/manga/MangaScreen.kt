package app.anim.manga
import androidx.compose.runtime.Composable
import androidx.compose.foundation.ScrollState
@Composable expect fun MangaScreen(searchRequest: String, scrollState: ScrollState, profileId: String = "default")
