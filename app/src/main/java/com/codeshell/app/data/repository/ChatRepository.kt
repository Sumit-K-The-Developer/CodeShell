package com.codeshell.app.data.repository

import com.codeshell.app.data.model.ChatInterfaceStyle
import com.codeshell.app.data.model.ChatSummary
import com.codeshell.app.data.repository.MessageRepository
import com.codeshell.app.util.ChatIdUtil
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val messageRepository: MessageRepository = MessageRepository(),
    private val storageRepository: StorageRepository = StorageRepository()
) {
    private val chatsCollection get() = firestore.collection(COLLECTION_CHATS)

    fun observeChats(userId: String): Flow<List<ChatSummary>> = callbackFlow {
        val registration = chatsCollection
            .whereArrayContains(FIELD_PARTICIPANTS, userId)
            .orderBy(FIELD_LAST_MESSAGE_TIME, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents?.mapNotNull { doc ->
                    val participants = doc.get(FIELD_PARTICIPANTS) as? List<*>
                        ?: return@mapNotNull null
                    val participantIds = participants.filterIsInstance<String>()
                    val otherUserId = participantIds.firstOrNull { it != userId } ?: return@mapNotNull null
                    val unreadField = "unreadCount_$userId"
                    ChatSummary(
                        chatId = doc.id,
                        otherUserId = otherUserId,
                        lastMessage = doc.getString(FIELD_LAST_MESSAGE).orEmpty(),
                        lastMessageTime = doc.getTimestamp(FIELD_LAST_MESSAGE_TIME),
                        unreadCount = doc.getLong(unreadField)?.toInt() ?: 0
                    )
                }.orEmpty()
                trySend(chats)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getOrCreateChat(currentUserId: String, otherUserId: String): Result<String> {
        if (currentUserId == otherUserId) {
            return Result.failure(Exception("Cannot open session with yourself"))
        }
        return try {
            val chatId = ChatIdUtil.create(currentUserId, otherUserId)
            val docRef = chatsCollection.document(chatId)
            val participants = listOf(currentUserId, otherUserId).sorted()
            val chatData = hashMapOf(
                FIELD_PARTICIPANTS to participants,
                FIELD_LAST_MESSAGE to "",
                FIELD_LAST_MESSAGE_TIME to Timestamp.now(),
                "unreadCount_${participants[0]}" to 0,
                "unreadCount_${participants[1]}" to 0
            )
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    transaction.set(docRef, chatData)
                }
                null
            }.await()
            Result.success(chatId)
        } catch (e: Exception) {
            Result.failure(
                if (e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                    Exception(
                        "Terminal access denied. Publish the latest firestore.rules " +
                            "from this project in Firebase Console → Firestore → Rules."
                    )
                } else {
                    e
                }
            )
        }
    }

    suspend fun deleteChats(chatIds: List<String>): Result<Unit> {
        return try {
            chatIds.forEach { chatId ->
                messageRepository.deleteAllMessages(chatId)
                chatsCollection.document(chatId).delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listChatIdsForUser(userId: String): Result<List<String>> {
        return try {
            val snapshot = chatsCollection
                .whereArrayContains(FIELD_PARTICIPANTS, userId)
                .get()
                .await()
            Result.success(snapshot.documents.map { it.id })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val COLLECTION_CHATS = "chats"
        const val FIELD_PARTICIPANTS = "participants"
        const val FIELD_LAST_MESSAGE = "lastMessage"
        const val FIELD_LAST_MESSAGE_TIME = "lastMessageTime"
    }
}
