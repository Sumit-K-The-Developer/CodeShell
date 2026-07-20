package com.codeshell.app.data.ai

import com.codeshell.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit

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
    private val client: OkHttpClient = defaultClient,
) {
    private val apiKey: String
        get() = BuildConfig.CODESHELL_GROQ_API_KEY.trim()

    suspend fun chat(messages: List<AiChatMessage>): AiChatResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext AiChatResult.NoApiKey

        try {
            val messagesArray = JSONArray()
            messages.forEach { message ->
                messagesArray.put(
                    JSONObject().apply {
                        put("role", message.role)
                        put("content", message.content)
                    },
                )
            }

            val requestJson = JSONObject().apply {
                put("model", MODEL)
                put("temperature", 0.3)
                put("max_tokens", MAX_RESPONSE_TOKENS)
                put("messages", messagesArray)
            }

            val requestBody = requestJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(GROQ_CHAT_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            repeat(2) { attempt ->
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val json = JSONObject(body)
                        val choices = json.optJSONArray("choices") ?: return@withContext AiChatResult.Error("Empty response")
                        if (choices.length() == 0) return@withContext AiChatResult.Error("Empty response")
                        val content = choices.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content")
                            ?.trim()
                            .orEmpty()
                        return@withContext if (content.isBlank()) {
                            AiChatResult.Error("Empty response")
                        } else {
                            AiChatResult.Success(content)
                        }
                    }
                    if (response.code == 429) {
                        if (attempt == 0) {
                            sleep(1200)
                        } else {
                            return@withContext AiChatResult.RateLimited("Usage limit reached. Please try again shortly.")
                        }
                    } else {
                        val errorMessage = runCatching {
                            JSONObject(body).optJSONObject("error")?.optString("message")
                        }.getOrNull()?.takeIf { it.isNotBlank() }
                        return@withContext AiChatResult.Error(errorMessage ?: "Request failed (${response.code})")
                    }
                }
            }
            AiChatResult.Error("Request failed")
        } catch (e: Exception) {
            AiChatResult.Error(e.message ?: "Network error")
        }
    }

    companion object {
        private const val GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL = "llama-3.1-8b-instant"
        private const val MAX_RESPONSE_TOKENS = 400

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
