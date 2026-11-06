package com.agentictv.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** One app we can show on the home screen. */
data class TvApp(
    val packageName: String,
    val label: String,
    val image: ImageBitmap,
    /** true when [image] is a wide TV banner, false when it's a square icon. */
    val isBanner: Boolean,
)

/** Finds every installed app that can be opened, sorted by name. */
fun loadInstalledApps(context: Context): List<TvApp> {
    val pm = context.packageManager
    val found = linkedMapOf<String, ResolveInfo>()

    // TV apps first (they have nice wide banners), then normal apps.
    for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (info in pm.queryIntentActivities(intent, 0)) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && pkg !in found) found[pkg] = info
        }
    }

    return found.values.map { info -> info.toTvApp(pm) }.sortedBy { it.label.lowercase() }
}

private fun ResolveInfo.toTvApp(pm: PackageManager): TvApp {
    val banner = activityInfo.loadBanner(pm) ?: activityInfo.applicationInfo.loadBanner(pm)
    val drawable = banner ?: loadIcon(pm)
    val bitmap = if (banner != null) drawable.toBitmap(320, 180) else drawable.toBitmap(160, 160)
    return TvApp(
        packageName = activityInfo.packageName,
        label = loadLabel(pm).toString(),
        image = bitmap.asImageBitmap(),
        isBanner = banner != null,
    )
}

/** Opens an app. Returns false if it couldn't be opened. */
fun launchApp(context: Context, packageName: String): Boolean {
    val pm = context.packageManager
    val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
        ?: pm.getLaunchIntentForPackage(packageName)
        ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}

/** Opens the TV's own settings. */
fun openSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
