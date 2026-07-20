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
    ) {
        val intent = Intent(context, CallForegroundService::class.java).apply {
            putExtra(CallForegroundService.EXTRA_CALL_TYPE, callType.name)
            putExtra(CallForegroundService.EXTRA_SCREEN_SHARING, isScreenSharing)
            putExtra(CallForegroundService.EXTRA_SCREEN_SHARE_PAUSED, isScreenSharePaused)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, CallForegroundService::class.java))
    }
}
