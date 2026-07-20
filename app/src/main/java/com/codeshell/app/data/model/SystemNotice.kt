package com.codeshell.app.data.model

enum class SystemNoticeType {
    AUTO_CLEAR_ENABLED,
    AUTO_CLEAR_DISABLED,
    ;

    fun toStorage(): String = when (this) {
        AUTO_CLEAR_ENABLED -> "auto_clear_enabled"
        AUTO_CLEAR_DISABLED -> "auto_clear_disabled"
    }

    companion object {
        fun fromStorage(value: String?): SystemNoticeType? = when (value?.lowercase()) {
            "auto_clear_enabled" -> AUTO_CLEAR_ENABLED
            "auto_clear_disabled" -> AUTO_CLEAR_DISABLED
            else -> null
        }
    }
}

fun ChatMessage.systemNoticeType(): SystemNoticeType? =
    if (type == MessageType.SYSTEM) SystemNoticeType.fromStorage(content) else null

fun ChatMessage.systemActorLabel(): String =
    fileName?.takeIf { it.isNotBlank() }.orEmpty()
