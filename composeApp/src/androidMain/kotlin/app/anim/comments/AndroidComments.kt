package app.anim.comments
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import app.anim.profile.validProfileId
import java.io.File
@Composable actual fun rememberComments(profileId: String): CommentsStore {
    val root = LocalContext.current.applicationContext.filesDir
    return remember(root, profileId) { require(validProfileId(profileId)); FileComments(File(root, "profiles/$profileId/comments.json")) }
}
