package com.codeshell.app.data.news

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class NewsCache private constructor(private val cacheDir: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun loadAll(categoryId: String, region: NewsRegion): List<NewsArticle>? {
        val file = cacheFile(categoryId, region)
        if (!file.exists()) return null
        return try {
            val raw = file.readText()
            val entry = json.decodeFromString<CacheEntry>(raw)
            if (isExpired(entry)) {
                file.delete()
                return null
            }
            entry.articles.ifEmpty { null }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    fun save(categoryId: String, region: NewsRegion, articles: List<NewsArticle>) {
        val file = cacheFile(categoryId, region)
        try {
            cacheDir.mkdirs()
            val capped = articles.take(MAX_ARTICLES)
            val entry = CacheEntry(
                timestamp = System.currentTimeMillis(),
                articleCount = capped.size,
                articles = capped,
            )
            file.writeText(json.encodeToString(entry))
        } catch (_: Exception) {
            try { file.delete() } catch (_: Exception) { /* ignore */ }
        }
    }

    fun clear(categoryId: String, region: NewsRegion) {
        try {
            cacheFile(categoryId, region).delete()
        } catch (_: Exception) { /* ignore */ }
    }

    fun trimExpired() {
        try {
            cacheDir.listFiles()?.forEach { file ->
                try {
                    val entry = json.decodeFromString<CacheEntry>(file.readText())
                    if (isExpired(entry)) file.delete()
                } catch (_: Exception) {
                    file.delete()
                }
            }
        } catch (_: Exception) { /* ignore */ }
    }

    /**
     * Result of picking a random cached article. Carries the article plus the
     * (categoryId, region) pair it lives in, so the caller can route the user
     * straight to that article's page when the notification is tapped.
     */
    data class CachedArticleRef(
        val article: NewsArticle,
        val categoryId: String,
        val region: NewsRegion,
    )

    /**
     * Picks one random cached article (across all categories × regions),
     * skipping expired entries. Returns null when the cache is empty.
     */
    fun randomArticle(): CachedArticleRef? {
        return try {
            val refs = collectArticleRefs()
            if (refs.isEmpty()) return null
            refs[Random.nextInt(refs.size)]
        } catch (_: Exception) {
            null
        }
    }

    /** Convenience: random headline only. */
    fun randomHeadline(): String? = randomArticle()?.article?.title?.takeIf { it.isNotBlank() }

    private fun collectArticleRefs(): List<CachedArticleRef> {
        val files = cacheDir.listFiles { _, name -> name.endsWith(".json") } ?: return emptyList()
        val refs = mutableListOf<CachedArticleRef>()
        for (file in files) {
            try {
                val entry = json.decodeFromString<CacheEntry>(file.readText())
                if (isExpired(entry)) continue
                val (categoryId, region) = decodeFileName(file.name) ?: continue
                entry.articles.forEach { a -> refs.add(CachedArticleRef(a, categoryId, region)) }
            } catch (_: Exception) {
                // skip unreadable/expired/corrupt file
            }
        }
        return refs
    }

    /** Reverse the cacheFile name → (categoryId, region). */
    private fun decodeFileName(name: String): Pair<String, NewsRegion>? {
        val base = name.removeSuffix(".json")
        val sep = base.lastIndexOf('_')
        if (sep <= 0 || sep >= base.length - 1) return null
        val categoryId = base.substring(0, sep)
        val countryCode = base.substring(sep + 1)
        val region = NewsRegion.entries.firstOrNull { it.countryCode == countryCode } ?: return null
        return categoryId to region
    }

    private fun cacheFile(categoryId: String, region: NewsRegion): File {
        val name = "${categoryId}_${region.countryCode}.json"
            .replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        return File(cacheDir, name)
    }

    private fun isExpired(entry: CacheEntry): Boolean {
        val age = System.currentTimeMillis() - entry.timestamp
        return age > TimeUnit.HOURS.toMillis(MAX_AGE_HOURS)
    }

    @Serializable
    private data class CacheEntry(
        val timestamp: Long = 0L,
        val articleCount: Int = 0,
        val articles: List<NewsArticle> = emptyList(),
    )

    companion object {
        private const val MAX_ARTICLES = 25
        private const val MAX_AGE_HOURS = 24L

        @Volatile
        private var _instance: NewsCache? = null

        val instance: NewsCache
            get() = _instance
                ?: throw IllegalStateException("NewsCache not initialized. Call NewsCache.init(context) first.")

        fun init(context: Context) {
            if (_instance == null) {
                synchronized(this) {
                    if (_instance == null) {
                        val dir = File(context.filesDir, "news_cache")
                        _instance = NewsCache(dir)
                        _instance!!.trimExpired()
                    }
                }
            }
        }
    }
}
