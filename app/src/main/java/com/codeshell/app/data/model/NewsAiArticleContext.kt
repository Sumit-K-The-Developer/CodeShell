package com.codeshell.app.data.model

/** Article context handed from the News page to the article-scoped assistant. */
data class NewsAiArticleContext(
    val title: String,
    val sourceName: String,
    val publishedAt: String,
    val url: String,
    val cachedText: String,
    val fullArticleFetched: Boolean = false,
)
