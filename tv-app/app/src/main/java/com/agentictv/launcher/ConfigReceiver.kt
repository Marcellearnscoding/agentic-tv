package com.agentictv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import java.io.File

/**
 * Lets the scripts on your Mac save settings (guest Wi-Fi, assistant address, weather town) on the TV.
 *
 * Safety: in the manifest this needs the DUMP permission, which adb has but normal
 * apps can't get. So only a computer you allowed for USB debugging can send this.
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("file") ?: return
        if (name !in setOf("guest.json", "agent.json", "weather.json")) return
        val data = intent.getStringExtra("b64") ?: return
        val bytes = runCatching { Base64.decode(data, Base64.DEFAULT) }.getOrNull() ?: return
        File(context.filesDir, name).writeBytes(bytes)
    }
}
