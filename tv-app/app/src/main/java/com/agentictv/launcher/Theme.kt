package com.agentictv.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// All the colours in one place. Change these to re-theme the whole screen.
val Accent = Color(0xFF8E9BFF)
val TextMain = Color(0xFFF4F5FA)
val TextDim = Color(0xFFA7ADC2)
val CardColor = Color(0x33FFFFFF)      // see-through white, so the wallpaper shows
val CardBorder = Color(0x22FFFFFF)
val TileColor = Color(0xFF232842)

/** The colours of the wallpaper glow, changing with the time of day. */
private fun palette(hour: Int): List<Color> = when (hour) {
    in 6..10 -> listOf(Color(0xFF1B2A4A), Color(0xFFFF9A6B), Color(0xFF6BC4FF))   // morning
    in 11..17 -> listOf(Color(0xFF14233F), Color(0xFF4FB3FF), Color(0xFF7CF2C4))  // day
    in 18..21 -> listOf(Color(0xFF1A1636), Color(0xFFFF6BA8), Color(0xFF7C6BFF))  // evening
    else -> listOf(Color(0xFF0B0D1A), Color(0xFF3B2F8F), Color(0xFF1F5F8F))        // night
}

/**
 * A soft, calm wallpaper drawn in code: a dark base with two big blurry glows.
 * Drawn, not a photo, so it costs almost no memory on the TV.
 */
@Composable
fun Wallpaper(hour: Int, modifier: Modifier = Modifier.fillMaxSize()) {
    val (base, glow1, glow2) = palette(hour)
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(base, Color(0xFF05060B))))
        drawRect(
            Brush.radialGradient(
                listOf(glow1.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.05f),
                radius = size.width * 0.55f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(glow2.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.95f),
                radius = size.width * 0.6f,
            )
        )
    }
}
