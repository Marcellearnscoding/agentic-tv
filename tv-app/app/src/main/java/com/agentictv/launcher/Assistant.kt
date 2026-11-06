package com.agentictv.launcher

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The TV side of the AI assistant.
 *
 * 1. [askAgent] sends what you said, plus the list of installed apps, to our
 *    "brain" on Supabase (see supabase/functions/tv-agent).
 * 2. The brain asks Claude what to do and answers with a short reply and a list
 *    of actions, like {"type":"open_app","package":"com.netflix.ninja"}.
 * 3. [runActions] does those actions on the TV.
 *
 * Privacy: only the words you said, the names of installed apps and, for a
 * person (not Guest), their name and the likes Ask remembered are sent.
 * Nothing is stored on the server; the likes live only on the TV.
 */
data class AgentAnswer(val reply: String, val actions: List<JSONObject>)

suspend fun askAgent(config: AgentConfig, text: String, apps: List<TvApp>, person: Person?): AgentAnswer =
    withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("text", text)
            .put("apps", JSONArray(apps.map { JSONObject().put("name", it.label).put("package", it.packageName) }))
        if (person != null) {
            body.put("person", JSONObject().put("name", person.name).put("likes", JSONArray(person.likes)))
        }

        val connection = URL(config.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-tv-token", config.token)
            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val ok = connection.responseCode in 200..299
            val stream = if (ok) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!ok) return@withContext AgentAnswer("Sorry, the assistant isn't working right now (error ${connection.responseCode}).", emptyList())

            val json = JSONObject(responseText)
            val list = json.optJSONArray("actions") ?: JSONArray()
            AgentAnswer(
                reply = json.optString("reply"),
                actions = (0 until list.length()).mapNotNull { list.optJSONObject(it) },
            )
        } finally {
            connection.disconnect()
        }
    }

/** Does what the brain asked for. Returns a note for anything that didn't work. */
fun runActions(context: Context, actions: List<JSONObject>): String? {
    var problem: String? = null
    for (action in actions) {
        val worked = when (action.optString("type")) {
            "open_app" -> launchApp(context, action.optString("package"))
            "search_youtube" -> searchYouTube(context, action.optString("query"))
            "set_volume" -> setVolume(context, action.optInt("percent", -1))
            "change_volume" -> changeVolume(context, action.optString("direction"))
            "open_settings" -> { openSettings(context); true }
            else -> true // "say" and unknown actions need nothing from the TV
        }
        if (!worked) problem = "I couldn't do that on this TV."
    }
    return problem
}

private fun audio(context: Context) = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

private fun setVolume(context: Context, percent: Int): Boolean {
    if (percent !in 0..100) return false
    val am = audio(context)
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    am.setStreamVolume(AudioManager.STREAM_MUSIC, max * percent / 100, AudioManager.FLAG_SHOW_UI)
    return true
}

private fun changeVolume(context: Context, direction: String): Boolean {
    val adjust = when (direction) {
        "up" -> AudioManager.ADJUST_RAISE
        "down" -> AudioManager.ADJUST_LOWER
        "mute" -> AudioManager.ADJUST_MUTE
        "unmute" -> AudioManager.ADJUST_UNMUTE
        else -> return false
    }
    audio(context).adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, AudioManager.FLAG_SHOW_UI)
    return true
}
