package com.codeshell.app.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeshell.app.CodeShellApplication
import com.codeshell.app.R
import com.codeshell.app.data.model.ChatInterfaceStyle
import com.codeshell.app.data.model.ChatMessage
import com.codeshell.app.data.model.MessageReply
import com.codeshell.app.data.model.MessageType
import com.codeshell.app.data.model.PendingUpload
import com.codeshell.app.data.model.UploadStage
import com.codeshell.app.data.model.replyPreviewForMessage
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.data.repository.AuthRepository
import com.codeshell.app.data.repository.MessageRepository
import com.codeshell.app.data.repository.PresenceRepository
import com.codeshell.app.data.repository.StorageRepository
import com.codeshell.app.data.repository.UserRepository
import com.codeshell.app.messaging.LocalActiveChatStore
import com.codeshell.app.messaging.MessageReceiptCoordinator
import com.codeshell.app.messaging.TerminalNotificationManager
import com.codeshell.app.util.ChatMessageFontScale
import com.codeshell.app.util.FirestoreErrors
import com.codeshell.app.util.ImageUploadProcessor
import com.codeshell.app.util.MessageEditUtil
import com.codeshell.app.util.MessageExpiryUtil
import com.codeshell.app.util.MessageMediaCacheEvictor
import com.codeshell.app.util.NetworkConnectivity
import com.codeshell.app.util.UsernameFormatter
import com.codeshell.app.util.VideoUploadProcessor
import com.codeshell.app.util.isVideoUri
import com.codeshell.app.util.queryDisplayName
import com.codeshell.app.util.queryFileSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ChatViewModel(
    application: Application,
    private val chatId: String,
    private val authRepository: AuthRepository = AuthRepository(),
    private val messageRepository: MessageRepository = MessageRepository(),
    private val storageRepository: StorageRepository = StorageRepository(),
    private val presenceRepository: PresenceRepository = PresenceRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val appPreferences: AppPreferences = AppPreferences(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val currentUserId: String?
        get() = authRepository.currentUser?.uid

    private var typingResetJob: Job? = null
    private var receiptsJob: Job? = null
    private var publishJob: Job? = null
    private var unreadResetJob: Job? = null
    private var expirySweepJob: Job? = null
    private val activeUploadJobs = ConcurrentHashMap<String, Job>()
    private val activeUploadMessageIds = ConcurrentHashMap<String, String>()
    private val markedSeenIds = mutableSetOf<String>()
    private val markedExpiryBackfillIds = mutableSetOf<String>()
    private val receiptWriteMutex = Mutex()
    private var cachedParticipantIds: List<String> = emptyList()
    private var olderMessages: List<ChatMessage> = emptyList()
    private var recentMessages: List<ChatMessage> = emptyList()
    private var hasMoreOlderMessages = true
    private val locallyHiddenMessageIds = mutableSetOf<String>()
    private val evictedMediaMessageIds = mutableSetOf<String>()

    private fun offlineMessage(): String =
        NetworkConnectivity.message(
            getApplication(),
            R.string.no_internet_action_blocked,
        )

    private fun blockIfOffline(): Boolean {
        if (NetworkConnectivity.isOffline(getApplication())) {
            _uiState.update { it.copy(errorMessage = offlineMessage()) }
            return true
        }
        return false
    }

    private fun messagingErrorMessage(error: Throwable?): String? {
        error ?: return null
        if (FirestoreErrors.isPermissionDenied(error)) {
            return getApplication<Application>().getString(R.string.messaging_blocked_by_recipient)
        }
        return error.message
    }

    init {
        currentUserId?.let { loadSession(it) }
        viewModelScope.launch {
            val draft = appPreferences.getChatDraft(chatId)
            if (draft.isNotBlank()) {
                _uiState.update { state ->
                    if (state.inputText.isBlank() && state.editingMessageId == null) {
                        state.copy(inputText = draft)
                    } else {
                        state
                    }
                }
            }
        }
        viewModelScope.launch {
            appPreferences.chatInterfaceStyle.collect { style ->
                _uiState.update { it.copy(chatInterfaceStyle = style) }
            }
        }
        viewModelScope.launch {
            appPreferences.chatMessageFontScale.collect { scale ->
                _uiState.update { it.copy(chatMessageFontScale = scale) }
            }
        }
        viewModelScope.launch {
            appPreferences.profilePicturesEnabled.collect { enabled ->
                _uiState.update { it.copy(profilePicturesEnabled = enabled) }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text, errorMessage = null) }
        if (_uiState.value.editingMessageId == null) {
            viewModelScope.launch {
                appPreferences.setChatDraft(chatId, text)
            }
        }
        val userId = currentUserId ?: return
        viewModelScope.launch {
            presenceRepository.setTyping(userId, chatId, isTyping = text.isNotBlank())
        }
        typingResetJob?.cancel()
        if (text.isNotBlank()) {
            typingResetJob = viewModelScope.launch {
                delay(TYPING_DEBOUNCE_MS)
                presenceRepository.setTyping(userId, chatId, isTyping = false)
            }
        }
    }

    fun sendMessage() {
        val editingId = _uiState.value.editingMessageId
        if (editingId != null) {
            saveEditedMessage(editingId)
            return
        }
        if (_uiState.value.isSending) return
        val userId = currentUserId ?: return
        val recipientId = _uiState.value.otherUserId ?: return
        val text = _uiState.value.inputText
        if (text.isBlank()) return
        if (blockIfOffline()) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSending = true, errorMessage = null) }
                presenceRepository.setTyping(userId, chatId, isTyping = false)
                val reply = _uiState.value.replyTarget?.toMessageReply()
                val result = messageRepository.sendTextMessage(
                    chatId = chatId,
                    senderId = userId,
                    recipientId = recipientId,
                    text = text,
                    reply = reply,
                )
                _uiState.update {
                    it.copy(
                        inputText = if (result.isSuccess) "" else it.inputText,
                        replyTarget = if (result.isSuccess) null else it.replyTarget,
                        errorMessage = messagingErrorMessage(result.exceptionOrNull()),
                    )
                }
                if (result.isSuccess) {
                    appPreferences.clearChatDraft(chatId)
                }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun setReplyTarget(message: ChatMessage) {
        if (message.deleted) return
        _uiState.update {
            it.copy(
                replyTarget = ReplyTarget(
                    messageId = message.id,
                    senderId = message.senderId,
                    type = message.type,
                    preview = replyPreviewForMessage(message),
                ),
                messageSelectionMode = false,
                selectedMessageIds = emptySet(),
            )
        }
    }

    fun clearReplyTarget() {
        _uiState.update { it.copy(replyTarget = null) }
    }

    fun cancelPendingUpload(localId: String) {
        activeUploadJobs.remove(localId)?.cancel()
        activeUploadMessageIds.remove(localId)?.let { messageId ->
            storageRepository.cancelUpload(messageId)
        }
        VideoUploadProcessor.cancelActive()
        _uiState.update { state ->
            state.copy(pendingUploads = state.pendingUploads.filterNot { it.localId == localId })
        }
    }

    fun sendImage(uri: Uri) {
        uploadMedia(uri, MessageType.IMAGE, fileName = "image.jpg")
    }

    fun sendVideo(uri: Uri) {
        uploadMedia(uri, MessageType.VIDEO, fileName = "video.mp4")
    }

    fun sendPickedMedia(uri: Uri) {
        val app = getApplication<Application>()
        if (isVideoUri(app, app.contentResolver, uri)) {
            sendVideo(uri)
        } else {
            sendImage(uri)
        }
    }

    fun sendFile(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val fileName = queryDisplayName(resolver, uri)
        val fileSize = queryFileSize(resolver, uri)
        uploadMedia(uri, MessageType.FILE, fileName = fileName, fileSize = fileSize)
    }

    private fun uploadMedia(
        uri: Uri,
        type: MessageType,
        fileName: String?,
        fileSize: Long? = null
    ) {
        val userId = currentUserId ?: return
        val recipientId = _uiState.value.otherUserId ?: return
        if (blockIfOffline()) return
        val localId = UUID.randomUUID().toString()
        val pending = PendingUpload(
            localId = localId,
            type = type,
            localUri = uri,
            fileName = fileName,
            fileSize = fileSize
        )

        _uiState.update { it.copy(pendingUploads = it.pendingUploads + pending, errorMessage = null) }

        val job = viewModelScope.launch {
            presenceRepository.setTyping(userId, chatId, isTyping = false)
            val messageId = UUID.randomUUID().toString()
            activeUploadMessageIds[localId] = messageId
            val resolver = getApplication<Application>().contentResolver
            val reply = _uiState.value.replyTarget?.toMessageReply()

            val preparedMedia = withContext(Dispatchers.IO) {
                when (type) {
                    MessageType.IMAGE -> {
                        val prepared = ImageUploadProcessor.prepare(
                            context = getApplication(),
                            contentResolver = resolver,
                            sourceUri = uri
                        )
                        Result.success(prepared.uri to (prepared.fileSize ?: fileSize))
                    }
                    MessageType.VIDEO -> {
                        runCatching {
                            val prepared = VideoUploadProcessor.prepare(
                                context = getApplication(),
                                contentResolver = resolver,
                                sourceUri = uri
                            )
                            prepared.uri to (prepared.fileSize ?: fileSize)
                        }
                    }
                    else -> Result.success(uri to fileSize)
                }
            }

            if (preparedMedia.isFailure) {
                val error = preparedMedia.exceptionOrNull()
                if (error is CancellationException || error is VideoUploadProcessor.VideoCancelledException) {
                    removePendingUpload(localId, messageId)
                    return@launch
                }
                val errorMessage = when (error) {
                    is VideoUploadProcessor.VideoTooLargeException ->
                        getApplication<Application>().getString(R.string.video_too_large)
                    else ->
                        getApplication<Application>().getString(R.string.video_prepare_failed)
                }
                _uiState.update { state ->
                    state.copy(
                        pendingUploads = state.pendingUploads.filterNot { it.localId == localId },
                        errorMessage = errorMessage
                    )
                }
                activeUploadMessageIds.remove(localId)
                return@launch
            }

            val (uploadUri, uploadSize) = preparedMedia.getOrThrow()

            _uiState.update { state ->
                state.copy(
                    pendingUploads = state.pendingUploads.map { upload ->
                        if (upload.localId == localId) {
                            upload.copy(stage = UploadStage.UPLOADING, progress = 0f)
                        } else {
                            upload
                        }
                    }
                )
            }

            val uploadResult = storageRepository.uploadChatMedia(
                chatId = chatId,
                messageId = messageId,
                localUri = uploadUri,
                contentResolver = resolver
            ) { progress ->
                _uiState.update { state ->
                    state.copy(
                        pendingUploads = state.pendingUploads.map { upload ->
                            if (upload.localId == localId) {
                                upload.copy(stage = UploadStage.UPLOADING, progress = progress)
                            } else {
                                upload
                            }
                        }
                    )
                }
            }

            uploadResult.fold(
                onSuccess = { url ->
                    val sendResult = messageRepository.sendMediaMessage(
                        chatId = chatId,
                        senderId = userId,
                        recipientId = recipientId,
                        type = type,
                        contentUrl = url,
                        fileName = fileName,
                        fileSize = uploadSize,
                        messageId = messageId,
                        reply = reply,
                    )
                    _uiState.update { state ->
                        state.copy(
                            pendingUploads = state.pendingUploads.filterNot { it.localId == localId },
                            replyTarget = if (sendResult.isSuccess) null else state.replyTarget,
                            errorMessage = messagingErrorMessage(sendResult.exceptionOrNull())
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) {
                        removePendingUpload(localId, messageId)
                        return@fold
                    }
                    _uiState.update { state ->
                        state.copy(
                            pendingUploads = state.pendingUploads.filterNot { it.localId == localId },
                            errorMessage = error.message ?: "Upload failed"
                        )
                    }
                }
            )
            activeUploadJobs.remove(localId)
            activeUploadMessageIds.remove(localId)
        }
        activeUploadJobs[localId] = job
    }

    private fun removePendingUpload(localId: String, messageId: String) {
        activeUploadJobs.remove(localId)
        activeUploadMessageIds.remove(localId)
        storageRepository.cancelUpload(messageId)
        _uiState.update { state ->
            state.copy(pendingUploads = state.pendingUploads.filterNot { it.localId == localId })
        }
    }

    fun unsendMessage(messageId: String) {
        viewModelScope.launch {
            messageRepository.unsendMessage(chatId, messageId)
        }
    }

    fun startMessageSelection(messageId: String) {
        _uiState.update {
            it.copy(
                messageSelectionMode = true,
                selectedMessageIds = setOf(messageId)
            )
        }
    }

    fun toggleMessageSelection(messageId: String) {
        _uiState.update { state ->
            val updated = state.selectedMessageIds.toMutableSet()
            if (messageId in updated) updated.remove(messageId) else updated.add(messageId)
            state.copy(selectedMessageIds = updated)
        }
    }

    fun cancelMessageSelection() {
        _uiState.update {
            it.copy(messageSelectionMode = false, selectedMessageIds = emptySet())
        }
    }

    fun canDeleteSelectedForEveryone(): Boolean {
        val userId = currentUserId ?: return false
        return _uiState.value.selectedMessageIds.any { messageId ->
            _uiState.value.messages.any { it.id == messageId && it.senderId == userId && !it.deleted }
        }
    }

    fun removeSelectedMessagesForEveryone() {
        val userId = currentUserId ?: return
        val ownMessageIds = _uiState.value.selectedMessageIds.filter { messageId ->
            _uiState.value.messages.any { it.id == messageId && it.senderId == userId && !it.deleted }
        }
        if (ownMessageIds.isEmpty()) return
        if (blockIfOffline()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    messageSelectionMode = false,
                    selectedMessageIds = emptySet(),
                )
            }
            ownMessageIds.forEach { messageId ->
                messageRepository.unsendMessage(chatId, messageId)
            }
        }
    }

    fun removeSelectedMessagesForMeOnly() {
        val messageIds = _uiState.value.selectedMessageIds.filter { messageId ->
            _uiState.value.messages.any { it.id == messageId }
        }
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    removingMessageIds = messageIds.toSet(),
                    messageSelectionMode = false,
                    selectedMessageIds = emptySet(),
                )
            }
            delay(MESSAGE_REMOVE_ANIM_MS)
            persistLocalHideForMe(messageIds)
            _uiState.update { it.copy(removingMessageIds = emptySet()) }
        }
    }
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun editableSelectedMessage(): ChatMessage? {
        val messageId = _uiState.value.selectedMessageIds.singleOrNull() ?: return null
        val message = _uiState.value.messages.find { it.id == messageId } ?: return null
        return if (MessageEditUtil.canEdit(message, currentUserId)) message else null
    }

    fun startEditingSelectedMessage() {
        val message = editableSelectedMessage() ?: return
        startEditingMessage(message.id)
    }

    fun startEditingMessage(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        if (!MessageEditUtil.canEdit(message, currentUserId)) return
        _uiState.update {
            it.copy(
                editingMessageId = message.id,
                inputText = message.content,
                replyTarget = null,
                messageSelectionMode = false,
                selectedMessageIds = emptySet(),
                errorMessage = null,
            )
        }
    }

    fun cancelEditing() {
        viewModelScope.launch {
            val draft = appPreferences.getChatDraft(chatId)
            _uiState.update { it.copy(editingMessageId = null, inputText = draft) }
        }
    }

    private fun saveEditedMessage(messageId: String) {
        val userId = currentUserId ?: return
        val text = _uiState.value.inputText
        if (text.isBlank()) return
        if (blockIfOffline()) return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSending = true) }
                val result = messageRepository.editTextMessage(
                    chatId = chatId,
                    messageId = messageId,
                    senderId = userId,
                    newText = text,
                )
                val draft = if (result.isSuccess) appPreferences.getChatDraft(chatId) else null
                _uiState.update {
                    it.copy(
                        editingMessageId = if (result.isSuccess) null else it.editingMessageId,
                        inputText = if (result.isSuccess) draft.orEmpty() else it.inputText,
                        errorMessage = result.exceptionOrNull()?.let { error ->
                            if (error.message == "Edit window expired") {
                                getApplication<Application>().getString(R.string.edit_expired)
                            } else {
                                error.message
                            }
                        },
                    )
                }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    private fun loadSession(userId: String) {
        viewModelScope.launch {
            locallyHiddenMessageIds.clear()
            locallyHiddenMessageIds.addAll(appPreferences.getHiddenMessageIds(chatId))
            evictedMediaMessageIds.clear()
            _uiState.update { it.copy(isLoadingSession = true, currentUserId = userId) }

            observeMessages(userId)
            startExpirySweep()

            messageRepository.getParticipantIds(chatId).fold(
                onSuccess = { participants ->
                    val otherUserId = participants.firstOrNull { it != userId }
                    if (otherUserId == null) {
                        _uiState.update {
                            it.copy(errorMessage = "Session not found", isLoadingSession = false)
                        }
                        return@launch
                    }
                    cachedParticipantIds = participants
                    _uiState.update { it.copy(otherUserId = otherUserId) }
                    messageRepository.resetUnreadCount(chatId, userId)
                    setActiveInChat()
                    observeOtherUser(otherUserId)
                    observePresence(otherUserId)
                    processDeliveredReceiptsImmediate(userId, recentMessages)
                    handleSeenAndReceipts(userId, recentMessages)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            errorMessage = error.message ?: "Could not load session",
                            isLoadingSession = false,
                        )
                    }
                },
            )
        }
    }

    private fun observeOtherUser(otherUserId: String) {
        viewModelScope.launch {
            userRepository.observeUserProfile(otherUserId)
                .catch { /* ignore */ }
                .collect { profile ->
                    _uiState.update {
                        it.copy(
                            otherDisplayName = profile?.username?.let { name ->
                                UsernameFormatter.display(name)
                            }.orEmpty(),
                            otherAvatarUrl = profile?.avatarUrl,
                        )
                    }
                }
        }
    }

    private fun observePresence(otherUserId: String) {
        viewModelScope.launch {
            presenceRepository.observePresence(otherUserId)
                .catch { /* ignore */ }
                .collect { presence ->
                    val typingVisible = presence.isTyping && presence.typingInChat == chatId
                    _uiState.update {
                        it.copy(
                            isOtherTyping = typingVisible,
                            isOtherOnline = PresenceRepository.isOnline(presence),
                        )
                    }
                }
        }
    }

    private fun observeMessages(userId: String) {
        viewModelScope.launch {
            messageRepository.observeMessages(chatId)
                .catch { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "Could not load output") }
                }
                .collect { recent ->
                    recentMessages = recent
                    if (olderMessages.isEmpty() && recent.size < MessageRepository.MESSAGE_PAGE_SIZE) {
                        hasMoreOlderMessages = false
                    }
                    publishMessages()
                    _uiState.update { it.copy(isLoadingSession = false) }
                    scheduleUnreadReset(userId)
                    processDeliveredReceiptsImmediate(userId, recent)
                    handleSeenAndReceipts(userId, recent)
                }
        }
    }

    fun loadOlderMessages() {
        if (_uiState.value.isLoadingOlderMessages || !hasMoreOlderMessages) return
        val oldestTimestamp = olderMessages.firstOrNull()?.timestamp
            ?: recentMessages.firstOrNull()?.timestamp
            ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOlderMessages = true) }
            messageRepository.fetchOlderMessages(chatId, oldestTimestamp)
                .onSuccess { batch ->
                    if (batch.isEmpty() || batch.size < MessageRepository.MESSAGE_PAGE_SIZE) {
                        hasMoreOlderMessages = false
                    }
                    if (batch.isNotEmpty()) {
                        val existingIds = (olderMessages + recentMessages).map { it.id }.toSet()
                        olderMessages = (batch.filter { it.id !in existingIds } + olderMessages)
                            .sortedWith(compareBy { it.timestamp?.seconds ?: Long.MIN_VALUE })
                        publishMessages()
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not load older output")
                    }
                }
            _uiState.update { it.copy(isLoadingOlderMessages = false, hasMoreOlderMessages = hasMoreOlderMessages) }
        }
    }

    private fun mergeMessages(older: List<ChatMessage>, recent: List<ChatMessage>): List<ChatMessage> {
        if (older.isEmpty()) return recent
        if (recent.isEmpty()) return older
        val recentIds = recent.asSequence().map { it.id }.toSet()
        return (older.filter { it.id !in recentIds } + recent)
            .sortedWith(compareBy { it.timestamp?.seconds ?: Long.MIN_VALUE })
    }

    fun markMessageSeen(messageId: String) {
        val userId = currentUserId ?: return
        if (messageId in markedSeenIds) return
        markedSeenIds.add(messageId)
        val participants = cachedParticipantIds.takeIf { it.size >= 2 }
        viewModelScope.launch {
            delay(MARK_READ_DELAY_MS)
            receiptWriteMutex.withLock {
                messageRepository.markSeenBy(chatId, messageId, userId, participants)
            }.onFailure { markedSeenIds.remove(messageId) }
        }
    }

    private fun scheduleUnreadReset(userId: String) {
        unreadResetJob?.cancel()
        unreadResetJob = viewModelScope.launch {
            delay(UNREAD_RESET_DEBOUNCE_MS)
            messageRepository.resetUnreadCount(chatId, userId)
        }
    }

    private fun ensureExpiryTimers(userId: String, messages: List<ChatMessage>) {
        if (cachedParticipantIds.size < 2) return
        messages.forEach { message ->
            if (message.deleted || message.type == MessageType.SYSTEM) return@forEach
            val needsExpiryBackfill = message.read &&
                message.id !in markedExpiryBackfillIds &&
                (message.expiresAt == null ||
                    MessageExpiryUtil.isBelowPolicyWindow(
                        message.expiresAt!!,
                        message.timestamp,
                    ))
            if (!needsExpiryBackfill) return@forEach
            markedExpiryBackfillIds.add(message.id)
        }
    }

    private fun processDeliveredReceiptsImmediate(userId: String, messages: List<ChatMessage>) {
        if (cachedParticipantIds.size < 2) return
        viewModelScope.launch {
            processDeliveredReceipts(messages, userId)
        }
    }

    private fun handleSeenAndReceipts(userId: String, messages: List<ChatMessage>) {
        if (cachedParticipantIds.size < 2) return
        receiptsJob?.cancel()
        receiptsJob = viewModelScope.launch {
            delay(RECEIPTS_DEBOUNCE_MS)
            ensureExpiryTimers(userId, messages)
            processExpiryBackfill(userId, messages)
        }
    }

    private suspend fun processDeliveredReceipts(messages: List<ChatMessage>, userId: String) {
        MessageReceiptCoordinator.processIncomingDeliveries(
            chatId = chatId,
            currentUserId = userId,
            messages = messages,
            messageRepository = messageRepository,
            writeGapMs = RECEIPT_WRITE_GAP_MS,
        )
    }

    private suspend fun processExpiryBackfill(userId: String, messages: List<ChatMessage>) {
        val participants = cachedParticipantIds.takeIf { it.size >= 2 } ?: return
        for (message in messages) {
            if (message.id !in markedExpiryBackfillIds) continue
            receiptWriteMutex.withLock {
                messageRepository.markSeenBy(chatId, message.id, userId, participants)
            }.onFailure {
                markedExpiryBackfillIds.remove(message.id)
            }
            delay(RECEIPT_WRITE_GAP_MS)
        }
    }

    fun clearTerminalForEveryone() {
        if (blockIfOffline()) return
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val result = messageRepository.deleteAllMessages(chatId)
            _uiState.update {
                it.copy(errorMessage = result.exceptionOrNull()?.message)
            }
        }
    }

    fun clearTerminalForMeOnly() {
        viewModelScope.launch {
            val allIds = (olderMessages + recentMessages).map { it.id }
            if (allIds.isEmpty()) return@launch
            persistLocalHideForMe(allIds)
        }
    }

    private suspend fun persistLocalHideForMe(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        locallyHiddenMessageIds.addAll(messageIds)
        appPreferences.addHiddenMessageIds(chatId, messageIds)
        publishMessages()
    }

    private fun isHiddenFromUi(message: ChatMessage): Boolean {
        return message.id in locallyHiddenMessageIds ||
            MessageExpiryUtil.shouldHideFromClient(
                message.expiresAt,
                message.unseenExpiresAt,
                message.read,
            )
    }

    private fun startExpirySweep() {
        expirySweepJob?.cancel()
        expirySweepJob = viewModelScope.launch {
            while (isActive) {
                delay(EXPIRY_SWEEP_INTERVAL_MS)
                publishMessages()
            }
        }
    }

    private fun evictExpiredMedia(messages: List<ChatMessage>) {
        val pending = messages.filter { message ->
            message.id !in evictedMediaMessageIds && isHiddenFromUi(message)
        }
        if (pending.isEmpty()) return
        evictedMediaMessageIds.addAll(pending.map { it.id })
        viewModelScope.launch(Dispatchers.IO) {
            MessageMediaCacheEvictor.evict(getApplication(), pending)
        }
    }

    private fun publishMessages() {
        publishJob?.cancel()
        publishJob = viewModelScope.launch {
            delay(PUBLISH_DEBOUNCE_MS)
            val merged = mergeMessages(olderMessages, recentMessages)
            evictExpiredMedia(merged)
            val visible = merged.filterNot { message -> isHiddenFromUi(message) }
            _uiState.update {
                it.copy(
                    messages = visible,
                    listEntries = buildReversedChatListEntries(visible),
                    hasMoreOlderMessages = hasMoreOlderMessages,
                )
            }
        }
    }
    fun blockOtherUser(onBlocked: () -> Unit) {
        val userId = currentUserId ?: return
        val otherId = _uiState.value.otherUserId ?: return
        if (blockIfOffline()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBlocking = true, errorMessage = null) }
            val result = userRepository.blockUser(userId, otherId)
            _uiState.update { it.copy(isBlocking = false) }
            if (result.isSuccess) {
                onBlocked()
            } else {
                _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun clearTyping() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            presenceRepository.setTyping(userId, chatId, isTyping = false)
        }
    }

    fun setActiveInChat() {
        val userId = currentUserId ?: return
        LocalActiveChatStore.setActiveChatId(chatId)
        TerminalNotificationManager.clearAllForChat(getApplication(), chatId)
        viewModelScope.launch {
            presenceRepository.setActiveInChat(userId, chatId)
        }
    }

    fun clearActiveInChat() {
        val userId = currentUserId ?: return
        LocalActiveChatStore.setActiveChatId(null)
        viewModelScope.launch {
            presenceRepository.setActiveInChat(userId, null)
        }
    }

    override fun onCleared() {
        typingResetJob?.cancel()
        receiptsJob?.cancel()
        publishJob?.cancel()
        unreadResetJob?.cancel()
        expirySweepJob?.cancel()
        currentUserId?.let { userId ->
            val app = getApplication<CodeShellApplication>()
            app.applicationScope.launch {
                presenceRepository.setTyping(userId, chatId, isTyping = false)
                presenceRepository.setActiveInChat(userId, null)
            }
        }
        activeUploadJobs.values.forEach { it.cancel() }
        activeUploadJobs.clear()
        activeUploadMessageIds.keys.forEach { localId ->
            activeUploadMessageIds[localId]?.let { storageRepository.cancelUpload(it) }
        }
        activeUploadMessageIds.clear()
        VideoUploadProcessor.cancelActive()
        super.onCleared()
    }

    companion object {
        private const val TYPING_DEBOUNCE_MS = 2_000L
        private const val RECEIPTS_DEBOUNCE_MS = 400L
        private const val MARK_READ_DELAY_MS = 500L
        private const val RECEIPT_WRITE_GAP_MS = 75L
        private const val UNREAD_RESET_DEBOUNCE_MS = 300L
        private const val PUBLISH_DEBOUNCE_MS = 50L
        private const val EXPIRY_SWEEP_INTERVAL_MS = 60_000L
        private const val MESSAGE_REMOVE_ANIM_MS = 220L
    }
}

data class ReplyTarget(
    val messageId: String,
    val senderId: String,
    val type: MessageType,
    val preview: String,
) {
    fun toMessageReply(): MessageReply = MessageReply(
        messageId = messageId,
        senderId = senderId,
        type = type,
        preview = preview,
    )
}

data class ChatUiState(
    val currentUserId: String? = null,
    val otherUserId: String? = null,
    val otherDisplayName: String = "",
    val otherAvatarUrl: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val listEntries: List<ChatListEntry> = emptyList(),
    val pendingUploads: List<PendingUpload> = emptyList(),
    val replyTarget: ReplyTarget? = null,
    val editingMessageId: String? = null,
    val inputText: String = "",
    val isSending: Boolean = false,
    val isLoadingSession: Boolean = true,
    val isOtherTyping: Boolean = false,
    val isOtherOnline: Boolean = false,
    val messageSelectionMode: Boolean = false,
    val selectedMessageIds: Set<String> = emptySet(),
    val removingMessageIds: Set<String> = emptySet(),
    val chatInterfaceStyle: ChatInterfaceStyle = ChatInterfaceStyle.DEFAULT,
    val chatMessageFontScale: Float = ChatMessageFontScale.DEFAULT,
    val profilePicturesEnabled: Boolean = false,
    val isBlocking: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val hasMoreOlderMessages: Boolean = true,
    val errorMessage: String? = null,
)
