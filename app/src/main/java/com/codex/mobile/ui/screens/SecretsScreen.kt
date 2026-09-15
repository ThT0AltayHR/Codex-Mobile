package com.codex.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.components.*
import com.codex.mobile.ui.theme.*

/**
 * A real front-end for SecretVault (engine/SecretVault.kt), which already
 * existed fully wired into the prompt/env pipeline but had no screen of its
 * own. Values are never read back once saved — only names are listed,
 * matching how the vault itself is used (injected as env vars by name).
 */
@Composable
fun SecretsScreen(
    secretNames: List<String>,
    onBack: () -> Unit,
    onAdd: (name: String, value: String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(PureBlack).statusBarsPadding()) {
        ScreenTopBar(
            title = "Gizli Anahtarlar",
            onBack = onBack,
            actions = {
                androidx.compose.material3.IconButton(onClick = { showAddDialog = true }) {
                    CodexIcon(CIcon.Add, tint = OffWhite, modifier = Modifier.size(20.dp))
                }
            }
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg)) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Codex bir göreve başlarken API anahtarı gibi bir sır gerektiğini fark ederse, burada kayıtlı isimler ortam değişkeni olarak sağlanır. Değerler kaydedildikten sonra bir daha görüntülenmez.",
                color = FaintWhite, fontFamily = BodyFamily, fontSize = 12.5.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(Dimens.lg))

            if (secretNames.isEmpty()) {
                EmptyState(CIcon.Key, "Henüz sır yok", "Örn. OPENAI_API_KEY, STRIPE_SECRET_KEY gibi bir anahtar ekleyin.")
            } else {
                SettingsCard {
                    secretNames.forEachIndexed { index, name ->
                        SettingsRow(
                            icon = CIcon.Key,
                            title = name,
                            subtitle = "•••••••••••••",
                            trailing = {
                                androidx.compose.material3.IconButton(onClick = { pendingDelete = name }) {
                                    CodexIcon(CIcon.Trash, tint = ErrorRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                        if (index != secretNames.lastIndex) RowDivider()
                    }
                }
            }
            Spacer(Modifier.height(Dimens.md))
            SecondaryButton(text = "Yeni anahtar ekle", leadingIcon = CIcon.Add, onClick = { showAddDialog = true })
            Spacer(Modifier.height(Dimens.xxl).navigationBarsPadding())
        }
    }

    if (showAddDialog) {
        AddSecretDialog(onDismiss = { showAddDialog = false }, onConfirm = { n, v -> onAdd(n, v); showAddDialog = false })
    }

    pendingDelete?.let { name ->
        ConfirmDialog(
            title = "Anahtarı sil",
            message = "\"$name\" kalıcı olarak silinecek.",
            confirmLabel = "Sil",
            onConfirm = { onDelete(name) },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun AddSecretDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    val normalizedName = name.trim().uppercase().replace(" ", "_")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBlack,
        title = { Text("Yeni gizli anahtar", color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("İsim (örn. OPENAI_API_KEY)", fontFamily = BodyFamily, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OffWhite, unfocusedTextColor = OffWhite,
                        focusedBorderColor = OffWhite, unfocusedBorderColor = BorderGray, cursorColor = OffWhite
                    )
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Değer", fontFamily = BodyFamily, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OffWhite, unfocusedTextColor = OffWhite,
                        focusedBorderColor = OffWhite, unfocusedBorderColor = BorderGray, cursorColor = OffWhite
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (normalizedName.isNotBlank() && value.isNotBlank()) onConfirm(normalizedName, value) },
                enabled = normalizedName.isNotBlank() && value.isNotBlank()
            ) { Text("Kaydet", color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = FaintWhite, fontFamily = BodyFamily) } }
    )
}
