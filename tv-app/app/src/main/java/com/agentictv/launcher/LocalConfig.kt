package com.agentictv.launcher

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Settings that live only on the TV, never in GitHub (the repo is public).
 * They're written by scripts on your Mac:
 *   scripts/set-guest-info.sh  -> files/guest.json  (Wi-Fi for guests)
 *   scripts/set-agent.sh       -> files/agent.json  (where the AI brain lives)
 * Only this app can read its own files folder.
 */
data class GuestInfo(val wifiName: String, val wifiPassword: String, val welcome: String)

data class AgentConfig(val url: String, val token: String)

fun loadGuestInfo(context: Context): GuestInfo? = readJson(context, "guest.json")?.let {
    GuestInfo(
        wifiName = it.optString("wifiName"),
        wifiPassword = it.optString("wifiPassword"),
        welcome = it.optString("welcome").ifBlank { "Welcome! Make yourself at home." },
    )
}

fun loadAgentConfig(context: Context): AgentConfig? = readJson(context, "agent.json")?.let {
    val url = it.optString("url")
    val token = it.optString("token")
    if (url.isBlank() || token.isBlank()) null else AgentConfig(url, token)
}

private fun readJson(context: Context, name: String): JSONObject? {
    val file = File(context.filesDir, name)
    if (!file.exists()) return null
    return runCatching { JSONObject(file.readText()) }.getOrNull()
}
