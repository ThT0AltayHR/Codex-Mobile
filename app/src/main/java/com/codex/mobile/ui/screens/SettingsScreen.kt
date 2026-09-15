package com.codex.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.components.*
import com.codex.mobile.ui.theme.*

@Composable
fun SettingsScreen(
    userName: String,
    openAiEmail: String?,
    githubConnected: Boolean,
    onBack: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenGitHub: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAppearance: () -> Unit = {},
    onOpenSecrets: () -> Unit = {},
    onOpenAdvanced: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onLogout: () -> Unit
) {
    var confirmLogout by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(PureBlack).statusBarsPadding()) {
        ScreenTopBar(title = "Ayarlar", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.lg, vertical = Dimens.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(Dimens.radiusLg)).background(OffWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (userName.trim().firstOrNull() ?: '?').uppercaseChar().toString(),
                        color = PureBlack, fontFamily = BodyFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp
                    )
                }
                Spacer(Modifier.width(Dimens.md))
                Column(Modifier.weight(1f)) {
                    Text(userName.ifBlank { "İsimsiz" }, color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    if (openAiEmail != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(openAiEmail, color = FaintWhite, fontFamily = BodyFamily, fontSize = 12.5.sp)
                    }
                }
            }

            SectionLabel("Yapay Zeka")
            SettingsCard(Modifier.padding(horizontal = Dimens.lg)) {
                SettingsRow(icon = CIcon.Sparkle, title = "Kişiselleştirme", subtitle = "Ton ve özel talimatlar", onClick = onOpenPersonalization)
                RowDivider()
                SettingsRow(icon = CIcon.Person, title = "Hafıza", subtitle = "Codex'in seni nasıl hatırladığı", onClick = onOpenMemory)
            }

            SectionLabel("Görünüm")
            SettingsCard(Modifier.padding(horizontal = Dimens.lg)) {
                SettingsRow(icon = CIcon.Appearance, title = "Görünüm ve hareket", subtitle = "Metin boyutu, dokunsal geri bildirim", onClick = onOpenAppearance)
            }

            SectionLabel("Genel")
            SettingsCard(Modifier.padding(horizontal = Dimens.lg)) {
                SettingsRow(icon = CIcon.Globe, title = "Dil", onClick = onOpenLanguage)
                RowDivider()
                SettingsRow(icon = CIcon.StorageBox, title = "Depolama", subtitle = "Çalışma alanı ve önbellek", onClick = onOpenStorage)
                RowDivider()
                SettingsRow(icon = CIcon.Bell, title = "Bildirimler", onClick = onOpenNotifications)
            }

            SectionLabel("Bağlayıcılar")
            SettingsCard(Modifier.padding(horizontal = Dimens.lg)) {
                SettingsRow(
                    icon = CIcon.GitBranch,
                    title = "GitHub",
                    subtitle = if (githubConnected) "Bağlı" else "Bağlı değil",
                    onClick = onOpenGitHub,
                    trailing = {
                        if (githubConnected) StatusPill("Bağlı", SuccessGreen) else CodexIcon(CIcon.ChevronRight, tint = FaintWhite, modifier = Modifier.size(16.dp))
                    }
                )
            }

            SectionLabel("Gelişmiş")
            SettingsCard(Modifier.padding(horizontal = Dimens.lg)) {
                SettingsRow(icon = CIcon.Key, title = "Gizli anahtarlar kasası", subtitle = "API anahtarları ve sırlar", onClick = onOpenSecrets)
                RowDivider()
                SettingsRow(icon = CIcon.Chip, title = "Geliştirici ve motor", subtitle = "Çalışma zamanı, yeniden başlatma, config.toml", onClick = onOpenAdvanced)
            }

            SectionLabel("Hakkında")
            SettingsCard(Modifier.padding(horizontal = Dimens.lg)) {
                SettingsRow(icon = CIcon.Info, title = "CodexMobile hakkında", subtitle = "Sürüm, lisanslar", onClick = onOpenAbout)
            }

            Spacer(Modifier.height(Dimens.lg))
            SettingsCard(Modifier.padding(horizontal = Dimens.lg)) {
                SettingsRow(icon = CIcon.Logout, title = "Çıkış yap", danger = true, trailing = null, onClick = { confirmLogout = true })
            }
            Spacer(Modifier.height(Dimens.xxl).navigationBarsPadding())
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Çıkış yap",
            message = "Codex hesabınızın oturumu kapatılacak. Yerel sohbetleriniz cihazda kalır.",
            confirmLabel = "Çıkış yap",
            onConfirm = onLogout,
            onDismiss = { confirmLogout = false }
        )
    }
}
