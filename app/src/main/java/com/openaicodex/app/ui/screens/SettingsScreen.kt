package com.openaicodex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.openaicodex.app.ui.theme.*

data class SettingsRow(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userName: String,
    githubConnected: Boolean,
    onBack: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenGitHub: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        containerColor = PureBlack,
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar", color = PureWhite, style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri", tint = PureWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(CardBlack, RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MutedWhite)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(userName.ifBlank { "Codex kullanıcısı" }, color = PureWhite, style = MaterialTheme.typography.titleLarge)
                }
            }

            item { SectionLabel("Codex'im") }
            item { SettingsCard(SettingsRow(Icons.Filled.Face, "Kişiselleştirme", "Ton, üslup ve özel talimatlar", onOpenPersonalization)) }
            item { SettingsCard(SettingsRow(Icons.Filled.AutoAwesome, "Hafıza", "Codex'in seninle ilgili hatırladıkları (AGENTS.md)", onOpenMemory)) }

            item { SectionLabel("Genel") }
            item { SettingsCard(SettingsRow(Icons.Filled.Language, "Dil", onClick = onOpenLanguage)) }
            item { SettingsCard(SettingsRow(Icons.Filled.Storage, "Depolama", "Dosyalar ve görseller", onOpenStorage)) }
            item { SettingsCard(SettingsRow(Icons.Filled.Notifications, "Bildirimler", onClick = onOpenNotifications)) }

            item { SectionLabel("Bağlayıcılar") }
            item { SettingsCard(SettingsRow(Icons.Filled.Code, "GitHub", if (githubConnected) "Bağlı" else "Bağlı değil", onOpenGitHub)) }

            item { SectionLabel("Hesap") }
            item { SettingsCard(SettingsRow(Icons.Filled.Logout, "Çıkış yap", onClick = onLogout)) }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = FaintWhite,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsCard(row: SettingsRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBlack, RoundedCornerShape(14.dp))
            // Fix: a prior version had `onClick` on the data model but the
            // card composable never attached a clickable modifier at all,
            // so nothing was ever reachable by touch.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = row.onClick
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(row.icon, contentDescription = null, tint = MutedWhite)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = OffWhite, style = MaterialTheme.typography.bodyLarge)
            row.subtitle?.let {
                Text(it, color = FaintWhite, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = GhostWhite)
    }
}
