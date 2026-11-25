package app.anim.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.anim.resources.Res
import app.anim.resources.spaceanime_loader
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.sin

// Geometry of the loader art (443×443, transparent background), as fractions of its size.
private const val BAR_LEFT = 63f / 443f
private const val BAR_RIGHT = 379f / 443f
private const val BAR_TOP = 318f / 443f
private const val BAR_BOTTOM = 343f / 443f
private const val ORB_X = 152f / 443f
private const val ORB_Y = 247f / 443f

/**
 * The SpaceAnime loading art with its motion drawn in code: the progress bar fills and the orb in
 * the character's hand pulses. A single static image plus Canvas runs the same on Android 8+ and
 * Windows, with no frame decoding. Static when the user turned motion off.
 */
@Composable fun LoaderArt(contentDescription: String?, modifier: Modifier = Modifier) {
    val motion = LocalSpaceMotionEnabled.current
    val fill: Float
    val pulse: Float
    if (motion) {
        val transition = rememberInfiniteTransition(label = "loader")
        fill = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing)), label = "fill").value
        pulse = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Reverse), label = "pulse").value
    } else {
        fill = 0.6f
        pulse = 0.5f
    }
    Box(modifier.aspectRatio(1f)) {
        Image(painterResource(Res.drawable.spaceanime_loader), contentDescription, Modifier.matchParentSize())
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            // Orb glow: a soft magenta halo that breathes around the light in the hand.
            val orb = Offset(w * ORB_X, h * ORB_Y)
            val r = w * (0.07f + 0.035f * pulse)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFF0ABFC).copy(alpha = 0.45f + 0.3f * pulse), Color(0xFFA855F7).copy(alpha = 0.22f), Color.Transparent), orb, r * 1.8f),
                r * 1.8f, orb,
            )
            // Progress bar: the fill grows across the slot (eased so it never looks stuck), with a bright head.
            val left = w * BAR_LEFT
            val top = h * BAR_TOP
            val barW = w * (BAR_RIGHT - BAR_LEFT)
            val barH = h * (BAR_BOTTOM - BAR_TOP)
            val filled = barW * (1f - (1f - fill) * (1f - fill))
            clipRect(left, top, left + barW, top + barH) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color(0xFF6D28D9), Color(0xFFA855F7), Color(0xFFE879F9)), left, left + filled.coerceAtLeast(1f)),
                    Offset(left, top + barH * 0.18f), Size(filled, barH * 0.64f), CornerRadius(barH / 2, barH / 2),
                )
                val head = Offset(left + filled, top + barH / 2)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Transparent), head, barH), barH, head)
                val shine = left + filled * ((sin(fill * 2 * PI).toFloat() + 1f) / 2f)
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent), shine - barH * 2, shine + barH * 2), Offset(left, top), Size(filled, barH))
            }
        }
    }
}

/** Branded loading state: the loader art + one short line of status. */
@Composable fun SpaceLoader(message: String? = null, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LoaderArt("Carregando", Modifier.width(size))
        if (message != null) Text(message, color = Color(0xFFC4B5FD), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}
