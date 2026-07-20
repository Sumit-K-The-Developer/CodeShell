package com.codeshell.app.data.local

import com.codeshell.app.data.model.AiTutorMessage
import com.codeshell.app.data.model.AiTutorMessageRole
import kotlinx.serialization.Serializable

@Serializable
data class StoredAiTutorMessage(
    val id: String,
    val role: String,
    val content: String,
)

@Serializable
data class StoredAiTutorSession(
    val savedAtMillis: Long,
    val messages: List<StoredAiTutorMessage>,
)

@Serializable
data class AiTutorSessionCollection(
    val sessions: Map<String, StoredAiTutorSession> = emptyMap(),
)

fun AiTutorMessage.toStored(): StoredAiTutorMessage = StoredAiTutorMessage(
    id = id,
    role = role.name,
    content = content,
)

fun StoredAiTutorMessage.toDomain(): AiTutorMessage? {
    val messageRole = runCatching { AiTutorMessageRole.valueOf(role) }.getOrNull() ?: return null
    return AiTutorMessage(
        id = id,
        role = messageRole,
        content = content,
        displayedContent = content,
        isTyping = false,
    )
}
