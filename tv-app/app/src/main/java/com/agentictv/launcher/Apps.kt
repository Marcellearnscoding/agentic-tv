package com.agentictv.launcher

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** One app we can show on the home screen. */
data class TvApp(
    val packageName: String,
    val label: String,
    /** A wide 16:9 TV banner, or null if the app only has a square icon. */
    val banner: ImageBitmap?,
    val icon: ImageBitmap,
    /** A colour picked from the icon, used as the tile background for icon-only apps. */
    val color: Color,
)

/**
 * Finds the apps worth showing, sorted by name.
 *
 * Built-in system apps (like "TvServices" or "E-Manual") are skipped, unless they've
 * been updated from the Play Store (that's how YouTube and Netflix show up) or are
 * in [FAVORITE_PACKAGES]. Anything in [HIDDEN_PACKAGES] is always skipped.
 */
fun loadInstalledApps(context: Context): List<TvApp> {
    val pm = context.packageManager
    val found = linkedMapOf<String, ResolveInfo>()

    for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (info in pm.queryIntentActivities(intent, 0)) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName || pkg in HIDDEN_PACKAGES || pkg in found) continue
            if (!isWorthShowing(info.activityInfo.applicationInfo, pkg)) continue
            found[pkg] = info
        }
    }
    return found.values.map { it.toTvApp(pm) }.sortedBy { it.label.lowercase() }
}

private fun isWorthShowing(app: ApplicationInfo, pkg: String): Boolean {
    val isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
    val isUpdated = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
    return !isSystem || isUpdated || pkg in FAVORITE_PACKAGES
}

private fun ResolveInfo.toTvApp(pm: PackageManager): TvApp {
    val bannerDrawable = activityInfo.loadBanner(pm) ?: activityInfo.applicationInfo.loadBanner(pm)
    val iconBitmap = loadIcon(pm).toBitmap(96, 96)
    return TvApp(
        packageName = activityInfo.packageName,
        label = loadLabel(pm).toString(),
        // Keep the banner's own shape; the tile crops it to 16:9 without stretching.
        banner = bannerDrawable?.let {
            val w = it.intrinsicWidth.takeIf { w -> w > 0 } ?: 320
            val h = it.intrinsicHeight.takeIf { h -> h > 0 } ?: 180
            val scale = 240f / maxOf(w, h)
            it.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
        },
        icon = iconBitmap.asImageBitmap(),
        color = averageColor(iconBitmap),
    )
}

/** Average colour of the icon, darkened so white text stays readable on it. */
private fun averageColor(bitmap: Bitmap): Color {
    val small = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
    var r = 0f; var g = 0f; var b = 0f; var n = 0f
    for (x in 0 until 8) for (y in 0 until 8) {
        val p = small.getPixel(x, y)
        val alpha = (p ushr 24) / 255f
        if (alpha < 0.5f) continue
        r += (p shr 16 and 0xFF); g += (p shr 8 and 0xFF); b += (p and 0xFF); n++
    }
    if (n == 0f) return Color(0xFF2A3050)
    val k = 0.55f / 255f // darken
    return Color(r / n * k, g / n * k, b / n * k)
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

/** Searches YouTube for something. Falls back to a YouTube web link. */
fun searchYouTube(context: Context, query: String): Boolean {
    val search = Intent(Intent.ACTION_SEARCH)
        .setPackage("com.google.android.youtube.tv")
        .putExtra(SearchManager.QUERY, query)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(search) }.isSuccess) return true
    val link = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(link) }.isSuccess
}
