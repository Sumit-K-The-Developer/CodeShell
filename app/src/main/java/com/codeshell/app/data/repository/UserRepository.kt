package com.codeshell.app.data.repository

import com.codeshell.app.data.model.UserProfile
import com.codeshell.app.data.model.UserSearchResult
import com.codeshell.app.data.auth.BotAccountConstants
import com.codeshell.app.util.UsernameValidator
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val accountRepository: AccountRepository = AccountRepository()
) {
    private val usersCollection get() = firestore.collection(COLLECTION_USERS)
    private val usernamesCollection get() = firestore.collection(COLLECTION_USERNAMES)

    /**
     * Checks username availability via Cloud Function, which also removes orphaned
     * [usernames] registry docs when the linked user profile no longer exists.
     */
    suspend fun checkUsernameAvailability(normalizedUsername: String): Result<Boolean> {
        if (normalizedUsername.isBlank()) {
            return Result.success(true)
        }
        return accountRepository.checkUsernameAvailability(normalizedUsername)
    }

    suspend fun createUserProfile(userId: String, username: String): Result<Unit> {
        return try {
            val normalized = UsernameValidator.normalize(username)
            val profile = hashMapOf(
                FIELD_USERNAME to normalized,
                FIELD_AVATAR_URL to null,
                FIELD_FCM_TOKEN to "",
                FIELD_BLOCKED_USERS to emptyList<String>(),
                FIELD_CREATED_AT to Timestamp.now()
            )
            val usernameClaim = hashMapOf(FIELD_USER_ID to userId)

            firestore.runBatch { batch ->
                batch.set(usersCollection.document(userId), profile)
                batch.set(usernamesCollection.document(normalized), usernameClaim)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapFirestoreError(e))
        }
    }

    fun observeCurrentUserProfile(userId: String): Flow<UserProfile?> =
        observeUserProfile(userId)

    fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val registration = usersCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot.toUserProfile())
            }
        awaitClose { registration.remove() }
    }

    suspend fun updateFcmToken(userId: String, token: String): Result<Unit> {
        return try {
            usersCollection.document(userId).update(FIELD_FCM_TOKEN, token).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapFirestoreError(e))
        }
    }

    suspend fun updateAvatarUrl(userId: String, avatarUrl: String?): Result<Unit> {
        return try {
            usersCollection.document(userId).update(FIELD_AVATAR_URL, avatarUrl).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapFirestoreError(e))
        }
    }

    suspend fun blockUser(currentUserId: String, targetUserId: String): Result<Unit> {
        if (currentUserId == targetUserId) {
            return Result.failure(Exception("Cannot block yourself"))
        }
        return try {
            usersCollection.document(currentUserId)
                .update(FIELD_BLOCKED_USERS, FieldValue.arrayUnion(targetUserId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapFirestoreError(e))
        }
    }

    suspend fun unblockUser(currentUserId: String, targetUserId: String): Result<Unit> {
        return try {
            usersCollection.document(currentUserId)
                .update(FIELD_BLOCKED_USERS, FieldValue.arrayRemove(targetUserId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapFirestoreError(e))
        }
    }

    suspend fun getUsername(userId: String): Result<String?> {
        return try {
            val snapshot = usersCollection.document(userId).get().await()
            if (!snapshot.exists()) return Result.success(null)
            Result.success(snapshot.getString(FIELD_USERNAME))
        } catch (e: Exception) {
            Result.failure(mapFirestoreError(e))
        }
    }

    suspend fun getUserIdByUsernameClaim(username: String): Result<String?> {
        return try {
            val normalized = UsernameValidator.normalize(username)
            if (normalized.isBlank()) return Result.success(null)
            val snapshot = usernamesCollection.document(normalized).get().await()
            if (!snapshot.exists()) return Result.success(null)
            Result.success(snapshot.getString(FIELD_USER_ID))
        } catch (e: Exception) {
            Result.failure(mapFirestoreError(e))
        }
    }

    suspend fun resolveUserIdByUsername(username: String): Result<String?> {
        return try {
            val normalized = UsernameValidator.normalize(username)
            if (normalized.isBlank()) return Result.success(null)
            val snapshot = usersCollection
                .whereEqualTo(FIELD_USERNAME, normalized)
                .limit(1)
                .get()
                .await()
            Result.success(snapshot.documents.firstOrNull()?.id)
        } catch (e: Exception) {
            Result.failure(mapFirestoreError(e))
        }
    }

    /**
     * Exact username search for user discovery.
     * Blocked users are hidden from search results.
     */
    suspend fun findUserByExactUsername(
        query: String,
        currentUserId: String,
        blockedUserIds: List<String>
    ): Result<UserSearchResult> {
        return try {
            val normalized = UsernameValidator.normalize(query)
            if (normalized.isBlank()) return Result.success(UserSearchResult.NotFound)
            if (normalized == BotAccountConstants.USERNAME) return Result.success(UserSearchResult.NotFound)

            val snapshot = usersCollection
                .whereEqualTo(FIELD_USERNAME, normalized)
                .limit(1)
                .get()
                .await()

            if (snapshot.isEmpty) return Result.success(UserSearchResult.NotFound)

            val doc = snapshot.documents.first()
            val profile = doc.toUserProfile()

            when {
                profile.userId == currentUserId -> Result.success(UserSearchResult.NotFound)
                profile.userId in blockedUserIds -> Result.success(UserSearchResult.InBlockList)
                else -> Result.success(UserSearchResult.Found(profile))
            }
        } catch (e: Exception) {
            Result.failure(mapFirestoreError(e))
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toUserProfile(): UserProfile {
        return UserProfile(
            userId = id,
            username = getString(FIELD_USERNAME).orEmpty(),
            avatarUrl = getString(FIELD_AVATAR_URL),
            fcmToken = getString(FIELD_FCM_TOKEN).orEmpty(),
            blockedUsers = get(FIELD_BLOCKED_USERS) as? List<String> ?: emptyList(),
            createdAt = getTimestamp(FIELD_CREATED_AT)
        )
    }

    private fun mapFirestoreError(error: Exception): Exception {
        return when {
            error.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ->
                Exception("Firestore rules not configured. Republish firestore.rules from the project.")
            else -> error
        }
    }

    companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_USERNAMES = "usernames"
        const val FIELD_USERNAME = "username"
        const val FIELD_AVATAR_URL = "avatarUrl"
        const val FIELD_FCM_TOKEN = "fcmToken"
        const val FIELD_BLOCKED_USERS = "blockedUsers"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_USER_ID = "userId"
    }
}
