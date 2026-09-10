package com.openaicodex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.openaicodex.app.ui.theme.*

/**
 * This chat's own Secret Vault (opened from the 3-dot menu at the top
 * right of ChatScreen). Isolated per conversation: [secretNames] must come
 * from the SecretVault instance scoped to THIS conversationId only (see
 * SecretVault.forConversation), so a secret saved here is invisible to
 * every other chat's vault.
 *
 * By design there is no "reveal" or "copy" affordance anywhere on this
 * screen — once a value is saved, only its NAME is ever shown again. The
 * value itself is only ever read back by the process-launch code path
 * that injects it into Codex's environment for a task in this chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretVaultScreen(
    conversationTitle: String,
    secretNames: List<String>,
    onBack: () -> Unit,
    onAddSecret: (name: String, value: String) -> Unit,
    onDeleteSecret: (name: String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            TopAppBar(
                title = { Text("Gizli Anahtar Kasası", color = PureWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri", tint = PureWhite)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Anahtar ekle", tint = PureWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                "\"$conversationTitle\" sohbetine özel",
                color = MutedWhite,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = FaintWhite, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Değerler kaydedildikten sonra bir daha hiçbir yerde görüntülenmez veya kopyalanamaz. Sadece isim listelenir.",
                    color = FaintWhite,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(16.dp))

            if (secretNames.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bu sohbette henüz kayıtlı gizli anahtar yok", color = FaintWhite)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(secretNames, key = { it }) { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PanelBlack, RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Key, contentDescription = null, tint = MutedWhite, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(name, color = OffWhite, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { onDeleteSecret(name) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Sil", tint = ErrorRed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSecretDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, value ->
                onAddSecret(name, value)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddSecretDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    val normalizedName = name.trim().uppercase().replace(Regex("[^A-Z0-9_]"), "_")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gizli anahtar ekle") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("İsim (ör. EXPO_TOKEN)") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Değer") },
                    singleLine = true,
                    // Value is masked while typing and never re-shown after
                    // save — this dialog is the one and only place its
                    // plaintext ever passes through the UI layer.
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(normalizedName, value) },
                enabled = normalizedName.isNotBlank() && value.isNotBlank()
            ) { Text("Kaydet") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}
