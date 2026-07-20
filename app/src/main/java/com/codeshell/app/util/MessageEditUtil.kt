package com.codeshell.app.util

import com.codeshell.app.data.model.ChatMessage
import com.codeshell.app.data.model.MessageType
import com.google.firebase.Timestamp

object MessageEditUtil {
    const val EDIT_WINDOW_MS = 5 * 60_000L

    fun canEdit(message: ChatMessage, currentUserId: String?): Boolean {
        if (currentUserId == null || message.senderId != currentUserId) return false
        if (message.deleted || message.type != MessageType.TEXT) return false
        return isWithinEditWindow(message.timestamp)
    }

    fun isWithinEditWindow(timestamp: Timestamp?): Boolean {
        if (timestamp == null) return false
        val elapsed = System.currentTimeMillis() - timestamp.toDate().time
        return elapsed in 0..EDIT_WINDOW_MS
    }
}
