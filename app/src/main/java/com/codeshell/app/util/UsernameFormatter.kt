package com.codeshell.app.util

object UsernameFormatter {
    const val PREFIX = "CodeShell#"

    fun display(storedUsername: String): String {
        val digits = parseRawInput(storedUsername)
        return if (digits.isEmpty()) "" else "$PREFIX$digits"
    }

    fun unreadPrefix(unreadCount: Int): String? {
        if (unreadCount <= 0) return null
        return minOf(unreadCount, 99).toString().padStart(2, '0')
    }

    fun displayWithUnread(storedUsername: String, unreadCount: Int): String {
        val base = display(storedUsername)
        if (base.isEmpty()) return ""
        val prefix = unreadPrefix(unreadCount) ?: return base
        return "$prefix$base"
    }

    fun parseRawInput(input: String): String {
        return input
            .removePrefix(PREFIX)
            .filter { it.isDigit() }
            .take(10)
    }
}
