package com.agentictv.launcher

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.speech.RecognizerIntent
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
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
    private val profiles = mutableStateOf(Profiles(emptyList(), GuestName))
    private val people = mutableStateOf<PeopleState>(PeopleState.Hidden)
    private val showPhone = mutableStateOf(false)
    private lateinit var phoneRemote: PhoneRemote
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
        phoneRemote = PhoneRemote(this) { text -> withContext(Dispatchers.Main) { answer(text) } }
        phoneRemote.start()
        setContent {
            BackHandler {
                // Back closes the assistant. On the home screen itself, Back does nothing.
                if (showWifi.value) showWifi.value = false
                else if (showPhone.value) showPhone.value = false
                else if (people.value != PeopleState.Hidden) people.value = PeopleState.Hidden
                else if (assistant.value != AssistantState.Hidden) closeAssistant()
            }
            Box {
                HomeScreen(
                    apps = apps.value,
                    guest = guest.value,
                    profiles = profiles.value,
                    focusKey = focusKey.value,
                    onOpenApp = { openApp(it.packageName) },
                    onOpenSettings = { openSettings(this@MainActivity) },
                    onOpenLiveTv = { launchApp(this@MainActivity, LIVE_TV_PACKAGE) },
                    onOpenAssistant = { openAssistant() },
                    onOpenWifi = { showWifi.value = true },
                    onOpenPhone = { showPhone.value = true },
                    onPersonChip = { name ->
                        if (name == profiles.value.active && name != GuestName) people.value = PeopleState.Managing(name)
                        else updateProfiles { it.switchTo(name) }
                    },
                    onAddPerson = { people.value = PeopleState.Adding },
                )
                if (showWifi.value) GuestPanel(guest.value) { showWifi.value = false }
                if (showPhone.value) PhonePanel(phoneRemote.link()) { showPhone.value = false }
                PeoplePanel(
                    state = people.value,
                    profiles = profiles.value,
                    onAdd = { name -> updateProfiles { it.add(name) }; closePeople() },
                    onForget = { updateProfiles { it.forgetLikes() } },
                    onRemove = { name -> updateProfiles { it.remove(name) }; closePeople() },
                    onClose = { closePeople() },
                )
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
            profiles.value = withContext(Dispatchers.IO) { loadProfiles(this@MainActivity) }
            agent = withContext(Dispatchers.IO) { loadAgentConfig(this@MainActivity) }
            if (assistant.value == AssistantState.Hidden) focusKey.value++
        }
    }

    /** Pressing Home while already on the home screen: close pop-ups, go back to the top. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        closeAssistant()
        showWifi.value = false
        showPhone.value = false
        people.value = PeopleState.Hidden
        focusKey.value++
    }

    /** Changes the people list and saves it on the TV. */
    private fun updateProfiles(change: (Profiles) -> Profiles) {
        val updated = change(profiles.value)
        profiles.value = updated
        lifecycleScope.launch(Dispatchers.IO) { saveProfiles(this@MainActivity, updated) }
    }

    private fun closePeople() {
        people.value = PeopleState.Hidden
        focusKey.value++
    }

    /** Opens an app and counts it, so each person's favourite apps move to the front. */
    private fun openApp(pkg: String) {
        if (launchApp(this, pkg)) updateProfiles { it.countOpen(pkg) }
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

    override fun onDestroy() {
        phoneRemote.stop()
        super.onDestroy()
    }

    private fun ask(question: String) {
        askJob?.cancel()
        askJob = lifecycleScope.launch { answer(question) }
    }

    /** Asks the assistant, shows the answer on the TV and returns it (for the phone remote). */
    private suspend fun answer(question: String): String {
        val config = agent ?: run { assistant.value = AssistantState.NotSetUp; return "The assistant isn't set up on this TV yet." }
        showPhone.value = false
        assistant.value = AssistantState.Thinking(question)
        val answer = try {
            askAgent(config, question, apps.value, profiles.value.current)
        } catch (e: CancellationException) {
            throw e // asked again or closed the panel: stay quiet
        } catch (e: Exception) {
            Log.w("AgenticTV", "Assistant request failed", e)
            AgentAnswer("Sorry, I couldn't reach the assistant. Is the TV online?", emptyList())
        }
        val opensSomething = answer.actions.any { it.optString("type") in setOf("open_app", "search_youtube", "open_settings") }
        // "remember" and "forget" are about the person, not the TV, so handle them here.
        answer.actions.forEach { action ->
            when (action.optString("type")) {
                "remember" -> updateProfiles { it.remember(action.optString("note")) }
                "forget_likes" -> updateProfiles { it.forgetLikes() }
            }
        }
        val problem = runActions(this@MainActivity, answer.actions)
        val reply = listOfNotNull(answer.reply.ifBlank { null }, problem).joinToString(" ").ifBlank { "Done!" }
        // If an app opened, close the panel so it's gone when you come back.
        assistant.value = if (opensSomething && problem == null) AssistantState.Hidden
        else AssistantState.Answered(question, reply)
        return reply
    }
}
