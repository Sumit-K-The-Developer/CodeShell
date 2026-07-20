package com.codeshell.app.util

/**
 * Process-wide guard so secure-exit logic does not fire while a system permission
 * overlay is on screen (the flag must survive even if the activity reference is lost).
 */
object SecureExitGuard {
    @Volatile
    var suppressHideOnHome: Boolean = false
}
