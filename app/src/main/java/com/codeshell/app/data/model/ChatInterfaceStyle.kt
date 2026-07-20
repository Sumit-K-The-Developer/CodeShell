package com.codeshell.app.data.model

enum class ChatInterfaceStyle(val storageKey: String) {
    DEFAULT("default"),
    VINTAGE("vintage"),
    IDE("ide");

    companion object {
        fun fromStorageKey(key: String?): ChatInterfaceStyle {
            return entries.firstOrNull { it.storageKey == key } ?: DEFAULT
        }
    }
}
