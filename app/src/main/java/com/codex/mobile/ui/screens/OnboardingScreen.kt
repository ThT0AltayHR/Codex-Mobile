package com.codex.mobile.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.data.LanguageOption
import com.codex.mobile.data.Languages
import com.codex.mobile.ui.components.*
import com.codex.mobile.ui.theme.*
import com.codex.mobile.viewmodel.OnboardingStage
import com.codex.mobile.viewmodel.OnboardingUiState

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onLanguageSelected: (LanguageOption) -> Unit,
    onLoginClicked: () -> Unit,
    onNameSubmitted: (String) -> Unit,
    onBioSubmitted: (String) -> Unit,
    onSkipBio: () -> Unit
) {
    val stageOrder = listOf(OnboardingStage.LANGUAGE, OnboardingStage.LOGIN, OnboardingStage.NAME, OnboardingStage.BIO)
    val stageIndex = stageOrder.indexOf(state.stage).coerceAtLeast(0)

    Column(Modifier.fillMaxSize().background(PureBlack).statusBarsPadding().navigationBarsPadding()) {
        if (state.stage != OnboardingStage.DONE) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.xl, vertical = Dimens.lg),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                stageOrder.forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i <= stageIndex) OffWhite else BorderGray)
                    )
                }
            }
        }

        AnimatedContent(
            targetState = state.stage,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn(tween(Motion.medium)) togetherWith fadeOut(tween(Motion.fast)))
            },
            label = "onboarding-stage"
        ) { stage ->
            when (stage) {
                OnboardingStage.LANGUAGE -> LanguageStage(onLanguageSelected)
                OnboardingStage.LOGIN -> LoginStage(state, onLoginClicked)
                OnboardingStage.NAME -> NameStage(onNameSubmitted)
                OnboardingStage.BIO -> BioStage(onBioSubmitted, onSkipBio)
                OnboardingStage.DONE -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun LanguageStage(onLanguageSelected: (LanguageOption) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) Languages.ALL
        else Languages.ALL.filter { it.nativeName.contains(query, true) || it.englishName.contains(query, true) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.xl)) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(Dimens.radiusLg)).background(OffWhite),
            contentAlignment = Alignment.Center
        ) { CodexIcon(CIcon.Globe, tint = PureBlack, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(Dimens.lg))
        Text("Dilini seç", color = OffWhite, fontFamily = DisplayItalicFamily, fontStyle = FontStyle.Italic, fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text("Codex seninle bu dilde konuşacak.", color = FaintWhite, fontFamily = BodyFamily, fontSize = 13.5.sp)
        Spacer(Modifier.height(Dimens.lg))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ara…", color = FaintWhite, fontFamily = BodyFamily) },
            leadingIcon = { CodexIcon(CIcon.Search, tint = FaintWhite, modifier = Modifier.size(16.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(Dimens.radiusMd),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = OffWhite, unfocusedTextColor = OffWhite,
                focusedBorderColor = OffWhite, unfocusedBorderColor = BorderGray, cursorColor = OffWhite
            )
        )
        Spacer(Modifier.height(Dimens.md))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filtered, key = { it.code }) { option ->
                val interactionSource = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .clickable(interactionSource = interactionSource, indication = null) { onLanguageSelected(option) }
                        .padding(vertical = 13.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.nativeName, color = OffWhite, fontFamily = BodyFamily, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(option.englishName, color = FaintWhite, fontFamily = BodyFamily, fontSize = 12.sp)
                    }
                    CodexIcon(CIcon.ChevronRight, tint = FaintWhite, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun LoginStage(state: OnboardingUiState, onLoginClicked: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.xl),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(Dimens.radiusLg)).background(OffWhite),
            contentAlignment = Alignment.Center
        ) { CodexIcon(CIcon.CodeBrackets, tint = PureBlack, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(Dimens.lg))
        Text("Codex'e hoş geldin", color = OffWhite, fontFamily = DisplayItalicFamily, fontStyle = FontStyle.Italic, fontSize = 27.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Devam etmek için OpenAI hesabınla giriş yap. Codex, cihazında çalışan gerçek bir kodlama motorunu bu hesapla kullanır.",
            color = FaintWhite, fontFamily = BodyFamily, fontSize = 13.5.sp, lineHeight = 20.sp
        )
        Spacer(Modifier.height(Dimens.xl))

        if (state.errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.radiusMd))
                    .background(ErrorRed.copy(alpha = 0.12f))
                    .border(BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)), RoundedCornerShape(Dimens.radiusMd))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                CodexIcon(CIcon.Warning, tint = ErrorRed, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(state.errorMessage, color = ErrorRed, fontFamily = BodyFamily, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(Dimens.md))
        }

        if (state.isLoggingIn) {
            Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = OffWhite, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Tarayıcıda oturum açılıyor…", color = MutedWhite, fontFamily = BodyFamily, fontSize = 13.sp)
            }
        } else {
            PrimaryButton(text = "OpenAI ile giriş yap", leadingIcon = CIcon.Person, onClick = onLoginClicked)
        }
        Spacer(Modifier.height(Dimens.md))
        Text(
            "Codex, resmi olmayan bağımsız bir istemcidir; OpenAI'nin mobil uygulaması değildir.",
            color = GhostWhite, fontFamily = BodyFamily, fontSize = 10.5.sp, lineHeight = 15.sp
        )
    }
}

@Composable
private fun NameStage(onNameSubmitted: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.xl), verticalArrangement = Arrangement.Center) {
        CodexIcon(CIcon.Sparkle, tint = OffWhite, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(Dimens.lg))
        Text("Sana nasıl hitap edelim?", color = OffWhite, fontFamily = DisplayItalicFamily, fontStyle = FontStyle.Italic, fontSize = 25.sp)
        Spacer(Modifier.height(Dimens.lg))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Adın", color = FaintWhite, fontFamily = BodyFamily) },
            singleLine = true,
            shape = RoundedCornerShape(Dimens.radiusMd),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (name.isNotBlank()) onNameSubmitted(name) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = OffWhite, unfocusedTextColor = OffWhite,
                focusedBorderColor = OffWhite, unfocusedBorderColor = BorderGray, cursorColor = OffWhite
            )
        )
        Spacer(Modifier.height(Dimens.lg))
        PrimaryButton(text = "Devam et", enabled = name.isNotBlank(), onClick = { onNameSubmitted(name) })
    }
}

@Composable
private fun BioStage(onBioSubmitted: (String) -> Unit, onSkip: () -> Unit) {
    var bio by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.xl), verticalArrangement = Arrangement.Center) {
        CodexIcon(CIcon.FileGeneric, tint = OffWhite, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(Dimens.lg))
        Text("Kendinden bahset", color = OffWhite, fontFamily = DisplayItalicFamily, fontStyle = FontStyle.Italic, fontSize = 25.sp)
        Spacer(Modifier.height(4.dp))
        Text("Ne üzerinde çalışıyorsun, hangi dilleri kullanıyorsun? (isteğe bağlı)", color = FaintWhite, fontFamily = BodyFamily, fontSize = 12.5.sp)
        Spacer(Modifier.height(Dimens.lg))
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
            placeholder = { Text("Örn: Android geliştiricisiyim, Kotlin ve Compose kullanıyorum…", color = FaintWhite, fontFamily = BodyFamily, fontSize = 13.sp) },
            shape = RoundedCornerShape(Dimens.radiusMd),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = OffWhite, unfocusedTextColor = OffWhite,
                focusedBorderColor = OffWhite, unfocusedBorderColor = BorderGray, cursorColor = OffWhite
            )
        )
        Spacer(Modifier.height(Dimens.lg))
        PrimaryButton(text = "Bitir", onClick = { onBioSubmitted(bio) })
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onSkip).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text("Şimdilik atla", color = FaintWhite, fontFamily = BodyFamily, fontSize = 13.sp)
        }
    }
}
