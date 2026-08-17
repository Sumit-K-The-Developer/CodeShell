package com.codeshell.app.util

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
import androidx.core.content.ContextCompat
import com.codeshell.app.MainActivity
import com.codeshell.app.R

/**
 * Keeps process-global transfers alive after the task is removed from recents.
 * The notification uses neutral shell/news wording and never exposes upload or
 * sending details.
 */
class BackgroundTransferForegroundService : Service() {

    private var activeTransfers = 0

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACQUIRE -> activeTransfers++
            ACTION_RELEASE -> {
                activeTransfers = (activeTransfers - 1).coerceAtLeast(0)
                if (activeTransfers == 0) stopSelfResult(startId)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.background_update_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.background_update_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.background_update_title))
            .setContentText(getString(R.string.background_update_body))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "background_updates"
        private const val NOTIFICATION_ID = 7778
        private const val ACTION_ACQUIRE = "com.codeshell.app.action.BACKGROUND_ACQUIRE"
        private const val ACTION_RELEASE = "com.codeshell.app.action.BACKGROUND_RELEASE"

        fun acquire(context: Context) {
            val intent = Intent(context, BackgroundTransferForegroundService::class.java)
                .setAction(ACTION_ACQUIRE)
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun release(context: Context) {
            val intent = Intent(context, BackgroundTransferForegroundService::class.java)
                .setAction(ACTION_RELEASE)
            context.applicationContext.startService(intent)
        }
    }
}
