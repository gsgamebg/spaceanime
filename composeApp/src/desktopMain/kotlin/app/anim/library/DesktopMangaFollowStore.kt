package app.anim.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private object DesktopMangaFollowStores {
    private val stores = mutableMapOf<String, FileMangaFollowStore>()

    @Synchronized
    fun get(profileId: String): FileMangaFollowStore {
        return stores.getOrPut(profileId) {
            FileMangaFollowStore(mangaFollowFile(app.anim.profile.desktopDataRoot(), profileId)).also { store ->
                Runtime.getRuntime().addShutdownHook(Thread({ store.close() }, "anim-manga-follows-close"))
            }
        }
    }
}

@Composable
actual fun rememberMangaFollowStore(profileId: String): MangaFollowStore =
    remember(profileId) { DesktopMangaFollowStores.get(profileId) }
