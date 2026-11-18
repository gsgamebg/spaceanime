package app.anim.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anim.design.SpaceBorderColor
import app.anim.design.SpaceColors

internal data class Plan(val name: String, val monthly: String, val annual: String, val benefits: List<String>)
internal val plans = listOf(
    Plan("Basic", "12,99", "128,00", listOf("Acesso privilegiado no Discord", "Descontos")),
    Plan("Platina", "16,89", "142,00", listOf("Suporte 24 horas", "Privilégios no Discord", "Avatares e banners exclusivos", "XP multiplicado por 3,3")),
    Plan("Premium", "24,99", "234,00", listOf("Suporte 24 horas", "Privilégios no Discord", "Acesso antecipado a versões", "Avatares e banners exclusivos", "XP multiplicado por 4,4", "Prioridade em tickets e sugestões", "Nome no mural de contribuidores", "Cupons de desconto")),
)
@Composable fun PremiumPlans() {
    var annual by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Planos SpaceAnime", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Planos em preparação. Os benefícios e a contratação ainda não estão disponíveis.", color = SpaceColors.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !annual, onClick = { annual = false }, label = { Text("Mensal") })
            FilterChip(selected = annual, onClick = { annual = true }, label = { Text("Anual") })
        }
        plans.forEach { plan ->
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color(0xFF151024), border = androidx.compose.foundation.BorderStroke(1.dp, SpaceBorderColor)) {
                Column(Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF211037), Color(0xFF151024)))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(plan.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("R$ ${if (annual) plan.annual else plan.monthly} / ${if (annual) "ano" else "mês"}", style = MaterialTheme.typography.headlineSmall, color = SpaceColors.secondary)
                    plan.benefits.forEach { Text("✦  $it", color = Color(0xFFE8E1F2), style = MaterialTheme.typography.bodyMedium) }
                    Text("Em preparação", color = SpaceColors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
