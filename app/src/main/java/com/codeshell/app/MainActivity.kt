package com.codeshell.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.messaging.PendingChatNavigation
import com.codeshell.app.messaging.PendingSharedContent
import com.codeshell.app.messaging.SharedContent
import com.codeshell.app.messaging.TerminalNotificationManager
import com.codeshell.app.navigation.CodeShellNavHost
import com.codeshell.app.util.SecureExitGuard
import com.codeshell.app.ui.theme.CodeShellTheme
import com.codeshell.app.ui.version.VersionGateOverlay
import com.codeshell.app.ui.version.VersionGateViewModel
import com.codeshell.app.ui.version.rememberExitAppAction
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var appPreferences: AppPreferences
    private var hideOnHomePress = false
    private var terminateOnLock = false
    private var secureTerminalOnly = false
    private var lockReceiverRegistered = false

    /** Updated from [CodeShellNavHost] when the visible route changes. */
    var isOnTerminalScreen = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF &&
                terminateOnLock &&
                !suppressTerminateOnLock &&
                shouldApplySecureExit()
            ) {
                finishAffinity()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appPreferences = AppPreferences(applicationContext)
        lifecycleScope.launch {
            appPreferences.hideOnHomePress.collect { enabled ->
                hideOnHomePress = enabled
            }
        }
        lifecycleScope.launch {
            appPreferences.terminateOnLock.collect { enabled ->
                terminateOnLock = enabled
                updateLockReceiverRegistration(enabled)
            }
        }
        lifecycleScope.launch {
            appPreferences.secureTerminalOnly.collect { enabled ->
                secureTerminalOnly = enabled
            }
        }

        if (intent?.getBooleanExtra(EXTRA_FROM_SECRET_CODE, false) == true) {
            intent.removeExtra(EXTRA_FROM_SECRET_CODE)
        }
        consumeStartupIntent(intent)

        setContent {
            CodeShellTheme {
                val versionGateViewModel: VersionGateViewModel = viewModel()
                val versionState by versionGateViewModel.uiState.collectAsStateWithLifecycle()
                val exitApp = rememberExitAppAction()
                val navController = rememberNavController()

                Box(modifier = Modifier.fillMaxSize()) {
                    if (versionState.canProceed) {
                        CodeShellNavHost(
                            navController = navController,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    VersionGateOverlay(
                        uiState = versionState,
                        onDismissSoftUpdate = versionGateViewModel::dismissSoftUpdate,
                        onOpenStore = { versionGateViewModel.openStore(this@MainActivity) },
                        onExitApp = exitApp
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeStartupIntent(intent)
    }

    private fun consumeStartupIntent(intent: Intent?) {
        if (consumeShareIntent(intent)) return
        consumeNotificationChatIntent(intent)
    }

    private fun consumeShareIntent(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_SEND) return false
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        val sharedVideo = intent.sharedStreamUri()
            ?.takeIf { intent.type?.startsWith("video/") == true }
        if (sharedText == null && sharedVideo == null) return false

        PendingSharedContent.set(
            SharedContent(
                text = sharedText,
                videoUri = sharedVideo,
            )
        )
        intent.setAction(null)
        shareOpenTick++
        return true
    }

    private fun Intent.sharedStreamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

    private fun consumeNotificationChatIntent(intent: Intent?) {
        if (intent == null) return

        val openChatId = intent.getStringExtra(TerminalNotificationManager.EXTRA_OPEN_CHAT_ID)
        if (openChatId != null) {
            intent.removeExtra(TerminalNotificationManager.EXTRA_OPEN_CHAT_ID)
            PendingChatNavigation.set(openChatId)
            TerminalNotificationManager.clearAllForChat(applicationContext, openChatId)
            notificationOpenTick++
            return
        }

        if (intent.getBooleanExtra(TerminalNotificationManager.EXTRA_OPEN_LEARNING, false)) {
            intent.removeExtra(TerminalNotificationManager.EXTRA_OPEN_LEARNING)
            val chatId = intent.getStringExtra(TerminalNotificationManager.EXTRA_NOTIFICATION_CHAT_ID)
            intent.removeExtra(TerminalNotificationManager.EXTRA_NOTIFICATION_CHAT_ID)
            if (!chatId.isNullOrBlank()) {
                TerminalNotificationManager.clear(applicationContext, chatId, isCall = false)
            }
            learningOpenTick++
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (hideOnHomePress &&
            !suppressHideOnHome &&
            !SecureExitGuard.suppressHideOnHome &&
            shouldApplySecureExit()
        ) {
            finish()
        }
    }

    fun applySuppressHideOnHome(suppress: Boolean) {
        suppressHideOnHome = suppress
        SecureExitGuard.suppressHideOnHome = suppress
    }

    /** Prevents [onUserLeaveHint] from closing the app during system overlays (e.g. mic permission). */
    var suppressHideOnHome = false

    /** Prevents lock-screen exit while proximity blanks the display during a voice call. */
    var suppressTerminateOnLock = false

    var notificationOpenTick by mutableIntStateOf(0)
        private set

    var learningOpenTick by mutableIntStateOf(0)
        private set

    var shareOpenTick by mutableIntStateOf(0)
        private set

    private fun shouldApplySecureExit(): Boolean =
        !secureTerminalOnly || isOnTerminalScreen

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        if (lockReceiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            lockReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun updateLockReceiverRegistration(enabled: Boolean) {
        if (enabled && !lockReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenOffReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(screenOffReceiver, filter)
            }
            lockReceiverRegistered = true
        } else if (!enabled && lockReceiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            lockReceiverRegistered = false
        }
    }

    companion object {
        const val EXTRA_FROM_SECRET_CODE = "extra_from_secret_code"
    }
}
