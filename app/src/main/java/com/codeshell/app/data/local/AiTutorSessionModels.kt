package com.codeshell.app.data.local

import com.codeshell.app.data.model.AiTutorMessage
import com.codeshell.app.data.model.AiTutorMessageRole
import com.codeshell.app.data.model.NewsAiArticleContext
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
    val articleContext: StoredNewsAiArticleContext? = null,
)

@Serializable
data class StoredNewsAiArticleContext(
    val title: String,
    val sourceName: String,
    val publishedAt: String,
    val url: String,
    val cachedText: String,
    val fullArticleFetched: Boolean,
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

fun NewsAiArticleContext.toStored(): StoredNewsAiArticleContext = StoredNewsAiArticleContext(
    title = title,
    sourceName = sourceName,
    publishedAt = publishedAt,
    url = url,
    cachedText = cachedText,
    fullArticleFetched = fullArticleFetched,
)

fun StoredNewsAiArticleContext.toDomain(): NewsAiArticleContext = NewsAiArticleContext(
    title = title,
    sourceName = sourceName,
    publishedAt = publishedAt,
    url = url,
    cachedText = cachedText,
    fullArticleFetched = fullArticleFetched,
)
