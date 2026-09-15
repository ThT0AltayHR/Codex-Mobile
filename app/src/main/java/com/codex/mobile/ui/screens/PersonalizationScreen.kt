package com.codex.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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

private data class ToneOption(val key: String, val label: String, val description: String, val icon: CIcon)

private val toneOptions = listOf(
    ToneOption("balanced", "Dengeli", "Doğal ve nötr bir ton", CIcon.Sparkle),
    ToneOption("concise", "Kısa ve öz", "Az laf, hızlı sonuç", CIcon.CodeBrackets),
    ToneOption("friendly", "Sıcak", "Daha samimi bir anlatım", CIcon.Person),
    ToneOption("direct", "Doğrudan", "Süslemesiz, net geri bildirim", CIcon.Warning),
    ToneOption("detailed", "Ayrıntılı", "Adım adım, kapsamlı açıklamalar", CIcon.FileGeneric)
)

@Composable
fun PersonalizationScreen(
    currentTone: String,
    currentCustomPrompt: String,
    onBack: () -> Unit,
    onToneSelected: (String) -> Unit,
    onCustomPromptSaved: (String) -> Unit
) {
    var promptText by remember(currentCustomPrompt) { mutableStateOf(currentCustomPrompt) }
    val dirty = promptText != currentCustomPrompt

    Column(Modifier.fillMaxSize().background(PureBlack).statusBarsPadding()) {
        ScreenTopBar(title = "Kişiselleştirme", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg)) {
            Spacer(Modifier.height(Dimens.sm))
            Text("Ton", color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("Codex'in yanıtlarında hangi üslubu kullanacağını seç.", color = FaintWhite, fontFamily = BodyFamily, fontSize = 12.5.sp)
            Spacer(Modifier.height(Dimens.md))

            toneOptions.forEach { option ->
                val selected = option.key == currentTone
                val interactionSource = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(Dimens.radiusMd))
                        .background(if (selected) CardBlack else PanelBlack)
                        .border(BorderStroke(1.dp, if (selected) OffWhite.copy(alpha = 0.4f) else BorderGray), RoundedCornerShape(Dimens.radiusMd))
                        .clickable(interactionSource = interactionSource, indication = null) { onToneSelected(option.key) }
                        .padding(horizontal = Dimens.md, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RowScopeIconBadge(option.icon, tint = if (selected) OffWhite else FaintWhite)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(option.label, color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 14.5.sp)
                        Text(option.description, color = FaintWhite, fontFamily = BodyFamily, fontSize = 12.sp)
                    }
                    if (selected) CodexIcon(CIcon.Check, tint = OffWhite, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(Dimens.lg))
            Text("Özel talimatlar", color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("Codex'in her göreve başlarken göz önünde bulunduracağı ek talimatlar.", color = FaintWhite, fontFamily = BodyFamily, fontSize = 12.5.sp)
            Spacer(Modifier.height(Dimens.md))
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                placeholder = { Text("Örn: Her zaman Kotlin idiomatic kod yaz, testleri unutma…", color = FaintWhite, fontFamily = BodyFamily, fontSize = 13.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = BodyFamily, fontSize = 14.sp, color = OffWhite),
                shape = RoundedCornerShape(Dimens.radiusMd),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OffWhite, unfocusedTextColor = OffWhite,
                    focusedBorderColor = OffWhite, unfocusedBorderColor = BorderGray,
                    cursorColor = OffWhite
                )
            )
            Spacer(Modifier.height(Dimens.md))
            PrimaryButton(
                text = if (dirty) "Kaydet" else "Kaydedildi",
                enabled = dirty,
                leadingIcon = if (dirty) null else CIcon.Check,
                onClick = { onCustomPromptSaved(promptText) }
            )
            Spacer(Modifier.height(Dimens.xxl).navigationBarsPadding())
        }
    }
}
