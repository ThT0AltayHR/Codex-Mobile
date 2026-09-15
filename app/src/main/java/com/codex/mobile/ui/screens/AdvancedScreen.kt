package com.codex.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.components.*
import com.codex.mobile.ui.theme.*

@Composable
fun AdvancedScreen(
    codexHomePath: String,
    workspacePath: String,
    isEngineRunning: Boolean,
    configTomlContent: String,
    onBack: () -> Unit,
    onStopEngine: () -> Unit
) {
    var showConfig by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(PureBlack).statusBarsPadding()) {
        ScreenTopBar(title = "Geliştirici ve Motor", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg)) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Motor durumu", color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                StatusPill(if (isEngineRunning) "Çalışıyor" else "Boşta", if (isEngineRunning) SuccessGreen else GhostWhite)
            }
            Spacer(Modifier.height(Dimens.md))

            SettingsCard {
                SettingsRow(icon = CIcon.Chip, title = "codex çalışma zamanı", subtitle = "Termux portu üzerinden native ikili", trailing = null)
                RowDivider()
                SettingsRow(icon = CIcon.Folder, title = "CODEX_HOME", subtitle = codexHomePath, trailing = null)
                RowDivider()
                SettingsRow(icon = CIcon.StorageBox, title = "Aktif çalışma alanı", subtitle = workspacePath, trailing = null)
            }

            SectionLabel("Yapılandırma")
            SettingsCard {
                SettingsRow(
                    icon = CIcon.FileData,
                    title = "config.toml",
                    subtitle = if (configTomlContent.isBlank()) "Henüz oluşturulmadı" else "${configTomlContent.lines().size} satır",
                    onClick = { showConfig = !showConfig },
                    trailing = { CodexIcon(if (showConfig) CIcon.ChevronDown else CIcon.ChevronRight, tint = FaintWhite, modifier = Modifier.size(16.dp)) }
                )
                if (showConfig) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.lg)
                            .padding(bottom = Dimens.md)
                            .background(PureBlack, androidx.compose.foundation.shape.RoundedCornerShape(Dimens.radiusSm))
                            .padding(10.dp)
                    ) {
                        Text(
                            configTomlContent.ifBlank { "(boş)" },
                            color = MutedWhite,
                            fontFamily = TerminalMonoFamily,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            SectionLabel("Eylemler")
            SettingsCard {
                SettingsRow(
                    icon = CIcon.RestartEngine,
                    title = "Çalışan görevi durdur",
                    subtitle = "codex sürecini şimdi sonlandırır",
                    danger = isEngineRunning,
                    trailing = null,
                    onClick = { if (isEngineRunning) confirmStop = true }
                )
            }
            Spacer(Modifier.height(Dimens.xxl).navigationBarsPadding())
        }
    }

    if (confirmStop) {
        ConfirmDialog(
            title = "Görevi durdur",
            message = "Şu anda çalışan Codex görevi hemen sonlandırılacak.",
            confirmLabel = "Durdur",
            onConfirm = onStopEngine,
            onDismiss = { confirmStop = false }
        )
    }
}
