package com.agentictv.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * Screen layout. The TV is 1280x720, which Android treats as about 960 x 540 "dp".
 * Everything here is sized for that:
 *   - 40dp margin left and right, 24dp at the top (TVs can cut off the edges)
 *   - top bar (clock, welcome card, assistant card): 112dp tall
 *   - app tiles: 160 x 90 (16:9), 5 per row with 16dp gaps
 */
private val SidePadding = 40.dp
private val TileWidth = 160.dp
private val TileHeight = 90.dp
private val TileGap = 16.dp
private const val TilesPerRow = 5

@Composable
fun HomeScreen(
    apps: List<TvApp>,
    guest: GuestInfo?,
    /** Changes every time the home screen comes back, so focus returns to the first tile. */
    focusKey: Int,
    onOpenApp: (TvApp) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLiveTv: () -> Unit,
    onOpenAssistant: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { now = LocalDateTime.now(); delay(1_000) }
    }

    val favorites = FAVORITE_PACKAGES.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    val others = apps.filter { it !in favorites }
    val firstTile = remember { FocusRequester() }
    LaunchedEffect(focusKey, apps.isNotEmpty()) {
        if (apps.isNotEmpty()) {
            withFrameNanos { } // wait one frame so the tiles exist
            runCatching { firstTile.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Wallpaper(now.hour)
        Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
            TopBar(now, guest, onOpenAssistant)
            Spacer(Modifier.height(14.dp))
            AppRows(favorites, others, firstTile, focusKey, onOpenApp, onOpenSettings, onOpenLiveTv)
        }
    }
}

/** Clock on the left, welcome card in the middle, assistant on the right. */
@Composable
private fun TopBar(now: LocalDateTime, guest: GuestInfo?, onOpenAssistant: () -> Unit) {
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(112.dp).padding(horizontal = SidePadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.width(200.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = TextMain, fontSize = 44.sp, fontWeight = FontWeight.Light)
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = TextDim, fontSize = 13.sp)
            Text(greeting, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        GuestCard(guest, Modifier.weight(1f).fillMaxHeight())
        AssistantCard(onOpenAssistant, Modifier.width(220.dp).fillMaxHeight())
    }
}

/** The "Ask the TV" card. Press OK on it to open the assistant. */
@Composable
private fun AssistantCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .graphicsLayer { val s = if (focused) 1.04f else 1f; scaleX = s; scaleY = s }
            .clip(shape)
            .background(if (focused) Accent.copy(alpha = 0.35f) else CardColor)
            .then(if (focused) Modifier.border(2.dp, Accent, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("✦  Ask the TV", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("“Put on something funny”\n“Turn the volume down”", color = TextDim, fontSize = 12.sp)
        Text(if (focused) "Press OK to ask" else " ", color = Accent, fontSize = 11.sp)
    }
}

/**
 * Scrolls the app rows so the row you're on stays comfortably on screen,
 * instead of hugging the very top or bottom edge.
 */
@OptIn(ExperimentalFoundationApi::class)
private val ComfortableScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val top = containerSize * 0.12f
        val bottom = containerSize * 0.92f
        return when {
            offset < top -> offset - top
            offset + size > bottom -> offset + size - bottom
            else -> 0f
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRows(
    favorites: List<TvApp>,
    others: List<TvApp>,
    firstTile: FocusRequester,
    focusKey: Int,
    onOpenApp: (TvApp) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLiveTv: () -> Unit,
) {
    // Jump back to the top whenever you return to the home screen.
    val listState = rememberLazyListState()
    LaunchedEffect(focusKey) { listState.scrollToItem(0) }
    // Rows of exactly 5, so moving up and down lands in the same column.
    val otherRows = others.chunked(TilesPerRow)
    val defaultSpec = LocalBringIntoViewSpec.current

    CompositionLocalProvider(LocalBringIntoViewSpec provides ComfortableScroll) {
        LazyColumn(Modifier.fillMaxSize().fadeTopEdge(), state = listState, contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
            item {
                Section("Watch") {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                        AppRow(favorites, onOpenApp, firstTile, onOpenLiveTv)
                    }
                }
            }
            items(otherRows.size) { index ->
                val content = @Composable {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                        AppRow(otherRows[index], onOpenApp, null)
                    }
                }
                if (index == 0) Section("All apps", content) else content()
            }
            item {
                Section("More") {
                    Row(Modifier.padding(horizontal = SidePadding, vertical = 8.dp)) {
                        Tile(label = "Settings", onClick = onOpenSettings) {
                            IconTileContent("⚙", "Settings", Color(0xFF2E3550))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title, color = TextDim, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = SidePadding, top = 6.dp),
        )
        content()
    }
}

@Composable
private fun AppRow(
    apps: List<TvApp>,
    onOpenApp: (TvApp) -> Unit,
    firstTile: FocusRequester?,
    /** If set, a "TV & HDMI" tile is added at the end of the row. */
    onOpenLiveTv: (() -> Unit)? = null,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(TileGap),
        // 8dp of room around tiles so the "grow on focus" effect isn't cut off.
        contentPadding = PaddingValues(horizontal = SidePadding, vertical = 8.dp),
    ) {
        items(apps.size) { index ->
            val app = apps[index]
            Tile(
                label = app.label,
                onClick = { onOpenApp(app) },
                modifier = if (index == 0 && firstTile != null) Modifier.focusRequester(firstTile) else Modifier,
            ) {
                if (app.banner != null) {
                    Image(app.banner, app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    IconTileContent(null, app.label, app.color, app)
                }
            }
        }
        if (onOpenLiveTv != null) {
            item {
                Tile(
                    label = "TV & HDMI",
                    onClick = onOpenLiveTv,
                    modifier = if (apps.isEmpty() && firstTile != null) Modifier.focusRequester(firstTile) else Modifier,
                ) {
                    IconTileContent("📺", "TV & HDMI", Color(0xFF2E3550))
                }
            }
        }
    }
}

/** Softly fades rows out at the top edge, so a title scrolling away doesn't leave bits behind. */
private fun Modifier.fadeTopEdge(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 24.dp.toPx() / size.height to Color.Black),
            blendMode = BlendMode.DstIn,
        )
    }

/** For apps without a TV banner: coloured tile with the icon and the app's name. */
@Composable
private fun IconTileContent(symbol: String?, label: String, color: Color, app: TvApp? = null) {
    Row(
        modifier = Modifier.fillMaxSize().background(color).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app != null) {
            Image(app.icon, null, modifier = Modifier.size(36.dp))
        } else if (symbol != null) {
            Text(symbol, color = TextMain, fontSize = 28.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One 16:9 tile. Grows a little and gets an outline when the remote is on it. */
@Composable
private fun Tile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(TileWidth)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .graphicsLayer { val s = if (focused) 1.08f else 1f; scaleX = s; scaleY = s }
                .size(TileWidth, TileHeight)
                .clip(shape)
                .background(TileColor)
                .then(if (focused) Modifier.border(2.dp, Accent, shape) else Modifier)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        ) { content() }
        // The name only shows on the tile you're on, to keep the screen calm.
        Text(
            if (focused) label else "",
            color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(16.dp),
        )
    }
}
