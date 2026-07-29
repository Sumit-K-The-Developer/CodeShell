package com.codeshell.app.ui.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton that tracks the state of a vault media save operation.
 *
 * The save operation runs on the process-global
 * [com.codeshell.app.util.BackgroundWorkCoordinator] so it survives the
 * originating ViewModel / Activity being cleared (even when the user leaves the
 * screen or closes the app) until it completes or is cancelled manually. No
 * progress notification is posted; the active screen observes this state for
 * in-app progress only.
 */
object VaultSaveTracker {

    private val _state = MutableStateFlow<VaultSaveState>(VaultSaveState.Idle)
    val state: StateFlow<VaultSaveState> = _state.asStateFlow()

    fun saving(progress: Float) {
        _state.value = VaultSaveState.Saving(progress.coerceIn(0f, 1f))
    }

    fun success(fileName: String) {
        _state.value = VaultSaveState.Success(fileName)
    }

    fun error(message: String) {
        _state.value = VaultSaveState.Error(message)
    }

    fun reset() {
        _state.value = VaultSaveState.Idle
    }
}

sealed interface VaultSaveState {
    object Idle : VaultSaveState
    data class Saving(val progress: Float) : VaultSaveState
    data class Success(val fileName: String) : VaultSaveState
    data class Error(val message: String) : VaultSaveState
}
