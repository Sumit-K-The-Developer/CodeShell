package com.codeshell.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codeshell.app.MainActivity

/**
 * Handles the secret dialer code *#7777#* (default host 7777).
 *
 * Platform note: [android.provider.Telephony.SECRET_CODE] broadcasts are blocked or
 * unreliable on ColorOS 13+, some Samsung builds, and other OEM dialers. The hidden
 * CLI FAB inside the learning screen is the primary entry fallback on those devices.
 *
 * User-configurable dialer codes will be supported in Phase 6 via dynamic receiver
 * re-registration in Settings.
 */
class SecretCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SECRET_CODE_ACTION) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_FROM_SECRET_CODE, true)
        }
        context.startActivity(launchIntent)
    }

    companion object {
        const val SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE"
    }
}
