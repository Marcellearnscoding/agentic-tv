package com.agentictv.launcher

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
 *   │ (Guest)(Marcel)(+)       (Ask)(Inputs)(Wi-Fi)(Settings)
 *   │ GOOD AFTERNOON, MARCEL                               │
 *   │ 13:55 | FRIDAY 6 NOVEMBER                            │
 *   │                  ( coloured glow )                   │
 *   │ Netflix                       ← the app you're on    │
 *   │ ╭────────────────────────────────────────────────╮   │
 *   │ │ [tile]   [tile]   [tile]   [tile]              │   │  the row you're on
 *   │ ╰────────────────────────────────────────────────╯   │
 *   │  [tile]   [tile]   [tile]   [tile]                   │  next row peeks in
 *   └──────────────────────────────────────────────────────┘
 *
 * 48dp side margins (TVs can cut off the edges). Tiles are 156 x 88 (16:9),
 * 5 per row, so all five streaming apps fit in one row.
 */
private val SidePadding = 48.dp
private val TileWidth = 156.dp
private val TileHeight = 88.dp
private val TileGap = 14.dp
private val TrayPadding = 14.dp
private const val TilesPerRow = 5

/** The big title above the tiles: the thing you're on right now, and its colour. */
data class Hero(val title: String, val subtitle: String = "", val glow: Color? = null)

@Composable
fun HomeScreen(
    apps: List<TvApp>,
    guest: GuestInfo?,
    profiles: Profiles,
    /** Changes every time the home screen comes back, so focus returns to the first tile. */
    focusKey: Int,
    onOpenApp: (TvApp) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLiveTv: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenWifi: () -> Unit,
    /** A name chip was pressed: switch to that person (or, if it's already them, show their details). */
    onPersonChip: (String) -> Unit,
    onAddPerson: () -> Unit,
) {
    var hero by remember { mutableStateOf(Hero("")) }

    val favorites = profiles.sortForMe(
        FAVORITE_PACKAGES.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } },
    ) { it.packageName }
    val others = apps.filter { it !in favorites }
    val rows = (favorites.chunked(TilesPerRow) + others.chunked(TilesPerRow)).filter { it.isNotEmpty() }

    val firstTile = remember { FocusRequester() }
    LaunchedEffect(focusKey, apps.isNotEmpty()) {
        if (apps.isNotEmpty()) {
            withFrameNanos { } // wait one frame so the tiles exist
            runCatching { firstTile.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(hero.glow)
        Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
            Header(
                guest = guest,
                profiles = profiles,
                onPersonChip = onPersonChip,
                onAddPerson = onAddPerson,
                onFocused = { hero = it },
                onOpenAssistant = onOpenAssistant,
                onOpenLiveTv = onOpenLiveTv,
                onOpenWifi = onOpenWifi,
                onOpenSettings = onOpenSettings,
            )
            Spacer(Modifier.weight(1f))
            Title(hero)
            AppRows(rows, firstTile, focusKey, onOpenApp, onFocused = { hero = it })
        }
    }
}

/** Greeting, clock and date on the left; small frosted buttons on the right. */
@Composable
private fun Header(
    guest: GuestInfo?,
    profiles: Profiles,
    onPersonChip: (String) -> Unit,
    onAddPerson: () -> Unit,
    onFocused: (Hero) -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenLiveTv: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { now = LocalDateTime.now(); delay(1_000) }
    }
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val timeGreeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val greeting = profiles.current?.let { "$timeGreeting, ${it.name}" }
        ?: guest?.welcome?.takeIf { it.isNotBlank() } ?: timeGreeting

    Column(Modifier.fillMaxWidth().padding(horizontal = SidePadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Who's watching: Guest, then each person, then "+" to add someone.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val names = listOf(GuestName) + profiles.people.map { it.name }
                names.forEach { name ->
                    Chip(name, active = name == profiles.active, onClick = { onPersonChip(name) }) {
                        onFocused(
                            if (name == GuestName) Hero("Guest", "For visitors. Ask won't remember anything.")
                            else if (name == profiles.active) Hero(name, "Press OK to see what Ask remembers about you.")
                            else Hero(name, "Press OK to switch to $name.")
                        )
                    }
                }
                Chip("+", active = false, onClick = onAddPerson) {
                    onFocused(Hero("Add a person", "Get your own app order, and Ask remembers what you like."))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("✦  Ask", onOpenAssistant) { onFocused(Hero("Ask the TV", "Say or type what you'd like, like “put on something funny”.")) }
                Pill("Inputs", onOpenLiveTv) { onFocused(Hero("TV & HDMI", "Live TV and the HDMI inputs.")) }
                Pill("Wi-Fi", onOpenWifi) { onFocused(Hero("Guest Wi-Fi", "Show the Wi-Fi details and a code to scan with your phone.")) }
                Pill("Settings", onOpenSettings) { onFocused(Hero("Settings", "The TV's own settings.")) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Caps(greeting)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                now.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm")),
                color = TextMain, fontSize = 46.sp, fontWeight = FontWeight.Light,
            )
            if (!is24h) {
                Text(
                    if (now.hour < 12) " AM" else " PM",
                    color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Light,
                )
            }
            Box(Modifier.padding(horizontal = 16.dp).width(1.dp).height(26.dp).background(TextDim.copy(alpha = 0.5f)))
            Caps(now.format(DateTimeFormatter.ofPattern("EEEE  d MMMM")))
        }
    }
}

/**
 * A small name chip. The person using the TV has a soft filled chip;
 * the one the remote is on turns white.
 */
@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit, onFocus: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus() }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(stiffness = 600f), label = "chip")
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                when {
                    focused -> FrostedFocused
                    active -> Color.White.copy(alpha = 0.22f)
                    else -> Color.Transparent
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = when {
                focused -> Background
                active -> TextMain
                else -> TextDim
            },
            fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/** Small, spaced-out capital letters, for the greeting and the date. */
@Composable
private fun Caps(text: String) {
    Text(
        text.uppercase(), color = TextMain.copy(alpha = 0.85f), fontSize = 12.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** A rounded frosted button with a thin edge. Turns white when the remote is on it. */
@Composable
private fun Pill(label: String, onClick: () -> Unit, onFocus: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus() }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(stiffness = 600f), label = "pill")
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (focused) FrostedFocused else Frosted)
            .border(1.dp, Color.White.copy(alpha = if (focused) 0f else 0.18f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (focused) Background else TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** The name of whatever you're on, big, just above the tiles. Fades when it changes. */
@Composable
private fun Title(hero: Hero) {
    AnimatedContent(
        targetState = hero,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        label = "title",
        modifier = Modifier.padding(horizontal = SidePadding).height(76.dp),
    ) { h ->
        Column(Modifier.width(620.dp).height(76.dp), verticalArrangement = Arrangement.Bottom) {
            Text(
                h.title, color = TextMain, fontSize = 40.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (h.subtitle.isNotEmpty()) {
                Text(h.subtitle, color = TextDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Scrolls so the row you're on always sits in the same place, like on Apple TV.
 * Rows above it slide out of sight; the next row peeks in below.
 */
@OptIn(ExperimentalFoundationApi::class)
private val PinToTop = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - containerSize * 0.10f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRows(
    rows: List<List<TvApp>>,
    firstTile: FocusRequester,
    focusKey: Int,
    onOpenApp: (TvApp) -> Unit,
    onFocused: (Hero) -> Unit,
) {
    // Jump back to the top whenever you return to the home screen.
    val listState = rememberLazyListState()
    LaunchedEffect(focusKey) { listState.scrollToItem(0) }

    CompositionLocalProvider(LocalBringIntoViewSpec provides PinToTop) {
        LazyColumn(
            Modifier.fillMaxWidth().height(206.dp),
            state = listState,
            // Space at the bottom lets even the last row scroll up into place.
            contentPadding = PaddingValues(top = 6.dp, bottom = 120.dp),
        ) {
            items(rows.size) { index ->
                AppRow(rows[index], onOpenApp, onFocused, if (index == 0) firstTile else null)
            }
        }
    }
}

/** One row of up to 5 tiles. The row you're on sits on a frosted strip. */
@Composable
private fun AppRow(
    apps: List<TvApp>,
    onOpenApp: (TvApp) -> Unit,
    onFocused: (Hero) -> Unit,
    firstTile: FocusRequester?,
) {
    var active by remember { mutableStateOf(false) }
    val tray by animateColorAsState(if (active) Color.White.copy(alpha = 0.07f) else Color.Transparent, tween(250), label = "tray")
    val edge by animateColorAsState(if (active) Color.White.copy(alpha = 0.10f) else Color.Transparent, tween(250), label = "edge")
    val shape = RoundedCornerShape(24.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(TileGap),
        modifier = Modifier
            .padding(horizontal = SidePadding - TrayPadding, vertical = 4.dp)
            .onFocusChanged { active = it.hasFocus }
            .background(tray, shape)
            .border(1.dp, edge, shape)
            .padding(TrayPadding),
    ) {
        apps.forEachIndexed { index, app ->
            Tile(
                onClick = { onOpenApp(app) },
                onFocus = { onFocused(Hero(app.label, glow = app.glow)) },
                modifier = if (index == 0 && firstTile != null) Modifier.focusRequester(firstTile) else Modifier,
            ) {
                if (app.banner != null) {
                    Image(app.banner, app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    IconTileContent(app)
                }
            }
        }
    }
}

/** For apps without a TV banner: their icon and name on a tile in their own colour. */
@Composable
private fun IconTileContent(app: TvApp) {
    Row(
        modifier = Modifier.fillMaxSize().background(app.color).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(app.icon, null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            app.label, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One 16:9 tile. When the remote is on it, it grows a little, gets a crisp
 * white edge and a soft shadow.
 */
@Composable
private fun Tile(
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, spring(dampingRatio = 0.75f, stiffness = 500f), label = "lift")
    val elevation by animateDpAsState(if (focused) 16.dp else 0.dp, tween(200), label = "shadow")
    val shape = RoundedCornerShape(14.dp)

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
        if (focused) Box(Modifier.fillMaxSize().border(3.dp, Color.White, shape))
    }
}
