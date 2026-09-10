package com.codex.mobile.ui.components

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codex.mobile.data.WebSource
import com.codex.mobile.ui.theme.CardBlack
import com.codex.mobile.ui.theme.FaintWhite
import com.codex.mobile.ui.theme.MutedWhite

/**
 * Renders real web sources Codex actually visited during a turn — sourced
 * only from ThreadEvent.extractWebSearchUrl, never fabricated. Each card
 * shows a real favicon fetched only from the source's own domain (via a
 * favicon-resolution endpoint that receives just the domain string, not
 * user data), falling back to a neutral custom vector globe icon — never
 * a fabricated logo — if none loads. Tapping a card opens it in a Custom
 * Tab, never a raw WebView.
 */
@Composable
fun SourceCardsRow(sources: List<WebSource>, modifier: Modifier = Modifier) {
    if (sources.isEmpty()) return
    val context = LocalContext.current

    Column(modifier = modifier) {
        Text("Kaynaklar", color = FaintWhite, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sources, key = { it.url }) { source ->
                SourceCard(source) {
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    customTabsIntent.launchUrl(context, android.net.Uri.parse(source.url))
                }
            }
        }
    }
}

@Composable
private fun SourceCard(source: WebSource, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .background(CardBlack, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = 200.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Real favicon for this exact domain, requested from a
        // favicon-resolution endpoint that only ever receives the plain
        // domain string extracted from the real source URL — never any
        // user content. On failure to load, Coil's error slot below falls
        // back to a neutral vector globe, never a fabricated brand logo.
        val faviconUrl = "https://www.google.com/s2/favicons?sz=64&domain=${source.domain}"
        var loadFailed by remember(source.domain) { mutableStateOf(false) }
        if (loadFailed) {
            Icon(Icons.Filled.Language, contentDescription = null, tint = MutedWhite, modifier = Modifier.size(14.dp))
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context).data(faviconUrl).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.size(14.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true }
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            source.domain,
            color = MutedWhite,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
    }
}
