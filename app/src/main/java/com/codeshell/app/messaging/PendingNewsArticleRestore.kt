package com.codeshell.app.messaging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Volatile handoff used to restore the exact news article after leaving AI chat. */
object PendingNewsArticleRestore {
    private val versionCounter = AtomicLong(0)
    private val pending = MutableStateFlow<String?>(null)
    private val versionState = MutableStateFlow(0L)
    val version = versionState.asStateFlow()

    fun set(url: String) {
        pending.value = url
        versionState.value = versionCounter.incrementAndGet()
    }

    fun consume(): String? {
        val url = pending.value
        pending.value = null
        return url
    }
}
