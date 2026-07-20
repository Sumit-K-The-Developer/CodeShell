package com.codeshell.app.util

import android.content.Context
import coil.memory.MemoryCache
import com.codeshell.app.data.model.ChatMessage
import com.codeshell.app.data.model.MessageType

object MessageMediaCacheEvictor {

    private val mediaTypes = setOf(MessageType.IMAGE, MessageType.VIDEO, MessageType.AUDIO)

    fun evict(context: Context, messages: Collection<ChatMessage>) {
        val urls = messages
            .asSequence()
            .filter { it.type in mediaTypes }
            .mapNotNull { it.mediaUrl }
            .distinct()
            .toList()
        if (urls.isEmpty()) return

        val appContext = context.applicationContext
        val imageLoader = MediaImageLoader.get(appContext)
        urls.forEach { url ->
            imageLoader.memoryCache?.remove(MemoryCache.Key(url))
            imageLoader.diskCache?.remove(url)
            MediaPreloadCache.evictUrl(appContext, url)
        }
    }
}
