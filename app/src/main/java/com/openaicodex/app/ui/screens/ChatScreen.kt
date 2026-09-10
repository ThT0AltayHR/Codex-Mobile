@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.openaicodex.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import com.openaicodex.app.data.ChatMessage
import com.openaicodex.app.data.Conversation
import com.openaicodex.app.data.Effort
import com.openaicodex.app.data.GeneratedFile
import com.openaicodex.app.data.ModelCatalog
import com.openaicodex.app.data.SelectedModel
import com.openaicodex.app.ui.components.CodeBlockCard
import com.openaicodex.app.ui.components.SamuraiStepStrip
import com.openaicodex.app.ui.components.SourceCardsRow
import com.openaicodex.app.ui.components.TextCopyButton
import com.openaicodex.app.ui.theme.*
import com.openaicodex.app.viewmodel.ChatUiState
import com.openaicodex.app.viewmodel.PendingAttachment
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    userName: String?,
    onSend: (String) -> Unit,
    onInputChange: (String) -> Unit,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAttachFile: () -> Unit,
    onClearAttachment: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    onDismissDeleteBlocked: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSecretVault: () -> Unit = {},
    onSelectModel: (SelectedModel) -> Unit = {},
    onDismissTokenLimit: () -> Unit = {},
    onOpenTokenPurchase: () -> Unit = {},
    onCopyGeneratedFile: (GeneratedFile) -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var overflowMenuOpen by remember { mutableStateOf(false) }

    // Auto-scroll fix: only jump to the newest message when the user is
    // already near the bottom (or a brand new message count arrives while
    // they haven't scrolled up to read history). If they've scrolled up
    // to read earlier messages, a new assistant message no longer yanks
    // them back down.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val totalItems = listState.layoutInfo.totalItemsCount
        val wasNearBottom = totalItems == 0 || lastVisible >= totalItems - 2
        if (wasNearBottom) {
            listState.animateScrollToItem((state.messages.size - 1).coerceAtLeast(0))
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = NearBlack) {
                ConversationHistoryDrawer(
                    conversations = state.conversations,
                    onOpen = {
                        onOpenConversation(it)
                        scope.launch { drawerState.close() }
                    },
                    onNew = {
                        onNewConversation()
                        scope.launch { drawerState.close() }
                    },
                    onRename = onRename,
                    onTogglePin = onTogglePin,
                    onDelete = onDelete,
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = PureBlack,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Codex",
                            style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic),
                            color = PureWhite
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Geçmiş sohbetler", tint = PureWhite)
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Ayarlar", tint = PureWhite)
                        }
                        IconButton(onClick = onNewConversation) {
                            Icon(Icons.Filled.Add, contentDescription = "Yeni sohbet", tint = PureWhite)
                        }
                        Box {
                            IconButton(onClick = { overflowMenuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Diğer", tint = PureWhite)
                            }
                            DropdownMenu(expanded = overflowMenuOpen, onDismissRequest = { overflowMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Gizli Anahtar Kasası") },
                                    leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                                    onClick = {
                                        overflowMenuOpen = false
                                        onOpenSecretVault()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
                )
            },
            bottomBar = {
                Column {
                    state.pendingAttachment?.let {
                        AttachmentPreviewBar(it, onClearAttachment)
                    }
                    ChatInputBar(
                        text = state.inputText,
                        onTextChange = onInputChange,
                        onSend = { onSend(state.inputText) },
                        onAttach = onAttachFile,
                        isRunning = state.isRunning,
                        onStop = onStop,
                        // Fix: Send is enabled with text OR a pending
                        // attachment — previously it required non-blank
                        // text even when a file was already picked, so an
                        // attachment-only message had no way to actually
                        // be sent from the UI.
                        canSend = state.inputText.isNotBlank() || state.pendingAttachment != null,
                        selectedModel = state.selectedModel,
                        onSelectModel = onSelectModel
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                state.errorMessage?.let { error ->
                    ErrorBanner(error, onDismissError)
                }
                state.deleteBlockedMessage?.let { msg ->
                    ErrorBanner(msg, onDismissDeleteBlocked)
                }
                if (state.messages.isEmpty()) {
                    EmptyStateGreeting(userName)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            MessageBubble(message, onCopyGeneratedFile)
                        }
                        if (state.isRunning && state.runningTaskConversationId == state.activeConversationId) {
                            item {
                                SamuraiStepStrip(step = state.currentStep, isActive = state.isRunning)
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.tokenLimitReached) {
        TokenLimitDialog(
            onWait = onDismissTokenLimit,
            onBuyMore = onOpenTokenPurchase
        )
    }
}

/**
 * Small, centered modal (not a full-screen takeover) shown when Codex's
 * own CLI reports a 429/quota/rate-limit response for this account. Two
 * actions: "Bekle" just dismisses (the user can retry once their quota
 * resets) and the Codex-branded button opens the account's usage/upgrade
 * page for buying more.
 */
@Composable
private fun TokenLimitDialog(
    onWait: () -> Unit,
    onBuyMore: () -> Unit
) {
    Dialog(onDismissRequest = onWait) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PanelBlack,
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.HourglassBottom, contentDescription = null, tint = MutedWhite, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(14.dp))
                Text(
                    "Token limitiniz bitti",
                    color = PureWhite,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Devam etmek için lütfen Codex hesabınızın API limitlerini yenileyin ya da satın alın.",
                    color = MutedWhite,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onWait) {
                        Text("Bekle")
                    }
                    Button(
                        onClick = onBuyMore,
                        colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = PureBlack)
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Codex hesabından satın al")
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErrorRed.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(message, color = OffWhite, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = MutedWhite, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AttachmentPreviewBar(attachment: PendingAttachment, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(iconForFileName(attachment.fileName), contentDescription = null, tint = MutedWhite, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(attachment.fileName, color = OffWhite, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Filled.Close, contentDescription = "Eki kaldır", tint = MutedWhite, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun EmptyStateGreeting(userName: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (!userName.isNullOrBlank()) "Merhaba, $userName" else "Merhaba",
            style = MaterialTheme.typography.displayMedium.copy(fontStyle = FontStyle.Italic),
            color = PureWhite
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Bugün ne üzerinde çalışalım?",
            style = MaterialTheme.typography.bodyLarge,
            color = MutedWhite
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onCopyGeneratedFile: (GeneratedFile) -> Unit) {
    val isUser = message.role == ChatMessage.Role.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (message.attachedFileName != null) {
            AttachedFileChip(message.attachedFileName)
            Spacer(Modifier.height(6.dp))
        }
        if (message.isCodeBlock) {
            CodeBlockCard(code = message.content, language = message.language, modifier = Modifier.fillMaxWidth(0.92f))
        } else if (message.content.isNotBlank()) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isUser) CardBlack else PanelBlack,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(if (isUser) 0.85f else 0.92f)
                ) {
                    Text(
                        text = message.content,
                        color = if (isUser) OffWhite else PureWhite,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (!isUser) {
                    TextCopyButton(text = message.content, modifier = Modifier.size(36.dp))
                }
            }
        }
        if (message.webSources.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SourceCardsRow(sources = message.webSources, modifier = Modifier.fillMaxWidth(0.92f))
        }
        message.generatedFiles.forEach { file ->
            Spacer(Modifier.height(8.dp))
            GeneratedFileCard(file, onCopyGeneratedFile)
        }
    }
}

@Composable
private fun GeneratedFileCard(file: GeneratedFile, onCopy: (GeneratedFile) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .background(CardBlack, RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCopy(file) }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = MutedWhite, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, color = PureWhite, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text("Kopyalamak için dokun", color = MutedWhite, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Filled.ContentCopy, contentDescription = "Dosyayı kopyala", tint = MutedWhite, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AttachedFileChip(fileName: String) {
    val icon = iconForFileName(fileName)
    Row(
        modifier = Modifier
            .background(CardBlack, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MutedWhite, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(fileName, color = MutedWhite, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Maps a real file extension to a representative icon — no fake/generic icon regardless of actual type. */
private fun iconForFileName(fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "zip", "rar", "7z", "tar", "gz" -> Icons.Filled.FolderZip
        "pdf" -> Icons.Filled.PictureAsPdf
        "png", "jpg", "jpeg", "webp", "gif" -> Icons.Filled.Image
        "kt", "java", "py", "js", "ts", "c", "cpp", "rs", "go" -> Icons.Filled.Code
        "json", "xml", "yml", "yaml", "toml" -> Icons.Filled.DataObject
        "txt", "md" -> Icons.Filled.Description
        else -> Icons.Filled.InsertDriveFile
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    isRunning: Boolean,
    onStop: () -> Unit,
    canSend: Boolean,
    selectedModel: SelectedModel = ModelCatalog.default,
    onSelectModel: (SelectedModel) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth().background(PureBlack)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModelSwitcherChip(selectedModel = selectedModel, onSelectModel = onSelectModel, enabled = !isRunning)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttach, enabled = !isRunning) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Dosya ekle", tint = if (isRunning) GhostWhite else MutedWhite)
            }
            OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Codex'e bir şey sor...", color = FaintWhite) },
            enabled = !isRunning,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PureWhite,
                unfocusedBorderColor = BorderGray,
                focusedTextColor = PureWhite,
                unfocusedTextColor = OffWhite,
                cursorColor = PureWhite
            ),
            maxLines = 5
        )
            Spacer(Modifier.width(6.dp))
            if (isRunning) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = "Durdur", tint = ErrorRed)
                }
            } else {
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(Icons.Filled.Send, contentDescription = "Gönder", tint = if (canSend) PureWhite else FaintWhite)
                }
            }
        }
    }
}

/**
 * The "+" model chip from the reference screenshot: tapping it opens a
 * dropdown of models (with an approximate relative token-cost hint per
 * effort level). Picking one animates the chip's label swap via
 * AnimatedContent so the change reads as a smooth transition rather than
 * an instant text pop.
 */
@Composable
private fun ModelSwitcherChip(
    selectedModel: SelectedModel,
    onSelectModel: (SelectedModel) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .background(PanelBlack, RoundedCornerShape(16.dp))
                .let { if (enabled) it else it }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .then(
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                        onClick = { expanded = true },
                        onLongClick = { expanded = true }
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = selectedModel,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.85f, animationSpec = tween(220))) togetherWith
                        (fadeOut(tween(150)) + scaleOut(targetScale = 0.85f, animationSpec = tween(150)))
                },
                label = "model-chip"
            ) { model ->
                Text(model.label(), color = if (enabled) OffWhite else FaintWhite, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "Model seç",
                tint = if (enabled) MutedWhite else FaintWhite,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ModelCatalog.models.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.displayName)
                            Text(
                                "${option.subtitle} · ~${ModelCatalog.estimatedRelativeCost(SelectedModel(option.id, option.defaultEffort))}x token",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedWhite
                            )
                        }
                    },
                    onClick = {
                        onSelectModel(SelectedModel(option.id, option.defaultEffort))
                        expanded = false
                    }
                )
            }
            HorizontalDivider()
            Effort.values().forEach { effort ->
                DropdownMenuItem(
                    text = { Text("Çaba: ${effort.label} (~${effort.relativeCost}x token)") },
                    onClick = {
                        onSelectModel(selectedModel.copy(effort = effort))
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ConversationHistoryDrawer(
    conversations: List<Conversation>,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onRename: (String, String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Sohbetler",
                style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic),
                color = PureWhite
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Ayarlar", tint = MutedWhite)
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNew) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = PureWhite)
            Spacer(Modifier.width(6.dp))
            Text("Yeni sohbet", color = PureWhite)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(conversations, key = { it.id }) { convo ->
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PanelBlack, RoundedCornerShape(10.dp))
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onOpen(convo.id) },
                                onLongClick = { expanded = true }
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (convo.pinned) {
                                Icon(Icons.Filled.PushPin, contentDescription = null, tint = MutedWhite, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                convo.title,
                                color = OffWhite,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Yeniden adlandır") },
                            onClick = {
                                renameTargetId = convo.id
                                renameText = convo.title
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (convo.pinned) "Sabitlemeyi kaldır" else "Sabitle") },
                            leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                            onClick = {
                                onTogglePin(convo.id)
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sil") },
                            leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                            onClick = {
                                onDelete(convo.id)
                                expanded = false
                            }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    renameTargetId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { renameTargetId = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRename(targetId, renameText)
                            renameTargetId = null
                        }
                    },
                    // Fix: rename accepted an empty title before — now
                    // disabled rather than silently saving a blank name.
                    enabled = renameText.isNotBlank()
                ) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetId = null }) { Text("İptal") }
            },
            title = { Text("Sohbeti yeniden adlandır") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
            }
        )
    }
}
