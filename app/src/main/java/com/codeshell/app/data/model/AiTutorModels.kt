package com.codeshell.app.data.model

import java.util.UUID

enum class AiTutorEntrySource {
    FAB,
    DISGUISE,
    BOT,
    NEWS_ARTICLE,
}

enum class AiTutorMessageRole {
    USER,
    ASSISTANT,
    WELCOME,
}

data class AiTutorMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: AiTutorMessageRole,
    val content: String,
    val displayedContent: String = content,
    val isTyping: Boolean = false,
    val isError: Boolean = false,
)
