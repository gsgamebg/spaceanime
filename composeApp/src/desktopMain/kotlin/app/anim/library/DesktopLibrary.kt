package app.anim.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

private object DesktopLibrary {
    private val stores = mutableMapOf<String, FileLibraryStore>()
    @Synchronized fun get(id: String): FileLibraryStore {
        require(app.anim.profile.validProfileId(id))
        return stores.getOrPut(id) { FileLibraryStore(File(app.anim.profile.desktopDataRoot(), if (id == "default") "library.json" else "profiles/$id/library.json")).also { store ->
            Runtime.getRuntime().addShutdownHook(Thread({ store.close() }, "anim-library-close"))
        } }
    }
}

@Composable
actual fun rememberLibraryStore(profileId: String): LibraryStore = remember(profileId) { DesktopLibrary.get(profileId) }
