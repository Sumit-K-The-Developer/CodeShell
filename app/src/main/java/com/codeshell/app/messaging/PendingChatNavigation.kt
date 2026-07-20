package com.codeshell.app.messaging

object PendingChatNavigation {

    @Volatile
    private var chatId: String? = null

    fun set(chatId: String) {
        this.chatId = chatId
    }

    fun consume(): String? {
        val pending = chatId
        chatId = null
        return pending
    }
}
