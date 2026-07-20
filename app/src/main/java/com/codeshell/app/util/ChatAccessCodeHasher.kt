package com.codeshell.app.util

import java.security.MessageDigest

object ChatAccessCodeHasher {
    private const val SALT = "codeshell_chat_access_v1"

    fun hash(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$SALT:$code".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(code: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank() || code.length != 4) return false
        return hash(code) == storedHash
    }
}

fun filterFourDigitCode(input: String): String =
    input.filter { it.isDigit() }.take(4)
