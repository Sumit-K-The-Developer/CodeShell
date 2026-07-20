package com.codeshell.app.data.model

enum class SessionLogStatus {
    COMPLETED,
    MISSED,
    DECLINED,
    FAILED;

    companion object {
        fun fromStorage(value: String?): SessionLogStatus? {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}

fun ChatMessage.sessionLogStatus(): SessionLogStatus? =
    if (type == MessageType.SESSION) SessionLogStatus.fromStorage(content) else null

fun ChatMessage.sessionDurationSec(): Long? =
    if (type == MessageType.SESSION && fileSize != null && fileSize > 0) fileSize else null

fun ChatMessage.sessionCallType(): CallType =
    if (type == MessageType.SESSION && fileName.equals(CallType.VIDEO.name, ignoreCase = true)) {
        CallType.VIDEO
    } else {
        CallType.VOICE
    }

fun ChatMessage.sessionCallInitiatorId(): String? =
    if (type == MessageType.SESSION) callInitiatorId?.takeIf { it.isNotBlank() } else null

fun ChatMessage.sessionLogAlignAsMine(currentUserId: String): Boolean {
    val initiator = sessionCallInitiatorId() ?: senderId
    return initiator == currentUserId
}

fun sessionLogPreview(callType: CallType): String =
    when (callType) {
        CallType.VIDEO -> "Live Connect"
        CallType.VOICE -> "Voice Connect"
    }

fun formatSessionDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
