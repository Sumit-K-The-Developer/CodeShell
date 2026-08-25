package com.codeshell.app.call

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.codeshell.app.data.model.CallType

object CallForegroundServiceController {
    fun start(
        context: Context,
        callType: CallType,
        isScreenSharing: Boolean = false,
        isScreenSharePaused: Boolean = false,
        callId: String? = null,
        chatId: String? = null,
        callStartedAt: Long? = null,
        showCallControls: Boolean = false,
    ) {
        if (!callId.isNullOrBlank() && !chatId.isNullOrBlank()) {
            ActiveCallSessionStore.set(callId, chatId, callType)
        }
        val intent = Intent(context, CallForegroundService::class.java).apply {
            putExtra(CallForegroundService.EXTRA_CALL_TYPE, callType.name)
            putExtra(CallForegroundService.EXTRA_SCREEN_SHARING, isScreenSharing)
            putExtra(CallForegroundService.EXTRA_SCREEN_SHARE_PAUSED, isScreenSharePaused)
            callId?.let { putExtra(CallForegroundService.EXTRA_CALL_ID, it) }
            chatId?.let { putExtra(CallForegroundService.EXTRA_CHAT_ID, it) }
            callStartedAt?.let { putExtra(CallForegroundService.EXTRA_CALL_STARTED_AT, it) }
            putExtra(CallForegroundService.EXTRA_SHOW_CALL_CONTROLS, showCallControls)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        ActiveCallSessionStore.clear()
        context.stopService(Intent(context, CallForegroundService::class.java))
    }
}
