package com.agentictv.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Colours used across the screen. Change these to re-theme everything.
private val Background = Brush.verticalGradient(listOf(Color(0xFF14182B), Color(0xFF05060A)))
private val Accent = Color(0xFF7C8CFF)
private val TextMain = Color(0xFFF2F3F7)
private val TextDim = Color(0xFF9AA0B4)
private val TileColor = Color(0xFF1F2438)

@Composable
fun HomeScreen(
    apps: List<TvApp>,
    onOpenApp: (TvApp) -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Favourites in the order of FAVORITE_PACKAGES, skipping ones that aren't installed.
    val favorites = FAVORITE_PACKAGES.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    val others = apps.filter { it !in favorites }
    val firstTileFocus = remember { FocusRequester() }

    // Put the remote's focus on the first tile once the apps have loaded.
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) runCatching { firstTileFocus.requestFocus() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(horizontal = 64.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item { Header() }

        if (favorites.isNotEmpty()) {
            item { SectionTitle("Watch") }
            item {
                AppRow(favorites, onOpenApp, Modifier.focusRequester(firstTileFocus))
            }
        }

        item { SectionTitle("All apps") }
        // Show "All apps" in rows of 5.
        val rows = others.chunked(5)
        items(rows.size) { index ->
            val first = if (favorites.isEmpty() && index == 0) Modifier.focusRequester(firstTileFocus) else Modifier
            AppRow(rows[index], onOpenApp, first)
        }

        item {
            Tile(label = "Settings", onClick = onOpenSettings) {
                Text("⚙", fontSize = 40.sp, color = TextMain)
            }
        }
    }
}

/** Big clock, date and a friendly hello. */
@Composable
private fun Header() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text("$greeting!", color = TextMain, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
            Text("Welcome. Pick something below with the remote.", color = TextDim, fontSize = 20.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = TextMain, fontSize = 56.sp, fontWeight = FontWeight.Light)
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = TextDim, fontSize = 18.sp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = TextDim, fontSize = 22.sp, fontWeight = FontWeight.Medium)
}

/** A horizontal row of app tiles. [firstModifier] goes on the first tile only. */
@Composable
private fun AppRow(apps: List<TvApp>, onOpenApp: (TvApp) -> Unit, firstModifier: Modifier = Modifier) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
    ) {
        items(apps.size) { index ->
            val app = apps[index]
            Tile(
                label = app.label,
                onClick = { onOpenApp(app) },
                modifier = if (index == 0) firstModifier else Modifier,
            ) {
                Image(
                    bitmap = app.image,
                    contentDescription = app.label,
                    contentScale = if (app.isBanner) ContentScale.Crop else ContentScale.Fit,
                    modifier = if (app.isBanner) Modifier.fillMaxSize() else Modifier.size(72.dp),
                )
            }
        }
    }
}

/** One tile. It grows and gets an outline when the remote is on it. */
@Composable
private fun Tile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(224.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .scale(if (focused) 1.1f else 1f)
                .width(224.dp)
                .height(126.dp)
                .clip(shape)
                .background(TileColor)
                .then(if (focused) Modifier.border(3.dp, Accent, shape) else Modifier)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        ) { content() }
        Spacer(Modifier.height(10.dp))
        Text(
            label,
            color = if (focused) TextMain else TextDim,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
    }
}
