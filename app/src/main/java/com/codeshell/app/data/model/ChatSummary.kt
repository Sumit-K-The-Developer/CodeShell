package com.codeshell.app.data.model

import com.google.firebase.Timestamp

data class ChatSummary(
    val chatId: String,
    val otherUserId: String,
    val otherUsername: String = "",
    val otherAvatarUrl: String? = null,
    val otherAvatarFullUrl: String? = null,
    val otherAvatarSharedUrl: String? = null,
    val lastMessage: String = "",
    val lastMessageTime: Timestamp? = null,
    val unreadCount: Int = 0,
)
