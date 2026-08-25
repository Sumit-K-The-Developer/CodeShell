package com.codeshell.app.data.repository

import com.codeshell.app.data.model.ChatMessage
import com.codeshell.app.data.model.MessageReply
import com.codeshell.app.data.model.CallType
import com.codeshell.app.data.model.MessageType
import com.codeshell.app.data.model.SessionLogStatus
import com.codeshell.app.data.model.sessionLogPreview
import com.codeshell.app.data.model.SystemNoticeType
import com.codeshell.app.data.model.lastMessagePreview
import com.codeshell.app.util.MessageExpiryUtil
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class MessageRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storageRepository: StorageRepository = StorageRepository()
) {
    private fun chatRef(chatId: String) = firestore.collection(ChatRepository.COLLECTION_CHATS).document(chatId)
    private fun messagesRef(chatId: String) = chatRef(chatId).collection(COLLECTION_MESSAGES)

    suspend fun getRecentMessages(
        chatId: String,
        pageSize: Int = MESSAGE_PAGE_SIZE,
    ): Result<List<ChatMessage>> {
        return try {
            val snapshot = messagesRef(chatId)
                .orderBy(FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                .limit(pageSize.toLong())
                .get()
                .await()
            val messages = snapshot.documents.mapNotNull { doc -> doc.toChatMessage() }
                .asReversed()
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeMessages(
        chatId: String,
        pageSize: Int = MESSAGE_PAGE_SIZE,
    ): Flow<List<ChatMessage>> = callbackFlow {
        val registration = messagesRef(chatId)
            .orderBy(FIELD_TIMESTAMP, Query.Direction.DESCENDING)
            .limit(pageSize.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc -> doc.toChatMessage() }.orEmpty()
                    .asReversed()
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }

    suspend fun fetchOlderMessages(
        chatId: String,
        beforeTimestamp: Timestamp,
        pageSize: Int = MESSAGE_PAGE_SIZE,
    ): Result<List<ChatMessage>> {
        return try {
            val snapshot = messagesRef(chatId)
                .orderBy(FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                .whereLessThan(FIELD_TIMESTAMP, beforeTimestamp)
                .limit(pageSize.toLong())
                .get()
                .await()
            val messages = snapshot.documents.mapNotNull { doc -> doc.toChatMessage() }
                .asReversed()
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMediaMessage(
        chatId: String,
        senderId: String,
        recipientId: String,
        type: MessageType,
        contentUrl: String,
        fileName: String? = null,
        fileSize: Long? = null,
        caption: String? = null,
        messageId: String? = null,
        reply: MessageReply? = null,
    ): Result<Unit> {
        return try {
            if (contentUrl.isBlank()) return Result.failure(Exception("Missing media URL"))

            val messageRef = messageId?.let { messagesRef(chatId).document(it) }
                ?: messagesRef(chatId).document()
            val preview = lastMessagePreview(type, fileName)
            val sentAt = Timestamp.now()
            val payload = buildMap {
                put(FIELD_SENDER_ID, senderId)
                put(FIELD_TYPE, type.name.lowercase())
                put(FIELD_CONTENT, contentUrl)
                put(FIELD_TIMESTAMP, sentAt)
                put(FIELD_UNSEEN_EXPIRES_AT, MessageExpiryUtil.unseenExpiresAt(sentAt))
                put(FIELD_SEEN_BY, mapOf(senderId to sentAt))
                put(FIELD_DELIVERED, false)
                put(FIELD_READ, false)
                put(FIELD_DELETED, false)
                fileName?.let { put(FIELD_FILE_NAME, it) }
                fileSize?.let { put(FIELD_FILE_SIZE, it) }
                caption?.takeIf { it.isNotBlank() }?.let { put(FIELD_CAPTION, it) }
                putAll(replyFields(reply))
            }

            val batch = firestore.batch()
            batch.set(messageRef, payload)
            batch.update(
                chatRef(chatId),
                mapOf(
                    ChatRepository.FIELD_LAST_MESSAGE to preview,
                    ChatRepository.FIELD_LAST_MESSAGE_TIME to sentAt,
                    "unreadCount_$recipientId" to FieldValue.increment(1),
                ),
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendSessionLogMessage(
        chatId: String,
        senderId: String,
        recipientId: String,
        status: SessionLogStatus,
        durationSec: Long? = null,
        callType: CallType = CallType.VOICE,
        callInitiatorId: String,
        callId: String,
    ): Result<Unit> {
        return try {
            val messageRef = messagesRef(chatId).document()
            val sentAt = Timestamp.now()
            val payload = buildMap {
                put(FIELD_SENDER_ID, senderId)
                put(FIELD_TYPE, MessageType.SESSION.name.lowercase())
                put(FIELD_CONTENT, status.name.lowercase())
                put(FIELD_TIMESTAMP, sentAt)
                put(FIELD_UNSEEN_EXPIRES_AT, MessageExpiryUtil.unseenExpiresAt(sentAt))
                put(FIELD_SEEN_BY, mapOf(senderId to sentAt))
                put(FIELD_DELIVERED, false)
                put(FIELD_READ, false)
                put(FIELD_DELETED, false)
                put(FIELD_FILE_NAME, callType.name.lowercase())
                put(FIELD_CALL_INITIATOR_ID, callInitiatorId)
                put(FIELD_CALL_ID, callId)
                durationSec?.let { put(FIELD_FILE_SIZE, it) }
            }
            val preview = sessionLogPreview(callType)
            val batch = firestore.batch()
            batch.set(messageRef, payload)
            batch.update(
                chatRef(chatId),
                mapOf(
                    ChatRepository.FIELD_LAST_MESSAGE to preview,
                    ChatRepository.FIELD_LAST_MESSAGE_TIME to sentAt,
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendSystemNotice(
        chatId: String,
        actorUserId: String,
        recipientId: String,
        noticeType: SystemNoticeType,
        actorLabel: String,
    ): Result<Unit> {
        return try {
            val messageRef = messagesRef(chatId).document()
            val sentAt = Timestamp.now()
            val payload = buildMap {
                put(FIELD_SENDER_ID, actorUserId)
                put(FIELD_TYPE, MessageType.SYSTEM.name.lowercase())
                put(FIELD_CONTENT, noticeType.toStorage())
                put(FIELD_FILE_NAME, actorLabel)
                put(FIELD_TIMESTAMP, sentAt)
                put(FIELD_UNSEEN_EXPIRES_AT, MessageExpiryUtil.unseenExpiresAt(sentAt))
                put(FIELD_SEEN_BY, mapOf(actorUserId to sentAt))
                put(FIELD_DELIVERED, false)
                put(FIELD_READ, false)
                put(FIELD_DELETED, false)
            }
            val preview = lastMessagePreview(MessageType.SYSTEM, null)
            val batch = firestore.batch()
            batch.set(messageRef, payload)
            batch.update(
                chatRef(chatId),
                mapOf(
                    ChatRepository.FIELD_LAST_MESSAGE to preview,
                    ChatRepository.FIELD_LAST_MESSAGE_TIME to sentAt,
                    "unreadCount_$recipientId" to FieldValue.increment(1),
                ),
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendTextMessage(
        chatId: String,
        senderId: String,
        recipientId: String,
        text: String,
        reply: MessageReply? = null,
    ): Result<Unit> {
        return try {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return Result.failure(Exception("Empty output"))

            val messageRef = messagesRef(chatId).document()
            val sentAt = Timestamp.now()
            val payload = buildMap {
                put(FIELD_SENDER_ID, senderId)
                put(FIELD_TYPE, MessageType.TEXT.name.lowercase())
                put(FIELD_CONTENT, trimmed)
                put(FIELD_TIMESTAMP, sentAt)
                put(FIELD_UNSEEN_EXPIRES_AT, MessageExpiryUtil.unseenExpiresAt(sentAt))
                put(FIELD_SEEN_BY, mapOf(senderId to sentAt))
                put(FIELD_DELIVERED, false)
                put(FIELD_READ, false)
                put(FIELD_DELETED, false)
                putAll(replyFields(reply))
            }
            val batch = firestore.batch()
            batch.set(messageRef, payload)
            batch.update(
                chatRef(chatId),
                mapOf(
                    ChatRepository.FIELD_LAST_MESSAGE to trimmed,
                    ChatRepository.FIELD_LAST_MESSAGE_TIME to sentAt,
                    "unreadCount_$recipientId" to FieldValue.increment(1)
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unsendMessage(chatId: String, messageId: String): Result<Unit> {
        return try {
            storageRepository.deleteMessageMedia(chatId, messageId)
            messagesRef(chatId).document(messageId).update(
                mapOf(
                    FIELD_DELETED to true,
                    FIELD_CONTENT to ""
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun purgeMessage(chatId: String, messageId: String): Result<Unit> {
        return try {
            storageRepository.deleteMessageMedia(chatId, messageId)
            messagesRef(chatId).document(messageId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun editTextMessage(
        chatId: String,
        messageId: String,
        senderId: String,
        newText: String,
    ): Result<Unit> {
        return try {
            val trimmed = newText.trim()
            if (trimmed.isEmpty()) return Result.failure(Exception("Empty output"))

            val messageRef = messagesRef(chatId).document(messageId)
            val snapshot = messageRef.get().await()
            val message = snapshot.toChatMessage()
                ?: return Result.failure(Exception("Output not found"))
            if (message.senderId != senderId) {
                return Result.failure(Exception("Not allowed"))
            }
            if (message.type != MessageType.TEXT || message.deleted) {
                return Result.failure(Exception("Cannot edit this output"))
            }
            if (!com.codeshell.app.util.MessageEditUtil.isWithinEditWindow(message.timestamp)) {
                return Result.failure(Exception("Edit window expired"))
            }
            if (trimmed == message.content) {
                return Result.success(Unit)
            }

            val editedAt = Timestamp.now()
            messageRef.update(
                mapOf(
                    FIELD_CONTENT to trimmed,
                    FIELD_EDITED_AT to editedAt,
                )
            ).await()

            val chatSnapshot = chatRef(chatId).get().await()
            val lastMessageTime = chatSnapshot.getTimestamp(ChatRepository.FIELD_LAST_MESSAGE_TIME)
            val messageTime = message.timestamp
            if (lastMessageTime != null && messageTime != null &&
                lastMessageTime.seconds == messageTime.seconds &&
                lastMessageTime.nanoseconds == messageTime.nanoseconds
            ) {
                chatRef(chatId).update(ChatRepository.FIELD_LAST_MESSAGE, trimmed).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAllMessages(chatId: String): Result<Unit> {
        return try {
            val snapshot = messagesRef(chatId).get().await()
            if (snapshot.isEmpty) return Result.success(Unit)
            val docs = snapshot.documents
            val batchSize = 500
            for (i in docs.indices step batchSize) {
                val batch = firestore.batch()
                docs.drop(i).take(batchSize).forEach { doc ->
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }
            storageRepository.deleteChatMedia(chatId)
            chatRef(chatId).update(
                mapOf(
                    ChatRepository.FIELD_LAST_MESSAGE to "",
                    ChatRepository.FIELD_LAST_MESSAGE_TIME to Timestamp.now(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markDelivered(chatId: String, messageId: String): Result<Unit> {
        return try {
            messagesRef(chatId).document(messageId).update(FIELD_DELIVERED, true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markRead(chatId: String, messageId: String): Result<Unit> {
        return try {
            messagesRef(chatId).document(messageId).update(
                mapOf(
                    FIELD_DELIVERED to true,
                    FIELD_READ to true
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markSeenBy(
        chatId: String,
        messageId: String,
        userId: String,
        participants: List<String>? = null,
    ): Result<Unit> {
        return try {
            val resolvedParticipants = participants
                ?: getParticipantIds(chatId).getOrElse { return Result.failure(it) }
            if (resolvedParticipants.size < 2) return Result.failure(Exception("Session not found"))

            val messageRef = messagesRef(chatId).document(messageId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(messageRef)
                if (!snapshot.exists()) return@runTransaction

                val senderId = snapshot.getString(FIELD_SENDER_ID).orEmpty()
                val seenBy = parseSeenBy(snapshot.get(FIELD_SEEN_BY))
                val mergedSeen = if (userId in seenBy) seenBy else seenBy + userId
                val recipientId = resolvedParticipants.firstOrNull { it != senderId }
                val recipientWillRead = userId != senderId && userId !in seenBy
                val recipientSeen = recipientId != null && (
                    recipientId in mergedSeen ||
                        snapshot.getBoolean(FIELD_READ) == true ||
                        recipientWillRead
                )
                val senderSeen = senderId in mergedSeen
                val bothSeen = senderSeen && recipientSeen
                val currentExpiresAt = snapshot.getTimestamp(FIELD_EXPIRES_AT)
                val messageTimestamp = snapshot.getTimestamp(FIELD_TIMESTAMP)
                val completingBothSeen = bothSeen && userId !in seenBy
                val needsExpiryBackfill = bothSeen && userId in seenBy &&
                    (currentExpiresAt == null ||
                        MessageExpiryUtil.isExpired(currentExpiresAt) ||
                        MessageExpiryUtil.isBelowPolicyWindow(currentExpiresAt, messageTimestamp))

                val updates = mutableMapOf<String, Any>()
                if (userId !in seenBy) {
                    updates["$FIELD_SEEN_BY.$userId"] = Timestamp.now()
                    if (userId != senderId) {
                        updates[FIELD_READ] = true
                        if (snapshot.getBoolean(FIELD_DELIVERED) != true) {
                            updates[FIELD_DELIVERED] = true
                        }
                    }
                }
                if (completingBothSeen || needsExpiryBackfill) {
                    updates[FIELD_EXPIRES_AT] = MessageExpiryUtil.expiresAt()
                }
                if (updates.isNotEmpty()) {
                    transaction.update(messageRef, updates)
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetUnreadCount(chatId: String, userId: String): Result<Unit> {
        return try {
            chatRef(chatId).update("unreadCount_$userId", 0).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setMessageLiked(chatId: String, messageId: String, liked: Boolean): Result<Unit> {
        return try {
            messagesRef(chatId).document(messageId).update(FIELD_LIKED, liked).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUnreadMessageCount(chatId: String, userId: String): Result<Int> {
        return try {
            val snapshot = messagesRef(chatId)
                .whereEqualTo(FIELD_READ, false)
                .get()
                .await()
            val count = snapshot.documents.count { document ->
                document.getString(FIELD_SENDER_ID) != userId &&
                    document.getBoolean(FIELD_DELETED) != true &&
                    MessageType.from(document.getString(FIELD_TYPE)) != MessageType.SESSION
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setUnreadCount(chatId: String, userId: String, count: Int): Result<Unit> {
        return try {
            chatRef(chatId).update("unreadCount_$userId", count).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getParticipantIds(chatId: String): Result<List<String>> {
        return try {
            val snapshot = chatRef(chatId).get().await()
            val participants = snapshot.get(ChatRepository.FIELD_PARTICIPANTS) as? List<*>
            Result.success(participants?.filterIsInstance<String>().orEmpty())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseHiddenBy(raw: Any?): Set<String> {
        val map = raw as? Map<*, *> ?: return emptySet()
        return map.keys.mapNotNull { it?.toString() }.toSet()
    }

    private fun parseSeenBy(raw: Any?): Set<String> {
        val map = raw as? Map<*, *> ?: return emptySet()
        return map.keys.mapNotNull { it?.toString() }.toSet()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.parseReply(): MessageReply? {
        val messageId = getString(FIELD_REPLY_MESSAGE_ID)?.takeIf { it.isNotBlank() } ?: return null
        val senderId = getString(FIELD_REPLY_SENDER_ID).orEmpty()
        val type = MessageType.from(getString(FIELD_REPLY_TYPE))
        val preview = getString(FIELD_REPLY_PREVIEW).orEmpty()
        return MessageReply(
            messageId = messageId,
            senderId = senderId,
            type = type,
            preview = preview,
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toChatMessage(): ChatMessage? {
        val timestamp = getTimestamp(FIELD_TIMESTAMP)
        val expiresAt = getTimestamp(FIELD_EXPIRES_AT)
        val unseenExpiresAt = getTimestamp(FIELD_UNSEEN_EXPIRES_AT)
            ?: timestamp?.let { MessageExpiryUtil.unseenExpiresAt(it) }
        return ChatMessage(
            id = id,
            senderId = getString(FIELD_SENDER_ID).orEmpty(),
            type = MessageType.from(getString(FIELD_TYPE)),
            content = getString(FIELD_CONTENT).orEmpty(),
            fileName = getString(FIELD_FILE_NAME),
            fileSize = getLong(FIELD_FILE_SIZE),
            caption = getString(FIELD_CAPTION),
            timestamp = timestamp,
            delivered = getBoolean(FIELD_DELIVERED) == true,
            read = getBoolean(FIELD_READ) == true,
            deleted = getBoolean(FIELD_DELETED) == true,
            seenBy = parseSeenBy(get(FIELD_SEEN_BY)),
            editedAt = getTimestamp(FIELD_EDITED_AT),
            expiresAt = expiresAt,
            unseenExpiresAt = unseenExpiresAt,
            reply = parseReply(),
            hiddenBy = parseHiddenBy(get(FIELD_HIDDEN_BY)),
            liked = getBoolean(FIELD_LIKED) == true,
            callInitiatorId = getString(FIELD_CALL_INITIATOR_ID),
            callId = getString(FIELD_CALL_ID),
        )
    }

    private fun replyFields(reply: MessageReply?): Map<String, Any> {
        if (reply == null) return emptyMap()
        return mapOf(
            FIELD_REPLY_MESSAGE_ID to reply.messageId,
            FIELD_REPLY_SENDER_ID to reply.senderId,
            FIELD_REPLY_TYPE to reply.type.name.lowercase(),
            FIELD_REPLY_PREVIEW to reply.preview,
        )
    }

    companion object {
        const val MESSAGE_PAGE_SIZE = 50
        const val COLLECTION_MESSAGES = "messages"
        const val FIELD_SENDER_ID = "senderId"
        const val FIELD_TYPE = "type"
        const val FIELD_CONTENT = "content"
        const val FIELD_FILE_NAME = "fileName"
        const val FIELD_FILE_SIZE = "fileSize"
        const val FIELD_CAPTION = "caption"
        const val FIELD_TIMESTAMP = "timestamp"
        const val FIELD_EXPIRES_AT = "expiresAt"
        const val FIELD_UNSEEN_EXPIRES_AT = "unseenExpiresAt"
        const val FIELD_SEEN_BY = "seenBy"
        const val FIELD_DELIVERED = "delivered"
        const val FIELD_READ = "read"
        const val FIELD_DELETED = "deleted"
        const val FIELD_REPLY_MESSAGE_ID = "replyMessageId"
        const val FIELD_REPLY_SENDER_ID = "replySenderId"
        const val FIELD_REPLY_TYPE = "replyType"
        const val FIELD_REPLY_PREVIEW = "replyPreview"
        const val FIELD_CALL_INITIATOR_ID = "callInitiatorId"
        const val FIELD_CALL_ID = "callId"
        const val FIELD_EDITED_AT = "editedAt"
        const val FIELD_HIDDEN_BY = "hiddenBy"
        const val FIELD_LIKED = "liked"
    }
}
