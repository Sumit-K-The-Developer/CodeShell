package com.codeshell.app.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-global long-lived coroutine scope for background media work that must
 * outlive the originating screen / ViewModel (and the Activity):
 *
 *  - saving chat / picker media to the vault
 *  - sending media chats (uploading + publishing)
 *  - downloading / saving media out of a chat
 *
 * Because the scope is tied to the application process (a SupervisorJob on
 * Dispatchers.IO), an in-flight operation keeps running after the user leaves
 * the screen or closes the app, until it either completes or is cancelled
 * manually. Transfer launches also acquire a neutral foreground service so the
 * process is kept alive after the task is removed from recents.
 */
object BackgroundWorkCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Launch a background unit of work. Use the returned [Job] to cancel the
     * work manually (it is detached from the calling ViewModel / Activity
     * lifecycle on purpose).
     */
    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    /** Launches work while a neutral foreground notification keeps the process alive. */
    fun launch(context: Context, block: suspend CoroutineScope.() -> Unit): Job {
        val appContext = context.applicationContext
        BackgroundTransferForegroundService.acquire(appContext)
        return scope.launch {
            try {
                block()
            } finally {
                BackgroundTransferForegroundService.release(appContext)
            }
        }
    }
}
