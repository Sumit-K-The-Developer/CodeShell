package com.codeshell.app.ui.vault

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.codeshell.app.MainActivity
import com.codeshell.app.R
import com.codeshell.app.data.repository.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that downloads a media file from a URL and saves it to
 * the vault.
 *
 * Running in a foreground service guarantees that the download continues even
 * when the app is backgrounded or the originating screen / ViewModel is
 * destroyed.  A persistent notification shows the current progress.
 */
class VaultSaveService : Service() {

    private val vaultRepository by lazy { VaultRepository(this) }
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url == null) return START_NOT_STICKY

        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "media"
        val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "application/octet-stream"
        val sourceChatId = intent.getStringExtra(EXTRA_SOURCE_CHAT_ID)
        val sourceMessageId = intent.getStringExtra(EXTRA_SOURCE_MESSAGE_ID)

        VaultSaveTracker.saving(0f)
        startForeground(NOTIFICATION_ID, buildNotification(0, fileName))

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            vaultRepository.saveFromUrlWithProgress(
                url = url,
                fileName = fileName,
                mimeType = mimeType,
                sourceChatId = sourceChatId,
                sourceMessageId = sourceMessageId,
                onProgress = { completed, total ->
                    val progress = if (total > 0) {
                        (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    VaultSaveTracker.saving(progress)
                    updateNotification((progress * 100).toInt(), fileName)
                },
            ).onSuccess {
                VaultSaveTracker.success(fileName)
                updateNotification(100, fileName)
                // Brief delay so the user sees 100 % before the notification disappears
                delay(500)
                stopSelf()
            }.onFailure { error ->
                VaultSaveTracker.error(error.message ?: "Save failed")
                updateNotification(-1, fileName)
                delay(1500)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vault_save_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.vault_save_channel_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(progress: Int, fileName: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vault_save_notification_title))
            .setContentText(fileName)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress >= 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun updateNotification(progress: Int, fileName: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(progress, fileName))
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_MIME_TYPE = "extra_mime_type"
        const val EXTRA_SOURCE_CHAT_ID = "extra_source_chat_id"
        const val EXTRA_SOURCE_MESSAGE_ID = "extra_source_message_id"

        private const val CHANNEL_ID = "vault_save"
        private const val NOTIFICATION_ID = 8001
    }
}
