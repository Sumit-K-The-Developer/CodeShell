package com.codeshell.app.util

object ChatIdUtil {

    fun create(userId1: String, userId2: String): String {
        return listOf(userId1, userId2).sorted().joinToString("_")
    }
}
