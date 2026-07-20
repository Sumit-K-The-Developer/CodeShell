package com.codeshell.app.call

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CallScreenShareActions {
    private val _events = MutableSharedFlow<ScreenShareAction>(extraBufferCapacity = 1)
    val events: SharedFlow<ScreenShareAction> = _events.asSharedFlow()

    fun emit(action: ScreenShareAction) {
        _events.tryEmit(action)
    }
}

enum class ScreenShareAction {
    STOP,
    TOGGLE_PAUSE,
}
