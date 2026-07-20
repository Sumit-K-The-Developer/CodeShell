package com.codeshell.app.util

import com.codeshell.app.data.auth.BotAccountConstants

object UsernameValidator {

    private val DIGIT_USERNAME_PATTERN = Regex("^[0-9]{4,10}$")

    sealed class Validation {
        data object Valid : Validation()
        data class Invalid(val reason: String) : Validation()
    }

    fun validateForLogin(username: String): Validation = validateDigits(username)

    fun validateForRegistration(username: String): Validation {
        val base = validateDigits(username)
        if (base is Validation.Invalid) return base
        val digits = normalize(username)
        return if (BotAccountConstants.isBotUsername(digits)) {
            Validation.Invalid("This CodeShell ID is reserved")
        } else {
            Validation.Valid
        }
    }

    fun validate(username: String): Validation = validateForRegistration(username)

    private fun validateDigits(username: String): Validation {
        val digits = normalize(username)
        return when {
            digits.isEmpty() -> Validation.Invalid("CodeShell ID is required")
            digits.length < 4 -> Validation.Invalid("Enter at least 4 digits")
            digits.length > 10 -> Validation.Invalid("Maximum 10 digits")
            !DIGIT_USERNAME_PATTERN.matches(digits) ->
                Validation.Invalid("Digits only, 4–10 characters")
            else -> Validation.Valid
        }
    }

    fun normalize(username: String): String = UsernameFormatter.parseRawInput(username)
}
