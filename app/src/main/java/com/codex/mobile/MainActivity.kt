package com.codex.mobile

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
import com.codex.mobile.data.ConversationRepository
import com.codex.mobile.data.UserPreferencesStore
import com.codex.mobile.engine.CodexAuthManager
import com.codex.mobile.engine.CodexNativeRuntime
import com.codex.mobile.engine.CodexProcessService
import com.codex.mobile.engine.CodexPromptComposer
import com.codex.mobile.ui.screens.*
import com.codex.mobile.ui.theme.CodexMobileTheme
import com.codex.mobile.viewmodel.ChatViewModel
import com.codex.mobile.viewmodel.OnboardingStage
import com.codex.mobile.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private enum class Screen {
    ONBOARDING, CHAT, SETTINGS, PERSONALIZATION, MEMORY, LANGUAGE, STORAGE, GITHUB,
    APPEARANCE, SECRETS, ADVANCED, ABOUT
}

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
    private lateinit var githubAuthManager: com.codex.mobile.engine.GitHubAuthManager
    private lateinit var secretVault: com.codex.mobile.engine.SecretVault
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

    private val chatViewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(repository, promptComposer, githubAuthManager, secretVault) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pathsHandle = CodexNativeRuntime(applicationContext)
        authManager = CodexAuthManager(applicationContext)
        githubAuthManager = com.codex.mobile.engine.GitHubAuthManager(applicationContext)
        secretVault = com.codex.mobile.engine.SecretVault(applicationContext)
        prefsStore = UserPreferencesStore(applicationContext)
        repository = ConversationRepository(applicationContext)
        promptComposer = CodexPromptComposer(prefsStore, pathsHandle)

        val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) copyPickedFileIntoWorkspace(uri)
        }

        Intent(this, CodexProcessService::class.java).also { intent ->
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            CodexMobileTheme {
                var screen by remember { mutableStateOf(Screen.ONBOARDING) }
                val onboardingState by onboardingViewModel.state.collectAsState()
                val chatState by chatViewModel.state.collectAsState()

                val onboardingDone by prefsStore.onboardingDone.collectAsState(initial = false)
                val userName by prefsStore.userName.collectAsState(initial = null)
                val userBio by prefsStore.userBio.collectAsState(initial = null)
                val tone by prefsStore.tone.collectAsState(initial = "balanced")
                val customPrompt by prefsStore.customPrompt.collectAsState(initial = null)
                val languageCode by prefsStore.languageCode.collectAsState(initial = null)
                val textScale by prefsStore.textScale.collectAsState(initial = 1.0f)
                val reduceMotion by prefsStore.reduceMotion.collectAsState(initial = false)
                val hapticsEnabled by prefsStore.hapticsEnabled.collectAsState(initial = true)
                val storedAuth = remember(chatState.serviceConnected, onboardingState.isLoggedIn) { authManager.loadStoredAuth() }
                val githubConnected = remember(chatState.serviceConnected) { githubAuthManager.isConnected() }
                var secretNames by remember { mutableStateOf(secretVault.listNames().toList()) }
                var configTomlText by remember { mutableStateOf("") }

                LaunchedEffect(onboardingDone) {
                    screen = if (onboardingDone) Screen.CHAT else Screen.ONBOARDING
                }
                LaunchedEffect(screen) {
                    if (screen == Screen.ADVANCED) {
                        val f = File(pathsHandle.codexHomeDir(), "config.toml")
                        configTomlText = if (f.exists()) f.readText() else ""
                    }
                    if (screen == Screen.SECRETS) {
                        secretNames = secretVault.listNames().toList()
                    }
                }

                when (screen) {
                    Screen.ONBOARDING -> OnboardingScreen(
                        state = onboardingState,
                        onLanguageSelected = { onboardingViewModel.selectLanguage(it) },
                        onLoginClicked = { launchLogin() },
                        onNameSubmitted = { onboardingViewModel.submitName(it) },
                        onBioSubmitted = { onboardingViewModel.submitBio(it) },
                        onSkipBio = { onboardingViewModel.skipBio() }
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
                        onDismissDeleteBlocked = { chatViewModel.dismissDeleteBlockedMessage() },
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onToggleStepHistory = { chatViewModel.toggleStepHistoryExpanded() },
                        listWorkspaceFiles = { chatViewModel.listWorkspaceFiles() },
                        readWorkspaceFile = { chatViewModel.readWorkspaceFilePreview(it) }
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        userName = userName ?: "",
                        openAiEmail = storedAuth?.email,
                        githubConnected = githubConnected,
                        onBack = { screen = Screen.CHAT },
                        onOpenPersonalization = { screen = Screen.PERSONALIZATION },
                        onOpenMemory = { screen = Screen.MEMORY },
                        onOpenLanguage = { screen = Screen.LANGUAGE },
                        onOpenStorage = { screen = Screen.STORAGE },
                        onOpenGitHub = { screen = Screen.GITHUB },
                        onOpenNotifications = { screen = Screen.CHAT },
                        onOpenAppearance = { screen = Screen.APPEARANCE },
                        onOpenSecrets = { screen = Screen.SECRETS },
                        onOpenAdvanced = { screen = Screen.ADVANCED },
                        onOpenAbout = { screen = Screen.ABOUT },
                        onLogout = {
                            authManager.logoutEverywhere(pathsHandle)
                            githubAuthManager.disconnect()
                            onboardingViewModel.resetForLogout()
                            screen = Screen.ONBOARDING
                        }
                    )

                    Screen.PERSONALIZATION -> PersonalizationScreen(
                        currentTone = tone,
                        currentCustomPrompt = customPrompt ?: "",
                        onBack = { screen = Screen.SETTINGS },
                        onToneSelected = { lifecycleScope.launch { prefsStore.setTone(it) } },
                        onCustomPromptSaved = { lifecycleScope.launch { prefsStore.setCustomPrompt(it) } }
                    )

                    Screen.MEMORY -> {
                        var agentsPreview by remember { mutableStateOf("") }
                        LaunchedEffect(chatState.activeConversationId) {
                            val agentsFile = File(pathsHandle.workspaceDir(chatState.activeConversationId), "AGENTS.md")
                            agentsPreview = if (agentsFile.exists()) agentsFile.readText() else ""
                        }
                        MemoryScreen(
                            currentName = userName ?: "",
                            currentBio = userBio ?: "",
                            agentsMdPreview = agentsPreview,
                            onBack = { screen = Screen.SETTINGS },
                            onSave = { name, bio ->
                                lifecycleScope.launch {
                                    prefsStore.setUserIdentity(name, bio)
                                    promptComposer.syncAgentsMd(chatState.activeConversationId)
                                }
                            }
                        )
                    }

                    Screen.LANGUAGE -> LanguageSettingsScreen(
                        currentLanguageCode = languageCode ?: "tr",
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

                    Screen.APPEARANCE -> AppearanceScreen(
                        textScale = textScale,
                        reduceMotion = reduceMotion,
                        hapticsEnabled = hapticsEnabled,
                        onBack = { screen = Screen.SETTINGS },
                        onTextScaleChange = { lifecycleScope.launch { prefsStore.setTextScale(it) } },
                        onReduceMotionChange = { lifecycleScope.launch { prefsStore.setReduceMotion(it) } },
                        onHapticsChange = { lifecycleScope.launch { prefsStore.setHapticsEnabled(it) } }
                    )

                    Screen.SECRETS -> SecretsScreen(
                        secretNames = secretNames,
                        onBack = { screen = Screen.SETTINGS },
                        onAdd = { name, value ->
                            secretVault.set(name, value)
                            secretNames = secretVault.listNames().toList()
                        },
                        onDelete = { name ->
                            secretVault.delete(name)
                            secretNames = secretVault.listNames().toList()
                        }
                    )

                    Screen.ADVANCED -> AdvancedScreen(
                        codexHomePath = pathsHandle.codexHomeDir().absolutePath,
                        workspacePath = pathsHandle.workspaceDir(chatState.activeConversationId).absolutePath,
                        isEngineRunning = chatState.isRunning,
                        configTomlContent = configTomlText,
                        onBack = { screen = Screen.SETTINGS },
                        onStopEngine = { chatViewModel.stopRunning() }
                    )

                    Screen.ABOUT -> AboutScreen(
                        versionName = remember {
                            try { packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" }
                        },
                        onBack = { screen = Screen.SETTINGS }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // No-op: OAuth completion arrives via the local callback server
        // (CodexAuthManager.startCallbackServerAndAwaitCode), matching the
        // real upstream redirect_uri — not via an app deep link.
    }

    private fun launchLogin() {
        if (authManager.isLoginInFlight()) {
            // Fix: a prior version silently returned here with zero UI
            // feedback — if loginInFlight ever got stuck true (e.g. the
            // Activity was reclaimed by the OS mid-login while a blocking
            // socket.accept() call couldn't respond to cancellation), the
            // login button would appear completely dead with no
            // explanation. Now the user at least sees why, and
            // isLoginInFlight() itself self-heals after a stale timeout
            // (see CodexAuthManager) so this state can't persist forever.
            onboardingViewModel.onLoginError("Zaten devam eden bir giriş denemesi var, birkaç saniye içinde tekrar deneyin")
            return
        }
        onboardingViewModel.onLoginStarted()
        lifecycleScope.launch {
            val serverReady = kotlinx.coroutines.CompletableDeferred<Unit>()
            lifecycleScope.launch {
                val result = authManager.startCallbackServerAndAwaitCode(onBound = { serverReady.complete(Unit) })
                result.onSuccess { (code, state) ->
                    val loginResult = authManager.completeLogin(code, state)
                    loginResult.onSuccess {
                        // writeAuthJsonForNativeRuntime now returns a Result: a prior
                        // version called this and unconditionally reported success
                        // right after, even if the write silently failed — which
                        // meant the UI could show "logged in" while codex.bin's own
                        // auth.json was missing or incomplete, so chat then failed
                        // with no clear reason. Now a failed write is a visible,
                        // specific error instead of a silent dead end.
                        authManager.writeAuthJsonForNativeRuntime(pathsHandle)
                            .onSuccess { onboardingViewModel.onLoginSuccess() }
                            .onFailure { err ->
                                onboardingViewModel.onLoginError("Giriş yapıldı ama yerel motora kaydedilemedi: ${err.message}")
                            }
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
            try {
                val customTabsIntent = CustomTabsIntent.Builder().build()
                customTabsIntent.launchUrl(this@MainActivity, Uri.parse(url))
            } catch (e: Exception) {
                // No browser available to handle the Custom Tab intent —
                // a prior version let this throw uncaught, which could
                // silently abort the whole login attempt with no
                // explanation while the callback server kept waiting.
                authManager.stopCallbackServer()
                onboardingViewModel.onLoginError("Giriş sayfası açılamadı: cihazda bir tarayıcı bulunamadı")
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

    override fun onDestroy() {
        super.onDestroy()
        authManager.stopCallbackServer()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
