package com.codex.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.codex.mobile.data.ChatMessage
import com.codex.mobile.data.Conversation
import com.codex.mobile.ui.components.CodeBlockCard
import com.codex.mobile.ui.components.SamuraiStepStrip
import com.codex.mobile.ui.components.SourceCardsRow
import com.codex.mobile.ui.components.TextCopyButton
import com.codex.mobile.ui.theme.*
import com.codex.mobile.viewmodel.ChatUiState
import com.codex.mobile.viewmodel.PendingAttachment
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    onOpenSettings: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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
                        canSend = state.inputText.isNotBlank() || state.pendingAttachment != null
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
                            MessageBubble(message)
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
private fun MessageBubble(message: ChatMessage) {
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
    canSend: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PureBlack)
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
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
