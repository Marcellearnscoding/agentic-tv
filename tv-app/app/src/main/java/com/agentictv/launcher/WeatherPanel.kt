package com.agentictv.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The forecast pop-up: now, the next hours, and the next days.
 * Back or OK closes it.
 */
@Composable
fun WeatherPanel(weather: Weather, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val (symbol, words) = weatherLook(weather.code, weather.isDay)
    val today = weather.days.firstOrNull()

    Box(
        Modifier.fillMaxSize().background(Color(0xD905060B))
            .focusRequester(focus)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(760.dp).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.08f)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Now
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(symbol, fontSize = 54.sp)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text("${weather.temperature}°", color = TextMain, fontSize = 56.sp, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(words, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text(
                        weather.place + (today?.let { "  ·  ${it.high}° / ${it.low}°" } ?: ""),
                        color = TextDim, fontSize = 14.sp,
                    )
                }
            }

            // Next hours
            if (weather.hours.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    weather.hours.forEach { hour ->
                        ForecastCell(
                            top = hour.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                            symbol = weatherLook(hour.code, hour.time.hour in 7..19).first,
                            bottom = "${hour.temperature}°",
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))

            // Next days
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weather.days.take(6).forEach { day ->
                    ForecastCell(
                        top = dayName(day.date),
                        symbol = weatherLook(day.code).first,
                        bottom = "${day.high}° / ${day.low}°",
                        extra = if (day.rainChance >= 30) "💧 ${day.rainChance}%" else "",
                    )
                }
            }

            Text("Press OK or Back to close", color = TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ForecastCell(top: String, symbol: String, bottom: String, extra: String = "") {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) {
        Text(top.uppercase(), color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(symbol, fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(bottom, color = TextMain, fontSize = 15.sp, textAlign = TextAlign.Center)
        if (extra.isNotEmpty()) Text(extra, color = TextDim, fontSize = 11.sp)
    }
}

private fun dayName(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().plusDays(1) -> "Tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern("EEE"))
}
