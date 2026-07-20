package com.codeshell.app.call

import android.content.Context
import android.os.PowerManager

class CallProximityController(context: Context) {

    private val powerManager =
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var wakeLock: PowerManager.WakeLock? = null

    @Suppress("DEPRECATION")
    fun start() {
        if (wakeLock?.isHeld == true) return
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "CodeShell:CallProximity",
            ).apply {
                acquire(MAX_CALL_MS)
            }
        }
    }

    fun stop() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wakeLock = null
    }

    companion object {
        private const val MAX_CALL_MS = 3 * 60 * 60 * 1000L
    }
}
