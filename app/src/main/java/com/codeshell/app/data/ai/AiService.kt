package com.codeshell.app.data.ai

import com.codeshell.app.data.firebase.FirebaseFunctionsProvider
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

data class AiChatMessage(
    val role: String,
    val content: String,
)

sealed class AiChatResult {
    data class Success(val text: String) : AiChatResult()
    data class RateLimited(val message: String) : AiChatResult()
    data class Error(val message: String) : AiChatResult()
    data object NoApiKey : AiChatResult()
}

class AiService(
) {
    suspend fun chat(messages: List<AiChatMessage>): AiChatResult {
        return try {
            val result = FirebaseFunctionsProvider.instance()
                .getHttpsCallable("aiTutorChat")
                .call(mapOf("messages" to messages.map { mapOf("role" to it.role, "content" to it.content) }))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val content = data?.get("content") as? String
            if (content.isNullOrBlank()) AiChatResult.Error("AI returned an empty response")
            else AiChatResult.Success(content)
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> AiChatResult.Error("Please sign in to use News AI")
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> AiChatResult.RateLimited(
                    "AI usage limit reached. Please try again shortly.",
                )
                else -> AiChatResult.Error(e.message ?: "AI service is temporarily unavailable")
            }
        } catch (e: Exception) {
            AiChatResult.Error("AI service is temporarily unavailable. Check your connection and try again.")
        }
    }
}
