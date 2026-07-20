package com.codeshell.app.presence

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.codeshell.app.CodeShellApplication
import com.codeshell.app.data.repository.AuthRepository
import com.codeshell.app.data.repository.PresenceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PresenceCoordinator(
    private val application: Application,
    private val authRepository: AuthRepository = AuthRepository(),
    private val presenceRepository: PresenceRepository = PresenceRepository(),
) : DefaultLifecycleObserver {

    private var heartbeatJob: Job? = null

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        val userId = authRepository.currentUser?.uid ?: return
        val scope = (application as CodeShellApplication).applicationScope
        scope.launch {
            presenceRepository.markForeground(userId)
        }
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                authRepository.currentUser?.uid?.let { presenceRepository.touchPresence(it) }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        val userId = authRepository.currentUser?.uid ?: return
        (application as CodeShellApplication).applicationScope.launch {
            presenceRepository.markBackground(userId)
        }
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }
}
