package com.codeshell.app.messaging

import android.net.Uri

data class SharedContent(
    val text: String? = null,
    val videoUri: Uri? = null,
)

object PendingSharedContent {

    @Volatile
    private var content: SharedContent? = null

    fun set(sharedContent: SharedContent) {
        content = sharedContent
    }

    fun peek(): SharedContent? = content

    fun consume(): SharedContent? {
        val pending = content
        content = null
        return pending
    }

    fun clear() {
        content = null
    }
}
