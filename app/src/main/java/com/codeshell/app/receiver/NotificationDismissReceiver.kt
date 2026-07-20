package com.codeshell.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codeshell.app.messaging.TerminalNotificationManager

class NotificationDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val chatId = intent?.getStringExtra(TerminalNotificationManager.EXTRA_CHAT_ID) ?: return
        val isCall = intent.getBooleanExtra(TerminalNotificationManager.EXTRA_IS_CALL, false)
        TerminalNotificationManager.clear(context.applicationContext, chatId, isCall)
    }
}
