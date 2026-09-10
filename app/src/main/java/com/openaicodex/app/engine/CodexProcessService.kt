package com.openaicodex.app.engine

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.openaicodex.app.CodexApplication
import com.openaicodex.app.MainActivity
import com.openaicodex.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Real Android foreground service. Without this, Android can and will kill
 * the process running codex.bin the moment the Activity goes to the
 * background — this class is the actual implementation behind the
 * manifest declaration.
 *
 * Fixes over a prior version:
 *  - Active task metadata (which conversation ID the running task belongs
 *    to, and whether a task is running at all) now lives in a StateFlow
 *    owned by the SERVICE, not just in a ViewModel field. If Activity/
 *    ViewModel is destroyed and recreated (rotation, process death, low
 *    memory), the new ChatViewModel can read `activeTaskState` on rebind
 *    and correctly resume showing progress for the right conversation
 *    instead of silently losing track of it.
 *  - runTask() now returns Boolean: false if a task was already running
 *    and this call was rejected, so the caller can't show "running" state
 *    for a task that was actually never started.
 *  - cancelTask() now also attempts to kill any child processes spawned
 *    by codex.bin (it can itself launch shell subprocesses for command
 *    execution) via Process#descendants() on API 26+, not just the direct
 *    child process handle.
 *  - START_STICKY replaced with START_NOT_STICKY: this service cannot
 *    actually resume an in-flight Codex turn after the OS kills and
 *    restarts it (the child process and all its state are gone), so
 *    claiming STICKY behavior would be a false recovery promise.
 */
class CodexProcessService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob())
    private var activeJob: Job? = null

    lateinit var runtime: CodexNativeRuntime
        private set

    private val _events = MutableSharedFlow<CodexEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<CodexEvent> = _events

    data class ActiveTaskState(val conversationId: String?, val isRunning: Boolean)

    private val _activeTaskState = MutableStateFlow(ActiveTaskState(conversationId = null, isRunning = false))
    val activeTaskState: StateFlow<ActiveTaskState> = _activeTaskState

    inner class LocalBinder : Binder() {
        fun getService(): CodexProcessService = this@CodexProcessService
    }

    override fun onCreate() {
        super.onCreate()
        runtime = CodexNativeRuntime(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Codex hazır"))
        // See class doc: this service cannot meaningfully resume an
        // in-flight native process after an OS-forced restart, so we do
        // not claim STICKY recovery semantics we can't back up.
        return START_NOT_STICKY
    }

    /**
     * Starts a task in this service's own coroutine scope, surviving
     * Activity recreation. Returns true if the task was actually started,
     * false if a task was already running and this call was rejected —
     * callers must not show "running" UI on a false return.
     */
    fun runTask(conversationId: String, args: List<String>, extraEnv: Map<String, String> = emptyMap()): Boolean {
        if (_activeTaskState.value.isRunning) return false
        _activeTaskState.value = ActiveTaskState(conversationId = conversationId, isRunning = true)
        updateNotification("Codex çalışıyor...")
        activeJob = serviceScope.launch {
            runtime.launch(args, extraEnv, conversationId).collect { event ->
                _events.emit(event)
                when (event) {
                    is CodexEvent.Exited -> {
                        _activeTaskState.value = _activeTaskState.value.copy(isRunning = false)
                        val success = event.code == 0
                        updateNotification(if (success) "Codex görevi tamamlandı" else "Codex görevi hata ile bitti (${event.code})")
                        postTaskCompleteAlert(conversationId, success)
                    }
                    is CodexEvent.Failed -> {
                        _activeTaskState.value = _activeTaskState.value.copy(isRunning = false)
                        updateNotification("Codex başlatılamadı")
                        postTaskCompleteAlert(conversationId, success = false)
                    }
                    else -> Unit
                }
            }
        }
        return true
    }

    /**
     * One-shot alert distinct from the ongoing "Codex is running" status
     * notification: this fires exactly once per finished task, on its own
     * notification id (derived from the conversation id, so completions in
     * different chats don't overwrite each other's alert), and uses a
     * DEFAULT-importance channel so it can actually make a sound / heads-up
     * instead of sitting silently like the ongoing one.
     */
    private fun postTaskCompleteAlert(conversationId: String, success: Boolean) {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_CONVERSATION_ID, conversationId)
        }
        val requestCode = conversationId.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CodexApplication.TASK_COMPLETE_CHANNEL_ID)
            .setContentTitle(if (success) "Görev tamamlandı" else "Görev başarısız oldu")
            .setContentText(if (success) "Codex görevi bitirdi." else "Codex görevi tamamlayamadı.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        // Distinct id per conversation (offset away from the ongoing
        // status notification's fixed id) so this never collides with it.
        manager.notify(TASK_COMPLETE_NOTIFICATION_ID_OFFSET + (requestCode and 0x0FFFFFFF), notification)
    }

    fun cancelTask() {
        activeJob?.cancel()
        runtime.destroyWithChildren()
        _activeTaskState.value = _activeTaskState.value.copy(isRunning = false)
        updateNotification("Codex durduruldu")
    }

    fun isRunning(): Boolean = _activeTaskState.value.isRunning

    /** conversationId the currently-running (or most-recently-run) task belongs to, if any. */
    fun activeConversationId(): String? = _activeTaskState.value.conversationId

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CodexApplication.CHANNEL_ID)
            .setContentTitle("Codex")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(_activeTaskState.value.isRunning)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        runtime.destroyWithChildren()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 4201
        const val TASK_COMPLETE_NOTIFICATION_ID_OFFSET = 50000
    }
}
