package app.anim.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.anim.design.LocalSpaceMotionEnabled
import app.anim.design.SpaceColors

/** Original lightweight chibi mascot; no third-party character or animated media file. */
@Composable fun PlayerLoading(message: String, modifier: Modifier = Modifier) {
    val motion = LocalSpaceMotionEnabled.current
    val swing = if (motion) {
        val transition = rememberInfiniteTransition(label = "mascot")
        transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse), label = "walk").value
    } else 0f
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(84.dp)) {
            val s = size.width / 100f
            fun p(x: Float, y: Float) = Offset(x*s, y*s)
            drawOval(Color(0x553B1764), p(22f, 89f), Size(58*s, 6*s))
            drawLine(Color(0xFFC084FC), p(46f, 70f), p(39f + 9f*swing, 87f), 7*s, StrokeCap.Round)
            drawLine(Color(0xFF9333EA), p(54f, 70f), p(61f - 9f*swing, 87f), 7*s, StrokeCap.Round)
            drawRoundRect(Color(0xFF7E32D4), p(35f, 46f), Size(30*s, 30*s), androidx.compose.ui.geometry.CornerRadius(10*s))
            drawLine(Color(0xFFE9BAFF), p(38f, 52f), p(27f - 5f*swing, 66f), 6*s, StrokeCap.Round)
            drawLine(Color(0xFFE9BAFF), p(62f, 52f), p(73f + 5f*swing, 66f), 6*s, StrokeCap.Round)
            drawCircle(Color(0xFFFFD9E8), 21*s, p(50f, 28f))
            drawArc(Color(0xFF422165), 180f, 185f, true, p(28f, 6f), Size(44*s, 38*s))
            drawCircle(Color(0xFF20112D), 2*s, p(42f, 31f)); drawCircle(Color(0xFF20112D), 2*s, p(58f, 31f))
            drawArc(Color(0xFF9E5D80), 15f, 150f, false, p(44f, 32f), Size(12*s, 7*s), style = Stroke(1.5f*s))
            drawCircle(Color(0xFFA855F7), 2*s, p(17f, 34f)); drawCircle(Color(0xFFE879F9), 1.5f*s, p(82f, 18f))
        }
        Text("Aguarde...", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(message, color = SpaceColors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
