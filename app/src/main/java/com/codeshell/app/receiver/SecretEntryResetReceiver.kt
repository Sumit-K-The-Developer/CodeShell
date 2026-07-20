package com.codeshell.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.scheduler.SecretEntryResetScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SecretEntryResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent?.action) {
                    ACTION_DAILY_RESET -> {
                        AppPreferences(context.applicationContext).applyDailySecretEntryResetIfDue()
                        SecretEntryResetScheduler.scheduleNextReset(context.applicationContext)
                    }
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        SecretEntryResetScheduler.scheduleNextReset(context.applicationContext)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DAILY_RESET = "com.codeshell.app.action.SECRET_ENTRY_DAILY_RESET"
    }
}
