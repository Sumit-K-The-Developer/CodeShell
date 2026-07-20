package com.codeshell.app.messaging

object LocalActiveChatStore {

    @Volatile
    private var activeChatId: String? = null

    fun setActiveChatId(chatId: String?) {
        activeChatId = chatId?.takeIf { it.isNotBlank() }
    }

    fun isViewingChat(chatId: String): Boolean = activeChatId == chatId
}
