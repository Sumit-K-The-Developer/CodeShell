package com.codeshell.app.util

object ChatMessageFontScale {
    const val DEFAULT = 1f
    const val MIN = 0.85f
    const val MAX = 1.25f
    const val SLIDER_STEPS = 4

    fun clamp(value: Float): Float = value.coerceIn(MIN, MAX)
}
