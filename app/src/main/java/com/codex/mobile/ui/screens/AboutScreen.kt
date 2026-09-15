package com.codex.mobile.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.components.*
import com.codex.mobile.ui.theme.*

@Composable
fun AboutScreen(versionName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(PureBlack).statusBarsPadding()) {
        ScreenTopBar(title = "Hakkında", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg)) {
            Spacer(Modifier.height(Dimens.md))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(Dimens.radiusLg)).background(OffWhite),
                    contentAlignment = Alignment.Center
                ) { CodexIcon(CIcon.CodeBrackets, tint = PureBlack, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.height(Dimens.md))
                Text("Codex", color = OffWhite, fontFamily = DisplayItalicFamily, fontStyle = FontStyle.Italic, fontSize = 24.sp)
                Spacer(Modifier.height(2.dp))
                Text("Sürüm $versionName", color = FaintWhite, fontFamily = TerminalMonoFamily, fontSize = 12.sp)
            }

            Spacer(Modifier.height(Dimens.xl))
            SectionLabel("Bu Uygulama Hakkında")
            SettingsCard(Modifier.padding(horizontal = Dimens.lg)) {
                Text(
                    "CodexMobile, açık kaynaklı Codex CLI motorunu (Apache 2.0) Android üzerinde çalıştıran, resmî olmayan bağımsız bir istemcidir. OpenAI'nin resmî mobil uygulaması değildir. \"Codex Termux\" portu üzerine inşa edilmiştir; tam telif hakkı bildirimleri NOTICE ve LICENSE dosyalarında yer alır.",
                    color = MutedWhite,
                    fontFamily = BodyFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(Dimens.lg)
                )
            }

            SectionLabel("Bağlantılar")
            SettingsCard(Modifier.padding(horizontal = Dimens.lg)) {
                SettingsRow(
                    icon = CIcon.GitBranch,
                    title = "Codex CLI (OpenAI, açık kaynak)",
                    trailing = { CodexIcon(CIcon.ExternalLink, tint = FaintWhite, modifier = Modifier.size(14.dp)) },
                    onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/openai/codex"))) } catch (_: Exception) {}
                    }
                )
            }
            Spacer(Modifier.height(Dimens.xxl).navigationBarsPadding())
        }
    }
}
