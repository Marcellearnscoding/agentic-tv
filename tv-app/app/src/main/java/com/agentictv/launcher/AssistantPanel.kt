package com.agentictv.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the assistant panel is showing right now. */
sealed interface AssistantState {
    data object Hidden : AssistantState
    data object Asking : AssistantState
    data class Thinking(val question: String) : AssistantState
    data class Answered(val question: String, val reply: String) : AssistantState
    data object NotSetUp : AssistantState
}

/**
 * The assistant pop-up. Talk with the "Speak" button (uses the TV's own voice
 * typing), or type with the on-screen keyboard.
 */
@Composable
fun AssistantPanel(
    state: AssistantState,
    onSpeak: () -> Unit,
    onAsk: (String) -> Unit,
    onClose: () -> Unit,
) {
    if (state == AssistantState.Hidden) return
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(state) { runCatching { firstButton.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color(0xCC05060B)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1E33))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("✦  Ask the TV", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

            when (state) {
                AssistantState.NotSetUp -> {
                    Text("The assistant isn't set up on this TV yet.", color = TextDim, fontSize = 15.sp)
                    PanelButton("Close", onClose, Modifier.focusRequester(firstButton))
                }

                AssistantState.Asking -> {
                    Text("Try: “Open Netflix”, “Find cat videos on YouTube”, “Volume to 20”.", color = TextDim, fontSize = 13.sp)
                    var text by remember { mutableStateOf("") }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PanelButton("🎤  Speak", onSpeak, Modifier.focusRequester(firstButton))
                        PanelButton("Close", onClose)
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("…or type here", color = TextDim) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onAsk(text) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                            focusedBorderColor = Accent, unfocusedBorderColor = Color(0x44FFFFFF),
                            cursorColor = Accent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is AssistantState.Thinking -> {
                    Text("“${state.question}”", color = TextMain, fontSize = 16.sp)
                    Text("Thinking…", color = Accent, fontSize = 15.sp)
                    PanelButton("Cancel", onClose, Modifier.focusRequester(firstButton))
                }

                is AssistantState.Answered -> {
                    Text("“${state.question}”", color = TextDim, fontSize = 14.sp)
                    Text(state.reply, color = TextMain, fontSize = 17.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PanelButton("🎤  Ask again", onSpeak, Modifier.focusRequester(firstButton))
                        PanelButton("Close", onClose)
                    }
                }

                AssistantState.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun PanelButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (focused) Accent else Color(0x22FFFFFF))
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (focused) Color(0xFF0B0D1A) else TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
