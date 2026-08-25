package com.codeshell.app.ui.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Session-only switch for the user's intentional device screen capture. */
object DeviceCaptureState {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun toggle(): Boolean {
        val next = !_enabled.value
        _enabled.value = next
        return next
    }

    fun disable() {
        _enabled.value = false
    }
}
