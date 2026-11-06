package com.agentictv.launcher

import androidx.compose.foundation.background
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

/** What the people pop-up is showing. */
sealed interface PeopleState {
    data object Hidden : PeopleState
    data object Adding : PeopleState
    data class Managing(val name: String) : PeopleState
}

/**
 * Pop-up for adding a person ("+" chip), or for seeing and clearing what the
 * assistant knows about someone (press their chip again).
 */
@Composable
fun PeoplePanel(
    state: PeopleState,
    profiles: Profiles,
    onAdd: (String) -> Unit,
    onForget: () -> Unit,
    onRemove: (String) -> Unit,
    onClose: () -> Unit,
) {
    if (state == PeopleState.Hidden) return
    val first = remember { FocusRequester() }
    LaunchedEffect(state) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color(0xCC05060B)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1C1C21))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (state) {
                PeopleState.Adding -> {
                    Text("Who's watching?", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Add your name to get your own app order. Ask will remember what you like, on this TV only.",
                        color = TextDim, fontSize = 13.sp,
                    )
                    var name by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        placeholder = { Text("Your name", color = TextDim) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onAdd(name) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                            focusedBorderColor = Accent, unfocusedBorderColor = Color(0x44FFFFFF),
                            cursorColor = Accent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(first),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PanelButton("Add", { if (name.isNotBlank()) onAdd(name) })
                        PanelButton("Cancel", onClose)
                    }
                }

                is PeopleState.Managing -> {
                    val person = profiles.people.firstOrNull { it.name == state.name }
                    Text(state.name, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("What Ask remembers about you:", color = TextDim, fontSize = 13.sp)
                    val likes = person?.likes.orEmpty()
                    Text(
                        if (likes.isEmpty()) "Nothing yet. Tell Ask what you like, e.g. “I love cooking shows”."
                        else likes.joinToString("\n") { "•  $it" },
                        color = TextMain, fontSize = 15.sp, maxLines = 8,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PanelButton("Close", onClose, Modifier.focusRequester(first))
                        if (likes.isNotEmpty()) PanelButton("Forget all", onForget)
                        // Ask twice, so nobody removes a person by accident.
                        var sure by remember(state.name) { mutableStateOf(false) }
                        PanelButton(
                            if (sure) "Yes, remove ${state.name}" else "Remove ${state.name}",
                            { if (sure) onRemove(state.name) else sure = true },
                        )
                    }
                }

                PeopleState.Hidden -> Unit
            }
        }
    }
}
