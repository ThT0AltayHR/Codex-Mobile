package com.openaicodex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.openaicodex.app.ui.theme.*
import java.io.File

data class StorageBreakdown(
    val workspaceBytes: Long,
    val codexHomeBytes: Long,
    val conversationsJsonBytes: Long,
    val totalBytes: Long
)

/**
 * Fix over a prior version: the "Depolama" settings row was clickable but
 * its callback was an empty comment — no screen ever opened, no numbers
 * were ever shown. This screen walks the actual on-device directories
 * (workspace, CODEX_HOME, conversations.json) and reports real byte
 * counts, not placeholder values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    workspaceDir: File,
    codexHomeDir: File,
    conversationsFile: File,
    onBack: () -> Unit
) {
    var breakdown by remember { mutableStateOf<StorageBreakdown?>(null) }

    LaunchedEffect(Unit) {
        val workspaceBytes = dirSizeRecursive(workspaceDir)
        val codexHomeBytes = dirSizeRecursive(codexHomeDir)
        val conversationsBytes = if (conversationsFile.exists()) conversationsFile.length() else 0L
        breakdown = StorageBreakdown(
            workspaceBytes = workspaceBytes,
            codexHomeBytes = codexHomeBytes,
            conversationsJsonBytes = conversationsBytes,
            totalBytes = workspaceBytes + codexHomeBytes + conversationsBytes
        )
    }

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            TopAppBar(
                title = { Text("Depolama", color = PureWhite, style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri", tint = PureWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            val current = breakdown
            if (current == null) {
                CircularProgressIndicator(color = PureWhite, modifier = Modifier.padding(24.dp))
            } else {
                Text(
                    formatBytes(current.totalBytes),
                    color = PureWhite,
                    style = MaterialTheme.typography.displayMedium.copy(fontStyle = FontStyle.Italic)
                )
                Text("toplam kullanım", color = MutedWhite, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(24.dp))

                StorageRow("Çalışma alanı", "Ekli dosyalar, üretilen çıktılar", current.workspaceBytes)
                Spacer(Modifier.height(8.dp))
                StorageRow("Codex oturum verisi", "Kimlik doğrulama, oturum geçmişi", current.codexHomeBytes)
                Spacer(Modifier.height(8.dp))
                StorageRow("Sohbet geçmişi", "conversations.json", current.conversationsJsonBytes)
            }
        }
    }
}

@Composable
private fun StorageRow(title: String, subtitle: String, bytes: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBlack, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, color = OffWhite, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = FaintWhite, style = MaterialTheme.typography.bodyMedium)
        }
        Text(formatBytes(bytes), color = MutedWhite, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun dirSizeRecursive(dir: File): Long {
    if (!dir.exists()) return 0L
    if (dir.isFile) return dir.length()
    return dir.listFiles()?.sumOf { dirSizeRecursive(it) } ?: 0L
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
