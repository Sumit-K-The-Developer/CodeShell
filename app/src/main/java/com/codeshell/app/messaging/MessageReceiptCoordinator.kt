package com.codeshell.app.messaging

import com.codeshell.app.data.model.ChatMessage
import com.codeshell.app.data.model.MessageType
import com.codeshell.app.data.repository.MessageRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

object MessageReceiptCoordinator {

    private val markedDeliveredIds = ConcurrentHashMap.newKeySet<String>()
    private val writeMutex = Mutex()

    private fun deliveryKey(chatId: String, messageId: String): String = "$chatId:$messageId"

    suspend fun markDelivered(
        chatId: String,
        messageId: String,
        messageRepository: MessageRepository,
    ): Result<Unit> {
        val key = deliveryKey(chatId, messageId)
        if (!markedDeliveredIds.add(key)) return Result.success(Unit)
        return writeMutex.withLock {
            messageRepository.markDelivered(chatId, messageId)
        }.onFailure {
            markedDeliveredIds.remove(key)
        }
    }

    suspend fun processIncomingDeliveries(
        chatId: String,
        currentUserId: String,
        messages: List<ChatMessage>,
        messageRepository: MessageRepository,
        writeGapMs: Long = 75L,
    ) {
        for (message in messages) {
            if (message.deleted || message.type == MessageType.SYSTEM) continue
            if (message.senderId == currentUserId || message.delivered) continue
            markDelivered(chatId, message.id, messageRepository)
            delay(writeGapMs)
        }
    }
}
