package com.codeshell.app.ui.util

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.codeshell.app.BuildConfig
import com.codeshell.app.util.findActivity

@Composable
fun SecureScreenEffect(secure: Boolean) {
    val context = LocalContext.current
    val captureEnabled by DeviceCaptureState.enabled.collectAsStateWithLifecycle()
    DisposableEffect(secure, captureEnabled) {
        val window = context.findActivity()?.window
        if (secure && !captureEnabled && !BuildConfig.ALLOW_DEMO_CAPTURE) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (secure) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
