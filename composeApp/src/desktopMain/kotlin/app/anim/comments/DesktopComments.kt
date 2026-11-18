package app.anim.comments
import androidx.compose.runtime.*
import app.anim.profile.*
import java.io.File
@Composable actual fun rememberComments(profileId: String): CommentsStore = remember(profileId) {
    require(validProfileId(profileId)); FileComments(File(desktopDataRoot(), "profiles/$profileId/comments.json"))
}
