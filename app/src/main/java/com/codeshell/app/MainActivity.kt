package com.codeshell.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.data.model.ChatInterfaceStyle
import com.codeshell.app.data.model.CallType
import com.codeshell.app.call.PendingCallStore
import com.codeshell.app.messaging.PendingChatNavigation
import com.codeshell.app.messaging.PendingNewsArticle
import com.codeshell.app.messaging.PendingSharedContent
import com.codeshell.app.messaging.SharedContent
import com.codeshell.app.messaging.TerminalNotificationManager
import com.codeshell.app.messaging.NotificationVisibilityStore
import com.codeshell.app.navigation.CodeShellNavHost
import com.codeshell.app.util.SecureExitGuard
import com.codeshell.app.ui.theme.CodeShellTheme
import com.codeshell.app.ui.version.VersionGateOverlay
import com.codeshell.app.ui.vault.VaultAccessSession
import com.codeshell.app.ui.version.VersionGateViewModel
import com.codeshell.app.ui.version.rememberExitAppAction
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), ActivityCompat.OnRequestPermissionsResultCallback {

    private lateinit var appPreferences: AppPreferences
    private var hideOnHomePress = false
    private var terminateOnLock = false
    private var secureTerminalOnly = true
    private var disconnectOnDeviceLock = true
    private var lockReceiverRegistered = false

    /** Outgoing call requested before the platform permission activity opened. */
    var pendingOutgoingCallType: CallType? = null

    /** Updated from [CodeShellNavHost] when the visible route changes. */
    var isOnTerminalScreen = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                VaultAccessSession.lock()
                if (hasActiveCallForPrivacy() && disconnectOnDeviceLock) {
                    activeCallDeviceLockHandler?.invoke()
                    learningOpenTick++
                    return
                }
                if (terminateOnLock && !suppressTerminateOnLock && shouldApplySecureExit()) {
                    if (secureTerminalOnly) learningOpenTick++ else finishAffinity()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appPreferences = AppPreferences(applicationContext)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            ensureCallNotificationControlsDefault()
        }
        lifecycleScope.launch {
            appPreferences.hideOnHomePress.collect { enabled ->
                hideOnHomePress = enabled
            }
        }
        lifecycleScope.launch {
            appPreferences.terminateOnLock.collect { enabled ->
                terminateOnLock = enabled
                updateLockReceiverRegistration(enabled || disconnectOnDeviceLock)
            }
        }
        lifecycleScope.launch {
            appPreferences.secureTerminalOnly.collect { enabled ->
                secureTerminalOnly = enabled
            }
        }
        lifecycleScope.launch {
            appPreferences.disconnectOnDeviceLock.collect { enabled ->
                disconnectOnDeviceLock = enabled
                updateLockReceiverRegistration(terminateOnLock || enabled)
            }
        }

        if (intent?.getBooleanExtra(EXTRA_FROM_SECRET_CODE, false) == true) {
            intent.removeExtra(EXTRA_FROM_SECRET_CODE)
        }
        consumeStartupIntent(intent)

        setContent {
            val chatInterfaceStyle by appPreferences.chatInterfaceStyle.collectAsStateWithLifecycle(
                initialValue = ChatInterfaceStyle.DEFAULT,
            )
            CodeShellTheme(chatInterfaceStyle) {
                LaunchedEffect(Unit) {
                    requestNotificationPermissionOnFirstInstall()
                }
                val versionGateViewModel: VersionGateViewModel = viewModel()
                val versionState by versionGateViewModel.uiState.collectAsStateWithLifecycle()
                val exitApp = rememberExitAppAction()
                val navController = rememberNavController()

                var lastBackPressTime by remember { mutableStateOf(0L) }

                // Two presses within a short window exit the app. We gate on a
                // wall-clock timestamp rather than a mutable boolean + delayed
                // reset so that rapid presses reliably reach exitApp().
                BackHandler(enabled = versionState.canProceed) {
                    val now: Long = SystemClock.elapsedRealtime()
                    val last: Long = lastBackPressTime
                    if (now - last <= EXIT_BACK_WINDOW_MS) {
                        lastBackPressTime = 0L
                        exitApp()
                    } else {
                        lastBackPressTime = now
                        Toast.makeText(this@MainActivity, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show()
                    }
                }

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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            applySuppressHideOnHome(false)
            val granted = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                ensureCallNotificationControlsDefault()
            }
            val message = if (granted) {
                getString(R.string.notification_permission_enabled)
            } else {
                getString(R.string.notification_permission_disabled)
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun consumeStartupIntent(intent: Intent?) {
        if (consumeShareIntent(intent)) return
        consumeNotificationChatIntent(intent)
    }

    private fun requestNotificationPermissionOnFirstInstall() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ensureCallNotificationControlsDefault()
            return
        }

        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        if (preferences.getBoolean(NOTIFICATION_PERMISSION_REQUESTED, false)) return

        preferences.edit().putBoolean(NOTIFICATION_PERMISSION_REQUESTED, true).apply()
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            // Opening the system permission dialog triggers onUserLeaveHint().
            // Prevent the disguise's home-press exit logic from finishing us.
            applySuppressHideOnHome(true)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE,
            )
        } else {
            ensureCallNotificationControlsDefault()
        }
    }

    private fun ensureCallNotificationControlsDefault() {
        lifecycleScope.launch {
            appPreferences.ensureCallNotificationControlsDefault(notificationPermissionGranted = true)
        }
    }

    private fun consumeShareIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        val content = when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = intent.sharedStreamUri()
                val mimeType = intent.type?.lowercase().orEmpty()
                SharedContent(
                    text = sharedText,
                    videoUris = stream?.takeIf { mimeType.startsWith("video/") }?.let(::listOf).orEmpty(),
                    imageUris = stream?.takeIf { mimeType.startsWith("image/") }?.let(::listOf).orEmpty(),
                    fileUris = stream?.takeIf { isFileMimeType(mimeType) }?.let(::listOf).orEmpty(),
                )
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val streams = intent.sharedStreamUris()
                if (streams.isEmpty() && sharedText == null) return false
                val fallbackMime = intent.type?.lowercase().orEmpty()
                val videos = mutableListOf<Uri>()
                val images = mutableListOf<Uri>()
                val files = mutableListOf<Uri>()
                streams.forEach { uri ->
                    val mimeType = runCatching { contentResolver.getType(uri)?.lowercase().orEmpty() }
                        .getOrDefault("")
                        .takeIf { it.isNotBlank() } ?: fallbackMime
                    when {
                        mimeType.startsWith("video/") -> videos.add(uri)
                        mimeType.startsWith("image/") -> images.add(uri)
                        else -> files.add(uri)
                    }
                }
                SharedContent(
                    text = sharedText,
                    videoUris = videos,
                    imageUris = images,
                    fileUris = files,
                )
            }
            else -> return false
        }
        if (sharedText == null && !content.hasMedia) return false

        PendingSharedContent.set(content)
        intent.setAction(null)
        shareOpenTick++
        return true
    }

    private fun isFileMimeType(mimeType: String): Boolean =
        mimeType.startsWith("application/") ||
            (mimeType.startsWith("text/") && mimeType != "text/plain")

    private fun Intent.sharedStreamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

    private fun Intent.sharedStreamUris(): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }

    private fun consumeNotificationChatIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(TerminalNotificationManager.EXTRA_DIRECT_CALL, false)) {
            val chatId = intent.getStringExtra(TerminalNotificationManager.EXTRA_CHAT_ID).orEmpty()
            val callId = intent.getStringExtra(TerminalNotificationManager.EXTRA_CALL_ID).orEmpty()
            if (chatId.isNotBlank() && callId.isNotBlank()) {
                intent.removeExtra(TerminalNotificationManager.EXTRA_DIRECT_CALL)
                intent.removeExtra(TerminalNotificationManager.EXTRA_CHAT_ID)
                intent.removeExtra(TerminalNotificationManager.EXTRA_CALL_ID)
                PendingCallStore.save(applicationContext, chatId, callId)
                PendingChatNavigation.set(chatId)
                TerminalNotificationManager.clearAllForChat(applicationContext, chatId)
                notificationOpenTick++
                return
            }
        }

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

        val newsUrl = intent.getStringExtra(TerminalNotificationManager.EXTRA_NEWS_ARTICLE_URL)
        if (!newsUrl.isNullOrBlank()) {
            intent.removeExtra(TerminalNotificationManager.EXTRA_NEWS_ARTICLE_URL)
            val categoryId = intent.getStringExtra(TerminalNotificationManager.EXTRA_NEWS_CATEGORY_ID) ?: ""
            val regionCc = intent.getStringExtra(TerminalNotificationManager.EXTRA_NEWS_REGION_CC) ?: ""
            intent.removeExtra(TerminalNotificationManager.EXTRA_NEWS_CATEGORY_ID)
            intent.removeExtra(TerminalNotificationManager.EXTRA_NEWS_REGION_CC)
            PendingNewsArticle.set(newsUrl, categoryId, regionCc)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (hasActiveCallForPrivacy() && disconnectOnDeviceLock) {
            learningOpenTick++
            return
        }
        if (hideOnHomePress &&
            !suppressHideOnHome &&
            !SecureExitGuard.suppressHideOnHome &&
            shouldApplySecureExit()
        ) {
            if (secureTerminalOnly) learningOpenTick++ else finish()
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

    var isCallActiveForPrivacy = false
    var activeCallStateProvider: (() -> Boolean)? = null
    var activeCallDeviceLockHandler: (() -> Unit)? = null
    val disconnectOnDeviceLockForPrivacy: Boolean get() = disconnectOnDeviceLock

    private fun hasActiveCallForPrivacy(): Boolean =
        isCallActiveForPrivacy || activeCallStateProvider?.invoke() == true

    var notificationOpenTick by mutableIntStateOf(0)
        private set

    var learningOpenTick by mutableIntStateOf(0)
        private set

    var shareOpenTick by mutableIntStateOf(0)
        private set

    var vaultSendOpenTick by mutableIntStateOf(0)
        private set

    private fun shouldApplySecureExit(): Boolean =
        !secureTerminalOnly || isOnTerminalScreen

    override fun onStop() {
        NotificationVisibilityStore.setAppInForeground(false)
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        NotificationVisibilityStore.setAppInForeground(true)
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

        private const val PREFERENCES_NAME = "codeshell_first_launch"
        private const val NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

        /** Double-back-exit window in milliseconds. */
        private const val EXIT_BACK_WINDOW_MS = 2500L
    }
}
