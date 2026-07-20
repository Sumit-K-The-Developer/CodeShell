package com.codeshell.app.ui.chat

import com.codeshell.app.data.model.ChatMessage
import com.codeshell.app.util.dayKey
import com.codeshell.app.util.formatDateSeparator
import java.util.Date

sealed class ChatListEntry {
    data class DateSeparator(val label: String, val dayKey: Int) : ChatListEntry()
    data class Message(val message: ChatMessage) : ChatListEntry()
}

fun buildChatListEntries(messages: List<ChatMessage>): List<ChatListEntry> {
    if (messages.isEmpty()) return emptyList()
    val entries = mutableListOf<ChatListEntry>()
    var lastDayKey = Int.MIN_VALUE
    messages.forEach { message ->
        val ts = message.timestamp
        val key = dayKey(ts)
        if (key != lastDayKey && ts != null) {
            entries.add(
                ChatListEntry.DateSeparator(
                    label = formatDateSeparator(Date(ts.seconds * 1000)),
                    dayKey = key,
                ),
            )
            lastDayKey = key
        }
        entries.add(ChatListEntry.Message(message))
    }
    return entries
}

/** Newest-first entries for [LazyColumn] with reverseLayout = true (index 0 anchors at bottom). */
fun buildReversedChatListEntries(messages: List<ChatMessage>): List<ChatListEntry> {
    if (messages.isEmpty()) return emptyList()
    val entries = mutableListOf<ChatListEntry>()
    for (index in messages.indices.reversed()) {
        val message = messages[index]
        entries.add(ChatListEntry.Message(message))
        val ts = message.timestamp ?: continue
        val older = messages.getOrNull(index - 1)
        if (older == null || dayKey(older.timestamp) != dayKey(ts)) {
            entries.add(
                ChatListEntry.DateSeparator(
                    label = formatDateSeparator(Date(ts.seconds * 1000)),
                    dayKey = dayKey(ts),
                ),
            )
        }
    }
    return entries
}

fun chatListEntryContentType(entry: ChatListEntry): String = when (entry) {
    is ChatListEntry.DateSeparator -> "date"
    is ChatListEntry.Message -> when (entry.message.type) {
        com.codeshell.app.data.model.MessageType.SESSION -> "session"
        com.codeshell.app.data.model.MessageType.SYSTEM -> "system"
        com.codeshell.app.data.model.MessageType.IMAGE,
        com.codeshell.app.data.model.MessageType.VIDEO -> "visual"
        else -> "message"
    }
}
