package com.codeshell.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.codeshell.app.receiver.SecretEntryResetReceiver
import com.codeshell.app.util.SecretEntryResetTime

object SecretEntryResetScheduler {

    private const val REQUEST_CODE = 7703

    fun scheduleNextReset(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SecretEntryResetTime.nextResetBoundaryAfter()
        val pendingIntent = resetPendingIntent(context)

        alarmManager.cancel(pendingIntent)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent,
        )
    }

    fun resetPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SecretEntryResetReceiver::class.java).apply {
            action = SecretEntryResetReceiver.ACTION_DAILY_RESET
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
