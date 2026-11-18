package app.anim.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anim.design.*

@Composable
fun MandatoryUpdateScreen(
    release: AppRelease,
    checking: Boolean,
    message: String?,
    onCheck: () -> Unit
) {
    val uri = LocalUriHandler.current
    var openError by remember { mutableStateOf(false) }
    val motion = LocalSpaceMotionEnabled.current
    val transition = rememberInfiniteTransition(label = "update-glow")
    val pulseGlow by if (motion) {
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse-glow"
        )
    } else {
        remember { mutableStateOf(0.7f) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SpaceColors.background,
        contentColor = SpaceColors.onSurface
    ) {
        Box(Modifier.fillMaxSize()) {
            OrbitalArt(Modifier.fillMaxSize())

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xCC020617),
                                Color(0xF2020617),
                                Color(0xFF020617)
                            )
                        )
                    )
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Logo com halo de brilho pulsante
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        SpaceColors.primary.copy(alpha = pulseGlow * 0.45f),
                                        SpaceColors.secondary.copy(alpha = pulseGlow * 0.2f),
                                        Color.Transparent
                                    )
                                )
                            )
                    ) {
                        SpaceLogo(Modifier.size(72.dp))
                    }

                    // Badges informativas
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpaceBadge("✦ ATUALIZAÇÃO OBRIGATÓRIA ✦", color = SpaceColors.primary)
                        SpaceBadge("SPACE v${release.version}", color = SpaceColors.secondary)
                    }

                    // Títulos
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Uma Nova Órbita Está Pronta",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Para continuar assistindo aos novos episódios, preservar a segurança da sua transmissão e acessar os servidores Space atualizados, instale a versão ${release.version}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpaceColors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }

                    // Card de detalhes com comparativo de versões
                    SpacePanel(
                        modifier = Modifier.fillMaxWidth(),
                        tonal = Color(0xFF0F172A),
                        contentPadding = PaddingValues(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Versão Atual", style = MaterialTheme.typography.labelSmall, color = SpaceColors.onSurfaceVariant)
                                Text("v$APP_VERSION", style = MaterialTheme.typography.titleMedium, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                Text("Build $APP_VERSION_CODE", style = MaterialTheme.typography.bodySmall, color = SpaceColors.onSurfaceVariant.copy(alpha = 0.7f))
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SpaceColors.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                SpaceIcon("arrow-right", null, tint = SpaceColors.secondary, modifier = Modifier.size(18.dp))
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("Nova Versão", style = MaterialTheme.typography.labelSmall, color = SpaceColors.secondary)
                                Text("v${release.version}", style = MaterialTheme.typography.titleMedium, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                Text("Build ${release.code}", style = MaterialTheme.typography.bodySmall, color = SpaceColors.secondary.copy(alpha = 0.8f))
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = SpaceColors.outline.copy(alpha = 0.3f)
                        )

                        // Notas de versão
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SpaceIcon("sparkles", null, tint = SpaceColors.secondary, modifier = Modifier.size(16.dp))
                                Text(
                                    "O que mudou nesta versão:",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0A0F1E))
                                    .border(1.dp, SpaceBorderColor, RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = release.notes.ifBlank { "Melhorias de desempenho, novo visual, animações fluidas e correções de reprodução." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SpaceColors.onSurfaceVariant,
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        // Indicador de segurança
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SpaceIcon("shield-check", null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Text(
                                "Pacote verificado com SHA-256 e distribuído via HTTPS seguro.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SpaceColors.onSurfaceVariant
                            )
                        }
                    }

                    // Ações principais
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                openError = runCatching { uri.openUri(release.url) }.isFailure
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpaceColors.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SpaceIcon("download", null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Text(
                                    "Baixar Atualização Agora",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onCheck,
                            enabled = !checking,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.2.dp, SpaceBorderColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SpaceColors.secondary)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SpaceIcon("refresh-cw", null, tint = SpaceColors.secondary, modifier = Modifier.size(16.dp))
                                Text(
                                    if (checking) "Verificando…" else "Verificar Novamente",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
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

                    AnimatedVisibility(visible = openError) {
                        Text(
                            "Não foi possível abrir o navegador padrão. Copie o link ou tente manualmente.",
                            color = SpaceColors.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    AnimatedVisibility(visible = message != null) {
                        message?.let {
                            Text(
                                it,
                                color = SpaceColors.secondary,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Instrução amigável
                    Text(
                        "O download abre no navegador. Depois, instale o APK no Android ou extraia o pacote ZIP no Windows. Seus perfis, biblioteca e episódios salvos não serão perdidos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpaceColors.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
