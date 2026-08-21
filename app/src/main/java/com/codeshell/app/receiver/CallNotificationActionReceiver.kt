package com.codeshell.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codeshell.app.call.PendingCallStore
import com.codeshell.app.call.CallForegroundServiceController
import com.codeshell.app.data.model.CallType
import com.codeshell.app.data.model.VoiceCallState
import com.codeshell.app.data.repository.CallRepository
import com.codeshell.app.messaging.TerminalNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val callId = intent.getStringExtra(TerminalNotificationManager.EXTRA_CALL_ID) ?: return
        val chatId = intent.getStringExtra(TerminalNotificationManager.EXTRA_CHAT_ID).orEmpty()
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (action == TerminalNotificationManager.ACTION_CONNECT_CALL) {
                    val callType = CallType.fromStorage(
                        intent.getStringExtra(TerminalNotificationManager.EXTRA_CALL_TYPE),
                    )
                    if (callType == CallType.VOICE) {
                        // Voice calls can be accepted from the notification without
                        // bringing the disguise/app UI to the foreground.
                        CallRepository().updateCallState(callId, VoiceCallState.ACCEPTED)
                    } else {
                        // Video Connect is intentionally a screen-opening action;
                        // camera preview and permission handling must remain visible.
                        PendingCallStore.clear(appContext)
                        PendingCallStore.save(appContext, chatId, callId)
                        val openIntent = Intent(appContext, com.codeshell.app.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(TerminalNotificationManager.EXTRA_DIRECT_CALL, true)
                            putExtra(TerminalNotificationManager.EXTRA_CHAT_ID, chatId)
                            putExtra(TerminalNotificationManager.EXTRA_CALL_ID, callId)
                        }
                        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        appContext.startActivity(openIntent)
                    }
                } else if (action == TerminalNotificationManager.ACTION_REJECT_CALL) {
                    CallRepository().updateCallState(
                        callId,
                        VoiceCallState.DECLINED,
                        endedReason = "declined_from_notification",
                    )
                    PendingCallStore.clear(appContext)
                } else if (action == TerminalNotificationManager.ACTION_DISCONNECT_CALL) {
                    CallRepository().updateCallState(
                        callId,
                        VoiceCallState.ENDED,
                        endedReason = "ended_from_notification",
                    )
                    CallForegroundServiceController.stop(appContext)
                }
                if (chatId.isNotBlank()) {
                    TerminalNotificationManager.clear(appContext, chatId, isCall = true)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
