package com.codeshell.app.messaging

import android.util.Log
import com.codeshell.app.call.PendingCallStore
import com.codeshell.app.data.model.CallType
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
            // A signed-out device must not surface delayed FCM payloads that
            // were sent while the account was still active.
            if (authRepository.currentUser == null) return@launch
            val type = message.data["type"]
            val chatId = message.data["chatId"] ?: return@launch
            if (type == "call_update") {
                // A missed/declined outcome ends the ringing state remotely; remove
                // the original action notification before showing the outcome.
                TerminalNotificationManager.clear(applicationContext, chatId, isCall = true)
            }
            val alertsEnabled = AppPreferences(applicationContext).newTerminalAlert.first()
            if (!alertsEnabled) return@launch

            when (type) {
                "incoming_call" -> {
                    val callId = message.data["callId"] ?: return@launch
                    val callType = CallType.fromStorage(message.data["callType"])
                    val controlsEnabled = AppPreferences(applicationContext).callNotificationControls.first()
                    // Call-control notifications remain available on every
                    // screen when enabled. Only disguised call alerts follow
                    // the chat-surface suppression rule.
                    if (!controlsEnabled && NotificationVisibilityStore.shouldSuppressOrdinaryNotifications()) {
                        return@launch
                    }
                    PendingCallStore.save(applicationContext, chatId, callId)
                    TerminalNotificationManager.showCallNotification(
                        context = applicationContext,
                        chatId = chatId,
                        callId = callId,
                        callType = callType,
                        controlsEnabled = controlsEnabled,
                    )
                    Log.d(TAG, "Queued incoming call for chat=$chatId")
                }
                "call_update" -> {
                    if (AppPreferences(applicationContext).callNotificationControls.first()) {
                        TerminalNotificationManager.showCallOutcomeNotification(
                            context = applicationContext,
                            callType = CallType.fromStorage(message.data["callType"]),
                            status = message.data["status"].orEmpty(),
                        )
                    }
                }
                else -> {
                    if (NotificationVisibilityStore.shouldSuppressOrdinaryNotifications()) return@launch
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
