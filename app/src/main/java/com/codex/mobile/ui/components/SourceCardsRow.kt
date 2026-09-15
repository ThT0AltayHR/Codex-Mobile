package com.codex.mobile.ui.components

/**
 * Real web-search source cards shown under an assistant message, whenever
 * that turn actually issued a web_search item (see ThreadEvent.ItemCompleted
 * handling in ChatViewModel — sources are only ever added from a real
 * web_search event's own URL, never guessed).
 */

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.codex.mobile.data.WebSource
import com.codex.mobile.ui.theme.*

@Composable
fun SourceCardsRow(sources: List<WebSource>, modifier: Modifier = Modifier) {
    if (sources.isEmpty()) return
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            CodexIcon(CIcon.Globe, tint = FaintWhite, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                "${sources.size} kaynak",
                color = FaintWhite,
                fontFamily = BodyFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sources) { source ->
                val interactionSource = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .widthIn(max = 190.dp)
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(PanelBlack)
                        .border(BorderStroke(1.dp, BorderGray), RoundedCornerShape(Dimens.radiusSm))
                        .pressableScale(interactionSource)
                        .clickable(interactionSource = interactionSource, indication = null) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                            } catch (_: Exception) { /* no browser available — silently ignore */ }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(CardBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = "https://www.google.com/s2/favicons?domain=${source.domain}&sz=64",
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Column {
                        Text(
                            source.domain,
                            color = OffWhite,
                            fontFamily = BodyFamily,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    CodexIcon(CIcon.ExternalLink, tint = GhostWhite, modifier = Modifier.size(11.dp))
                }
            }
        }
    }
}
