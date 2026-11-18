package app.anim.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

private object AndroidMangaFollowStores {
    private val stores = mutableMapOf<String, FileMangaFollowStore>()

    @Synchronized
    fun get(directory: File, profileId: String): FileMangaFollowStore {
        return stores.getOrPut(profileId) {
            FileMangaFollowStore(mangaFollowFile(directory, profileId))
        }
    }
}

@Composable
actual fun rememberMangaFollowStore(profileId: String): MangaFollowStore {
    val context = LocalContext.current.applicationContext
    return remember(context, profileId) { AndroidMangaFollowStores.get(context.filesDir, profileId) }
}
