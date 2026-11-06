package com.agentictv.launcher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

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
 * The big picture behind the top of the screen. It shows the banner of the app
 * you're on, stretched wide and faded into the dark background. Because banners
 * are small images, stretching them makes a naturally soft, blurry backdrop.
 * When nothing has a banner, a soft glow is drawn instead.
 */
@Composable
fun Backdrop(banner: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Background)) {
        Crossfade(targetState = banner, animationSpec = tween(450), label = "backdrop") { image ->
            Box(Modifier.fillMaxWidth().height(340.dp)) {
                if (image != null) {
                    Image(
                        image, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.55f),
                    )
                } else {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color(0x553A4A8A), Color.Transparent),
                                center = Offset(size.width * 0.75f, 0f),
                                radius = size.width * 0.7f,
                            )
                        )
                    }
                }
                // Fade to dark at the bottom and on the left, so the text stays readable.
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Background))
                    drawRect(Brush.horizontalGradient(0f to Background.copy(alpha = 0.85f), 0.6f to Color.Transparent))
                }
            }
        }
    }
}
