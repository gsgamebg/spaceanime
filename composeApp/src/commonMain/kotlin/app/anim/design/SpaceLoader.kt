package app.anim.design

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Plays an animated WebP/GIF from composeResources with full alpha (no black box). Static when motion is reduced. */
@Composable expect fun AnimatedAsset(path: String, contentDescription: String?, modifier: Modifier = Modifier)

const val LOADING_ANIMATION = "files/spaceanime-loading.webp"
const val PLAYER_LOADING_IMAGE = "files/spaceanime-player-loading.webp"

/** Branded loading state: transparent character animation + one short line of status. */
@Composable fun SpaceLoader(message: String? = null, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AnimatedAsset(LOADING_ANIMATION, "Carregando", Modifier.width(size).height(size * (512f / 384f)))
        if (message != null) Text(message, color = Color(0xFFC4B5FD), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}
