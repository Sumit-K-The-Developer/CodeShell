package com.codeshell.app.messaging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds a pending news-article deep-link requested by a notification tap.
 * Consumed once by the news flavor's DisguiseLearnScreen to open the
 * matching card directly.
 *
 * The [version] flow increments on every [set] so that a screen already
 * on-screen can observe the change and re-fire its consume LaunchedEffect.
 */
object PendingNewsArticle {

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    @Volatile
    private var pending: PendingArticle? = null

    data class PendingArticle(
        val url: String,
        val categoryId: String,
        val regionCountryCode: String,
    )

    fun set(url: String, categoryId: String, regionCountryCode: String) {
        pending = PendingArticle(url, categoryId, regionCountryCode)
        _version.value = _version.value + 1
    }

    fun peek(): PendingArticle? = pending

    fun consume(): PendingArticle? {
        val p = pending
        pending = null
        return p
    }
}