package com.agentictv.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The home screen. Android starts this when you press Home (once it's the default). */
class MainActivity : ComponentActivity() {

    private val apps = mutableStateOf<List<TvApp>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Back does nothing on the home screen. There's nowhere to go "back" to.
            BackHandler {}
            HomeScreen(
                apps = apps.value,
                onOpenApp = { launchApp(this, it.packageName) },
                onOpenSettings = { openSettings(this) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-read the app list every time we come back, so new installs show up.
        lifecycleScope.launch {
            apps.value = withContext(Dispatchers.IO) { loadInstalledApps(this@MainActivity) }
        }
    }
}
