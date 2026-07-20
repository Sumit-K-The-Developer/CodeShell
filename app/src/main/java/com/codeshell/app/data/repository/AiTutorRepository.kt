package com.codeshell.app.data.repository

import com.codeshell.app.data.ai.AiChatMessage
import com.codeshell.app.data.ai.AiChatResult
import com.codeshell.app.data.ai.AiPromptBuilder
import com.codeshell.app.data.ai.AiService
import com.codeshell.app.data.model.AiTutorMessage
import com.codeshell.app.data.model.AiTutorMessageRole
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
    ): AiChatResult {
        val systemPrompt = AiPromptBuilder.systemPrompt(topicLabel, lesson, generic = generic)
        val apiMessages = buildList {
            add(AiChatMessage(role = "system", content = systemPrompt))
            priorMessages
                .filter { it.role == AiTutorMessageRole.USER || it.role == AiTutorMessageRole.ASSISTANT }
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
}
