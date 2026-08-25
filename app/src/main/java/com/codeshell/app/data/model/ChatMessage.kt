package com.codeshell.app.data.model

import com.google.firebase.Timestamp
enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    SESSION,
    SYSTEM;

    companion object {
        fun from(value: String?): MessageType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TEXT
        }
    }
}

data class MessageReply(
    val messageId: String,
    val senderId: String,
    val type: MessageType,
    val preview: String,
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val type: MessageType = MessageType.TEXT,
    val content: String = "",
    val fileName: String? = null,
    val fileSize: Long? = null,
    val caption: String? = null,
    val timestamp: Timestamp? = null,
    val delivered: Boolean = false,
    val read: Boolean = false,
    val deleted: Boolean = false,
    val seenBy: Set<String> = emptySet(),
    val editedAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,
    val unseenExpiresAt: Timestamp? = null,
    val reply: MessageReply? = null,
    val hiddenBy: Set<String> = emptySet(),
    val liked: Boolean = false,
    val callInitiatorId: String? = null,
    val callId: String? = null,
) {
    fun isHiddenFor(userId: String): Boolean = userId in hiddenBy

    fun seenByBoth(participantIds: Collection<String>): Boolean {
        if (participantIds.isEmpty()) return false
        return participantIds.all { it in seenBy }
    }

    val displayText: String
        get() = when {
            deleted -> DELETED_PLACEHOLDER
            type == MessageType.TEXT -> content
            type == MessageType.FILE -> fileName?.takeIf { it.isNotBlank() } ?: "Attachment"
            type == MessageType.AUDIO -> "Voice output"
            type == MessageType.IMAGE -> "Image output"
            type == MessageType.VIDEO -> "Video output"
            type == MessageType.SESSION -> sessionLogPreview(
                if (fileName.equals(CallType.VIDEO.name, ignoreCase = true)) CallType.VIDEO else CallType.VOICE,
            )
            type == MessageType.SYSTEM -> ""
            else -> content
        }

    val mediaUrl: String?
        get() = if (deleted || type == MessageType.TEXT) null else content.takeIf { it.isNotBlank() }

    companion object {
        const val DELETED_PLACEHOLDER = "This line was removed for everyone."
    }
}

fun lastMessagePreview(type: MessageType, fileName: String?): String {
    return when (type) {
        MessageType.TEXT -> ""
        MessageType.IMAGE -> "Image output"
        MessageType.VIDEO -> "Video output"
        MessageType.AUDIO -> "Voice output"
        MessageType.FILE -> fileName?.takeIf { it.isNotBlank() } ?: "File attachment"
        MessageType.SESSION -> sessionLogPreview(CallType.VOICE)
        MessageType.SYSTEM -> ""
    }
}

fun replyPreviewForMessage(message: ChatMessage, maxLength: Int = 80): String {
    val text = when (message.type) {
        MessageType.TEXT -> message.content.trim()
        MessageType.SESSION -> message.displayText
        else -> message.displayText
    }
    if (text.length <= maxLength) return text
    val truncated = text.take(maxLength - 1)
    val lastSpace = truncated.lastIndexOf(' ')
    return if (lastSpace > maxLength / 2) {
        truncated.take(lastSpace).trimEnd() + "…"
    } else {
        truncated + "…"
    }
}
