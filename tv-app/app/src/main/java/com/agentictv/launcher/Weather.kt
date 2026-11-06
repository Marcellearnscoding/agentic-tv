package com.agentictv.launcher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Weather for the home screen, from Open-Meteo (open-meteo.com): free, no
 * account, no API key, and no tracking.
 *
 * Privacy: the TV only sends the town's coordinates, which you set with
 * scripts/set-weather.sh. They're saved on the TV (files/weather.json), never in GitHub.
 */
data class WeatherPlace(val name: String, val latitude: Double, val longitude: Double)

data class Weather(
    val place: String,
    val temperature: Int,
    val code: Int,
    val isDay: Boolean,
    val hours: List<HourForecast>,
    val days: List<DayForecast>,
    val fetchedAt: Long = System.currentTimeMillis(),
)

data class HourForecast(val time: LocalDateTime, val temperature: Int, val code: Int)
data class DayForecast(val date: LocalDate, val code: Int, val high: Int, val low: Int, val rainChance: Int)

fun loadWeatherPlace(context: Context): WeatherPlace? {
    val json = runCatching { JSONObject(File(context.filesDir, "weather.json").readText()) }.getOrNull() ?: return null
    val lat = json.optDouble("latitude", Double.NaN)
    val lon = json.optDouble("longitude", Double.NaN)
    if (lat.isNaN() || lon.isNaN()) return null
    return WeatherPlace(json.optString("name"), lat, lon)
}

/** Gets the current weather plus the next hours and days. Returns null when offline. */
suspend fun fetchWeather(place: WeatherPlace): Weather? = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
                "&current=temperature_2m,weather_code,is_day" +
                "&hourly=temperature_2m,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=6"
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        val text = try { connection.inputStream.bufferedReader().use { it.readText() } } finally { connection.disconnect() }
        val json = JSONObject(text)

        val current = json.getJSONObject("current")
        val now = LocalDateTime.parse(current.getString("time"))

        val hourly = json.getJSONObject("hourly")
        val hourTimes = hourly.getJSONArray("time")
        val hourTemps = hourly.getJSONArray("temperature_2m")
        val hourCodes = hourly.getJSONArray("weather_code")
        val hours = (0 until hourTimes.length())
            .map { HourForecast(LocalDateTime.parse(hourTimes.getString(it)), hourTemps.getDouble(it).roundToInt(), hourCodes.getInt(it)) }
            .filter { it.time.isAfter(now) }
            .filterIndexed { i, _ -> i % 3 == 0 } // every 3 hours
            .take(6)

        val daily = json.getJSONObject("daily")
        val dayDates = daily.getJSONArray("time")
        val days = (0 until dayDates.length()).map {
            DayForecast(
                date = LocalDate.parse(dayDates.getString(it)),
                code = daily.getJSONArray("weather_code").getInt(it),
                high = daily.getJSONArray("temperature_2m_max").getDouble(it).roundToInt(),
                low = daily.getJSONArray("temperature_2m_min").getDouble(it).roundToInt(),
                rainChance = daily.getJSONArray("precipitation_probability_max").optInt(it, 0),
            )
        }

        Weather(
            place = place.name,
            temperature = current.getDouble("temperature_2m").roundToInt(),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            hours = hours,
            days = days,
        )
    }.getOrNull()
}

private fun Double.roundToInt() = Math.round(this).toInt()

/** A symbol and a few words for each weather code (the WMO standard Open-Meteo uses). */
fun weatherLook(code: Int, isDay: Boolean = true): Pair<String, String> = when (code) {
    0 -> (if (isDay) "☀️" else "🌙") to "Clear"
    1 -> (if (isDay) "🌤️" else "🌙") to "Mostly clear"
    2 -> (if (isDay) "⛅" else "☁️") to "Partly cloudy"
    3 -> "☁️" to "Cloudy"
    45, 48 -> "🌫️" to "Foggy"
    51, 53, 55, 56, 57 -> "🌦️" to "Drizzle"
    61, 63, 80, 81 -> "🌧️" to "Rain"
    65, 82, 66, 67 -> "🌧️" to "Heavy rain"
    71, 73, 75, 77, 85, 86 -> "🌨️" to "Snow"
    95, 96, 99 -> "⛈️" to "Thunderstorms"
    else -> "🌡️" to "Weather"
}
