package com.codeshell.app.data.repository

import com.codeshell.app.data.firebase.FirebaseFunctionsProvider
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

class AccountRepository(
) {
    suspend fun checkUsernameAvailability(username: String): Result<Boolean> {
        return try {
            val result = FirebaseFunctionsProvider.instance()
                .getHttpsCallable("checkUsernameAvailability")
                .call(mapOf("username" to username))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val available = data?.get("available") as? Boolean ?: false
            Result.success(available)
        } catch (e: Exception) {
            Result.failure(mapFunctionsError(e))
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        return try {
            FirebaseFunctionsProvider.instance()
                .getHttpsCallable("deleteAccount")
                .call()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapFunctionsError(e))
        }
    }

    private fun mapFunctionsError(error: Exception): Exception {
        if (error is FirebaseFunctionsException) {
            val message = when (error.code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Not connected"
                FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                    error.message ?: "Invalid request"
                FirebaseFunctionsException.Code.INTERNAL ->
                    "Server error. Try again later."
                else -> error.message ?: "Request failed"
            }
            return Exception(message)
        }
        return error
    }
}
