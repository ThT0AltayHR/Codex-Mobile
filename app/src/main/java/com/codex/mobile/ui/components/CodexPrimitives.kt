package com.codex.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.theme.*

/** Spacing / radius / motion — the app's shared design tokens. One place, reused everywhere. */
object Dimens {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val radiusSm = 10.dp
    val radiusMd = 14.dp
    val radiusLg = 20.dp
    val radiusXl = 28.dp
}

object Motion {
    const val fast = 150
    const val medium = 260
    const val slow = 420
}

/** Subtle press-down feedback used on every tappable surface in the app instead of a default ripple. */
fun Modifier.pressableScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(Motion.fast), label = "pressScale")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = androidx.compose.ui.text.TextStyle(
            fontFamily = BodyFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp
        ),
        color = FaintWhite,
        modifier = modifier.padding(start = Dimens.lg, bottom = Dimens.xs, top = Dimens.lg)
    )
}

@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(CardBlack)
            .border(BorderStroke(1.dp, BorderGray), RoundedCornerShape(Dimens.radiusMd))
    ) { content() }
}

@Composable
fun RowScopeIconBadge(icon: CIcon, tint: Color = OffWhite, bg: Color = PanelBlack) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        CodexIcon(icon, tint = tint, modifier = Modifier.size(17.dp))
    }
}

/**
 * The workhorse settings row: leading icon badge, title, optional subtitle, and a trailing
 * slot (chevron / switch / value text / nothing). Used across every settings-style screen.
 */
@Composable
fun SettingsRow(
    icon: CIcon,
    title: String,
    subtitle: String? = null,
    iconTint: Color = OffWhite,
    danger: Boolean = false,
    trailing: @Composable (() -> Unit)? = { CodexIcon(CIcon.ChevronRight, tint = FaintWhite, modifier = Modifier.size(16.dp)) },
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = Dimens.lg, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowScopeIconBadge(icon, tint = if (danger) ErrorRed else iconTint)
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (danger) ErrorRed else OffWhite,
                fontFamily = BodyFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = FaintWhite, fontFamily = BodyFamily, fontSize = 12.5.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.width(Dimens.sm))
        trailing?.invoke()
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(DividerGray))
}

@Composable
fun ToggleRow(icon: CIcon, title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PureBlack,
                    checkedTrackColor = OffWhite,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = FaintWhite,
                    uncheckedTrackColor = PanelBlack,
                    uncheckedBorderColor = BorderGray
                )
            )
        },
        onClick = { onCheckedChange(!checked) }
    )
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: CIcon? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(if (enabled) OffWhite else GhostWhite)
            .pressableScale(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            CodexIcon(leadingIcon, tint = PureBlack, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = PureBlack, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, leadingIcon: CIcon? = null, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .border(BorderStroke(1.dp, BorderGray), RoundedCornerShape(Dimens.radiusMd))
            .pressableScale(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            CodexIcon(leadingIcon, tint = OffWhite, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

@Composable
fun StatusPill(text: String, tone: Color = SuccessGreen) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tone.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(tone))
        Spacer(Modifier.width(6.dp))
        Text(text, color = tone, fontFamily = BodyFamily, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(icon: CIcon, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Dimens.xxl, horizontal = Dimens.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(Dimens.radiusLg)).background(PanelBlack),
            contentAlignment = Alignment.Center
        ) { CodexIcon(icon, tint = FaintWhite, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(Dimens.md))
        Text(title, color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = FaintWhite, fontFamily = BodyFamily, fontSize = 13.sp, lineHeight = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit, actions: @Composable (RowScope.() -> Unit) = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { CodexIcon(CIcon.Back, tint = OffWhite, modifier = Modifier.size(20.dp)) }
        Text(
            title,
            color = OffWhite,
            fontFamily = BodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f).padding(start = 2.dp)
        )
        actions()
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBlack,
        titleContentColor = OffWhite,
        textContentColor = MutedWhite,
        title = { Text(title, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold) },
        text = { Text(message, fontFamily = BodyFamily, fontSize = 13.5.sp, lineHeight = 19.sp) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (danger) ErrorRed else OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç", color = FaintWhite, fontFamily = BodyFamily) }
        }
    )
}

@Composable
fun SegmentedTabRow(labels: List<String>, icons: List<CIcon>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(PanelBlack)
            .padding(3.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val isSelected = i == selected
            val interactionSource = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(if (isSelected) CardBlack else Color.Transparent)
                    .clickable(interactionSource = interactionSource, indication = null) { onSelect(i) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CodexIcon(icons[i], tint = if (isSelected) OffWhite else FaintWhite, modifier = Modifier.size(16.dp))
                Spacer(Modifier.height(3.dp))
                Text(
                    label,
                    color = if (isSelected) OffWhite else FaintWhite,
                    fontFamily = BodyFamily,
                    fontSize = 10.5.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Very small, intentionally limited inline-markdown renderer: **bold**, *italic*, `code`.
 * Not a full Markdown engine — just enough to stop raw asterisks/backticks from showing
 * up literally in chat prose, without pulling in a parsing dependency.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = OffWhite,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 22.sp
) {
    val annotated = remember(text) { buildInlineMarkdown(text) }
    Text(annotated, modifier = modifier, color = color, fontFamily = BodyFamily, fontSize = fontSize, lineHeight = lineHeight)
}

private fun buildInlineMarkdown(raw: String) = buildAnnotatedString {
    var i = 0
    val n = raw.length
    while (i < n) {
        val c = raw[i]
        when {
            c == '*' && i + 1 < n && raw[i + 1] == '*' -> {
                val end = raw.indexOf("**", i + 2)
                if (end == -1) { append(c); i++ } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(raw.substring(i + 2, end)) }
                    i = end + 2
                }
            }
            c == '`' -> {
                val end = raw.indexOf('`', i + 1)
                if (end == -1) { append(c); i++ } else {
                    withStyle(SpanStyle(fontFamily = TerminalMonoFamily, background = PanelBlack, color = AccentSilver)) {
                        append(' ' + raw.substring(i + 1, end) + ' ')
                    }
                    i = end + 1
                }
            }
            c == '*' && i + 1 < n && raw[i + 1] != ' ' -> {
                val end = raw.indexOf('*', i + 1)
                if (end == -1) { append(c); i++ } else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(raw.substring(i + 1, end)) }
                    i = end + 1
                }
            }
            else -> { append(c); i++ }
        }
    }
}
