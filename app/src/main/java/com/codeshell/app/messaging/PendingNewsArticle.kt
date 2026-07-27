package com.codeshell.app.messaging

/**
 * Holds a pending news-article deep-link requested by a notification tap.
 * Consumed once by the news flavor's DisguiseLearnScreen to open the
 * matching card directly.
 */
object PendingNewsArticle {

    @Volatile
    private var pending: PendingArticle? = null

    data class PendingArticle(
        val url: String,
        val categoryId: String,
        val regionCountryCode: String,
    )

    fun set(url: String, categoryId: String, regionCountryCode: String) {
        pending = PendingArticle(url, categoryId, regionCountryCode)
    }

    fun peek(): PendingArticle? = pending

    fun consume(): PendingArticle? {
        val p = pending
        pending = null
        return p
    }
}