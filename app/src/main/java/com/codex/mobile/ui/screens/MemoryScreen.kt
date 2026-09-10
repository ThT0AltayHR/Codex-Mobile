package com.codex.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.codex.mobile.ui.theme.*

/**
 * Shows what Codex actually has persisted about the user, and lets them
 * edit it. The "AGENTS.md preview" section renders the literal file
 * CodexPromptComposer writes before every turn, so the "Codex remembers
 * you" claim is something the user can directly verify rather than take
 * on faith.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    currentName: String,
    currentBio: String,
    agentsMdPreview: String,
    onBack: () -> Unit,
    onSave: (name: String, bio: String) -> Unit
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    var bio by remember(currentBio) { mutableStateOf(currentBio) }

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            TopAppBar(
                title = { Text("Hafıza", color = PureWhite, style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic)) },
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
            Text("Adınız", color = MutedWhite, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PureWhite, unfocusedBorderColor = BorderGray,
                    focusedTextColor = PureWhite, unfocusedTextColor = OffWhite, cursorColor = PureWhite
                )
            )
            Spacer(Modifier.height(16.dp))
            Text("Kendinizi tanımlayın", color = MutedWhite, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PureWhite, unfocusedBorderColor = BorderGray,
                    focusedTextColor = PureWhite, unfocusedTextColor = OffWhite, cursorColor = PureWhite
                )
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSave(name, bio) },
                colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = PureBlack),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Kaydet") }

            Spacer(Modifier.height(28.dp))
            Text("Codex'e gönderilen bağlam (AGENTS.md)", color = MutedWhite, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Bu, bir sonraki mesajınızda Codex'in gerçekten okuyacağı dosyanın önizlemesidir.",
                color = FaintWhite,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBlack, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = agentsMdPreview.ifBlank { "Henüz oluşturulmadı — ilk mesajınızı gönderdiğinizde oluşacak." },
                    color = OffWhite,
                    style = CodeBlockStyle
                )
            }
        }
    }
}
