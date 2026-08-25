package com.codeshell.app.call

import com.codeshell.app.data.model.CallType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveCallSession(
    val callId: String,
    val chatId: String,
    val callType: CallType,
)

object ActiveCallSessionStore {
    private val _active = MutableStateFlow<ActiveCallSession?>(null)
    val active: StateFlow<ActiveCallSession?> = _active.asStateFlow()

    fun set(callId: String, chatId: String, callType: CallType) {
        if (callId.isBlank() || chatId.isBlank()) return
        _active.value = ActiveCallSession(callId, chatId, callType)
    }

    fun clear(callId: String? = null) {
        if (callId == null || _active.value?.callId == callId) {
            _active.value = null
        }
    }
}
