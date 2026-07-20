package com.codeshell.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.util.ChatInactivityTimeout

/**
 * Reads terminal inactivity prefs and fires [onTimeout] after idle time.
 * Pass user-activity signals via [resetKeys] (touches, scroll, text input, etc.).
 */
@Composable
fun TerminalInactivityEffect(
    onTimeout: () -> Unit,
    vararg resetKeys: Any?,
    enabled: Boolean = true,
    blocked: Boolean = false,
) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    var closeOnInactivity by remember { mutableStateOf(true) }
    var timeoutMs by remember { mutableLongStateOf(ChatInactivityTimeout.defaultMillis()) }

    LaunchedEffect(Unit) {
        appPreferences.closeChatOnInactivity.collect { closeOnInactivity = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.chatInactivityTimeoutSeconds.collect { seconds ->
            timeoutMs = ChatInactivityTimeout.toMillis(seconds)
        }
    }

    InactivityTimeoutEffect(
        enabled = enabled && closeOnInactivity && !blocked,
        timeoutMs = timeoutMs,
        onTimeout = onTimeout,
        *resetKeys,
    )
}

@Composable
fun rememberTerminalActivity(): Pair<() -> Unit, Int> {
    var generation by remember { mutableIntStateOf(0) }
    val bump: () -> Unit = remember {
        { generation++ }
    }
    return bump to generation
}
