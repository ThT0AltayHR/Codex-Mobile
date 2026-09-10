package com.openaicodex.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.openaicodex.app.data.ConversationRepository
import com.openaicodex.app.data.UserPreferencesStore
import com.openaicodex.app.engine.CodexAuthManager
import com.openaicodex.app.engine.CodexNativeRuntime
import com.openaicodex.app.engine.CodexProcessService
import com.openaicodex.app.engine.CodexPromptComposer
import com.openaicodex.app.ui.screens.*
import com.openaicodex.app.ui.theme.CodexMobileTheme
import com.openaicodex.app.viewmodel.ChatViewModel
import com.openaicodex.app.viewmodel.OnboardingStage
import com.openaicodex.app.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private enum class Screen { ONBOARDING, CHAT, SETTINGS, PERSONALIZATION, MEMORY, LANGUAGE, STORAGE, GITHUB, SECRET_VAULT }

/**
 * Fix over a prior version: ChatViewModel no longer owns a
 * CodexNativeRuntime directly. This Activity binds to CodexProcessService
 * (a real foreground service — see engine/CodexProcessService.kt) and
 * hands the bound instance to ChatViewModel, so the actual Codex child
 * process's lifecycle is tied to the service, which Android will keep
 * alive independent of this Activity's lifecycle.
 */
class MainActivity : ComponentActivity() {

    private lateinit var authManager: CodexAuthManager
    private lateinit var githubAuthManager: com.openaicodex.app.engine.GitHubAuthManager
    private lateinit var prefsStore: UserPreferencesStore
    private lateinit var repository: ConversationRepository
    private lateinit var promptComposer: CodexPromptComposer

    // Not used to run any process directly — only to resolve stable paths
    // (workspaceDir, codexHomeDir) that are identical regardless of which
    // CodexNativeRuntime instance computes them, e.g. for writing auth.json
    // or previewing AGENTS.md before any service connection exists yet.
    private lateinit var pathsHandle: CodexNativeRuntime

    private var boundService: CodexProcessService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? CodexProcessService.LocalBinder ?: return
            val service = localBinder.getService()
            boundService = service
            serviceBound = true
            chatViewModel.attachService(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            serviceBound = false
            chatViewModel.detachService()
        }
    }

    private val onboardingViewModel: OnboardingViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return OnboardingViewModel(prefsStore) as T
            }
        }
    }

    // Each conversation gets its own SecretVault instance/file — see
    // SecretVault.forConversation. Cached per id so repeated calls (e.g.
    // once per sent message, once when opening the vault screen) reuse the
    // same EncryptedSharedPreferences handle instead of re-deriving it.
    private val secretVaultsByConversation = mutableMapOf<String, com.openaicodex.app.engine.SecretVault>()
    private fun secretVaultFor(conversationId: String): com.openaicodex.app.engine.SecretVault =
        secretVaultsByConversation.getOrPut(conversationId) {
            com.openaicodex.app.engine.SecretVault.forConversation(applicationContext, conversationId)
        }

    private val chatViewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(
                    repository,
                    promptComposer,
                    githubAuthManager,
                    com.openaicodex.app.engine.DownloadFileExporter(applicationContext),
                    ::secretVaultFor
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pathsHandle = CodexNativeRuntime(applicationContext)
        authManager = CodexAuthManager(applicationContext)
        githubAuthManager = com.openaicodex.app.engine.GitHubAuthManager(applicationContext)
        prefsStore = UserPreferencesStore(applicationContext)
        repository = ConversationRepository(applicationContext)
        promptComposer = CodexPromptComposer(prefsStore, pathsHandle)

        val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) copyPickedFileIntoWorkspace(uri)
        }

        // Start the foreground service (survives independent of this
        // Activity) AND bind to it (lets us observe its event flow / call
        // methods while we're in the foreground).
        val serviceIntent = Intent(this, CodexProcessService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        openConversationFromIntentIfPresent(intent)

        setContent {
            CodexMobileTheme {
                var screen by remember { mutableStateOf(if (authManager.isLoggedIn()) Screen.CHAT else Screen.ONBOARDING) }
                val onboardingState by onboardingViewModel.state.collectAsState()
                val chatState by chatViewModel.state.collectAsState()
                val userName by prefsStore.userName.collectAsState(initial = null)
                val userBio by prefsStore.userBio.collectAsState(initial = null)
                val tone by prefsStore.tone.collectAsState(initial = "balanced")
                val customPrompt by prefsStore.customPrompt.collectAsState(initial = null)
                val languageCode by prefsStore.languageCode.collectAsState(initial = null)

                LaunchedEffect(onboardingState.stage) {
                    if (onboardingState.stage == OnboardingStage.DONE) {
                        screen = Screen.CHAT
                    }
                }

                when (screen) {
                    Screen.ONBOARDING -> OnboardingScreen(
                        state = onboardingState,
                        onLanguageSelected = { onboardingViewModel.selectLanguage(it) },
                        onStartLogin = { launchLogin() },
                        onNameSubmit = { onboardingViewModel.submitName(it) },
                        onBioSubmit = { onboardingViewModel.submitBio(it) },
                        onBioSkip = { onboardingViewModel.skipBio() }
                    )
                    Screen.CHAT -> ChatScreen(
                        state = chatState,
                        userName = userName,
                        onSend = { chatViewModel.sendMessage(it) },
                        onInputChange = { chatViewModel.updateInputText(it) },
                        onNewConversation = { chatViewModel.startNewConversation() },
                        onOpenConversation = { chatViewModel.openConversation(it) },
                        onRename = { id, title -> chatViewModel.renameConversation(id, title) },
                        onTogglePin = { chatViewModel.togglePinned(it) },
                        onDelete = { chatViewModel.deleteConversation(it) },
                        onAttachFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                        onClearAttachment = { chatViewModel.clearAttachment() },
                        onStop = { chatViewModel.stopRunning() },
                        onDismissError = { chatViewModel.dismissError() },
                        onOpenSecretVault = { screen = Screen.SECRET_VAULT },
                        onSelectModel = { chatViewModel.selectModel(it) },
                        onDismissTokenLimit = { chatViewModel.dismissTokenLimitDialog() },
                        onOpenTokenPurchase = {
                            val customTabsIntent = CustomTabsIntent.Builder().build()
                            customTabsIntent.launchUrl(this@MainActivity, Uri.parse("https://chatgpt.com/codex/settings/usage"))
                        },
                        onDismissDeleteBlocked = { chatViewModel.dismissDeleteBlockedMessage() },
                        onOpenSettings = { screen = Screen.SETTINGS }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        userName = userName ?: "",
                        githubConnected = githubAuthManager.isConnected(),
                        onBack = { screen = Screen.CHAT },
                        onOpenPersonalization = { screen = Screen.PERSONALIZATION },
                        onOpenMemory = { screen = Screen.MEMORY },
                        onOpenLanguage = { screen = Screen.LANGUAGE },
                        onOpenStorage = { screen = Screen.STORAGE },
                        onOpenGitHub = { screen = Screen.GITHUB },
                        onOpenNotifications = {
                            val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                            startActivity(intent)
                        },
                        onLogout = {
                            authManager.logoutEverywhere(pathsHandle)
                            // Reset OnboardingViewModel's in-memory stage
                            // BEFORE switching Screen — otherwise its stale
                            // DONE stage immediately bounces us back to
                            // Chat via LaunchedEffect(onboardingState.stage),
                            // making "Log out" appear to do nothing.
                            onboardingViewModel.resetForLogout()
                            screen = Screen.ONBOARDING
                        }
                    )
                    Screen.PERSONALIZATION -> PersonalizationScreen(
                        currentTone = tone,
                        currentCustomPrompt = customPrompt ?: "",
                        onBack = { screen = Screen.SETTINGS },
                        onToneSelected = { selectedTone ->
                            lifecycleScope.launch { prefsStore.setTone(selectedTone) }
                        },
                        onCustomPromptSaved = { prompt ->
                            lifecycleScope.launch { prefsStore.setCustomPrompt(prompt) }
                        }
                    )
                    Screen.MEMORY -> {
                        var agentsMdPreview by remember { mutableStateOf("") }
                        val activeConvoId = chatState.activeConversationId
                        LaunchedEffect(screen, userName, userBio, activeConvoId) {
                            val agentsFile = File(pathsHandle.workspaceDir(activeConvoId), "AGENTS.md")
                            agentsMdPreview = if (agentsFile.exists()) agentsFile.readText() else ""
                        }
                        MemoryScreen(
                            currentName = userName ?: "",
                            currentBio = userBio ?: "",
                            agentsMdPreview = agentsMdPreview,
                            onBack = { screen = Screen.SETTINGS },
                            onSave = { name, bio ->
                                lifecycleScope.launch {
                                    prefsStore.setUserIdentity(name, bio)
                                    promptComposer.syncAgentsMd(activeConvoId)
                                    val agentsFile = File(pathsHandle.workspaceDir(activeConvoId), "AGENTS.md")
                                    agentsMdPreview = if (agentsFile.exists()) agentsFile.readText() else ""
                                }
                            }
                        )
                    }
                    Screen.LANGUAGE -> LanguageSettingsScreen(
                        currentLanguageCode = languageCode,
                        onBack = { screen = Screen.SETTINGS },
                        onLanguageSelected = { lang ->
                            lifecycleScope.launch {
                                prefsStore.setLanguage(lang.code, lang.nativeName)
                                promptComposer.syncAgentsMd(chatState.activeConversationId)
                            }
                            screen = Screen.SETTINGS
                        }
                    )
                    Screen.STORAGE -> StorageScreen(
                        workspaceDir = File(filesDir, "codex_workspace"),
                        codexHomeDir = pathsHandle.codexHomeDir(),
                        conversationsFile = File(filesDir, "conversations.json"),
                        onBack = { screen = Screen.SETTINGS }
                    )
                    Screen.GITHUB -> GitHubConnectorScreen(
                        githubAuthManager = githubAuthManager,
                        onBack = { screen = Screen.SETTINGS },
                        onOpenVerificationUrl = { url ->
                            val customTabsIntent = CustomTabsIntent.Builder().build()
                            customTabsIntent.launchUrl(this@MainActivity, Uri.parse(url))
                        }
                    )
                    Screen.SECRET_VAULT -> {
                        val activeConvoId = chatState.activeConversationId
                        if (activeConvoId == null) {
                            screen = Screen.CHAT
                        } else {
                            val vault = secretVaultFor(activeConvoId)
                            var unlocked by remember(activeConvoId) { mutableStateOf(false) }
                            var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
                            LaunchedEffect(vault.lockoutUntil()) {
                                while (vault.isLockedOut()) {
                                    kotlinx.coroutines.delay(1000)
                                    nowTick = System.currentTimeMillis()
                                }
                            }
                            when {
                                !vault.hasPatternLock() -> PatternLockScreen(
                                    isSettingNewPattern = true,
                                    remainingAttempts = vault.remainingAttemptsBeforeLockout(),
                                    lockoutRemainingSeconds = 0,
                                    onBack = { screen = Screen.CHAT },
                                    onPatternDrawn = { cells ->
                                        vault.setPatternLock(cells)
                                        unlocked = true
                                    }
                                )
                                !unlocked -> {
                                    val remainingLockSec = ((vault.lockoutUntil() - nowTick) / 1000).coerceAtLeast(0)
                                    PatternLockScreen(
                                        isSettingNewPattern = false,
                                        remainingAttempts = vault.remainingAttemptsBeforeLockout(),
                                        lockoutRemainingSeconds = remainingLockSec,
                                        onBack = { screen = Screen.CHAT },
                                        onPatternDrawn = { cells ->
                                            if (vault.verifyPattern(cells)) {
                                                unlocked = true
                                            } else {
                                                nowTick = System.currentTimeMillis()
                                            }
                                        }
                                    )
                                }
                                else -> {
                                    var secretNames by remember(activeConvoId) { mutableStateOf(vault.listNames().toList().sorted()) }
                                    val activeTitle = chatState.conversations.find { it.id == activeConvoId }?.title ?: "Sohbet"
                                    SecretVaultScreen(
                                        conversationTitle = activeTitle,
                                        secretNames = secretNames,
                                        onBack = { screen = Screen.CHAT },
                                        onAddSecret = { name, value ->
                                            if (name.isNotBlank() && value.isNotBlank()) {
                                                vault.set(name, value)
                                                secretNames = vault.listNames().toList().sorted()
                                            }
                                        },
                                        onDeleteSecret = { name ->
                                            vault.delete(name)
                                            secretNames = vault.listNames().toList().sorted()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Copies a picked SAF Uri into the Codex workspace directory so the
     * native process (which only understands plain filesystem paths, not
     * content:// Uris) can actually read it.
     *
     * Fixes over a prior version:
     *  - The display name from the DocumentProvider is no longer used
     *    verbatim as the destination filename (a malicious/buggy provider
     *    could return a path-traversal-shaped value); it's sanitized to a
     *    safe basename first.
     *  - A random prefix avoids silently overwriting a previous attachment
     *    that happened to share the same display name.
     *  - Failures are now actually surfaced to the user via
     *    ChatViewModel.onFileAttachError instead of being swallowed.
     */
    private fun copyPickedFileIntoWorkspace(uri: Uri) {
        val rawName = queryDisplayName(uri) ?: "dosya"
        val safeName = sanitizeFileName(rawName)
        val uniqueName = "${UUID.randomUUID().toString().take(8)}_$safeName"
        val activeConvoId = chatViewModel.state.value.activeConversationId
        val destFile = File(pathsHandle.workspaceDir(activeConvoId), uniqueName)
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            } ?: throw java.io.IOException("Dosya akışı açılamadı")
            chatViewModel.onFileAttached(rawName, destFile.absolutePath)
        } catch (e: Exception) {
            chatViewModel.onFileAttachError("Dosya eklenemedi: ${e.message ?: "bilinmeyen hata"}")
        }
    }

    /** Strips any path separators/traversal segments, keeping only a safe basename. */
    private fun sanitizeFileName(name: String): String {
        val basenameOnly = name.substringAfterLast('/').substringAfterLast('\\')
        val stripped = basenameOnly.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return stripped.ifBlank { "dosya" }.take(120)
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) {
                return it.getString(nameIndex)
            }
        }
        return null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // OAuth completion itself arrives via the local callback server
        // (CodexAuthManager.startCallbackServerAndAwaitCode), matching the
        // real upstream redirect_uri — not via an app deep link. The only
        // deep-link-shaped extra this Activity actually reads is the
        // conversation id attached to a task-complete notification tap.
        openConversationFromIntentIfPresent(intent)
    }

    private fun openConversationFromIntentIfPresent(intent: Intent?) {
        val convoId = intent?.getStringExtra(EXTRA_OPEN_CONVERSATION_ID) ?: return
        chatViewModel.openConversation(convoId)
    }

    private fun launchLogin() {
        if (authManager.isLoginInFlight()) return
        onboardingViewModel.onLoginStarted()
        lifecycleScope.launch {
            val serverReady = kotlinx.coroutines.CompletableDeferred<Unit>()
            lifecycleScope.launch {
                val result = authManager.startCallbackServerAndAwaitCode(onBound = { serverReady.complete(Unit) })
                result.onSuccess { (code, state) ->
                    val loginResult = authManager.completeLogin(code, state)
                    loginResult.onSuccess {
                        authManager.writeAuthJsonForNativeRuntime(pathsHandle)
                        onboardingViewModel.onLoginSuccess()
                    }.onFailure {
                        onboardingViewModel.onLoginError(it.message ?: "Giriş başarısız oldu")
                    }
                }.onFailure {
                    onboardingViewModel.onLoginError(it.message ?: "Giriş zaman aşımına uğradı veya iptal edildi")
                }
            }
            // Wait for the server to actually bind before opening the
            // browser. Bounded with a timeout: if the callback server
            // never binds (both ports genuinely unavailable — see the
            // failure path above), this coroutine no longer waits
            // forever with no user-visible outcome.
            val bound = try {
                kotlinx.coroutines.withTimeoutOrNull(5000) { serverReady.await() }
            } catch (e: Exception) {
                null
            }
            if (bound == null) {
                onboardingViewModel.onLoginError("Giriş başlatılamadı (yerel bağlantı kurulamadı)")
                return@launch
            }
            val url = authManager.buildAuthorizeUrl()
            onboardingViewModel.setAuthUrl(url)
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(this@MainActivity, Uri.parse(url))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        authManager.stopCallbackServer()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    companion object {
        const val EXTRA_OPEN_CONVERSATION_ID = "com.openaicodex.app.EXTRA_OPEN_CONVERSATION_ID"
    }
}
