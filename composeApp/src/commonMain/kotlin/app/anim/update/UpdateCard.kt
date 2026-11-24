package app.anim.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anim.design.*

@Composable
fun UpdateCard(release: AppRelease?, checking: Boolean, message: String?, onCheck: () -> Unit) {
    val uri = LocalUriHandler.current
    var openError by remember { mutableStateOf(false) }

    SpacePanel(
        modifier = Modifier.fillMaxWidth(),
        tonal = Color(0xFF120B20),
        contentPadding = PaddingValues(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (release != null) "Nova Versão Disponível: ${release.version}" else "SpaceAnime v$APP_VERSION",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (release != null) "Atualização pronta para instalação" else "Versão estável e atualizada (Build $APP_VERSION_CODE)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (release != null) SpaceColors.secondary else SpaceColors.onSurfaceVariant
                )
            }
            if (release != null) {
                SpaceBadge("NOVA VERSÃO", SpaceColors.primary)
            } else {
                SpaceBadge("ATUALIZADO", Color(0xFF10B981))
            }
        }

        if (release != null && release.notes.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF08050F))
                    .border(1.dp, SpaceBorderColor, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpaceIcon("sparkles", null, tint = SpaceColors.secondary, modifier = Modifier.size(14.dp))
                        Text("Novidades:", style = MaterialTheme.typography.labelSmall, color = SpaceColors.secondary, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = release.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpaceColors.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            Text(
                text = if (UPDATE_MANIFEST_URL.isBlank()) "O canal de atualizações está em preparação." else "Mantenha seu SpaceAnime sempre atualizado para suporte contínuo aos servidores Space 1 e Space 2.",
                style = MaterialTheme.typography.bodySmall,
                color = SpaceColors.onSurfaceVariant
            )
        }

        if (checking) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = SpaceColors.secondary,
                trackColor = SpaceColors.surfaceVariant
            )
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = SpaceColors.secondary)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (release != null) {
                Button(
                    onClick = { openError = runCatching { uri.openUri(release.url) }.isFailure },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SpaceColors.primary, contentColor = Color.White)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SpaceIcon("download", null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Baixar atualização", fontWeight = FontWeight.Bold)
                    }
                }
            }
            OutlinedButton(
                enabled = !checking && UPDATE_MANIFEST_URL.isNotBlank(),
                onClick = onCheck,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SpaceBorderColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SpaceColors.secondary)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SpaceIcon("refresh-cw", null, tint = SpaceColors.secondary, modifier = Modifier.size(14.dp))
                    Text(if (checking) "Buscando…" else "Verificar atualizações")
                }
            }
        }

        AnimatedVisibility(visible = openError) {
            Text("Não foi possível abrir o navegador.", color = SpaceColors.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
