package com.agentictv.launcher

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The home screen. Android starts this when you press Home. */
class MainActivity : ComponentActivity() {

    private val apps = mutableStateOf<List<TvApp>>(emptyList())
    private val guest = mutableStateOf<GuestInfo?>(null)
    private val assistant = mutableStateOf<AssistantState>(AssistantState.Hidden)
    private val focusKey = mutableStateOf(0)
    private val showWifi = mutableStateOf(false)
    private var agent: AgentConfig? = null
    private var askJob: Job? = null

    /** Opens the TV's built-in "speak now" screen and gets back the words. */
    private val voiceInput = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val words = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!words.isNullOrBlank()) ask(words)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler {
                // Back closes the assistant. On the home screen itself, Back does nothing.
                if (showWifi.value) showWifi.value = false
                else if (assistant.value != AssistantState.Hidden) closeAssistant()
            }
            Box {
                HomeScreen(
                    apps = apps.value,
                    guest = guest.value,
                    focusKey = focusKey.value,
                    onOpenApp = { launchApp(this@MainActivity, it.packageName) },
                    onOpenSettings = { openSettings(this@MainActivity) },
                    onOpenLiveTv = { launchApp(this@MainActivity, LIVE_TV_PACKAGE) },
                    onOpenAssistant = { openAssistant() },
                    onOpenWifi = { showWifi.value = true },
                )
                if (showWifi.value) GuestPanel(guest.value) { showWifi.value = false }
                AssistantPanel(
                    state = assistant.value,
                    onSpeak = { startVoice() },
                    onAsk = { ask(it) },
                    onClose = { closeAssistant() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-read everything when we come back, so new apps and settings show up.
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { loadInstalledApps(this@MainActivity) }
            apps.value = loaded
            guest.value = withContext(Dispatchers.IO) { loadGuestInfo(this@MainActivity) }
            agent = withContext(Dispatchers.IO) { loadAgentConfig(this@MainActivity) }
            if (assistant.value == AssistantState.Hidden) focusKey.value++
        }
    }

    /** Pressing Home while already on the home screen: close pop-ups, go back to the top. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        closeAssistant()
        showWifi.value = false
        focusKey.value++
    }

    /** The search / mic key on the remote opens the assistant straight away. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST) {
            openAssistant()
            startVoice()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun openAssistant() {
        assistant.value = if (agent == null) AssistantState.NotSetUp else AssistantState.Asking
    }

    private fun closeAssistant() {
        askJob?.cancel()
        assistant.value = AssistantState.Hidden
    }

    private fun startVoice() {
        if (agent == null) { assistant.value = AssistantState.NotSetUp; return }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What would you like to watch?")
        runCatching { voiceInput.launch(intent) }.onFailure {
            assistant.value = AssistantState.Answered("", "Voice isn't available on this TV. Type instead.")
        }
    }

    private fun ask(question: String) {
        val config = agent ?: run { assistant.value = AssistantState.NotSetUp; return }
        assistant.value = AssistantState.Thinking(question)
        askJob?.cancel()
        askJob = lifecycleScope.launch {
            val answer = runCatching { askAgent(config, question, apps.value) }.getOrElse {
                AgentAnswer("Sorry, I couldn't reach the assistant. Is the TV online?", emptyList())
            }
            val opensSomething = answer.actions.any { it.optString("type") in setOf("open_app", "search_youtube", "open_settings") }
            val problem = runActions(this@MainActivity, answer.actions)
            val reply = listOfNotNull(answer.reply.ifBlank { null }, problem).joinToString(" ").ifBlank { "Done!" }
            // If an app opened, close the panel so it's gone when you come back.
            assistant.value = if (opensSomething && problem == null) AssistantState.Hidden
            else AssistantState.Answered(question, reply)
        }
    }
}
