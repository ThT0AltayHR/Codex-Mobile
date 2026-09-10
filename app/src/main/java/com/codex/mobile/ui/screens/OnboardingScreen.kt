package com.codex.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.codex.mobile.data.LanguageOption
import com.codex.mobile.data.Languages
import com.codex.mobile.ui.theme.*
import com.codex.mobile.viewmodel.OnboardingStage
import com.codex.mobile.viewmodel.OnboardingUiState

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onLanguageSelected: (LanguageOption) -> Unit,
    onStartLogin: () -> Unit,
    onNameSubmit: (String) -> Unit,
    onBioSubmit: (String) -> Unit,
    onBioSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(animationSpec = tween(500)) { it / 3 } + fadeIn(tween(500))
        ) {
            when (state.stage) {
                OnboardingStage.LANGUAGE -> LanguagePickerStep(onLanguageSelected)
                OnboardingStage.LOGIN -> LoginStep(onStartLogin, state.errorMessage, state.isLoggingIn)
                OnboardingStage.NAME -> NameStep(onNameSubmit)
                OnboardingStage.BIO -> BioStep(onBioSubmit, onBioSkip)
                OnboardingStage.DONE -> Unit
            }
        }
    }
}

@Composable
private fun LanguagePickerStep(onLanguageSelected: (LanguageOption) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { Languages.search(query) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = "Welcome to Codex",
            style = MaterialTheme.typography.displayMedium.copy(fontStyle = FontStyle.Italic),
            color = PureWhite
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Hangi dilde konuşmamı istersin?",
            style = MaterialTheme.typography.bodyLarge,
            color = MutedWhite
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Dil ara...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MutedWhite) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PureWhite,
                unfocusedBorderColor = BorderGray,
                focusedTextColor = PureWhite,
                unfocusedTextColor = OffWhite,
                cursorColor = PureWhite
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(results) { lang ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PanelBlack, RoundedCornerShape(10.dp))
                        .padding(vertical = 14.dp, horizontal = 16.dp)
                        .then(Modifier)
                        .clickableWithoutRipple { onLanguageSelected(lang) },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(lang.nativeName, color = PureWhite, style = MaterialTheme.typography.bodyLarge)
                    Text(lang.englishName, color = FaintWhite, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LoginStep(onStartLogin: () -> Unit, error: String?, isLoggingIn: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Codex hesabınla giriş yap",
            style = MaterialTheme.typography.displayMedium.copy(fontStyle = FontStyle.Italic),
            color = PureWhite
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Devam etmek için OpenAI Codex hesabınla oturum açman gerekiyor.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedWhite
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStartLogin,
            enabled = !isLoggingIn,
            colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = PureBlack),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (isLoggingIn) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PureBlack, strokeWidth = 2.dp)
            } else {
                Text("OpenAI ile Giriş Yap", style = MaterialTheme.typography.titleMedium)
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NameStep(onSubmit: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Lütfen adınızı girin",
            style = MaterialTheme.typography.displayMedium.copy(fontStyle = FontStyle.Italic),
            color = PureWhite
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Adınız") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PureWhite,
                unfocusedBorderColor = BorderGray,
                focusedTextColor = PureWhite,
                unfocusedTextColor = OffWhite,
                cursorColor = PureWhite
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { if (name.isNotBlank()) onSubmit(name) },
            colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = PureBlack),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Devam et") }
    }
}

@Composable
private fun BioStep(onSubmit: (String) -> Unit, onSkip: () -> Unit) {
    var bio by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Kendinizi kısaca tanımlar mısınız?",
            style = MaterialTheme.typography.displayMedium.copy(fontStyle = FontStyle.Italic),
            color = PureWhite
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Kim olduğunuzu, ne iş yaptığınızı bilirsem sana daha iyi yardımcı olurum.",
            color = MutedWhite,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            placeholder = { Text("Örn: Yazılım geliştiriciyim, mobil uygulamalarla ilgileniyorum...") },
            minLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PureWhite,
                unfocusedBorderColor = BorderGray,
                focusedTextColor = PureWhite,
                unfocusedTextColor = OffWhite,
                cursorColor = PureWhite
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSubmit(bio) },
            colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = PureBlack),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Bitir") }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Şimdilik atla", color = FaintWhite)
        }
    }
}

@Composable
private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
)
