package com.codeshell.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codeshell.app.call.CallForegroundService
import com.codeshell.app.call.CallScreenShareActions
import com.codeshell.app.call.ScreenShareAction

class ScreenShareActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            CallForegroundService.ACTION_STOP_SCREEN_SHARE -> {
                CallScreenShareActions.emit(ScreenShareAction.STOP)
            }
            CallForegroundService.ACTION_TOGGLE_SCREEN_SHARE_PAUSE -> {
                CallScreenShareActions.emit(ScreenShareAction.TOGGLE_PAUSE)
            }
        }
    }
}
