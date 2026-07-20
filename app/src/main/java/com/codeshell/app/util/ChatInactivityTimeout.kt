package com.codeshell.app.util

object ChatInactivityTimeout {
    const val DEFAULT_SECONDS = 30
    val OPTIONS_SECONDS = listOf(15, 30, 45)

    fun clampSeconds(seconds: Int): Int =
        if (seconds in OPTIONS_SECONDS) seconds else DEFAULT_SECONDS

    fun toMillis(seconds: Int): Long = clampSeconds(seconds) * 1_000L

    fun defaultMillis(): Long = toMillis(DEFAULT_SECONDS)
}
