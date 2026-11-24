package app.anim.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared page header for Início, Explorar, Mangás and Notícias: glass card, gradient icon tile,
 * title + one line of guidance, and a quiet ringed planet on the right (the SpaceAnime signature).
 */
@Composable fun ScreenHero(
    title: String,
    subtitle: String,
    icon: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.fillMaxWidth().spaceGlass(shape, tint = Color(0xFF120A22))) {
        Canvas(Modifier.matchParentSize()) {
            // Amethyst glow + ringed planet, kept faint so text always reads first.
            val c = Offset(size.width - 70.dp.toPx(), size.height * 0.45f)
            val r = 38.dp.toPx()
            drawCircle(Brush.radialGradient(listOf(SpaceAmethyst.copy(alpha = 0.35f), Color.Transparent), c, r * 3.2f), r * 3.2f, c)
            drawCircle(Brush.linearGradient(listOf(SpaceAmethyst.copy(alpha = 0.55f), Color(0xFF3B1361).copy(alpha = 0.4f)), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), r, c)
            rotate(-24f, c) {
                drawOval(SpaceLagoon.copy(alpha = 0.45f), Offset(c.x - r * 1.75f, c.y - r * 0.38f), Size(r * 3.5f, r * 0.76f), style = Stroke(1.6.dp.toPx()))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).padding(end = 120.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(SpaceAmethyst, Color(0xFF6D28D9), SpaceLagoon))),
                contentAlignment = Alignment.Center,
            ) { SpaceIcon(icon, null, Modifier.size(22.dp), Color.White, glow = false) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFFF8FAFC))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC4B5FD))
            }
            trailing?.invoke(this)
        }
    }
}
