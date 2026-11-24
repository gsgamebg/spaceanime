package app.anim.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anim.resources.Res
import app.anim.resources.spaceanime_neon
import app.anim.resources.inter
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

// ─── Paleta principal — SpaceAnime Dark ───────────────────────────────────────
val SpaceColors = darkColorScheme(
        primary            = Color(0xFFA855F7),
        onPrimary          = Color(0xFFF8FAFC),
        primaryContainer   = Color(0xFF22123A),
        onPrimaryContainer = Color(0xFFEDE9FE),
        secondary          = Color(0xFF2DD4BF),
        onSecondary        = Color(0xFF05030C),
        tertiary           = Color(0xFF6366F1),
        onTertiary         = Color(0xFFF8FAFC),
        background         = Color(0xFF05030C),
        onBackground       = Color(0xFFF8FAFC),
        surface            = Color(0xFF120B20),
        onSurface          = Color(0xFFF8FAFC),
        surfaceVariant     = Color(0xFF22123A),
        onSurfaceVariant   = Color(0xFF94A3B8),
        outline            = Color(0x40A855F7),
        error              = Color(0xFFFF6B8A),
)

val SpaceSidebarColor = Color(0xFF08050F)
val SpaceCardColor = Color(0xFF120B20)
val SpaceBorderColor = Color(0x40A855F7)
val SpaceHeaderBorder = Color(0x20A855F7)
val SpacePanelGradient = Brush.linearGradient(
    listOf(Color(0xFF22123A), Color(0xFF120B20), Color(0xFF05030C))
)
val SpaceAccentGradient = Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF6366F1), Color(0xFF2DD4BF)))
val LocalSpaceMotionEnabled = staticCompositionLocalOf { false }

// ─── Identidade 2026: preto do vazio, nébula ametista e lagoa teal (das referências VIP/Perfil) ───
val SpaceVoid = Color(0xFF05030C)
val SpaceNebula = Color(0xFF22123A)
val SpaceAmethyst = Color(0xFFA855F7)
val SpaceLagoon = Color(0xFF2DD4BF)
val SpaceSkyBlue = Color(0xFF3B82F6)
val SpaceGold = Color(0xFFF5C451)
val SpacePlatinum = Color(0xFFCBD5E1)
/** Rodapé/fundo das referências: preto que sobe para roxo. */
val SpaceNebulaFooter = Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF2A1048), Color(0xFF3B1361)))

/** Glass panel: translucent fill + hairline gradient border (no heavy shadows). */
fun Modifier.spaceGlass(shape: Shape = RoundedCornerShape(20.dp), tint: Color = Color(0xFF140C24)): Modifier =
    this.clip(shape)
        .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.86f), tint.copy(alpha = 0.66f))), shape)
        .border(1.dp, Brush.linearGradient(listOf(Color(0x66A855F7), Color(0x142DD4BF), Color(0x40A855F7))), shape)

// Cores dos avatares de perfil
val ProfileColors = listOf(
    Color(0xFFA855F7), Color(0xFF6366F1), Color(0xFF2DD4BF), Color(0xFFA78BFA)
)

// ─── Tipografia ───────────────────────────────────────────────────────────────
expect fun spaceFontFamily(): FontFamily
@Composable fun spaceTypography(): Typography {
val family = FontFamily(Font(Res.font.inter))
return Typography(
    headlineLarge  = TextStyle(fontFamily = family, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = family, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge     = TextStyle(fontFamily = family, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium    = TextStyle(fontFamily = family, fontSize = 16.sp, lineHeight = 24.sp,  fontWeight = FontWeight.SemiBold),
    titleSmall     = TextStyle(fontFamily = family, fontSize = 13.sp, lineHeight = 20.sp,  fontWeight = FontWeight.Medium),
    bodyLarge      = TextStyle(fontFamily = family, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium     = TextStyle(fontFamily = family, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall      = TextStyle(fontFamily = family, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge     = TextStyle(fontFamily = family, fontSize = 14.sp, lineHeight = 20.sp,  fontWeight = FontWeight.SemiBold),
    labelSmall     = TextStyle(fontFamily = family, fontSize = 10.sp, lineHeight = 16.sp,  fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
)

}

// ─── Logo Oficial SpaceAnime ──────────────────────────────────────────────────
@Composable fun SpaceLogo(modifier: Modifier = Modifier.size(46.dp)) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.radialGradient(listOf(Color(0xFFA855F7).copy(alpha = 0.4f), Color(0xFF22123A).copy(alpha = 0.2f), Color.Transparent)))
            .border(1.5.dp, SpaceAccentGradient, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.spaceanime_neon),
            contentDescription = "SpaceAnime Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp))
        )
    }
}

@Composable fun SpaceBrand(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SpaceLogo(if (compact) Modifier.size(38.dp) else Modifier.size(48.dp))
        if (!compact) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "SpaceAnime",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(SpaceColors.tertiary))
                Text("SEU UNIVERSO ANIME", fontSize = 9.sp, letterSpacing = 2.sp, color = SpaceColors.secondary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Arte de fundo orbital (hero) ────────────────────────────────────────────
@Composable fun OrbitalArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * .75f, size.height * .5f)
        val radius = size.minDimension * .42f
        // Glow ambiental mais intenso
        drawCircle(Brush.radialGradient(listOf(Color(0xFFA855F7).copy(alpha = .6f), Color(0xFFA78BFA).copy(alpha = .3f), Color.Transparent), c, radius * 2.2f), radius * 2.2f, c)
        // Planeta principal com gradiente mais rico
        drawCircle(Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFFA78BFA), Color(0xFF22123A))), radius, c)
        // Anel mais brilhante
        rotate(-32f, c) {
            drawOval(Color(0xFFA78BFA).copy(alpha = .65f), Offset(c.x - radius * 1.7f, c.y - radius * .42f), Size(radius * 3.4f, radius * .84f), style = Stroke(2.8.dp.toPx()))
            drawOval(Color(0xFF6366F1).copy(alpha = .25f), Offset(c.x - radius * 1.9f, c.y - radius * .55f), Size(radius * 3.8f, radius * 1.1f), style = Stroke(1.2.dp.toPx()))
        }
        // Campo de estrelas mais denso
        for (i in 0..45) {
            val alpha = .12f + (i % 6) * .14f
            val r = if (i % 4 == 0) 2.5.dp.toPx() else if (i % 3 == 0) 1.5.dp.toPx() else 1.dp.toPx()
            drawCircle(Color(0xFFE6DBFF).copy(alpha = alpha), r, Offset(size.width * ((i * 37 % 97) / 100f), size.height * ((i * 23 % 89) / 100f)))
        }
    }
}

// ─── Componentes visuais reutilizáveis ────────────────────────────────────────

@Composable
fun SpacePanel(
    modifier: Modifier = Modifier,
    tonal: Color = SpaceCardColor,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = tonal,
        border = BorderStroke(1.2.dp, SpaceBorderColor),
        shadowElevation = 0.dp,
    ) {
        Column(
            Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable fun AnimePosterCard(
    title: String,
    imageUrl: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 2f / 3f,
    onClick: () -> Unit = {},
    rank: Int? = null,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val motion = LocalSpaceMotionEnabled.current
    val elevation by animateFloatAsState(if (hovered && motion) -6f else 0f, animationSpec = tween(if (motion) 200 else 0), label = "poster-hover")
    val scale by animateFloatAsState(if (hovered && motion) 1.03f else 1f, animationSpec = tween(if (motion) 200 else 0), label = "poster-scale")
    val borderColor by animateColorAsState(
        if (focused || hovered) SpaceColors.secondary else SpaceBorderColor,
        animationSpec = tween(if (motion) 200 else 0),
        label = "poster-border"
    )
    val offset = with(LocalDensity.current) { elevation.dp.toPx() }
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .graphicsLayer {
                translationY = offset
                this.scaleX = scale
                this.scaleY = scale
            }
            .hoverable(interaction)
            .aspectRatio(aspectRatio)
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceColors.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (imageUrl.isNotBlank()) {
                Artwork(
                    model = imageUrl, contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF22123A), Color(0xFF120B20)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(title.take(2).uppercase(), style = MaterialTheme.typography.headlineMedium, color = SpaceColors.secondary)
                }
            }
            // Sombra/gradiente na base para leitura legível do título - mais suave
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(.6f).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC05030C), Color(0xF505030C))))
            )
            // Título na base
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 10.dp),
            )
            // Badge de Ranking (estilo Kokomi)
            if (rank != null) {
                RankingBadge(rank, Modifier.align(Alignment.TopStart))
            }
            badge?.invoke(this)
        }
    }
}

/** Badge de Ranking Top (ex: #1, #2, #3 estilo Kokomi) */
@Composable fun RankingBadge(rank: Int, modifier: Modifier = Modifier) {
    val bg = when (rank) {
        1 -> listOf(Color(0xFFFF2A55), Color(0xFFFF5277))
        2 -> listOf(Color(0xFFFF7A00), Color(0xFFFFA834))
        3 -> listOf(Color(0xFF9333EA), Color(0xFFC084FC))
        else -> listOf(Color(0xFF38235D), Color(0xFF21153B))
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(bottomEnd = 14.dp, topStart = 20.dp))
            .background(Brush.linearGradient(bg))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            "#$rank",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp
        )
    }
}

/** Badge colorida moderna (ex: "HD", "DUB", "LEG") */
@Composable fun SpaceBadge(text: String, color: Color = SpaceColors.primary, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.15f))))
            .border(1.2.dp, color.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

/** Divisor de seção com barra lateral neon */
@Composable fun SectionHeader(title: String, subtitle: String = "", action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SpaceAccentGradient)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpaceColors.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }
        action?.invoke()
    }
}

/** Avatar circular com iniciais e cor de perfil */
@Composable fun ProfileAvatar(name: String, colorIndex: Int, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val color = ProfileColors.getOrElse(colorIndex % ProfileColors.size) { ProfileColors[0] }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.38f), Color(0xFF140D22))))
            .border(1.5.dp, color.copy(alpha = 0.78f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** Chip de gênero/filtro com estilo Space */
@Composable fun GenreChip(label: String, selected: Boolean = false, onClick: () -> Unit = {}) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SpaceColors.primaryContainer,
            selectedLabelColor = Color.White,
            containerColor = SpaceCardColor,
            labelColor = SpaceColors.onSurfaceVariant,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = SpaceBorderColor, selectedBorderColor = SpaceColors.primary.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(999.dp),
    )
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF120B20),
        border = BorderStroke(1.dp, SpaceBorderColor),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = SpaceColors.onSurfaceVariant)
        }
    }
}

@Composable
fun SpaceEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    SpacePanel(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SpaceColors.onSurfaceVariant)
        action?.invoke()
    }
}

@Composable
fun spaceShimmerBrush(): Brush {
    val motion = LocalSpaceMotionEnabled.current
    if (!motion) {
        return Brush.linearGradient(listOf(Color(0xFF22123A), Color(0xFF120B20), Color(0xFF22123A)))
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-translate"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF120B20),
            Color(0xFF22123A),
            Color(0xFF3B2D68).copy(alpha = 0.65f),
            Color(0xFF22123A),
            Color(0xFF120B20),
        ),
        start = Offset(translateAnim - 450f, translateAnim - 450f),
        end = Offset(translateAnim, translateAnim)
    )
}

@Composable
fun PulseDot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    val motion = LocalSpaceMotionEnabled.current
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by if (motion) {
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse-alpha"
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun SpaceSkeletonBlock(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(spaceShimmerBrush())
    )
}
