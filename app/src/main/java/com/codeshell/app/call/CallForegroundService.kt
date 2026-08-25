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
import com.codeshell.app.data.repository.AuthRepository
import com.codeshell.app.data.repository.CallRepository
import com.codeshell.app.data.repository.UserRepository
import com.codeshell.app.data.model.CallType
import com.codeshell.app.receiver.ScreenShareActionReceiver
import com.codeshell.app.ui.call.CallViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var backgroundCallViewModel: CallViewModel? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callType = CallType.fromStorage(intent?.getStringExtra(EXTRA_CALL_TYPE))
        val isScreenSharing = intent?.getBooleanExtra(EXTRA_SCREEN_SHARING, false) == true
        val isScreenSharePaused = intent?.getBooleanExtra(EXTRA_SCREEN_SHARE_PAUSED, false) == true
        val callId = intent?.getStringExtra(EXTRA_CALL_ID)
        val chatId = intent?.getStringExtra(EXTRA_CHAT_ID)
        val callStartedAt = intent?.getLongExtra(EXTRA_CALL_STARTED_AT, 0L)?.takeIf { it > 0L }
        val showCallControls = intent?.getBooleanExtra(EXTRA_SHOW_CALL_CONTROLS, false) == true
        val notification = buildNotification(
            isScreenSharing = isScreenSharing,
            isScreenSharePaused = isScreenSharePaused,
            callId = callId,
            chatId = chatId,
            callType = callType,
            callStartedAt = callStartedAt,
            showCallControls = showCallControls,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType(callType, isScreenSharing))
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (intent?.action == ACTION_START_BACKGROUND_AUDIO && callType == CallType.VOICE &&
            !callId.isNullOrBlank() && !chatId.isNullOrBlank()
        ) {
            startBackgroundAudioCall(callId, chatId)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ActiveCallSessionStore.clear()
        backgroundCallViewModel?.clearForServiceShutdown()
        backgroundCallViewModel = null
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startBackgroundAudioCall(callId: String, chatId: String) {
        if (backgroundCallViewModel != null) return
        ActiveCallSessionStore.set(callId, chatId, CallType.VOICE)
        // Reconnect the visible chat tile and caller UI to this same call when
        // the ongoing notification is opened later.
        PendingCallStore.save(applicationContext, chatId, callId)
        serviceScope.launch {
            var userId: String? = null
            repeat(25) {
                userId = AuthRepository().currentUser?.uid
                if (!userId.isNullOrBlank()) return@repeat
                delay(200L)
            }
            val currentUserId = userId ?: run {
                stopSelf()
                return@launch
            }
            var call = CallRepository().getCall(callId).getOrNull()
            repeat(15) {
                if (call != null) return@repeat
                delay(200L)
                call = CallRepository().getCall(callId).getOrNull()
            }
            val currentCall = call
            if (currentCall == null || currentCall.chatId != chatId) {
                stopSelf()
                return@launch
            }
            val otherUserId = currentCall.otherUserId(currentUserId)
            val otherName = UserRepository().getUsername(otherUserId).getOrNull().orEmpty()
            val viewModel = CallViewModel(application)
            backgroundCallViewModel = viewModel
            viewModel.bindChat(chatId, currentUserId, otherUserId, otherName, restorePendingCall = false)
            viewModel.acceptIncomingCallInBackground(callId)
        }
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
        val ongoingCallChannel = NotificationChannel(
            ONGOING_CALL_CHANNEL_ID,
            getString(R.string.call_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.call_notification_channel_desc)
            setShowBadge(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(learningChannel)
        manager?.createNotificationChannel(screenShareChannel)
        manager?.createNotificationChannel(ongoingCallChannel)
    }

    private fun buildNotification(
        isScreenSharing: Boolean,
        isScreenSharePaused: Boolean,
        callId: String?,
        chatId: String?,
        callType: CallType,
        callStartedAt: Long?,
        showCallControls: Boolean,
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!isScreenSharing && showCallControls &&
                !callId.isNullOrBlank() && !chatId.isNullOrBlank()
            ) {
                // Direct caller-screen routing is available only with the
                // user's call-notification controls enabled.
                putExtra(com.codeshell.app.messaging.TerminalNotificationManager.EXTRA_DIRECT_CALL, true)
                putExtra(com.codeshell.app.messaging.TerminalNotificationManager.EXTRA_CHAT_ID, chatId)
                putExtra(com.codeshell.app.messaging.TerminalNotificationManager.EXTRA_CALL_ID, callId)
                putExtra(com.codeshell.app.messaging.TerminalNotificationManager.EXTRA_CALL_TYPE, callType.name)
            } else if (!isScreenSharing) {
                putExtra(com.codeshell.app.messaging.TerminalNotificationManager.EXTRA_OPEN_LEARNING, true)
            }
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
            when {
                isScreenSharing -> SCREEN_SHARE_CHANNEL_ID
                showCallControls -> ONGOING_CALL_CHANNEL_ID
                else -> CHANNEL_ID
            },
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
        if (!isScreenSharing && showCallControls && !callId.isNullOrBlank() && !chatId.isNullOrBlank()) {
            builder
                .setUsesChronometer(callStartedAt != null)
                .setWhen(callStartedAt ?: System.currentTimeMillis())
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.call_notification_disconnect),
                    callActionIntent(callId, chatId, callType),
                )
        }
        return builder.build()
    }

    private fun callActionIntent(callId: String, chatId: String, callType: CallType): PendingIntent {
        val intent = Intent(this, com.codeshell.app.receiver.CallNotificationActionReceiver::class.java).apply {
            action = com.codeshell.app.messaging.TerminalNotificationManager.ACTION_DISCONNECT_CALL
            putExtra(com.codeshell.app.messaging.TerminalNotificationManager.EXTRA_CALL_ID, callId)
            putExtra(com.codeshell.app.messaging.TerminalNotificationManager.EXTRA_CHAT_ID, chatId)
            putExtra(com.codeshell.app.messaging.TerminalNotificationManager.EXTRA_CALL_TYPE, callType.name)
        }
        return PendingIntent.getBroadcast(
            this,
            20_003,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_CALL_STARTED_AT = "extra_call_started_at"
        const val EXTRA_SHOW_CALL_CONTROLS = "extra_show_call_controls"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_SCREEN_SHARING = "extra_screen_sharing"
        const val EXTRA_SCREEN_SHARE_PAUSED = "extra_screen_share_paused"
        const val ACTION_STOP_SCREEN_SHARE = "com.codeshell.app.action.STOP_SCREEN_SHARE"
        const val ACTION_TOGGLE_SCREEN_SHARE_PAUSE = "com.codeshell.app.action.TOGGLE_SCREEN_SHARE_PAUSE"
        private const val CHANNEL_ID = "learning_session"
        private const val SCREEN_SHARE_CHANNEL_ID = "screen_share_session"
        private const val ONGOING_CALL_CHANNEL_ID = "ongoing_call_session"
        private const val NOTIFICATION_ID = 7001
    }
}
