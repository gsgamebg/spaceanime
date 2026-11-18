package app.anim.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

private object AndroidLibrary {
    private val stores = mutableMapOf<String, FileLibraryStore>()
    @Synchronized fun get(directory: File, id: String): FileLibraryStore {
        require(app.anim.profile.validProfileId(id))
        return stores.getOrPut(id) { FileLibraryStore(File(directory, if (id == "default") "library.json" else "profiles/$id/library.json")) }
    }
}

@Composable
actual fun rememberLibraryStore(profileId: String): LibraryStore {
    val context = LocalContext.current.applicationContext
    return remember(context, profileId) { AndroidLibrary.get(context.filesDir, profileId) }
}

