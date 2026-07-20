package com.codeshell.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Starts a countdown when [enabled] is true. Any change in [resetKeys] restarts the timer.
 */
@Composable
fun InactivityTimeoutEffect(
    enabled: Boolean,
    timeoutMs: Long,
    onTimeout: () -> Unit,
    vararg resetKeys: Any?
) {
    LaunchedEffect(enabled, timeoutMs, *resetKeys) {
        if (!enabled) return@LaunchedEffect
        kotlinx.coroutines.delay(timeoutMs)
        onTimeout()
    }
}
