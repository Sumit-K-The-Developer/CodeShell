package com.codeshell.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class PresenceState(
    val isTyping: Boolean = false,
    val typingInChat: String = "",
    val activeInChat: String = "",
    val lastSeen: Timestamp? = null,
    val online: Boolean? = null,
)

class PresenceRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun presenceRef(userId: String) =
        firestore.collection(COLLECTION_PRESENCE).document(userId)

    fun observePresence(userId: String): Flow<PresenceState> = callbackFlow {
        val registration = presenceRef(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                trySend(PresenceState())
                return@addSnapshotListener
            }
            trySend(
                PresenceState(
                    isTyping = snapshot.getBoolean(FIELD_IS_TYPING) == true,
                    typingInChat = snapshot.getString(FIELD_TYPING_IN_CHAT).orEmpty(),
                    activeInChat = snapshot.getString(FIELD_ACTIVE_IN_CHAT).orEmpty(),
                    lastSeen = snapshot.getTimestamp(FIELD_LAST_SEEN),
                    online = snapshot.getBoolean(FIELD_ONLINE),
                )
            )
        }
        awaitClose { registration.remove() }
    }

    suspend fun touchPresence(userId: String): Result<Unit> {
        return try {
            presenceRef(userId).set(
                mapOf(
                    FIELD_LAST_SEEN to Timestamp.now(),
                    FIELD_ONLINE to true,
                ),
                SetOptions.merge(),
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markForeground(userId: String): Result<Unit> {
        return try {
            presenceRef(userId).set(
                mapOf(
                    FIELD_LAST_SEEN to Timestamp.now(),
                    FIELD_ONLINE to true,
                ),
                SetOptions.merge(),
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markBackground(userId: String): Result<Unit> {
        return try {
            presenceRef(userId).set(
                mapOf(
                    FIELD_ACTIVE_IN_CHAT to "",
                    FIELD_IS_TYPING to false,
                    FIELD_TYPING_IN_CHAT to "",
                    FIELD_LAST_SEEN to Timestamp.now(),
                    FIELD_ONLINE to false,
                ),
                SetOptions.merge(),
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setActiveInChat(userId: String, chatId: String?): Result<Unit> {
        return try {
            presenceRef(userId).set(
                mapOf(
                    FIELD_ACTIVE_IN_CHAT to (chatId ?: ""),
                    FIELD_LAST_SEEN to Timestamp.now(),
                    FIELD_ONLINE to true,
                ),
                SetOptions.merge(),
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setTyping(userId: String, chatId: String, isTyping: Boolean): Result<Unit> {
        return try {
            presenceRef(userId).set(
                mapOf(
                    FIELD_IS_TYPING to isTyping,
                    FIELD_TYPING_IN_CHAT to if (isTyping) chatId else "",
                    FIELD_LAST_SEEN to Timestamp.now(),
                    FIELD_ONLINE to true,
                ),
                SetOptions.merge(),
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val COLLECTION_PRESENCE = "presence"
        const val FIELD_IS_TYPING = "isTyping"
        const val FIELD_TYPING_IN_CHAT = "typingInChat"
        const val FIELD_LAST_SEEN = "lastSeen"
        const val FIELD_ACTIVE_IN_CHAT = "activeInChat"
        const val FIELD_ONLINE = "online"
        private const val DEFAULT_ONLINE_THRESHOLD_MS = 45_000L

        fun isOnline(presence: PresenceState, thresholdMs: Long = DEFAULT_ONLINE_THRESHOLD_MS): Boolean {
            if (presence.online == false) return false
            if (presence.activeInChat.isNotEmpty()) return true
            val lastSeen = presence.lastSeen ?: return false
            val ageMs = System.currentTimeMillis() - lastSeen.toDate().time
            return ageMs in 0..thresholdMs
        }
    }
}
