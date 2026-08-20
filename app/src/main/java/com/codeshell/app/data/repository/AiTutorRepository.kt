package com.codeshell.app.data.repository

import com.codeshell.app.data.ai.AiChatMessage
import com.codeshell.app.data.ai.AiChatResult
import com.codeshell.app.data.ai.AiPromptBuilder
import com.codeshell.app.data.ai.AiService
import com.codeshell.app.data.model.AiTutorMessage
import com.codeshell.app.data.model.AiTutorMessageRole
import com.codeshell.app.data.model.NewsAiArticleContext
import com.codeshell.app.ui.disguise.snippets.LearnLesson

class AiTutorRepository(
    private val aiService: AiService = AiService(),
) {
    suspend fun sendMessage(
        topicLabel: String,
        lesson: LearnLesson?,
        priorMessages: List<AiTutorMessage>,
        userText: String,
        generic: Boolean = false,
        articleContext: NewsAiArticleContext? = null,
    ): AiChatResult {
        val systemPrompt = AiPromptBuilder.systemPrompt(
            topicLabel,
            lesson,
            generic = generic,
            articleContext = articleContext,
        )
        val apiMessages = buildList {
            add(AiChatMessage(role = "system", content = systemPrompt))
            // Keep the request responsive as an article conversation grows.
            // The local transcript remains complete; only the oldest turns are
            // omitted from the provider request.
            priorMessages
                .filter { it.role == AiTutorMessageRole.USER || it.role == AiTutorMessageRole.ASSISTANT }
                .takeLast(MAX_HISTORY_MESSAGES)
                .forEach { message ->
                    val role = when (message.role) {
                        AiTutorMessageRole.USER -> "user"
                        AiTutorMessageRole.ASSISTANT -> "assistant"
                        AiTutorMessageRole.WELCOME -> return@forEach
                    }
                    add(AiChatMessage(role = role, content = message.content))
                }
            add(AiChatMessage(role = "user", content = userText))
        }
        return aiService.chat(apiMessages)
    }

    companion object {
        private const val MAX_HISTORY_MESSAGES = 16
    }
}
