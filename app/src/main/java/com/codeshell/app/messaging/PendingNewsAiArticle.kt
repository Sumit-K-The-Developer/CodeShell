package com.codeshell.app.messaging

import com.codeshell.app.data.model.NewsAiArticleContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Volatile handoff for article context; long article text stays out of nav arguments. */
object PendingNewsAiArticle {
    private val versionCounter = AtomicLong(0)
    private val pending = MutableStateFlow<NewsAiArticleContext?>(null)
    private val versionState = MutableStateFlow(0L)
    val version = versionState.asStateFlow()

    fun set(article: NewsAiArticleContext) {
        pending.value = article
        versionState.value = versionCounter.incrementAndGet()
    }

    fun consume(): NewsAiArticleContext? {
        val article = pending.value
        pending.value = null
        return article
    }
}
