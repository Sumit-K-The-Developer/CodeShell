package com.codeshell.app.messaging

import android.util.Log
import com.codeshell.app.call.PendingCallStore
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.data.repository.AuthRepository
import com.codeshell.app.data.repository.MessageRepository
import com.codeshell.app.data.repository.UserRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CodeShellMessagingService : FirebaseMessagingService() {

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val messageRepository = MessageRepository()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val userId = authRepository.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val alertsEnabled = AppPreferences(applicationContext).newTerminalAlert.first()
            if (!alertsEnabled) return@launch
            userRepository.updateFcmToken(userId, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        CoroutineScope(Dispatchers.IO).launch {
            val alertsEnabled = AppPreferences(applicationContext).newTerminalAlert.first()
            if (!alertsEnabled) return@launch

            val type = message.data["type"]
            val chatId = message.data["chatId"] ?: return@launch
            when (type) {
                "incoming_call" -> {
                    val callId = message.data["callId"] ?: return@launch
                    PendingCallStore.save(applicationContext, chatId, callId)
                    TerminalNotificationManager.showCallNotification(applicationContext, chatId)
                    Log.d(TAG, "Queued incoming call for chat=$chatId")
                }
                else -> {
                    TerminalNotificationManager.showMessageNotification(applicationContext, chatId)
                    processDeliveryReceipt(chatId, message.data["messageId"])
                    Log.d(TAG, "Updated grouped notification for chat=$chatId")
                }
            }
        }
    }

    private suspend fun processDeliveryReceipt(chatId: String, messageId: String?) {
        val userId = authRepository.currentUser?.uid ?: return
        if (LocalActiveChatStore.isViewingChat(chatId)) return
        if (!messageId.isNullOrBlank()) {
            MessageReceiptCoordinator.markDelivered(chatId, messageId, messageRepository)
            return
        }
        messageRepository.getRecentMessages(chatId, pageSize = 5)
            .onSuccess { messages ->
                MessageReceiptCoordinator.processIncomingDeliveries(
                    chatId = chatId,
                    currentUserId = userId,
                    messages = messages,
                    messageRepository = messageRepository,
                )
            }
    }

    companion object {
        private const val TAG = "CodeShellFCM"
    }
}
