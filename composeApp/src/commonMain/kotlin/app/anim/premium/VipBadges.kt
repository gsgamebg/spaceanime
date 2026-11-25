package app.anim.premium

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.anim.design.LocalSpaceMotionEnabled
import app.anim.design.SpaceAmethyst
import app.anim.design.SpaceGold
import app.anim.design.SpaceLagoon
import app.anim.design.SpacePlatinum

/** Accent of each VIP plan (badge, avatar frame, highlighted comments). */
fun vipColor(plan: String?): Color = when (plan) {
    "ouro" -> SpaceGold
    "platina" -> SpacePlatinum
    else -> SpaceAmethyst
}

/** Small "✦ VIP Ouro" chip shown next to the name in the profile and in comments. */
@Composable fun VipBadge(plan: String?, modifier: Modifier = Modifier) {
    val color = vipColor(plan)
    Box(
        modifier.background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.15f))), CircleShape)
            .border(1.dp, color.copy(alpha = 0.8f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text("✦ VIP ${vipPlanName(plan)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** Avatar with the plan's frame; Ametista's frame slowly rotates (static when motion is off). */
@Composable fun VipFrame(plan: String?, size: Dp, content: @Composable () -> Unit) {
    if (plan == null) { content(); return }
    val color = vipColor(plan)
    val angle = if (plan == "ametista" && LocalSpaceMotionEnabled.current) {
        rememberInfiniteTransition(label = "vip-frame").animateFloat(0f, 360f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "spin").value
    } else 0f
    val ring = (size.value * 0.06f).coerceIn(2f, 5f).dp
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier.padding(0.dp).rotate(angle)
                .border(ring, Brush.sweepGradient(listOf(color, SpaceLagoon, color, Color.White, color)), CircleShape)
                .padding(ring + 2.dp),
        ) { Box(Modifier.rotate(-angle)) { content() } }
    }
}
