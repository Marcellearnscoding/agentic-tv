package com.agentictv.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// All the colours in one place. Change these to re-theme the whole screen.
// The look: calm and dark, with colour coming from the apps themselves.
val Background = Color(0xFF0A0A0D)
val Accent = Color(0xFFFFFFFF)          // focus highlights are white, not coloured
val TextMain = Color(0xFFF5F5F7)
val TextDim = Color(0xFF9A9AA2)
val Frosted = Color(0x2EFFFFFF)         // see-through white for pills and panels
val FrostedFocused = Color(0xE6FFFFFF)  // almost solid white when selected
val TileColor = Color(0xFF1C1C21)

/**
 * The soft light behind the top of the screen. It glows in the colour of the
 * app you're on (red for Netflix, blue for Disney+...) and blends smoothly
 * from one colour to the next. It's just a drawn gradient, no pictures, so it
 * costs almost no memory.
 */
@Composable
fun Backdrop(glow: Color?, modifier: Modifier = Modifier) {
    val color by animateColorAsState(glow ?: Color(0xFF3A4A8A), tween(600), label = "glow")
    Canvas(modifier.fillMaxSize().background(Background)) {
        drawRect(
            Brush.radialGradient(
                listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(size.width * 0.78f, -size.height * 0.1f),
                radius = size.width * 0.75f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(color.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(size.width * 0.1f, 0f),
                radius = size.width * 0.45f,
            )
        )
    }
}
