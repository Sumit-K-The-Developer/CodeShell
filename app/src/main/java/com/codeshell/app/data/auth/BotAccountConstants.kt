package com.codeshell.app.data.auth

import com.codeshell.app.util.UsernameValidator

object BotAccountConstants {
    const val USERNAME = "8787"
    const val WELCOME_DIGITS = "8787"
    const val GENERIC_TOPIC_ID = "general"
    const val LOCAL_ASSISTANT_CHAT_ID = "local-bot-assistant-chat"

    fun isBotUsername(username: String): Boolean =
        UsernameValidator.normalize(username) == USERNAME

    fun isBotUser(userId: String?, botUserId: String?): Boolean =
        !userId.isNullOrBlank() && userId == botUserId
}
