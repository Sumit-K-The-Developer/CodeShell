package com.codeshell.app.data.repository

import com.codeshell.app.data.auth.BotAccountConstants
import com.codeshell.app.data.firebase.FirebaseFunctionsProvider
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

class BotAccountRepository(
    private val userRepository: UserRepository = UserRepository(),
) {
    suspend fun ensureBotAccount(): Result<String> {
        userRepository.getUserIdByUsernameClaim(BotAccountConstants.USERNAME)
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return Result.success(it) }

        userRepository.resolveUserIdByUsername(BotAccountConstants.USERNAME)
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return Result.success(it) }

        return try {
            val result = FirebaseFunctionsProvider.instance()
                .getHttpsCallable("ensureBotAccount")
                .call()
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            val userId = data?.get("userId") as? String
            if (userId.isNullOrBlank()) {
                Result.failure(Exception("Could not prepare learning assistant account"))
            } else {
                Result.success(userId)
            }
        } catch (error: Exception) {
            Result.failure(mapFunctionsError(error))
        }
    }

    private fun mapFunctionsError(error: Exception): Exception {
        if (error is FirebaseFunctionsException) {
            val message = when (error.code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Not connected"
                FirebaseFunctionsException.Code.INTERNAL ->
                    "Server error. Try again later."
                else -> error.message ?: "Request failed"
            }
            return Exception(message)
        }
        return error
    }
}
