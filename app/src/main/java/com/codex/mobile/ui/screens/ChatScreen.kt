package com.codex.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.data.ChatMessage
import com.codex.mobile.data.Conversation
import com.codex.mobile.engine.SamuraiStep
import com.codex.mobile.ui.components.*
import com.codex.mobile.ui.theme.*
import com.codex.mobile.viewmodel.ChatUiState
import com.codex.mobile.viewmodel.WorkspaceEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onToggleStepHistory: () -> Unit,
    listWorkspaceFiles: () -> List<WorkspaceEntry> = { emptyList() },
    readWorkspaceFile: (String) -> String = { "" }
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var toolsPanelOpen by remember { mutableStateOf(false) }
    var pendingRename by remember { mutableStateOf<Conversation?>(null) }
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.messages.size, state.currentStep) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = NearBlack, drawerContentColor = OffWhite) {
                ConversationDrawer(
                    conversations = state.conversations,
                    activeId = state.activeConversationId,
                    onOpen = { scope.launch { drawerState.close() }; onOpenConversation(it) },
                    onNew = { scope.launch { drawerState.close() }; onNewConversation() },
                    onTogglePin = onTogglePin,
                    onRequestRename = { pendingRename = it },
                    onRequestDelete = { pendingDelete = it },
                    onOpenSettings = { scope.launch { drawerState.close() }; onOpenSettings() },
                    userName = userName
                )
            }
        }
    ) {
        Box(Modifier.fillMaxSize().background(PureBlack)) {
            Column(Modifier.fillMaxSize()) {
                ChatTopBar(
                    title = state.conversations.find { it.id == state.activeConversationId }?.title ?: "Yeni sohbet",
                    onMenu = { scope.launch { drawerState.open() } },
                    onNew = onNewConversation,
                    onTools = { toolsPanelOpen = true }
                )

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                if (dragAmount < -14) toolsPanelOpen = true
                                if (dragAmount > 14) toolsPanelOpen = false
                                change.consume()
                            }
                        }
                ) {
                    if (state.messages.isEmpty()) {
                        EmptyChatState(userName)
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = Dimens.lg, vertical = Dimens.md),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.messages, key = { it.id }) { message ->
                                MessageBubble(message)
                            }
                            if (state.currentStep != SamuraiStep.IDLE) {
                                item(key = "step-strip") {
                                    SamuraiStepStrip(
                                        currentStep = state.currentStep,
                                        stepHistory = state.stepHistory,
                                        expanded = state.stepHistoryExpanded,
                                        onToggleExpanded = onToggleStepHistory,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            item(key = "bottom-spacer") { Spacer(Modifier.height(4.dp)) }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = state.errorMessage != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    ErrorBanner(state.errorMessage ?: "", onDismissError)
                }

                AnimatedVisibility(visible = state.pendingAttachment != null) {
                    state.pendingAttachment?.let { attachment ->
                        AttachmentChip(attachment.fileName, onClearAttachment)
                    }
                }

                ChatInputBar(
                    text = state.inputText,
                    isRunning = state.isRunning,
                    onTextChange = onInputChange,
                    onAttach = onAttachFile,
                    onSend = {
                        if (state.inputText.isNotBlank() || state.pendingAttachment != null) {
                            keyboard?.hide()
                            onSend(state.inputText)
                        }
                    },
                    onStop = onStop
                )
            }

            ToolsPanelOverlay(
                visible = toolsPanelOpen,
                onDismiss = { toolsPanelOpen = false },
                stepHistory = state.stepHistory,
                shellLog = state.shellLog,
                listFiles = listWorkspaceFiles,
                readFile = readWorkspaceFile
            )
        }
    }

    pendingRename?.let { convo ->
        var text by remember(convo.id) { mutableStateOf(convo.title) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            containerColor = CardBlack,
            title = { Text("Sohbeti yeniden adlandır", color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OffWhite, unfocusedTextColor = OffWhite,
                        focusedBorderColor = OffWhite, unfocusedBorderColor = BorderGray,
                        cursorColor = OffWhite
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(convo.id, text); pendingRename = null }) {
                    Text("Kaydet", color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { pendingRename = null }) { Text("Vazgeç", color = FaintWhite, fontFamily = BodyFamily) } }
        )
    }

    pendingDelete?.let { convo ->
        ConfirmDialog(
            title = "Sohbeti sil",
            message = "\"${convo.title}\" kalıcı olarak silinecek. Bu işlem geri alınamaz.",
            confirmLabel = "Sil",
            onConfirm = { onDelete(convo.id) },
            onDismiss = { pendingDelete = null }
        )
    }

    if (state.deleteBlockedMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissDeleteBlocked,
            containerColor = CardBlack,
            title = { Text("Silinemedi", color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold) },
            text = { Text(state.deleteBlockedMessage, color = MutedWhite, fontFamily = BodyFamily, fontSize = 13.5.sp) },
            confirmButton = { TextButton(onClick = onDismissDeleteBlocked) { Text("Tamam", color = OffWhite, fontFamily = BodyFamily) } }
        )
    }
}

@Composable
private fun ChatTopBar(title: String, onMenu: () -> Unit, onNew: () -> Unit, onTools: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenu) { CodexIcon(CIcon.Menu, tint = OffWhite, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 2.dp)) {
            Text(
                "Codex",
                color = OffWhite,
                fontFamily = DisplayItalicFamily,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp
            )
            Text(title, color = FaintWhite, fontFamily = BodyFamily, fontSize = 11.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        IconButton(onClick = onTools) { CodexIcon(CIcon.PanelRight, tint = OffWhite, modifier = Modifier.size(19.dp)) }
        IconButton(onClick = onNew) { CodexIcon(CIcon.Add, tint = OffWhite, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun EmptyChatState(userName: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(Dimens.radiusLg)).background(PanelBlack).border(BorderStroke(1.dp, BorderGray), RoundedCornerShape(Dimens.radiusLg)),
            contentAlignment = Alignment.Center
        ) { CodexIcon(CIcon.CodeBrackets, tint = OffWhite, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(Dimens.lg))
        Text(
            if (userName.isNullOrBlank()) "Bugün ne üzerinde çalışalım?" else "Merhaba $userName, ne üzerinde çalışalım?",
            color = OffWhite,
            fontFamily = BodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Bir görev yazın, bir dosya ekleyin ya da bir depoyu bağlayın.",
            color = FaintWhite,
            fontFamily = BodyFamily,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    when (message.role) {
        ChatMessage.Role.SYSTEM_STEP -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    message.content,
                    color = FaintWhite,
                    fontFamily = BodyFamily,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PanelBlack)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        ChatMessage.Role.USER -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(Dimens.radiusLg).let { androidx.compose.foundation.shape.RoundedCornerShape(topStart = Dimens.radiusLg, topEnd = Dimens.radiusLg, bottomStart = Dimens.radiusLg, bottomEnd = 4.dp) })
                        .background(OffWhite)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (message.attachedFileName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 5.dp)) {
                            CodexIcon(CIcon.FileGeneric, tint = PureBlack, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(message.attachedFileName, color = PureBlack, fontFamily = BodyFamily, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (message.content.isNotBlank()) {
                        Text(message.content, color = PureBlack, fontFamily = BodyFamily, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
            }
        }
        ChatMessage.Role.ASSISTANT -> {
            Column(Modifier.fillMaxWidth()) {
                if (message.isCodeBlock) {
                    CodeBlockCard(code = message.content, language = message.language)
                } else if (message.content.isNotBlank()) {
                    MarkdownText(message.content, modifier = Modifier.fillMaxWidth())
                }
                if (message.webSources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SourceCardsRow(message.webSources)
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
            .padding(horizontal = Dimens.lg, vertical = 6.dp)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(ErrorRed.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)), RoundedCornerShape(Dimens.radiusMd))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CodexIcon(CIcon.Warning, tint = ErrorRed, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(9.dp))
        Text(message, color = ErrorRed, fontFamily = BodyFamily, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) { CodexIcon(CIcon.Close, tint = ErrorRed, modifier = Modifier.size(13.dp)) }
    }
}

@Composable
private fun AttachmentChip(fileName: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = Dimens.lg, vertical = 4.dp)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(PanelBlack)
            .border(BorderStroke(1.dp, BorderGray), RoundedCornerShape(Dimens.radiusSm))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CodexIcon(CIcon.Attach, tint = MutedWhite, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(fileName, color = MutedWhite, fontFamily = BodyFamily, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(16.dp).clip(RoundedCornerShape(50)).background(BorderGray).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClear),
            contentAlignment = Alignment.Center
        ) { CodexIcon(CIcon.Close, tint = OffWhite, modifier = Modifier.size(9.dp)) }
    }
}

@Composable
private fun ChatInputBar(text: String, isRunning: Boolean, onTextChange: (String) -> Unit, onAttach: () -> Unit, onSend: () -> Unit, onStop: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Dimens.radiusXl))
                .background(PanelBlack)
                .border(BorderStroke(1.dp, BorderGray), RoundedCornerShape(Dimens.radiusXl))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                IconButton(onClick = onAttach) { CodexIcon(CIcon.Attach, tint = FaintWhite, modifier = Modifier.size(18.dp)) }
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Codex'e bir görev yaz…", color = FaintWhite, fontFamily = BodyFamily, fontSize = 14.5.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        cursorColor = OffWhite,
                        focusedTextColor = OffWhite,
                        unfocusedTextColor = OffWhite
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = BodyFamily, fontSize = 14.5.sp),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSend() })
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(50))
                .background(if (isRunning) ErrorRed else OffWhite)
                .pressableScale(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = if (isRunning) onStop else onSend),
            contentAlignment = Alignment.Center
        ) {
            CodexIcon(if (isRunning) CIcon.Stop else CIcon.Send, tint = if (isRunning) PureWhite else PureBlack, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun ConversationDrawer(
    conversations: List<Conversation>,
    activeId: String?,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onTogglePin: (String) -> Unit,
    onRequestRename: (Conversation) -> Unit,
    onRequestDelete: (Conversation) -> Unit,
    onOpenSettings: () -> Unit,
    userName: String?
) {
    Column(Modifier.fillMaxHeight().width(300.dp)) {
        Column(Modifier.statusBarsPadding().padding(Dimens.lg)) {
            Text("Codex", color = OffWhite, fontFamily = DisplayItalicFamily, fontStyle = FontStyle.Italic, fontSize = 20.sp)
            Spacer(Modifier.height(Dimens.md))
            PrimaryButton(text = "Yeni sohbet", leadingIcon = CIcon.Add, onClick = onNew)
        }
        val pinned = conversations.filter { it.pinned }
        val others = conversations.filterNot { it.pinned }
        LazyColumn(Modifier.weight(1f)) {
            if (pinned.isNotEmpty()) {
                item { SectionLabel("Sabitlenmiş") }
                items(pinned, key = { "p-" + it.id }) { convo ->
                    ConversationRow(convo, convo.id == activeId, onOpen, onTogglePin, onRequestRename, onRequestDelete)
                }
            }
            item { SectionLabel("Sohbetler") }
            if (others.isEmpty() && pinned.isEmpty()) {
                item {
                    Text("Henüz sohbet yok", color = FaintWhite, fontFamily = BodyFamily, fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = Dimens.lg, vertical = 8.dp))
                }
            }
            items(others, key = { "o-" + it.id }) { convo ->
                ConversationRow(convo, convo.id == activeId, onOpen, onTogglePin, onRequestRename, onRequestDelete)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        RowDivider()
        SettingsRow(
            icon = CIcon.Person,
            title = userName?.takeIf { it.isNotBlank() } ?: "Profil ve ayarlar",
            subtitle = "Ayarları aç",
            onClick = onOpenSettings
        )
        Spacer(Modifier.navigationBarsPadding().height(4.dp))
    }
}

@Composable
private fun ConversationRow(
    convo: Conversation,
    isActive: Boolean,
    onOpen: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onRequestRename: (Conversation) -> Unit,
    onRequestDelete: (Conversation) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(if (isActive) PanelBlack else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onOpen(convo.id) }
            .padding(horizontal = Dimens.lg, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CodexIcon(if (convo.pinned) CIcon.Pin else CIcon.CodeBrackets, tint = if (isActive) OffWhite else FaintWhite, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            convo.title,
            color = if (isActive) OffWhite else MutedWhite,
            fontFamily = BodyFamily,
            fontSize = 13.5.sp,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                CodexIcon(CIcon.More, tint = FaintWhite, modifier = Modifier.size(14.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = CardBlack) {
                DropdownMenuItem(text = { Text(if (convo.pinned) "Sabitlemeyi kaldır" else "Sabitle", color = OffWhite, fontFamily = BodyFamily) }, onClick = { menuOpen = false; onTogglePin(convo.id) })
                DropdownMenuItem(text = { Text("Yeniden adlandır", color = OffWhite, fontFamily = BodyFamily) }, onClick = { menuOpen = false; onRequestRename(convo) })
                DropdownMenuItem(text = { Text("Sil", color = ErrorRed, fontFamily = BodyFamily) }, onClick = { menuOpen = false; onRequestDelete(convo) })
            }
        }
    }
}
