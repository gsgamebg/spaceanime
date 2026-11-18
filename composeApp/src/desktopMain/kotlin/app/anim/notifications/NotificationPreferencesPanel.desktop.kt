package app.anim.notifications

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.anim.auth.AccountState
import app.anim.design.SpacePanel
import app.anim.design.SpaceColors
import app.anim.library.MangaReference
import app.anim.sources.AnimeResult

@Composable
actual fun NotificationPreferencesPanel(
    accountState: AccountState,
    animeFavorites: List<AnimeResult>,
    followedManga: List<MangaReference>,
) {
    SpacePanel(Modifier.fillMaxWidth()) {
        Text("Notificações push estão disponíveis somente no Android.", color = SpaceColors.onSurfaceVariant)
    }
}
