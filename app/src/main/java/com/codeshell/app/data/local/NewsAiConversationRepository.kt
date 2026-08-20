package com.codeshell.app.data.local

import android.content.Context
import com.codeshell.app.data.model.AiTutorMessage
import com.codeshell.app.data.model.NewsAiArticleContext
import com.codeshell.app.data.news.NewsArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class NewsAiConversationRepository(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(articleKey: String): StoredAiTutorSession? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readCollection().sessions[articleKey]
        }
    }

    suspend fun save(
        articleKey: String,
        articleContext: NewsAiArticleContext,
        messages: List<AiTutorMessage>,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val collection = readCollection().toMutableSessions()
            collection[articleKey] = StoredAiTutorSession(
                savedAtMillis = System.currentTimeMillis(),
                messages = messages.map(AiTutorMessage::toStored),
                articleContext = articleContext.toStored(),
            )
            writeCollection(AiTutorSessionCollection(collection))
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (file.exists()) file.delete()
        }
    }

    suspend fun clear(articleKey: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val collection = readCollection().toMutableSessions()
            if (collection.remove(articleKey) != null) {
                writeCollection(AiTutorSessionCollection(collection))
            }
        }
    }

    suspend fun cachedArticleKeys(): Set<String> = withContext(Dispatchers.IO) {
        mutex.withLock { readCollection().sessions.keys }
    }

    private fun readCollection(): AiTutorSessionCollection {
        if (!file.exists()) return AiTutorSessionCollection()
        return runCatching {
            json.decodeFromString<AiTutorSessionCollection>(file.readText())
        }.getOrDefault(AiTutorSessionCollection())
    }

    private fun writeCollection(collection: AiTutorSessionCollection) {
        val temporary = File(file.parentFile, "$FILE_NAME.tmp")
        temporary.writeText(
            json.encodeToString(AiTutorSessionCollection.serializer(), collection),
        )
        if (!temporary.renameTo(file)) {
            temporary.delete()
            error("Could not save AI article conversations")
        }
    }

    private fun AiTutorSessionCollection.toMutableSessions(): MutableMap<String, StoredAiTutorSession> =
        sessions.toMutableMap()

    companion object {
        private const val FILE_NAME = "news_ai_conversations.json"
    }
}

fun newsAiConversationKey(article: NewsArticle): String = newsAiConversationKey(
    url = article.url,
    title = article.title,
    sourceName = article.source?.name,
    publishedAt = article.publishedAt,
)

fun newsAiConversationKey(
    url: String?,
    title: String?,
    sourceName: String?,
    publishedAt: String?,
): String = url?.takeIf { it.isNotBlank() }
    ?: "${title.orEmpty()}|${sourceName.orEmpty()}|${publishedAt.orEmpty()}"
