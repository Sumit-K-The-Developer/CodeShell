package com.codeshell.app.util

object FirestoreErrors {
    fun isPermissionDenied(error: Throwable): Boolean {
        return error.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true
    }
}
