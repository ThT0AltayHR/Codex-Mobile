package com.codex.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
fun AppearanceScreen(
    textScale: Float,
    reduceMotion: Boolean,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
    onTextScaleChange: (Float) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize().background(PureBlack).statusBarsPadding()) {
        ScreenTopBar(title = "Görünüm ve Hareket", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionLabel("Yazı Boyutu")
            SettingsCard(Modifier.padding(horizontal = Dimens.lg)) {
                Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RowScopeIconBadge(CIcon.Scale, tint = OffWhite)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Metin ölçeği", color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 14.5.sp)
                            Text("Font stilini değiştirmez, yalnızca boyutunu ölçekler", color = FaintWhite, fontFamily = BodyFamily, fontSize = 11.5.sp)
                        }
                        Text("%${(textScale * 100).toInt()}", color = MutedWhite, fontFamily = TerminalMonoFamily, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Slider(
                        value = textScale,
                        onValueChange = onTextScaleChange,
                        valueRange = 0.85f..1.3f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = OffWhite,
                            activeTrackColor = OffWhite,
                            inactiveTrackColor = BorderGray
                        )
                    )
                    Text(
                        "Önizleme: Codex bugün ne üzerinde çalışalım?",
                        color = OffWhite,
                        fontFamily = BodyFamily,
                        fontSize = (15 * textScale).sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            SectionLabel("Hareket ve Geri Bildirim")
            SettingsCard(Modifier.padding(horizontal = Dimens.lg)) {
                ToggleRow(
                    icon = CIcon.Motion,
                    title = "Hareketi azalt",
                    subtitle = "Animasyonları ve geçişleri sadeleştir",
                    checked = reduceMotion,
                    onCheckedChange = onReduceMotionChange
                )
                RowDivider()
                ToggleRow(
                    icon = CIcon.Haptics,
                    title = "Dokunsal geri bildirim",
                    subtitle = "Gönder, durdur ve seçim titreşimleri",
                    checked = hapticsEnabled,
                    onCheckedChange = onHapticsChange
                )
            }

            Spacer(Modifier.height(Dimens.md))
            Text(
                "Bu ayarlar cihazda saklanır ve yalnızca bu uygulamayı etkiler.",
                color = GhostWhite,
                fontFamily = BodyFamily,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(horizontal = Dimens.xl)
            )
            Spacer(Modifier.height(Dimens.xxl).navigationBarsPadding())
        }
    }
}
