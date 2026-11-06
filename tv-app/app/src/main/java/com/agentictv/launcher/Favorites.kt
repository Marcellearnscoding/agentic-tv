package com.agentictv.launcher

/**
 * The apps shown big in the "Watch" row, in this order.
 * Edit this list to change them. Apps that aren't installed are skipped.
 *
 * Tip: run scripts/list-apps.sh to see the package names on your TV.
 */
val FAVORITE_PACKAGES = listOf(
    "com.google.android.youtube.tv",          // YouTube
    "com.netflix.ninja",                      // Netflix
    "com.disney.disneyplus",                  // Disney+
    "com.amazon.amazonvideo.livingroom",      // Prime Video
    "com.apple.atve.androidtv.appletv",       // Apple TV
)

/**
 * Apps we never show, even if they're installed. Built-in TV system apps that
 * guests don't need. Add a package name here to hide something.
 */
val HIDDEN_PACKAGES = setOf(
    "com.android.tv.settings",               // Settings has its own tile
    "com.google.android.tvlauncher",         // the old home screen
    "com.smartdevice.leanbacklauncher.customizer",
)
