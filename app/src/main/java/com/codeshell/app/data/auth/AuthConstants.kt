package com.codeshell.app.data.auth

import com.codeshell.app.util.UsernameValidator

object AuthConstants {
    const val EMAIL_DOMAIN = "codeshell.app"

    fun toAuthEmail(username: String): String {
        return "${UsernameValidator.normalize(username)}@$EMAIL_DOMAIN"
    }
}
