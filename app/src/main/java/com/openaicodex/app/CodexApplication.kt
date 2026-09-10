package com.openaicodex.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class CodexApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Codex Background Tasks",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Codex is running a task in the background"
            }
            // Separate, higher-importance channel: fires a one-shot alert
            // (sound/heads-up) each time a task finishes, distinct from the
            // silent ongoing "Codex is running" notification above.
            val completeChannel = NotificationChannel(
                TASK_COMPLETE_CHANNEL_ID,
                "Codex Task Completed",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a Codex task finishes running"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
            manager?.createNotificationChannel(completeChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "codex_process_channel"
        const val TASK_COMPLETE_CHANNEL_ID = "codex_task_complete_channel"
    }
}
