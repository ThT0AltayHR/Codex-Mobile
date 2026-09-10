package com.codex.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codex.mobile.data.ChatMessage
import com.codex.mobile.data.Conversation
import com.codex.mobile.data.ConversationRepository
import com.codex.mobile.engine.CodexEvent
import com.codex.mobile.engine.CodexPromptComposer
import com.codex.mobile.engine.CodexProcessService
import com.codex.mobile.engine.MarkdownCodeSplitter
import com.codex.mobile.engine.SamuraiStep
import com.codex.mobile.engine.SamuraiStepMapper
import com.codex.mobile.engine.ThreadEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class PendingAttachment(val fileName: String, val filePath: String)

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val currentStep: SamuraiStep = SamuraiStep.IDLE,
    val isRunning: Boolean = false,
    val runningTaskConversationId: String? = null,
    val inputText: String = "",
    val errorMessage: String? = null,
    val pendingAttachment: PendingAttachment? = null,
    val serviceConnected: Boolean = false,
    val deleteBlockedMessage: String? = null
)

/**
 * Binds to CodexProcessService and drives everything through it, so the
 * real Codex child process's lifecycle is tied to the foreground service,
 * not to whether this ViewModel happens to be alive.
 *
 * Fixes in this revision:
 *  - attachService() reads the service's own activeTaskState (which
 *    conversation + running flag), not just a bare isRunning() boolean,
 *    so a recreated ViewModel learns WHICH conversation a still-running
 *    task belongs to.
 *  - AGENTS.md sync is wrapped so a failure can't leave the UI stuck in
 *    isRunning=true with no way to send another message.
 *  - service.runTask() is called with the conversation id and its
 *    Boolean result is checked — a false return no longer results in the
 *    UI claiming a task is running when the service actually rejected it.
 *  - deleteConversation() refuses to delete a conversation that has the
 *    currently-running task.
 *  - repository calls run on Dispatchers.IO instead of the caller thread.
 */
class ChatViewModel(
    private val repository: ConversationRepository,
    private val promptComposer: CodexPromptComposer,
    private val githubAuthManager: com.codex.mobile.engine.GitHubAuthManager? = null,
    private val secretVault: com.codex.mobile.engine.SecretVault? = null
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    private val threadIdByConversation = mutableMapOf<String, String>()

    private var boundService: CodexProcessService? = null
    private var eventCollectionJob: Job? = null
    private var taskStateCollectionJob: Job? = null

    private var sawFatalErrorForCurrentTurn = false
    // Real URLs collected from web_search item events during the current
    // turn, attached to the next assistant message that completes — never
    // fabricated, only ever populated from ThreadEvent.extractWebSearchUrl.
    private val collectedWebSourcesForCurrentTurn = mutableListOf<com.codex.mobile.data.WebSource>()

    init {
        refreshConversationList()
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = repository.loadThreadIdMap()
            threadIdByConversation.putAll(loaded)
        }
    }

    fun attachService(service: CodexProcessService) {
        boundService = service
        val serviceState = service.activeTaskState.value
        _state.value = _state.value.copy(
            serviceConnected = true,
            isRunning = serviceState.isRunning,
            runningTaskConversationId = serviceState.conversationId,
            currentStep = if (serviceState.isRunning && serviceState.conversationId == _state.value.activeConversationId) {
                _state.value.currentStep
            } else SamuraiStep.IDLE
        )

        eventCollectionJob?.cancel()
        eventCollectionJob = viewModelScope.launch {
            service.events.collect { event -> handleServiceEvent(event) }
        }

        taskStateCollectionJob?.cancel()
        taskStateCollectionJob = viewModelScope.launch {
            service.activeTaskState.collect { taskState ->
                _state.value = _state.value.copy(
                    isRunning = taskState.isRunning,
                    runningTaskConversationId = taskState.conversationId
                )
            }
        }
    }

    fun detachService() {
        eventCollectionJob?.cancel()
        taskStateCollectionJob?.cancel()
        boundService = null
        _state.value = _state.value.copy(serviceConnected = false)
    }

    fun refreshConversationList() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.listConversations()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(conversations = list)
            }
        }
    }

    fun startNewConversation() {
        viewModelScope.launch(Dispatchers.IO) {
            val convo = repository.createConversation()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    activeConversationId = convo.id,
                    messages = emptyList(),
                    errorMessage = null
                )
            }
            refreshConversationList()
        }
    }

    fun openConversation(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val convo = repository.getConversation(id) ?: return@launch
            withContext(Dispatchers.Main) {
                val runningHere = _state.value.runningTaskConversationId == id && _state.value.isRunning
                _state.value = _state.value.copy(
                    activeConversationId = id,
                    messages = convo.messages,
                    errorMessage = null,
                    currentStep = if (runningHere) _state.value.currentStep else SamuraiStep.IDLE
                )
            }
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        val normalized = normalizeTitle(newTitle)
        if (normalized.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.renameConversation(id, normalized)
            refreshConversationList()
        }
    }

    fun togglePinned(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.togglePinned(id)
            refreshConversationList()
        }
    }

    fun deleteConversation(id: String) {
        if (_state.value.runningTaskConversationId == id && _state.value.isRunning) {
            _state.value = _state.value.copy(
                deleteBlockedMessage = "Bu sohbette Codex hâlâ çalışıyor. Önce durdurun, sonra silin."
            )
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val convo = repository.getConversation(id)
            repository.deleteConversation(id)
            convo?.messages?.mapNotNull { it.attachedFilePath }?.forEach { path ->
                try {
                    val f = java.io.File(path)
                    if (f.exists()) f.delete()
                } catch (_: Exception) {
                }
            }
            threadIdByConversation.remove(id)
            repository.saveThreadIdMap(threadIdByConversation)
            withContext(Dispatchers.Main) {
                if (_state.value.activeConversationId == id) {
                    _state.value = _state.value.copy(activeConversationId = null, messages = emptyList())
                }
            }
            refreshConversationList()
        }
    }

    fun dismissDeleteBlockedMessage() {
        _state.value = _state.value.copy(deleteBlockedMessage = null)
    }

    fun updateInputText(text: String) {
        _state.value = _state.value.copy(inputText = text)
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun onFileAttached(fileName: String, filePath: String) {
        _state.value = _state.value.copy(pendingAttachment = PendingAttachment(fileName, filePath))
    }

    fun onFileAttachError(message: String) {
        _state.value = _state.value.copy(errorMessage = message)
    }

    fun clearAttachment() {
        _state.value = _state.value.copy(pendingAttachment = null)
    }

    /** A message is sendable if there's text OR a pending attachment — mirrored by ChatScreen's Send button enabled check. */
    fun canSend(text: String): Boolean = text.isNotBlank() || _state.value.pendingAttachment != null

    fun sendMessage(text: String) {
        if (!canSend(text)) return
        val service = boundService ?: run {
            _state.value = _state.value.copy(errorMessage = "Codex servisi henüz bağlı değil, lütfen tekrar deneyin")
            return
        }
        if (_state.value.isRunning) return

        viewModelScope.launch(Dispatchers.IO) {
            val convoId = _state.value.activeConversationId ?: repository.createConversation().id.also { newId ->
                viewModelScope.launch(Dispatchers.Main) {
                    _state.value = _state.value.copy(activeConversationId = newId)
                }
            }

            val attachment = _state.value.pendingAttachment
            // Fix: a prior version referenced the attachment as
            // "attachments/<filename>" in the prompt, but
            // MainActivity.copyPickedFileIntoWorkspace() actually copies
            // the file directly into workspaceDir() root — there is no
            // "attachments/" subdirectory. That mismatch meant Codex was
            // told to look for the file at a path that never physically
            // existed. The file's real location, relative to the
            // workspace root Codex's cwd is set to, is just its bare
            // filename.
            val relativeAttachmentRef = attachment?.let { java.io.File(it.filePath).name }
            val effectivePrompt = if (attachment != null) "$text\n\n[Ekli dosya: $relativeAttachmentRef]" else text

            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatMessage.Role.USER,
                content = text,
                timestamp = System.currentTimeMillis(),
                attachedFileName = attachment?.fileName,
                attachedFilePath = attachment?.filePath
            )
            repository.appendMessage(convoId, userMessage)

            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages + userMessage,
                    inputText = "",
                    pendingAttachment = null,
                    currentStep = SamuraiStep.THINKING,
                    errorMessage = null
                )
            }
            refreshConversationList()

            val syncSucceeded = try {
                promptComposer.syncAgentsMd(convoId)
                true
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(errorMessage = "Bağlam hazırlanamadı: ${e.message ?: "bilinmeyen hata"}")
                }
                false
            }
            if (!syncSucceeded) return@launch

            val existingThreadId = threadIdByConversation[convoId]
            val args = service.runtime.buildTurnArgs(effectivePrompt, existingThreadId)
            sawFatalErrorForCurrentTurn = false

            // Fix: a prior version injected EVERY vault secret into every
            // task's process environment. That's a real security bug —
            // a task that only needs EXPO_TOKEN had no business also
            // receiving GITHUB_TOKEN and anything else the vault held.
            // Now only GITHUB_TOKEN (when connected) plus any secret this
            // specific prompt text actually references by name are
            // injected — nothing else leaks into the child process.
            val secretEnv = mutableMapOf<String, String>()
            githubAuthManager?.tokenForProcessEnv()?.let { secretEnv["GITHUB_TOKEN"] = it }
            secretVault?.listNames()?.forEach { name ->
                if (effectivePrompt.contains(name)) {
                    secretVault.get(name)?.let { secretEnv[name] = it }
                }
            }

            val actuallyStarted = service.runTask(convoId, args, secretEnv)
            if (!actuallyStarted) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        errorMessage = "Codex şu anda başka bir görevi tamamlıyor, lütfen bekleyin",
                        currentStep = SamuraiStep.IDLE
                    )
                }
            }
        }
    }

    private fun handleServiceEvent(event: CodexEvent) {
        val convoId = _state.value.runningTaskConversationId ?: return
        when (event) {
            is CodexEvent.Stdout -> handleStdoutLine(convoId, event.line)
            is CodexEvent.Stderr -> {
                if (event.line.isNotBlank() && !sawFatalErrorForCurrentTurn) {
                    _state.value = _state.value.copy(errorMessage = event.line.take(500))
                }
            }
            is CodexEvent.Exited -> {
                if (event.code != 0 && !sawFatalErrorForCurrentTurn) {
                    _state.value = _state.value.copy(errorMessage = "Codex görevi hata koduyla sonlandı (exit ${event.code})")
                }
                _state.value = _state.value.copy(currentStep = SamuraiStep.IDLE)
                refreshConversationList()
            }
            is CodexEvent.Failed -> {
                sawFatalErrorForCurrentTurn = true
                _state.value = _state.value.copy(currentStep = SamuraiStep.IDLE, errorMessage = event.message)
            }
        }
    }

    private fun handleStdoutLine(convoId: String, line: String) {
        val parsed = ThreadEvent.parse(line) ?: return

        if (convoId == _state.value.activeConversationId) {
            val mapped = SamuraiStepMapper.mapEvent(parsed)
            if (mapped != null) {
                _state.value = _state.value.copy(currentStep = mapped)
            } else if (parsed is ThreadEvent.ItemStarted || parsed is ThreadEvent.ItemUpdated) {
                _state.value = _state.value.copy(currentStep = SamuraiStep.WORKING_GENERIC)
            }
        }

        when (parsed) {
            is ThreadEvent.ThreadStarted -> {
                threadIdByConversation[convoId] = parsed.threadId
                viewModelScope.launch(Dispatchers.IO) { repository.saveThreadIdMap(threadIdByConversation) }
            }
            is ThreadEvent.TurnFailed -> {
                sawFatalErrorForCurrentTurn = true
                _state.value = _state.value.copy(errorMessage = parsed.message ?: "Codex görevi başarısız oldu")
            }
            is ThreadEvent.ThreadErrorEvent -> {
                sawFatalErrorForCurrentTurn = true
                _state.value = _state.value.copy(errorMessage = parsed.message)
            }
            is ThreadEvent.Unknown -> {
                if (parsed.rawType != null) {
                    android.util.Log.w("ChatViewModel", "Bilinmeyen Codex event tipi: ${parsed.rawType}")
                }
            }
            is ThreadEvent.ItemCompleted -> {
                if (parsed.itemType == "web_search") {
                    val url = ThreadEvent.extractWebSearchUrl(parsed.raw)
                    if (url != null) {
                        val domain = try { java.net.URI(url).host ?: url } catch (e: Exception) { url }
                        val source = com.codex.mobile.data.WebSource(url, domain)
                        if (collectedWebSourcesForCurrentTurn.none { it.url == url }) {
                            collectedWebSourcesForCurrentTurn.add(source)
                        }
                    }
                }
                if (parsed.itemType == "agent_message") {
                    val rawText = ThreadEvent.extractAgentMessageText(parsed.raw)
                    if (rawText.isNotBlank()) appendAssistantMessageWithCodeSplitting(convoId, rawText)
                }
            }
            else -> Unit
        }
    }

    private fun appendAssistantMessageWithCodeSplitting(convoId: String, rawText: String) {
        val segments = MarkdownCodeSplitter.split(rawText)
        val sourcesForThisReply = collectedWebSourcesForCurrentTurn.toList()
        collectedWebSourcesForCurrentTurn.clear()
        val newMessages = segments.mapIndexed { index, segment ->
            ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatMessage.Role.ASSISTANT,
                content = segment.content,
                timestamp = System.currentTimeMillis(),
                isCodeBlock = segment.isCode,
                language = segment.language,
                // Attach real sources only to the last segment of this
                // reply, so they render once beneath the actual answer
                // rather than repeated under every code block.
                webSources = if (index == segments.lastIndex) sourcesForThisReply else emptyList()
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            newMessages.forEach { repository.appendMessage(convoId, it) }
        }
        if (convoId == _state.value.activeConversationId) {
            _state.value = _state.value.copy(messages = _state.value.messages + newMessages)
        }
    }

    fun stopRunning() {
        boundService?.cancelTask()
        _state.value = _state.value.copy(currentStep = SamuraiStep.IDLE)
    }

    private fun normalizeTitle(raw: String): String {
        val collapsed = raw.replace(Regex("\\s+"), " ").trim()
        return collapsed.take(60).ifBlank { "Yeni sohbet" }
    }
}
