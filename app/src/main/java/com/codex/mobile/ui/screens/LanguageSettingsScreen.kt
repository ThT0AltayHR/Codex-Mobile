package com.codex.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.codex.mobile.data.LanguageOption
import com.codex.mobile.data.Languages
import com.codex.mobile.ui.theme.*

/**
 * Previously, language was only selectable once during onboarding and
 * stored to DataStore with no way to change it and no effect on the
 * running model. This screen makes it changeable at any time, and
 * ChatViewModel's promptComposer.syncAgentsMd() (called before every
 * turn) picks up the new value immediately on the next message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(
    currentLanguageCode: String?,
    onBack: () -> Unit,
    onLanguageSelected: (LanguageOption) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { Languages.search(query) }

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            TopAppBar(
                title = { Text("Dil", color = PureWhite, style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic)) },
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Dil ara...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MutedWhite) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PureWhite, unfocusedBorderColor = BorderGray,
                    focusedTextColor = PureWhite, unfocusedTextColor = OffWhite, cursorColor = PureWhite
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results) { lang ->
                    val isSelected = lang.code == currentLanguageCode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) CardBlack else PanelBlack, RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onLanguageSelected(lang) }
                            )
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(lang.nativeName, color = PureWhite, style = MaterialTheme.typography.bodyLarge)
                        Text(lang.englishName, color = FaintWhite, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
