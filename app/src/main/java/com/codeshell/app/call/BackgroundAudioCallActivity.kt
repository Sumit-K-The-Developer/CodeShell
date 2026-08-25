package com.codeshell.app.call

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.codeshell.app.data.model.CallType
import com.codeshell.app.messaging.TerminalNotificationManager

/** Invisible host used when an audio call is accepted from its notification. */
class BackgroundAudioCallActivity : ComponentActivity() {

    private var callId: String = ""
    private var chatId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This Activity is only a lifecycle host for the background call.
        // It must never intercept touches intended for the visible screen.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )
        callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        if (callId.isBlank() || chatId.isBlank()) {
            finish()
            return
        }
        TerminalNotificationManager.clear(this, chatId, isCall = true)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_MICROPHONE,
            )
        } else {
            startBackgroundCall()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startBackgroundCall()
        } else {
            finish()
        }
    }

    private fun startBackgroundCall() {
        val serviceIntent = Intent(this, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_START_BACKGROUND_AUDIO
            putExtra(CallForegroundService.EXTRA_CALL_ID, callId)
            putExtra(CallForegroundService.EXTRA_CHAT_ID, chatId)
            putExtra(CallForegroundService.EXTRA_CALL_TYPE, CallType.VOICE.name)
            putExtra(CallForegroundService.EXTRA_SHOW_CALL_CONTROLS, true)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        finish()
    }

    companion object {
        const val EXTRA_CHAT_ID = "background_chat_id"
        const val EXTRA_CALL_ID = "background_call_id"
        private const val REQUEST_MICROPHONE = 8101
    }
}
