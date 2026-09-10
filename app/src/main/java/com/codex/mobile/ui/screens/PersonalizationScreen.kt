package com.codex.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.codex.mobile.ui.theme.*

data class ToneOption(val id: String, val label: String, val description: String)

private val TONE_OPTIONS = listOf(
    ToneOption("balanced", "Dengeli", "Nötr ve yardımsever"),
    ToneOption("friendly", "Sıcak", "Samimi ve rahat"),
    ToneOption("professional", "Profesyonel", "Net ve öz"),
    ToneOption("playful", "Esprili", "Enerjik ve hevesli")
)

/**
 * Real personalization screen: selecting a tone and saving a custom
 * prompt actually calls UserPreferencesStore, which CodexPromptComposer
 * then writes into AGENTS.md before every turn — so these choices reach
 * the model, not just local storage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationScreen(
    currentTone: String,
    currentCustomPrompt: String,
    onBack: () -> Unit,
    onToneSelected: (String) -> Unit,
    onCustomPromptSaved: (String) -> Unit
) {
    var promptText by remember(currentCustomPrompt) { mutableStateOf(currentCustomPrompt) }

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            TopAppBar(
                title = { Text("Kişiselleştirme", color = PureWhite, style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri", tint = PureWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Konuşma tonu", color = MutedWhite, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(10.dp))
            TONE_OPTIONS.forEach { option ->
                ToneRow(option, selected = option.id == currentTone, onSelect = { onToneSelected(option.id) })
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(24.dp))
            Text("Özel talimatlar", color = MutedWhite, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Codex'in her mesajda dikkate alacağı ek talimatlar. AGENTS.md içine yazılır.",
                color = FaintWhite,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                placeholder = { Text("Örn: Cevaplarını her zaman madde madde ver.") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PureWhite,
                    unfocusedBorderColor = BorderGray,
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = OffWhite,
                    cursorColor = PureWhite
                )
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onCustomPromptSaved(promptText) },
                colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = PureBlack),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Kaydet") }
        }
    }
}

@Composable
private fun ToneRow(option: ToneOption, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) CardBlack else PanelBlack, RoundedCornerShape(12.dp))
            .clickableNoRipple(onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(option.label, color = PureWhite, style = MaterialTheme.typography.bodyLarge)
            Text(option.description, color = FaintWhite, style = MaterialTheme.typography.bodyMedium)
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = PureWhite)
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
)
