package com.codex.mobile.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.codex.mobile.ui.theme.BorderGray
import com.codex.mobile.ui.theme.CardBlack
import com.codex.mobile.ui.theme.CodeBlockStyle
import com.codex.mobile.ui.theme.MutedWhite
import com.codex.mobile.ui.theme.OffWhite
import com.codex.mobile.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.border

/**
 * Renders a code block with a language label and a copy-to-clipboard
 * button, as requested: any code the assistant produces gets its own
 * copyable card, separated visually from prose text.
 */
@Composable
fun CodeBlockCard(code: String, language: String = "text", modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CardBlack, RoundedCornerShape(12.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = MutedWhite
            )
            IconButton(
                onClick = {
                    copyToClipboard(context, code)
                    copied = true
                    scope.launch {
                        delay(1600)
                        copied = false
                    }
                }
            ) {
                Icon(
                    imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = "Kopyala",
                    tint = if (copied) SuccessGreen else MutedWhite
                )
            }
        }
        Text(
            text = code,
            style = CodeBlockStyle,
            color = OffWhite,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

/** Simple copy button for plain-text assistant replies (not code). */
@Composable
fun TextCopyButton(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    IconButton(
        modifier = modifier,
        onClick = {
            copyToClipboard(context, text)
            copied = true
            scope.launch {
                delay(1600)
                copied = false
            }
        }
    ) {
        Icon(
            imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
            contentDescription = "Metni kopyala",
            tint = if (copied) SuccessGreen else MutedWhite
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("codex", text))
}
