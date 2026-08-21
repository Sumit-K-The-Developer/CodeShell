package com.codeshell.app.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.codeshell.app.MainActivity
import com.codeshell.app.R
import com.codeshell.app.data.model.CallType
import com.codeshell.app.receiver.ScreenShareActionReceiver

class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callType = CallType.fromStorage(intent?.getStringExtra(EXTRA_CALL_TYPE))
        val isScreenSharing = intent?.getBooleanExtra(EXTRA_SCREEN_SHARING, false) == true
        val isScreenSharePaused = intent?.getBooleanExtra(EXTRA_SCREEN_SHARE_PAUSED, false) == true
        val notification = buildNotification(isScreenSharing, isScreenSharePaused)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType(callType, isScreenSharing))
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val learningChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.learning_session_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.learning_session_channel_desc)
            setShowBadge(false)
        }
        val screenShareChannel = NotificationChannel(
            SCREEN_SHARE_CHANNEL_ID,
            getString(R.string.call_screen_share_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.call_screen_share_notification_body)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(learningChannel)
        manager?.createNotificationChannel(screenShareChannel)
    }

    private fun buildNotification(
        isScreenSharing: Boolean,
        isScreenSharePaused: Boolean,
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (isScreenSharing) {
            getString(R.string.call_screen_share_notification_title)
        } else {
            getString(R.string.learning_session_active_title)
        }
        val body = when {
            isScreenSharePaused -> getString(R.string.call_screen_share_notification_paused)
            isScreenSharing -> getString(R.string.call_screen_share_notification_body)
            else -> getString(R.string.learning_session_active_body)
        }
        val builder = NotificationCompat.Builder(
            this,
            if (isScreenSharing) SCREEN_SHARE_CHANNEL_ID else CHANNEL_ID,
        )
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (isScreenSharing) {
            builder
                .addAction(
                    android.R.drawable.ic_media_pause,
                    if (isScreenSharePaused) {
                        getString(R.string.call_screen_share_resume_action)
                    } else {
                        getString(R.string.call_screen_share_pause_action)
                    },
                    screenShareActionIntent(this, ACTION_TOGGLE_SCREEN_SHARE_PAUSE, 41),
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.call_screen_share_stop_action),
                    screenShareActionIntent(this, ACTION_STOP_SCREEN_SHARE, 42),
                )
        }
        return builder.build()
    }

    private fun screenShareActionIntent(
        context: Context,
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, ScreenShareActionReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun foregroundServiceType(callType: CallType, isScreenSharing: Boolean): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (callType == CallType.VIDEO) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isScreenSharing) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        return type
    }

    companion object {
        const val ACTION_START_BACKGROUND_AUDIO = "com.codeshell.app.action.START_BACKGROUND_AUDIO"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_SCREEN_SHARING = "extra_screen_sharing"
        const val EXTRA_SCREEN_SHARE_PAUSED = "extra_screen_share_paused"
        const val ACTION_STOP_SCREEN_SHARE = "com.codeshell.app.action.STOP_SCREEN_SHARE"
        const val ACTION_TOGGLE_SCREEN_SHARE_PAUSE = "com.codeshell.app.action.TOGGLE_SCREEN_SHARE_PAUSE"
        private const val CHANNEL_ID = "learning_session"
        private const val SCREEN_SHARE_CHANNEL_ID = "screen_share_session"
        private const val NOTIFICATION_ID = 7001
    }
}
