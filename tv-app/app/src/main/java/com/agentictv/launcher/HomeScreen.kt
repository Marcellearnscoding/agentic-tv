package com.agentictv.launcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
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
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │ (✦ Ask the TV) (Wi-Fi)                         14:39 │  top bar
 *   │                                                      │
 *   │ NETFLIX                                              │  hero: the app you're on,
 *   │ Press OK to open                                     │  with its picture behind
 *   │ [tile][tile][tile][tile][tile]                       │  Watch row
 *   │ [tile][tile][tile][tile][tile]                       │  All apps (scrolls)
 *   └──────────────────────────────────────────────────────┘
 *
 * 40dp side margins (TVs can cut off the edges); tiles are 160 x 90 (16:9),
 * 5 per row with 16dp gaps.
 */
private val SidePadding = 40.dp
private val TileWidth = 160.dp
private val TileHeight = 90.dp
private val TileGap = 16.dp
private const val TilesPerRow = 5

/** What the big area at the top shows: the thing you're on right now. */
data class Hero(val title: String, val subtitle: String, val banner: ImageBitmap?)

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
    onOpenWifi: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { now = LocalDateTime.now(); delay(1_000) }
    }
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val welcomeHero = Hero(greeting, guest?.welcome ?: "Welcome! Pick something to watch.", null)
    var hero by remember { mutableStateOf<Hero?>(null) }
    // Each time you come back home, show the welcome first. The automatic jump to
    // the first tile shouldn't replace it; moving with the remote does.
    var keepWelcome by remember { mutableStateOf(true) }
    LaunchedEffect(focusKey) { hero = null; keepWelcome = true }

    val favorites = FAVORITE_PACKAGES.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    val others = apps.filter { it !in favorites }
    val firstTile = remember { FocusRequester() }
    LaunchedEffect(focusKey, apps.isNotEmpty()) {
        if (apps.isNotEmpty()) {
            withFrameNanos { } // wait one frame so the tiles exist
            runCatching { firstTile.requestFocus() }
        }
    }

    val shown = hero ?: welcomeHero
    Box(Modifier.fillMaxSize()) {
        Backdrop(shown.banner)
        Column(Modifier.fillMaxSize().padding(top = 22.dp)) {
            TopBar(now, onOpenAssistant, onOpenWifi, onFocused = { hero = it })
            HeroText(shown, Modifier.padding(horizontal = SidePadding).height(132.dp))
            AppRows(
                favorites, others, firstTile, focusKey,
                onOpenApp, onOpenSettings, onOpenLiveTv,
                onFocused = { if (keepWelcome) keepWelcome = false else hero = it },
            )
        }
    }
}

/** Small frosted buttons on the left, a thin clock on the right. */
@Composable
private fun TopBar(
    now: LocalDateTime,
    onOpenAssistant: () -> Unit,
    onOpenWifi: () -> Unit,
    onFocused: (Hero) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = SidePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Pill("✦  Ask the TV", onOpenAssistant) {
            onFocused(Hero("Ask the TV", "Say or type what you'd like, like “put on something funny”.", null))
        }
        Pill("Wi-Fi", onOpenWifi) {
            onFocused(Hero("Guest Wi-Fi", "Show the Wi-Fi details and a code to scan with your phone.", null))
        }
        Spacer(Modifier.weight(1f))
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Light)
    }
}

/** A rounded frosted button. Turns white when the remote is on it. */
@Composable
private fun Pill(label: String, onClick: () -> Unit, onFocus: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus(true) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(stiffness = 600f), label = "pill")
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(if (focused) FrostedFocused else Frosted)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (focused) Background else TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Big title and a line under it, for whatever you're on. Fades when it changes. */
@Composable
private fun HeroText(hero: Hero, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
        AnimatedContent(
            targetState = hero,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "hero",
        ) { h ->
            Column(Modifier.width(560.dp).padding(bottom = 10.dp)) {
                Text(
                    h.title, color = TextMain, fontSize = 34.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(h.subtitle, color = TextDim, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Scrolls the app rows so the row you're on stays comfortably on screen,
 * instead of hugging the very top or bottom edge.
 */
@OptIn(ExperimentalFoundationApi::class)
private val ComfortableScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val top = containerSize * 0.10f
        val bottom = containerSize * 0.94f
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
    onFocused: (Hero) -> Unit,
) {
    // Jump back to the top whenever you return to the home screen.
    val listState = rememberLazyListState()
    LaunchedEffect(focusKey) { listState.scrollToItem(0) }

    // Rows of exactly 5, so moving up and down lands in the same column.
    val otherRows = others.chunked(TilesPerRow)
    val defaultSpec = LocalBringIntoViewSpec.current
    val liveTv = Shortcut("TV & HDMI", "📺", "Live TV and the HDMI inputs.", onOpenLiveTv)
    val settings = Shortcut("Settings", "⚙", "The TV's own settings.", onOpenSettings)

    CompositionLocalProvider(LocalBringIntoViewSpec provides ComfortableScroll) {
        LazyColumn(
            Modifier.fillMaxSize().fadeTopEdge(),
            state = listState,
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
        ) {
            item {
                CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                    AppRow(favorites, onOpenApp, onFocused, firstTile, listOf(liveTv))
                }
            }
            items(otherRows.size) { index ->
                Column {
                    if (index == 0) SectionTitle("All apps")
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                        val isLast = index == otherRows.lastIndex
                        AppRow(otherRows[index], onOpenApp, onFocused, null, if (isLast) listOf(settings) else emptyList())
                    }
                }
            }
            if (otherRows.isEmpty()) {
                item { AppRow(emptyList(), onOpenApp, onFocused, null, listOf(settings)) }
            }
        }
    }
}

/** A tile that isn't an app, like "TV & HDMI" or "Settings". */
private data class Shortcut(val label: String, val symbol: String, val about: String, val onClick: () -> Unit)

@Composable
private fun SectionTitle(title: String) {
    Text(
        title, color = TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = SidePadding, top = 4.dp),
    )
}

@Composable
private fun AppRow(
    apps: List<TvApp>,
    onOpenApp: (TvApp) -> Unit,
    onFocused: (Hero) -> Unit,
    firstTile: FocusRequester?,
    extras: List<Shortcut>,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(TileGap),
        // Room around the tiles so the "lift" and its shadow aren't cut off.
        contentPadding = PaddingValues(horizontal = SidePadding, vertical = 12.dp),
    ) {
        items(apps.size) { index ->
            val app = apps[index]
            Tile(
                label = app.label,
                onClick = { onOpenApp(app) },
                onFocus = { onFocused(Hero(app.label, "Press OK to open", app.banner)) },
                modifier = if (index == 0 && firstTile != null) Modifier.focusRequester(firstTile) else Modifier,
            ) {
                if (app.banner != null) {
                    Image(app.banner, app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    IconTileContent(app.label, app.color) { Image(app.icon, null, modifier = Modifier.size(36.dp)) }
                }
            }
        }
        items(extras.size) { index ->
            val shortcut = extras[index]
            Tile(
                label = shortcut.label,
                onClick = shortcut.onClick,
                onFocus = { onFocused(Hero(shortcut.label, shortcut.about, null)) },
                modifier = if (apps.isEmpty() && index == 0 && firstTile != null) Modifier.focusRequester(firstTile) else Modifier,
            ) {
                IconTileContent(shortcut.label, Color(0xFF2A2A31)) { Text(shortcut.symbol, fontSize = 28.sp) }
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
            Brush.verticalGradient(0f to Color.Transparent, 20.dp.toPx() / size.height to Color.Black),
            blendMode = BlendMode.DstIn,
        )
    }

/** For apps without a TV banner: coloured tile with the icon and the name. */
@Composable
private fun IconTileContent(label: String, color: Color, icon: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize().background(color).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(
            label, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One 16:9 tile. When the remote is on it, it lifts towards you: it grows,
 * casts a soft shadow and gets a glossy shine. No coloured outline.
 */
@Composable
private fun Tile(
    label: String,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "lift")
    val elevation by animateDpAsState(if (focused) 18.dp else 0.dp, tween(200), label = "shadow")
    val shape = RoundedCornerShape(12.dp)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(TileWidth)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .onFocusChanged { if (it.isFocused) onFocus() }
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .size(TileWidth, TileHeight)
                .clip(shape)
                .background(TileColor)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        ) {
            content()
            if (focused) {
                // The glossy shine across the top-left of the lifted tile.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent))
                    )
                )
            }
        }
        Text(
            if (focused) label else "",
            color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(16.dp),
        )
    }
}
