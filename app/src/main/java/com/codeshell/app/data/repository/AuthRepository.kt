package com.codeshell.app.data.repository

import com.codeshell.app.data.auth.AuthConstants
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    suspend fun signIn(username: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(
                AuthConstants.toAuthEmail(username),
                password
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapAuthError(e))
        }
    }

    suspend fun signUp(username: String, password: String): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(
                AuthConstants.toAuthEmail(username),
                password
            ).await()
            val userId = result.user?.uid
                ?: return Result.failure(Exception("Account creation failed"))
            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(mapAuthError(e))
        }
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun updatePassword(newPassword: String): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("Not connected"))
            user.updatePassword(newPassword).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapAuthError(e))
        }
    }

    suspend fun reauthenticate(password: String): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("Not connected"))
            val email = user.email
                ?: return Result.failure(Exception("Not connected"))
            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapAuthError(e))
        }
    }

    private fun mapAuthError(error: Exception): Exception {
        val message = error.message.orEmpty()
        return when {
            message.contains("password is invalid", ignoreCase = true) ||
                message.contains("no user record", ignoreCase = true) ||
                message.contains("invalid-credential", ignoreCase = true) ||
                message.contains("wrong-password", ignoreCase = true) ->
                Exception("Incorrect CodeShell ID or access key")
            message.contains("requires-recent-login", ignoreCase = true) ->
                Exception("Enter your current access key to continue")
            message.contains("email address is already in use", ignoreCase = true) ||
                message.contains("already in use", ignoreCase = true) ->
                Exception("CodeShell ID is already taken")
            message.contains("network", ignoreCase = true) ->
                Exception("Network error. Check your connection.")
            message.contains("weak password", ignoreCase = true) ||
                message.contains("Password should be at least", ignoreCase = true) ->
                Exception("Access key must be at least 6 characters")
            message.contains("API key not valid", ignoreCase = true) ||
                message.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
                Exception("Firebase is not configured yet. Add google-services.json.")
            else -> Exception(message.ifBlank { "Authentication failed" })
        }
    }
}
