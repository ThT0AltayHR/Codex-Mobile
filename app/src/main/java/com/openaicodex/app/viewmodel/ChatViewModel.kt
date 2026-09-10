package com.openaicodex.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openaicodex.app.data.ChatMessage
import com.openaicodex.app.data.Conversation
import com.openaicodex.app.data.ConversationRepository
import com.openaicodex.app.data.GeneratedFile
import com.openaicodex.app.data.ModelCatalog
import com.openaicodex.app.data.SelectedModel
import com.openaicodex.app.engine.CodexEvent
import com.openaicodex.app.engine.CodexPromptComposer
import com.openaicodex.app.engine.CodexProcessService
import com.openaicodex.app.engine.MarkdownCodeSplitter
import com.openaicodex.app.engine.SamuraiStep
import com.openaicodex.app.engine.SamuraiStepMapper
import com.openaicodex.app.engine.ThreadEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
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
    val deleteBlockedMessage: String? = null,
    val selectedModel: SelectedModel = ModelCatalog.default,
    val tokenLimitReached: Boolean = false
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
    private val githubAuthManager: com.openaicodex.app.engine.GitHubAuthManager? = null,
    // Fix: a single shared SecretVault leaked every chat's secrets into
    // every other chat's process env (anything whose name appeared in the
    // prompt text would match, regardless of which conversation actually
    // stored it). This is now a factory keyed by conversationId, so each
    // chat only ever sees the vault file scoped to its own id — see
    // SecretVault.forConversation.
    private val secretVaultFor: (conversationId: String) -> com.openaicodex.app.engine.SecretVault? = { null }
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    // Guarded by threadIdMutex: this map is read/written from both the
    // Main dispatcher (UI-triggered updates) and Dispatchers.IO (persist
    // calls, delete flows) concurrently. A plain mutableMapOf() is not
    // thread-safe and was previously being read on one dispatcher while
    // mutated on another, which is a real data race. All access now goes
    // through the suspend helpers below, which take the mutex.
    private val threadIdByConversation = mutableMapOf<String, String>()
    private val threadIdMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun putThreadId(conversationId: String, threadId: String) {
        threadIdMutex.withLock { threadIdByConversation[conversationId] = threadId }
    }

    private suspend fun removeThreadId(conversationId: String) {
        threadIdMutex.withLock { threadIdByConversation.remove(conversationId) }
    }

    private suspend fun threadIdSnapshot(): Map<String, String> =
        threadIdMutex.withLock { threadIdByConversation.toMap() }

    private suspend fun getThreadId(conversationId: String): String? =
        threadIdMutex.withLock { threadIdByConversation[conversationId] }

    private var boundService: CodexProcessService? = null
    private var eventCollectionJob: Job? = null
    private var taskStateCollectionJob: Job? = null

    private var sawFatalErrorForCurrentTurn = false
    // Real URLs collected from web_search item events during the current
    // turn, attached to the next assistant message that completes — never
    // fabricated, only ever populated from ThreadEvent.extractWebSearchUrl.
    private val collectedWebSourcesForCurrentTurn = mutableListOf<com.openaicodex.app.data.WebSource>()
    private val collectedGeneratedFilesForCurrentTurn = mutableListOf<GeneratedFile>()

    init {
        refreshConversationList()
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = repository.loadThreadIdMap()
            threadIdMutex.withLock { threadIdByConversation.putAll(loaded) }
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
            // Fix (item 3): deleting a conversation previously only removed
            // its JSON record and attached files — its isolated workspace
            // (files/terminal working dir), per-conversation CODEX_HOME, and
            // Secret Vault were left behind on disk indefinitely. Clean up
            // everything scoped to this conversation now that we've already
            // confirmed above no task is still running against it.
            try {
                val runtime = boundService?.runtime
                if (runtime != null) {
                    runtime.workspaceDir(id).deleteRecursively()
                    runtime.conversationCodexHomeDir(id).deleteRecursively()
                }
            } catch (_: Exception) {
                // Best-effort cleanup — a failure here must not block the
                // rest of the delete flow (metadata is already removed).
            }
            try {
                secretVaultFor(id)?.clearAll()
            } catch (_: Exception) {
            }
            removeThreadId(id)
            repository.saveThreadIdMap(threadIdSnapshot())
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

            val existingThreadId = getThreadId(convoId)
            val modelSelection = _state.value.selectedModel
            val args = service.runtime.buildTurnArgs(
                effectivePrompt,
                existingThreadId,
                modelId = modelSelection.modelId,
                effort = modelSelection.effort.label
            )
            sawFatalErrorForCurrentTurn = false
            collectedGeneratedFilesForCurrentTurn.clear()

            // Fix: a prior version injected EVERY vault secret into every
            // task's process environment. That's a real security bug —
            // a task that only needs EXPO_TOKEN had no business also
            // receiving GITHUB_TOKEN and anything else the vault held.
            // Now only GITHUB_TOKEN (when connected) plus any secret this
            // specific prompt text actually references by name are
            // injected — nothing else leaks into the child process.
            val secretEnv = mutableMapOf<String, String>()
            githubAuthManager?.tokenForProcessEnv()?.let { secretEnv["GITHUB_TOKEN"] = it }
            val vault = secretVaultFor(convoId)
            vault?.listNames()?.forEach { name ->
                if (effectivePrompt.contains(name)) {
                    vault.valueForInjection(name)?.let { secretEnv[name] = it }
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
                if (looksLikeTokenLimitMessage(event.line)) {
                    _state.value = _state.value.copy(tokenLimitReached = true)
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
                if (looksLikeTokenLimitMessage(event.message)) {
                    _state.value = _state.value.copy(tokenLimitReached = true)
                }
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
                viewModelScope.launch(Dispatchers.IO) {
                    putThreadId(convoId, parsed.threadId)
                    repository.saveThreadIdMap(threadIdSnapshot())
                }
            }
            is ThreadEvent.TurnFailed -> {
                sawFatalErrorForCurrentTurn = true
                _state.value = _state.value.copy(errorMessage = parsed.message ?: "Codex görevi başarısız oldu")
                if (parsed.message != null && looksLikeTokenLimitMessage(parsed.message)) {
                    _state.value = _state.value.copy(tokenLimitReached = true)
                }
            }
            is ThreadEvent.ThreadErrorEvent -> {
                sawFatalErrorForCurrentTurn = true
                _state.value = _state.value.copy(errorMessage = parsed.message)
                if (looksLikeTokenLimitMessage(parsed.message)) {
                    _state.value = _state.value.copy(tokenLimitReached = true)
                }
            }
            is ThreadEvent.Unknown -> {
                if (parsed.rawType != null) {
                    android.util.Log.w("ChatViewModel", "Bilinmeyen Codex event tipi: ${parsed.rawType}")
                }
            }
            is ThreadEvent.ItemCompleted -> {
                if (parsed.itemType == "file_change") {
                    val path = com.openaicodex.app.engine.SamuraiStepMapper.extractFilePath(parsed.raw)
                    if (path != null) {
                        val runtime = boundService?.runtime
                        if (runtime != null) {
                            val workspace = runtime.workspaceDir(convoId)
                            val source = resolveGeneratedFile(workspace, path)
                            if (source != null && collectedGeneratedFilesForCurrentTurn.none { it.path == source.absolutePath }) {
                                collectedGeneratedFilesForCurrentTurn += GeneratedFile(source.name, source.absolutePath)
                            }
                        }
                    }
                }
                if (parsed.itemType == "web_search") {
                    val url = ThreadEvent.extractWebSearchUrl(parsed.raw)
                    if (url != null) {
                        val domain = try { java.net.URI(url).host ?: url } catch (e: Exception) { url }
                        val source = com.openaicodex.app.data.WebSource(url, domain)
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
                webSources = if (index == segments.lastIndex) sourcesForThisReply else emptyList(),
                generatedFiles = if (index == segments.lastIndex) {
                    collectedGeneratedFilesForCurrentTurn.toList()
                } else {
                    emptyList()
                }
            )
        }
        collectedGeneratedFilesForCurrentTurn.clear()
        viewModelScope.launch(Dispatchers.IO) {
            newMessages.forEach { repository.appendMessage(convoId, it) }
        }
        if (convoId == _state.value.activeConversationId) {
            _state.value = _state.value.copy(messages = _state.value.messages + newMessages)
        }
    }

    /**
     * Changes the active model+effort for the NEXT turn sent in this chat.
     * Takes effect immediately in UI state; ChatScreen animates the chip
     * change. The actual model id is threaded into buildTurnArgs via
     * sendMessage() the next time the user sends a message.
     */
    fun selectModel(selection: SelectedModel) {
        _state.value = _state.value.copy(selectedModel = selection)
    }

    fun dismissTokenLimitDialog() {
        _state.value = _state.value.copy(tokenLimitReached = false)
    }

    /**
     * Heuristic detection of a Codex/OpenAI-side quota or rate-limit
     * response, distinct from a generic task failure — only patterns
     * Codex's own CLI/stderr actually surface for HTTP 429 / quota-
     * exceeded responses trip this, so it never fires on an unrelated
     * error just because that would be convenient to show.
     */
    private fun looksLikeTokenLimitMessage(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("429") ||
            lower.contains("rate limit") ||
            lower.contains("rate_limit") ||
            lower.contains("quota") ||
            lower.contains("usage limit") ||
            lower.contains("insufficient_quota")
    }

    fun stopRunning() {
        boundService?.cancelTask()
        _state.value = _state.value.copy(currentStep = SamuraiStep.IDLE)
    }

    private fun normalizeTitle(raw: String): String {
        val collapsed = raw.replace(Regex("\\s+"), " ").trim()
        return collapsed.take(60).ifBlank { "Yeni sohbet" }
    }

    private fun resolveGeneratedFile(workspace: java.io.File, rawPath: String): java.io.File? {
        return try {
            val root = workspace.canonicalFile
            val candidate = if (java.io.File(rawPath).isAbsolute) {
                java.io.File(rawPath)
            } else {
                java.io.File(root, rawPath)
            }.canonicalFile
            val rootPrefix = root.path.trimEnd(java.io.File.separatorChar) + java.io.File.separator
            if (candidate.path.startsWith(rootPrefix) && candidate.isFile && candidate.canRead()) candidate else null
        } catch (_: Exception) {
            null
        }
    }
}
