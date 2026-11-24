package app.anim.premium

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anim.design.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * VIP plans (pre-launch). Payments are not live yet, so nothing here is sold or unlocked:
 * the call to action only joins a waiting list. Never list benefits the app cannot deliver
 * (e.g. ad removal) until entitlements exist — see PremiumPlansTest.
 */
internal data class Plan(
    val id: String,
    val name: String,
    val price: String,
    val accent: Color,
    val tagline: String,
    val benefits: List<String>,
    val featured: Boolean = false,
)

internal val plans = listOf(
    Plan("ouro", "Ouro", "10", SpaceGold, "Seu nome brilha na comunidade", listOf("Símbolo VIP no perfil", "Moldura dourada no avatar")),
    Plan(
        "ametista", "Ametista", "30", SpaceAmethyst, "A experiência completa do SpaceAnime",
        listOf("Banner de fundo exclusivo", "Nome de usuário colorido", "Privilégios no servidor do Discord", "Símbolo VIP no perfil", "Comentários em destaque", "Suporte 24 horas"),
        featured = true,
    ),
    Plan("platina", "Platina", "20", SpacePlatinum, "Visual exclusivo para o seu perfil", listOf("Banner de fundo exclusivo", "Nome de usuário colorido", "Privilégios no servidor do Discord", "Símbolo VIP no perfil")),
)

@Composable private fun motion() = LocalSpaceMotionEnabled.current

/** 0→1 looping progress, frozen at [still] when the user turned motion off. */
@Composable private fun loop(durationMs: Int, still: Float = 0.5f, label: String): State<Float> =
    if (!motion()) remember { mutableFloatStateOf(still) }
    else rememberInfiniteTransition(label = label).animateFloat(0f, 1f, infiniteRepeatable(tween(durationMs, easing = LinearEasing)), label = label)

/** Diagonal light sweep across the content (buttons, featured card). */
private fun Modifier.shimmer(progress: () -> Float, strength: Float = 0.28f) = drawWithContent {
    drawContent()
    val x = (progress() * 2.2f - 0.6f) * size.width
    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = strength), Color.Transparent), start = Offset(x - size.width * 0.35f, 0f), end = Offset(x, size.height)))
}

private fun DrawScope.sparkle(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}

/** Teal waves + twinkling sparkles from the VIP reference: the one bold, moving element of the page. */
@Composable private fun VipBackdrop(modifier: Modifier) {
    val drift by loop(14_000, label = "vip-waves")
    val twinkle by loop(3_200, label = "vip-twinkle")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val phase = (drift * 2 * PI).toFloat()
        // Diagonal teal bands (as in the reference): high on the left, dipping, then rising behind the cards.
        // Positioned in dp from the top so the wordmark always sits on black, whatever the page height.
        fun band(startY: Float, thickness: Float, shift: Float, colors: List<Color>) {
            val wobble = 18.dp.toPx() * sin(phase + shift)
            val path = Path().apply {
                moveTo(0f, startY)
                cubicTo(w * 0.3f, startY + 170.dp.toPx() + wobble, w * 0.65f, startY + 40.dp.toPx() - wobble, w, startY - 60.dp.toPx())
                lineTo(w, startY - 60.dp.toPx() + thickness)
                cubicTo(w * 0.65f, startY + 40.dp.toPx() - wobble + thickness, w * 0.3f, startY + 170.dp.toPx() + wobble + thickness, 0f, startY + thickness)
                close()
            }
            drawPath(path, Brush.horizontalGradient(colors))
        }
        band(215.dp.toPx(), 150.dp.toPx(), 0f, listOf(Color(0xFF0E7490).copy(alpha = 0.75f), Color(0xFF2DD4BF).copy(alpha = 0.55f), Color(0xFF0F766E).copy(alpha = 0.8f)))
        band(295.dp.toPx(), 70.dp.toPx(), 1.6f, listOf(Color(0xFF134E4A).copy(alpha = 0.55f), Color(0xFF14B8A6).copy(alpha = 0.25f), Color(0xFF134E4A).copy(alpha = 0.5f)))
        // Bottom nebula glow, like the reference footer.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF3B1361).copy(alpha = 0.55f)), startY = h * 0.7f, endY = h))
        for (i in 0 until 14) {
            val x = w * ((i * 53 % 97) / 97f); val y = h * ((i * 31 % 89) / 89f)
            val a = 0.25f + 0.6f * (0.5f + 0.5f * sin((twinkle * 2 * PI + i * 1.3).toFloat()))
            sparkle(Offset(x, y), (if (i % 4 == 0) 7 else 4).dp.toPx(), (if (i % 3 == 0) SpaceAmethyst else SpaceLagoon).copy(alpha = a))
        }
    }
}

@Composable private fun VipWordmark() {
    val twinkle by loop(2_400, label = "vip-mark")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(96.dp).widthIn(min = 260.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val stroke = 3.dp.toPx()
            for (side in listOf(-1f, 1f)) {
                // Rays on both sides of the word, as in the reference.
                for ((dy, len) in listOf(-0.32f to 0.16f, 0f to 0.2f, 0.32f to 0.16f)) {
                    val sx = c.x + side * size.width * 0.27f
                    val y = c.y + dy * size.height
                    val tilt = dy * size.height * 0.35f
                    drawLine(Color.White.copy(alpha = 0.85f), Offset(sx, y), Offset(sx + side * size.width * len, y + tilt), stroke, StrokeCap.Round)
                }
            }
            sparkle(Offset(c.x + size.width * 0.13f, c.y - size.height * 0.36f), 11.dp.toPx() * (0.85f + 0.15f * twinkle), SpaceAmethyst)
        }
        Text(
            "VIP",
            style = TextStyle(
                brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFE9D5FF))),
                fontSize = 64.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                shadow = Shadow(SpaceSkyBlue.copy(alpha = 0.7f), Offset(0f, 6f), 18f),
            ),
        )
    }
}

@Composable private fun VipPlanCard(plan: Plan, joined: Boolean, onJoin: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered && motion()) 1.03f else 1f, tween(220), label = "lift")
    val sweep by loop(3_000, still = 0.15f, label = "card-${plan.id}")
    val orbit by loop(6_000, still = 0.125f, label = "border-${plan.id}")
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .hoverable(interaction)
            .clip(shape)
            .background(Brush.verticalGradient(0f to Color(0xFF151022), 0.42f to Color(0xFF1A1030), 1f to Color(0xFF3B1361)))
            .drawWithContent {
                drawContent()
                val r = CornerRadius(22.dp.toPx())
                if (plan.featured) {
                    // Rotating highlight around the featured card's blue frame.
                    val angle = orbit * 2 * PI
                    val c = Offset(size.width / 2, size.height / 2)
                    val d = Offset((cos(angle) * size.width).toFloat(), (sin(angle) * size.height).toFloat())
                    drawRoundRect(Brush.linearGradient(listOf(SpaceSkyBlue, SpaceLagoon, SpaceAmethyst, SpaceSkyBlue), c - d, c + d), cornerRadius = r, style = Stroke(2.5.dp.toPx()))
                } else drawRoundRect(plan.accent.copy(alpha = 0.35f), cornerRadius = r, style = Stroke(1.dp.toPx()))
            }
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.size(22.dp)) { sparkle(Offset(size.width / 2, size.height / 2), size.minDimension / 2, plan.accent) }
            Text(plan.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            if (plan.featured) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(CircleShape).background(SpaceSkyBlue.copy(alpha = 0.22f)).shimmer({ sweep }, 0.4f).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("Mais completo", style = MaterialTheme.typography.labelSmall, color = Color(0xFFBFDBFE), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(plan.tagline, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE2E8F0))
        HorizontalDivider(color = Color(0x22FFFFFF))
        plan.benefits.forEach { benefit ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(SpaceLagoon))
                Text(benefit, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE9E3F5))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("R$", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE9E3F5))
            Text(plan.price, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("/mês", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB8AECB), modifier = Modifier.padding(bottom = 6.dp))
        }
        OutlinedButton(
            onClick = onJoin,
            enabled = !joined,
            shape = CircleShape,
            border = BorderStroke(1.5.dp, if (joined) SpaceLagoon.copy(alpha = 0.4f) else SpaceLagoon),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (plan.featured) SpaceLagoon.copy(alpha = 0.14f) else Color.Transparent, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).then(if (joined) Modifier else Modifier.shimmer({ sweep })),
        ) {
            Text(if (joined) "Você está na lista ✓" else "Quero o ${plan.name}", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Dedicated VIP page: wordmark, three plans (featured in the middle on wide screens) and the waiting list. */
@Composable fun VipScreen() {
    var joined by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(SpaceVoid)) {
        VipBackdrop(Modifier.matchParentSize())
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp)) {
            val wide = maxWidth >= 860.dp
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                VipWordmark()
                Text(
                    "Apoie o SpaceAnime e ganhe destaque na comunidade.",
                    style = MaterialTheme.typography.titleMedium, color = Color(0xFFE9E3F5), textAlign = TextAlign.Center,
                )
                Text(
                    "As assinaturas abrem em breve. Por enquanto nenhuma cobrança é feita: escolha um plano para entrar na lista de espera.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8AECB), textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
                Spacer(Modifier.height(6.dp))
                val ordered = if (wide) plans else plans.sortedByDescending { it.featured }
                if (wide) {
                    Row(Modifier.fillMaxWidth().widthIn(max = 1080.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        ordered.forEach { plan ->
                            VipPlanCard(plan, joined == plan.id, { joined = plan.id }, Modifier.weight(if (plan.featured) 1.15f else 1f))
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ordered.forEach { plan -> VipPlanCard(plan, joined == plan.id, { joined = plan.id }, Modifier.fillMaxWidth()) }
                    }
                }
                AnimatedVisibility(joined != null, enter = fadeIn() + expandVertically()) {
                    val name = plans.firstOrNull { it.id == joined }?.name.orEmpty()
                    Text(
                        "Pronto! Você está na lista de espera do plano $name. Avisaremos aqui no app quando as assinaturas abrirem.",
                        modifier = Modifier.spaceGlass(RoundedCornerShape(16.dp)).padding(16.dp).widthIn(max = 560.dp),
                        color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** Compact, shimmering entry point to the VIP page (profile header, sidebar). */
@Composable fun VipPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sweep by loop(2_800, still = 0.2f, label = "vip-pill")
    Surface(onClick = onClick, shape = CircleShape, color = Color.Transparent, modifier = modifier) {
        Row(
            Modifier.background(Brush.horizontalGradient(listOf(SpaceAmethyst, Color(0xFF6D28D9), SpaceLagoon)), CircleShape)
                .shimmer({ sweep }, 0.45f)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Canvas(Modifier.size(12.dp)) { sparkle(Offset(size.width / 2, size.height / 2), size.minDimension / 2, Color.White) }
            Text("Seja VIP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
