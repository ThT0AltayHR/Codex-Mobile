package com.codex.mobile.ui.components

/** A fenced code block rendered as its own card, with a language label and a real copy-to-clipboard action. */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun CodeBlockCard(code: String, language: String?, modifier: Modifier = Modifier) {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(copied) {
        if (copied) { delay(1600); copied = false }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(PureBlack)
            .border(BorderStroke(1.dp, BorderGray), RoundedCornerShape(Dimens.radiusMd))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelBlack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(AccentSilver.copy(alpha = 0.6f)))
            Spacer(Modifier.width(8.dp))
            Text(
                (language?.takeIf { it.isNotBlank() } ?: "kod").lowercase(),
                color = MutedWhite,
                fontFamily = TerminalMonoFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(interactionSource = interactionSource, indication = null) {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                CodexIcon(if (copied) CIcon.Check else CIcon.Copy, tint = if (copied) SuccessGreen else FaintWhite, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    if (copied) "Kopyalandı" else "Kopyala",
                    color = if (copied) SuccessGreen else FaintWhite,
                    fontFamily = BodyFamily,
                    fontSize = 11.sp
                )
            }
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp)) {
            Text(
                code,
                color = OffWhite.copy(alpha = 0.92f),
                fontFamily = TerminalMonoFamily,
                fontSize = 12.8.sp,
                lineHeight = 19.sp
            )
        }
    }
}
