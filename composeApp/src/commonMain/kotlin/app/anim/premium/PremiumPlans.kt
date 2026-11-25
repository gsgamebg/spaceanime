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
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import app.anim.ads.AdsController
import app.anim.ads.LocalAdsController
import app.anim.auth.AccountState
import app.anim.design.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * VIP plans, paid once by Pix for 30 days. List only benefits the app actually delivers
 * (see PremiumPlansTest): no ads, the VIP badge, the avatar frame and highlighted comments.
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
    Plan("ouro", "Ouro", "10", SpaceGold, "Sem anúncios e seu nome brilhando", listOf(SEM_ANUNCIOS, "Selo VIP Ouro no perfil e nos comentários", "Moldura dourada no avatar")),
    Plan(
        "ametista", "Ametista", "30", SpaceAmethyst, "A experiência completa do SpaceAnime",
        listOf(SEM_ANUNCIOS, "Selo VIP Ametista no perfil e nos comentários", "Moldura ametista animada no avatar", "Comentários em destaque"),
        featured = true,
    ),
    Plan("platina", "Platina", "20", SpacePlatinum, "Sem anúncios com visual exclusivo", listOf(SEM_ANUNCIOS, "Selo VIP Platina no perfil e nos comentários", "Moldura platina no avatar")),
)
internal const val SEM_ANUNCIOS = "Sem anúncios"

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

@Composable private fun VipPlanCard(plan: Plan, current: Boolean, loading: Boolean, onSubscribe: () -> Unit, modifier: Modifier = Modifier) {
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
            Text("/30 dias", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB8AECB), modifier = Modifier.padding(bottom = 6.dp))
        }
        OutlinedButton(
            onClick = onSubscribe,
            enabled = !loading,
            shape = CircleShape,
            border = BorderStroke(1.5.dp, SpaceLagoon),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (plan.featured) SpaceLagoon.copy(alpha = 0.14f) else Color.Transparent, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).then(if (loading) Modifier else Modifier.shimmer({ sweep })),
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(if (current) "Renovar +30 dias com Pix" else "Assinar com Pix", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Dedicated VIP page: wordmark, current status, three plans paid by Pix and the rewarded ad-free pass. */
@Composable fun VipScreen(account: AccountState) {
    val scope = rememberCoroutineScope()
    val ads = LocalAdsController.current
    val vip = account.vip
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var charge by remember { mutableStateOf<PixCharge?>(null) }
    LaunchedEffect(account.isAuthenticated) { account.refreshVip() }
    fun subscribe(plan: Plan) {
        if (busy != null) return
        error = null
        busy = plan.id
        scope.launch {
            try { charge = account.createPix(plan.id) }
            catch (e: VipException) { error = e.message }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = "Não foi possível gerar o Pix. Tente novamente." }
            finally { busy = null }
        }
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(SpaceVoid)) {
        VipBackdrop(Modifier.matchParentSize())
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp)) {
            val wide = maxWidth >= 860.dp
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                VipWordmark()
                if (vip.active) {
                    Row(
                        Modifier.spaceGlass(RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        VipBadge(vip.plan)
                        Text("Seu VIP ${vipPlanName(vip.plan)} está ativo até ${vip.expiresLabel}. Sem anúncios até lá.", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(
                        "Apoie o SpaceAnime, fique sem anúncios e ganhe destaque no seu perfil.",
                        style = MaterialTheme.typography.titleMedium, color = Color(0xFFE9E3F5), textAlign = TextAlign.Center,
                    )
                }
                Text(
                    "Pagamento único via Pix: 30 dias de VIP, sem renovação automática. Pagar de novo soma mais 30 dias.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8AECB), textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
                error?.let {
                    Text(it, color = Color(0xFFFCA5A5), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                        modifier = Modifier.spaceGlass(RoundedCornerShape(14.dp)).padding(12.dp).widthIn(max = 560.dp))
                }
                Spacer(Modifier.height(6.dp))
                val ordered = if (wide) plans else plans.sortedByDescending { it.featured }
                val card: @Composable (Plan, Modifier) -> Unit = { plan, modifier ->
                    VipPlanCard(plan, current = vip.active && vip.plan == plan.id, loading = busy == plan.id, onSubscribe = { subscribe(plan) }, modifier = modifier)
                }
                if (wide) {
                    Row(Modifier.fillMaxWidth().widthIn(max = 1080.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        ordered.forEach { plan -> card(plan, Modifier.weight(if (plan.featured) 1.15f else 1f)) }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ordered.forEach { plan -> card(plan, Modifier.fillMaxWidth()) }
                    }
                }
                if (ads != null && !vip.active) RewardedPassCard(ads)
            }
        }
    }
    charge?.let { current ->
        PixDialog(current, account, onClose = { charge = null })
    }
}

/** "Watch a video, no ads for 2 h" — only when the rewarded format is enabled remotely and loaded. */
@Composable private fun RewardedPassCard(ads: AdsController) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    val left = remember(tick, ads.adFreeMinutesLeft()) { ads.adFreeMinutesLeft() }
    if (left <= 0 && !ads.rewardedReady) return
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().spaceGlass(RoundedCornerShape(18.dp)).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (left > 0) {
            Text("Passe sem anúncios ativo", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Mais ${if (left >= 60) "${left / 60} h ${left % 60} min" else "$left min"} sem anúncios.", color = Color(0xFFC4B5FD), style = MaterialTheme.typography.bodySmall)
        } else {
            val hours = ads.rewardedPassMinutes / 60.0
            val label = if (ads.rewardedPassMinutes % 60 == 0) "${ads.rewardedPassMinutes / 60} h" else "${ads.rewardedPassMinutes} min"
            Text("Sem dinheiro agora? Assista a um vídeo e fique $label sem anúncios.", color = Color.White, textAlign = TextAlign.Center)
            Button(
                onClick = { ads.showRewarded() },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = SpaceAmethyst, contentColor = Color.White),
            ) { Text(if (hours >= 1) "Assistir vídeo e ganhar passe" else "Assistir vídeo") }
        }
    }
}

/** Pix checkout: QR code, copy-and-paste code, Mercado Pago link and automatic confirmation. */
@OptIn(ExperimentalResourceApi::class)
@Composable private fun PixDialog(charge: PixCharge, account: AccountState, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    var state by remember(charge.orderId) { mutableStateOf(PixOrderState.Pending) }
    var copied by remember(charge.orderId) { mutableStateOf(false) }
    val qr = remember(charge.orderId) { runCatching { charge.qrPng.decodeToImageBitmap() }.getOrNull() }
    LaunchedEffect(charge.orderId) {
        // Up to ~35 min (the Pix expires in 30); the backend also asks Mercado Pago on each check.
        repeat(420) {
            delay(5_000)
            val latest = try { account.pixOrderState(charge.orderId) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { PixOrderState.Pending }
            if (latest != PixOrderState.Pending) { state = latest; return@LaunchedEffect }
        }
        state = PixOrderState.Expired
    }
    LaunchedEffect(copied) { if (copied) { delay(2_500); copied = false } }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1030), Color(0xFF120A22), Color(0xFF3B1361))))
                .border(1.dp, SpaceAmethyst.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VipBadge(charge.plan)
                Text("VIP ${vipPlanName(charge.plan)} · R$ ${charge.amountCents / 100},${(charge.amountCents % 100).toString().padStart(2, '0')}",
                    color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            when (state) {
                PixOrderState.Paid -> {
                    Text("Pagamento confirmado!", color = Color(0xFF86EFAC), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Seu VIP ${vipPlanName(charge.plan)} já está ativo por 30 dias. Os anúncios somem na hora.", color = Color(0xFFE9E3F5), textAlign = TextAlign.Center)
                    Button(onClick = onClose, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = SpaceAmethyst)) { Text("Fechar") }
                }
                PixOrderState.Expired, PixOrderState.Failed -> {
                    Text(if (state == PixOrderState.Expired) "Este Pix expirou." else "O pagamento não foi aprovado.", color = Color(0xFFFCA5A5), style = MaterialTheme.typography.titleMedium)
                    Text("Feche e gere um novo Pix para tentar de novo. Nada foi cobrado.", color = Color(0xFFE9E3F5), textAlign = TextAlign.Center)
                    OutlinedButton(onClick = onClose, shape = CircleShape) { Text("Fechar", color = Color.White) }
                }
                PixOrderState.Pending -> {
                    Text("Abra o app do seu banco, escolha Pix e escaneie o código ou use o Copia e Cola.", color = Color(0xFFE9E3F5), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.size(224.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp), contentAlignment = Alignment.Center) {
                        if (qr != null) Image(qr, "QR Code Pix", Modifier.fillMaxSize())
                        else Text("Use o código abaixo", color = Color.Black)
                    }
                    Button(
                        onClick = { clipboard.setText(AnnotatedString(charge.qrCode)); copied = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp), shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceAmethyst, contentColor = Color.White),
                    ) { Text(if (copied) "Código copiado ✓" else "Copiar código Pix", fontWeight = FontWeight.SemiBold) }
                    charge.ticketUrl?.let { link ->
                        OutlinedButton(
                            onClick = { runCatching { uri.openUri(link) } },
                            modifier = Modifier.fillMaxWidth().height(46.dp), shape = CircleShape,
                            border = BorderStroke(1.dp, SpaceLagoon),
                        ) { Text("Abrir link de pagamento", color = Color.White) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = SpaceLagoon, strokeWidth = 2.dp)
                        Text("Aguardando o pagamento. O VIP é liberado automaticamente.", color = Color(0xFFC4B5FD), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onClose) { Text("Fechar (o VIP ainda é liberado se você pagar)", color = Color(0xFFB8AECB), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
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
