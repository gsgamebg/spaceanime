package app.anim.design

import androidx.compose.foundation.hoverable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

// Lucide icons, ISC license. Source assets and attribution in assets/vendor.
private val paths = mapOf(
    "bell" to "M10.268 21a2 2 0 0 0 3.464 0 M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
    "calendar-days" to "M8 2v3 M16 2v3 M5.0 3.0H19.0Q21.0 3.0 21.0 5.0V19.0Q21.0 21.0 19.0 21.0H5.0Q3.0 21.0 3.0 19.0V5.0Q3.0 3.0 5.0 3.0Z M3 9h18 M8 13h.01 M12 13h.01 M16 13h.01 M8 17h.01 M12 17h.01 M16 17h.01",
    "chevron-left" to "m15 18-6-6 6-6",
    "chevron-right" to "m9 18 6-6-6-6",
    "flame" to "M12 3q1 4 4 6.5t3 5.5a1 1 0 0 1-14 0 5 5 0 0 1 1-3 1 1 0 0 0 5 0c0-2-1.5-3-1.5-5q0-2 2.5-4",
    "heart" to "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
    "house" to "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8 M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    "book-open" to "M12 7v14 M3 18V5a1 1 0 0 1 1-1h3a5 5 0 0 1 5 3 5 5 0 0 1 5-3h3a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-3a5 5 0 0 0-5 2 5 5 0 0 0-5-2H4a1 1 0 0 1-1-1z",
    "grid" to "M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z",
    "bookmark" to "M6 3h12a1 1 0 0 1 1 1v17l-7-4-7 4V4a1 1 0 0 1 1-1z",
    "newspaper" to "M4 4h14v16H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M18 8h4v10a2 2 0 0 1-4 0 M6 8h8 M6 12h8 M6 16h5",
    "library" to "m16 6 4 14 M12 6v14 M8 8v12 M4 4v16",
    "menu" to "M4 5h16 M4 12h16 M4 19h16",
    "message-circle" to "M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719",
    "pause" to "M6 4h4v16H6z M14 4h4v16h-4z",
    "rewind" to "M11 19 2 12l9-7v14z M22 19l-9-7 9-7v14z",
    "forward" to "m2 5 9 7-9 7V5z m11 0 9 7-9 7V5z",
    "volume" to "M11 5 6 9H2v6h4l5 4V5z M15 9a5 5 0 0 1 0 6 M19 5a10 10 0 0 1 0 14",
    "volume-off" to "M11 5 6 9H2v6h4l5 4V5z M16 9l5 6 M21 9l-5 6",
    "maximize" to "M4 9V4h5 M15 4h5v5 M20 15v5h-5 M9 20H4v-5",
    "minimize" to "M9 4v5H4 M15 4v5h5 M20 15h-5v5 M4 15h5v5",
    "play" to "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",
    "rotate-ccw" to "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8 M3 3v5h5",
    "search" to "m21 21-4.34-4.34 M3.0 11.0a8.0 8.0 0 1 0 16.0 0a8.0 8.0 0 1 0 -16.0 0",
    "settings" to "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915 M9.0 12.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0",
    "user-round" to "M7.0 8.0a5.0 5.0 0 1 0 10.0 0a5.0 5.0 0 1 0 -10.0 0 M20 21a8 8 0 0 0-16 0",
    "x" to "M18 6 6 18 m6 6 12 12",
    "edit" to "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z M15 5l4 4",
    "eye" to "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0 M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6",
    "eye-off" to "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49 M14.084 14.158a3 3 0 0 1-4.242-4.242 M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143 M2 2l20 20",
    "download" to "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3",
    "refresh-cw" to "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8 M21 3v5h-5 M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16 M3 21v-5h5",
    "shield-check" to "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z M9 12l2 2 4-4",
    "sparkles" to "m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z",
    "arrow-right" to "M5 12h14 M12 5l7 7-7 7",
    "external-link" to "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6 M15 3h6v6 M10 14L21 3"
)
private val icons = paths.mapValues { (name, data) ->
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(data).toNodes(),
        fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ).build()
}
@Composable fun SpaceIcon(name: String, description: String?, modifier: Modifier = Modifier, tint: Color = Color.Unspecified, glow: Boolean = true) {
    val finalTint = if (tint == Color.Unspecified) SpaceColors.onSurface else tint
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val motion = LocalSpaceMotionEnabled.current
    val glowAlpha by animateFloatAsState(
        if (hovered && motion && glow) 0.4f else 0f,
        animationSpec = tween(if (motion) 300 else 0),
        label = "icon-glow"
    )

    Icon(
        icons[name] ?: icons.getValue("house"),
        description,
        modifier
            .hoverable(interaction)
            .drawBehind {
                if (glowAlpha > 0f) {
                    drawCircle(
                        color = finalTint.copy(alpha = glowAlpha),
                        radius = size.width * 0.6f,
                        blendMode = BlendMode.Screen
                    )
                }
            },
        finalTint
    )
}

