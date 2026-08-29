package com.codeshell.app.navigation

object Routes {
    const val CODING_LEARN = "coding_learn"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val HOME = "home"
    const val CHAT = "chat/{chatId}"
    const val MEDIA = "media/{type}"
    const val SETTINGS = "settings?chatId={chatId}&openVaultPicker={openVaultPicker}"
    const val AI_TUTOR = "ai_tutor/{topicId}?entrySource={entrySource}&welcomeDigits={welcomeDigits}&topicLabel={topicLabel}"
    const val VAULT = "vault"

    fun chat(chatId: String) = "chat/$chatId"
    fun media(type: String) = "media/$type"
    fun home() = "home"
    fun vault() = "vault"
    fun settings(chatId: String? = null, openVaultPicker: Boolean = false): String {
        val query = buildList {
            if (!chatId.isNullOrBlank()) {
                add("chatId=${java.net.URLEncoder.encode(chatId, Charsets.UTF_8.name())}")
            }
            if (openVaultPicker) add("openVaultPicker=true")
        }
        return if (query.isEmpty()) "settings" else "settings?${query.joinToString("&")}"
    }
    fun aiTutor(
        topicId: String,
        topicLabel: String,
        entrySource: String = "fab",
        welcomeDigits: String = "",
    ): String {
        val encodedLabel = java.net.URLEncoder.encode(topicLabel, Charsets.UTF_8.name())
        val encodedDigits = java.net.URLEncoder.encode(welcomeDigits, Charsets.UTF_8.name())
        return "ai_tutor/$topicId?entrySource=$entrySource&welcomeDigits=$encodedDigits&topicLabel=$encodedLabel"
    }
}

fun String.decodeFromRoute(): String = java.net.URLDecoder.decode(this, Charsets.UTF_8.name())

fun String?.requiresSecureScreen(): Boolean =
    isTerminalRoute() || this == Routes.CODING_LEARN

fun String?.isTerminalRoute(): Boolean = when {
    this == null -> false
    startsWith("home") -> true
    startsWith("chat") -> true
    this == Routes.SETTINGS -> true
    this == Routes.VAULT -> true
    startsWith("media") -> true
    this == Routes.LOGIN -> true
    this == Routes.SIGNUP -> true
    else -> false
}
