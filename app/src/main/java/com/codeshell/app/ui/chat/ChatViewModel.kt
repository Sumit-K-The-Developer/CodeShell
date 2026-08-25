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
import com.codeshell.app.data.repository.PresenceState
import com.codeshell.app.data.repository.StorageRepository
import com.codeshell.app.data.repository.UserRepository
import com.codeshell.app.data.repository.VaultRepository
import com.codeshell.app.messaging.LocalActiveChatStore
import com.codeshell.app.messaging.MessageReceiptCoordinator
import com.codeshell.app.messaging.PendingChatDraft
import com.codeshell.app.messaging.PendingSharedContent
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
import com.codeshell.app.util.BackgroundWorkCoordinator
import com.codeshell.app.util.isVideoUri
import com.codeshell.app.util.queryDisplayName
import com.codeshell.app.util.queryFileSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
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
    private val vaultRepository: VaultRepository = VaultRepository(application),
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
    private var presenceRefreshJob: Job? = null
    private val activeUploadJobs = ConcurrentHashMap<String, Job>()
    private val activeUploadMessageIds = ConcurrentHashMap<String, String>()
    private val markedSeenIds = mutableSetOf<String>()
    private val markedExpiryBackfillIds = mutableSetOf<String>()
    private val receiptWriteMutex = Mutex()
    private var cachedParticipantIds: List<String> = emptyList()
    private var olderMessages: List<ChatMessage> = emptyList()
    private var recentMessages: List<ChatMessage> = emptyList()
    private var latestPresence: PresenceState? = null
    private var hasMoreOlderMessages = true
    private val locallyHiddenMessageIds = mutableSetOf<String>()
    private val evictedMediaMessageIds = mutableSetOf<String>()
    private val optimisticallyDeletedMessageIds = mutableSetOf<String>()

    fun toggleMessageLike(messageId: String) {
        viewModelScope.launch {
            val current = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return@launch
            val liked = !current.liked
            _uiState.update { state ->
                state.copy(
                    messages = state.messages.map { message ->
                        if (message.id == messageId) message.copy(liked = liked) else message
                    },
                    likedMessageIds = if (liked) {
                        state.likedMessageIds + messageId
                    } else {
                        state.likedMessageIds - messageId
                    },
                )
            }
            if (messageRepository.setMessageLiked(chatId, messageId, liked).isFailure) {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { message ->
                            if (message.id == messageId) message.copy(liked = !liked) else message
                        },
                        likedMessageIds = if (liked) {
                            state.likedMessageIds - messageId
                        } else {
                            state.likedMessageIds + messageId
                        },
                    )
                }
            }
        }
    }

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
            _uiState.update { it.copy(likedMessageIds = appPreferences.getLikedMessageIds(chatId)) }
        }
        viewModelScope.launch {
            BackgroundUploadRegistry.observe(chatId).collect { uploads ->
                _uiState.update { it.copy(pendingUploads = uploads) }
            }
        }
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
        viewModelScope.launch {
            appPreferences.chatWallpaperEnabled.collect { enabled ->
                _uiState.update { it.copy(chatWallpaperEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.chatWallpaperOverlay.collect { overlay ->
                _uiState.update { it.copy(chatWallpaperOverlay = overlay) }
            }
        }
        viewModelScope.launch {
            appPreferences.chatWallpaperSource(chatId).collect { source ->
                _uiState.update { it.copy(wallpaperSource = source) }
            }
        }
        viewModelScope.launch {
            appPreferences.chatWallpaperVaultId(chatId).collect { vaultId ->
                val uri = if (vaultId.isNullOrBlank()) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            VaultRepository(getApplication()).getVaultFileById(vaultId)
                                ?.takeIf { it.exists() }
                                ?.let { Uri.fromFile(it) }
                        }.getOrNull()
                    }
                }
                _uiState.update { it.copy(wallpaperVaultId = vaultId, wallpaperVaultImageUri = uri) }
                if (!vaultId.isNullOrBlank() && uri == null) {
                    appPreferences.setChatWallpaperSource(
                        chatId,
                        com.codeshell.app.data.preferences.AppPreferences.WALLPAPER_SOURCE_AVATAR,
                    )
                }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text, errorMessage = null, sendErrorMessage = null) }
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
        val drafts = _uiState.value.draftMedia
        if (text.isBlank() && drafts.isEmpty()) return
        if (blockIfOffline()) return

        // Draft attachments are dispatched first. Each one uploads and sends on
            // the process-global coordinator; progress surfaces via pendingUploads.
        // They are removed from the draft list immediately so the user cannot
        // re-trigger them by pressing send again. Typed text accompanies the
        // first draft as a caption inside the media bubble (like attaching text
        // to media), so it is not sent as a separate message.
        if (drafts.isNotEmpty()) {
            val caption = text.takeIf { it.isNotBlank() }
            _uiState.update {
                it.copy(
                    draftMedia = emptyList(),
                    inputText = if (caption != null) "" else it.inputText,
                )
            }
            if (caption != null) {
                viewModelScope.launch { appPreferences.clearChatDraft(chatId) }
            }
            drafts.forEachIndexed { index, draft ->
                uploadMedia(
                    uri = draft.localUri,
                    type = draft.type,
                    fileName = draft.fileName,
                    fileSize = draft.fileSize,
                    caption = if (index == 0) caption else null,
                )
            }
        }

        if (drafts.isEmpty() && text.isNotBlank()) {
            // Run on the process-global coordinator so the send survives the user
            // leaving the chat screen or closing the app until it completes / is
            // cancelled manually. No progress notification is posted.
            BackgroundWorkCoordinator.launch(getApplication<Application>()) {
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
                            sendErrorMessage = messagingErrorMessage(result.exceptionOrNull()),
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
    }

    fun addDraftMedia(uri: Uri, type: MessageType, fileName: String?, fileSize: Long?) {
        if (type == MessageType.VIDEO && fileSize != null && fileSize > VideoUploadProcessor.MAX_UPLOAD_BYTES) {
            _uiState.update { state ->
                state.copy(videoTooLargeMessage = getApplication<Application>().getString(R.string.video_too_large))
            }
            return
        }
        val draft = DraftMedia(
            localId = UUID.randomUUID().toString(),
            type = type,
            localUri = uri,
            fileName = fileName,
            fileSize = fileSize,
        )
        _uiState.update { it.copy(draftMedia = it.draftMedia + draft) }
    }

    fun removeDraftMedia(localId: String) {
        _uiState.update { it.copy(draftMedia = it.draftMedia.filterNot { it.localId == localId }) }
    }

    /**
     * Adds an image/video picked via the input-bar '+' row to the draft list
     * (which is rendered inside the input bar for a "tag message" preview).
     * Nothing is uploaded until the user presses send.
     */
    fun addPickedMediaDraft(uri: Uri) {
        val app = getApplication<Application>()
        val resolver = app.contentResolver
        val type = if (isVideoUri(app, resolver, uri)) MessageType.VIDEO else MessageType.IMAGE
        addDraftMedia(uri, type, queryDisplayName(resolver, uri), queryFileSize(resolver, uri))
    }

    /**
     * Adds a document picked via the '+' file chooser to the draft list.
     */
    fun addPickedFileDraft(uri: Uri) {
        val app = getApplication<Application>()
        val resolver = getApplication<Application>().contentResolver
        val mimeType = resolver.getType(uri).orEmpty().lowercase()
        val isVideo = isVideoUri(app, resolver, uri)
        val type = when {
            isVideo -> MessageType.VIDEO
            mimeType.startsWith("image/") -> MessageType.IMAGE
            else -> MessageType.FILE
        }
        addDraftMedia(uri, type, queryDisplayName(resolver, uri), queryFileSize(resolver, uri))
    }

    /**
     * Replaces a draft's local image URI with the cropped output so the original
     * image draft becomes the new, cropped attachment before sending.
     */
    fun replaceDraftMedia(localId: String, uri: Uri) {
        _uiState.update { state ->
            state.copy(
                draftMedia = state.draftMedia.map { draft ->
                    if (draft.localId == localId) draft.copy(localUri = uri) else draft
                }
            )
        }
    }

    /**
     * Turns the pending vault-send items (selected on the conversation tile
     * page) into draft attachments referencing the on-disk vault files. Nothing
     * is uploaded or sent until the user presses send.
     */
    fun addVaultDrafts(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val allItems = vaultRepository.getAllItems()
            allItems.filter { it.id in itemIds }.forEach { item ->
                val file = vaultRepository.getVaultFile(item)
                if (file != null && file.exists()) {
                    addDraftMedia(
                        uri = Uri.fromFile(file),
                        type = if (item.isVideo) MessageType.VIDEO else MessageType.IMAGE,
                        fileName = item.fileName,
                        fileSize = item.fileSize,
                    )
                }
            }
        }
    }

    /**
     * Consumes any pending draft payload the app was opened with:
     * vault items to drop in as attachments, and/or externally shared content
     * (text into the input bar, video as a draft attachment). Never auto-sends.
     */
    fun consumeIncomingDrafts() {
        viewModelScope.launch {
            val vaultIds = PendingChatDraft.consume()
            if (vaultIds.isNotEmpty()) addVaultDrafts(vaultIds)

            val shared = PendingSharedContent.consume()
            shared?.let { content ->
                content.text?.takeIf { it.isNotBlank() }?.let { text ->
                    onInputChange(text)
                }
                val resolver = getApplication<Application>().contentResolver
                content.videoUris.forEach { uri ->
                    val name = queryDisplayName(resolver, uri) ?: "video.mp4"
                    addDraftMedia(uri, MessageType.VIDEO, name, queryFileSize(resolver, uri))
                }
                content.imageUris.forEach { uri ->
                    val name = queryDisplayName(resolver, uri) ?: "image.jpg"
                    addDraftMedia(uri, MessageType.IMAGE, name, null)
                }
                content.fileUris.forEach { uri ->
                    val name = queryDisplayName(resolver, uri) ?: "file"
                    val mimeType = resolver.getType(uri).orEmpty().lowercase()
                    val isVideo = isVideoUri(getApplication<Application>(), resolver, uri)
                    val type = when {
                        isVideo -> MessageType.VIDEO
                        mimeType.startsWith("image/") -> MessageType.IMAGE
                        else -> MessageType.FILE
                    }
                    addDraftMedia(uri, type, name, queryFileSize(resolver, uri))
                }
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
        BackgroundUploadRegistry.cancel(localId)
        activeUploadMessageIds.remove(localId)?.let { messageId ->
            storageRepository.cancelUpload(messageId)
        }
        VideoUploadProcessor.cancelActive()
        _uiState.update { state ->
            state.copy(pendingUploads = state.pendingUploads.filterNot { it.localId == localId })
        }
        BackgroundUploadRegistry.remove(localId)
    }

    fun retryPendingUpload(localId: String) {
        val upload = _uiState.value.pendingUploads.firstOrNull { it.localId == localId } ?: return
        if (upload.stage != UploadStage.FAILED) return
        _uiState.update { state ->
            state.copy(pendingUploads = state.pendingUploads.filterNot { it.localId == localId })
        }
        BackgroundUploadRegistry.remove(localId)
        uploadMedia(upload.localUri, upload.type, upload.fileName, upload.fileSize)
    }

    fun sendImage(uri: Uri) {
        uploadMedia(uri, MessageType.IMAGE, fileName = "image.jpg")
    }

    fun sendVideo(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val fileSize = queryFileSize(resolver, uri)
        if (fileSize != null && fileSize > VideoUploadProcessor.MAX_UPLOAD_BYTES) {
            _uiState.update { state ->
                state.copy(videoTooLargeMessage = getApplication<Application>().getString(R.string.video_too_large))
            }
            return
        }
        uploadMedia(uri, MessageType.VIDEO, fileName = "video.mp4", fileSize = fileSize)
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
        val app = getApplication<Application>()
        val resolver = getApplication<Application>().contentResolver
        if (isVideoUri(app, resolver, uri)) {
            sendVideo(uri)
            return
        }
        val fileName = queryDisplayName(resolver, uri)
        val fileSize = queryFileSize(resolver, uri)
        uploadMedia(uri, MessageType.FILE, fileName = fileName, fileSize = fileSize)
    }

    private fun uploadMedia(
        uri: Uri,
        type: MessageType,
        fileName: String?,
        fileSize: Long? = null,
        caption: String? = null,
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
        BackgroundUploadRegistry.upsert(chatId, pending)

        // Run on the process-global coordinator so the upload + send survives
        // the user leaving the chat screen or closing the app until it
        // completes / is cancelled manually. No progress notification is posted;
        // in-app progress is driven via the pendingUploads UI state; the
        // foreground notification intentionally uses neutral shell wording.
        val job = BackgroundWorkCoordinator.launch(getApplication<Application>()) {
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
                    val tooLarge = error is VideoUploadProcessor.VideoTooLargeException
                    state.copy(
                        pendingUploads = state.pendingUploads.map { upload ->
                            if (upload.localId == localId) {
                                upload.copy(stage = UploadStage.FAILED, errorMessage = errorMessage)
                            } else upload
                        },
                        errorMessage = if (tooLarge) null else errorMessage,
                        videoTooLargeMessage = if (tooLarge) errorMessage else state.videoTooLargeMessage,
                    )
                }
                activeUploadMessageIds.remove(localId)
                return@launch
            }

            val (uploadUri, uploadSize) = preparedMedia.getOrThrow()

            updatePendingUpload(localId) { it.copy(stage = UploadStage.UPLOADING, progress = 0f) }

            val uploadResult = storageRepository.uploadChatMedia(
                chatId = chatId,
                messageId = messageId,
                localUri = uploadUri,
                contentResolver = resolver
            ) { progress ->
                updatePendingUpload(localId) { it.copy(stage = UploadStage.UPLOADING, progress = progress) }
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
                        caption = caption,
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
                    BackgroundUploadRegistry.remove(localId)
                },
                onFailure = { error ->
                    if (error is CancellationException) {
                        removePendingUpload(localId, messageId)
                        return@fold
                    }
                    _uiState.update { state ->
                        state.copy(
                            pendingUploads = state.pendingUploads.map { upload ->
                                if (upload.localId == localId) {
                                    upload.copy(stage = UploadStage.FAILED, errorMessage = error.message ?: "Upload failed")
                                } else upload
                            },
                            errorMessage = error.message ?: "Upload failed"
                        )
                    }
                    }
            )
            activeUploadJobs.remove(localId)
            activeUploadMessageIds.remove(localId)
        }
        activeUploadJobs[localId] = job
        BackgroundUploadRegistry.setCanceler(localId) {
            job.cancel()
            activeUploadMessageIds[localId]?.let(storageRepository::cancelUpload)
        }
    }

    private fun updatePendingUpload(
        localId: String,
        transform: (PendingUpload) -> PendingUpload,
    ) {
        _uiState.update { state ->
            state.copy(
                pendingUploads = state.pendingUploads.map { upload ->
                    if (upload.localId == localId) transform(upload) else upload
                },
            )
        }
        BackgroundUploadRegistry.update(chatId, localId, transform)
    }

    private fun removePendingUpload(localId: String, messageId: String) {
        activeUploadJobs.remove(localId)
        activeUploadMessageIds.remove(localId)
        storageRepository.cancelUpload(messageId)
        _uiState.update { state ->
            state.copy(pendingUploads = state.pendingUploads.filterNot { it.localId == localId })
        }
        BackgroundUploadRegistry.remove(localId)
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

        // Render the deletion before media cleanup or the Firestore write.
        // unsendMessage removes media first, which can otherwise leave the
        // original message visible for several seconds.
        optimisticallyDeletedMessageIds.addAll(ownMessageIds)
        _uiState.update {
            it.copy(
                messageSelectionMode = false,
                selectedMessageIds = emptySet(),
            )
        }
        publishMessages(immediate = true)

        viewModelScope.launch {
            ownMessageIds.forEach { messageId ->
                messageRepository.unsendMessage(chatId, messageId)
                    .onFailure { error ->
                        optimisticallyDeletedMessageIds.remove(messageId)
                        publishMessages(immediate = true)
                        _uiState.update {
                            it.copy(errorMessage = messagingErrorMessage(error))
                        }
                    }
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

    fun dismissVideoTooLargeDialog() {
        _uiState.update { it.copy(videoTooLargeMessage = null) }
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
                            otherAvatarFullUrl = profile?.avatarFullUrl,
                            otherAvatarSharedUrl = profile?.avatarSharedUrl ?: profile?.avatarFullUrl,
                        )
                    }
                }
        }
    }

    private fun observePresence(otherUserId: String) {
        presenceRefreshJob?.cancel()
        presenceRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(PRESENCE_REFRESH_INTERVAL_MS)
                latestPresence?.let { presence ->
                    _uiState.update {
                        it.copy(isOtherOnline = PresenceRepository.isOnline(presence))
                    }
                }
            }
        }
        viewModelScope.launch {
            presenceRepository.observePresence(otherUserId)
                .catch {
                    latestPresence = null
                    _uiState.update { it.copy(isOtherTyping = false, isOtherOnline = false) }
                }
                .collect { presence ->
                    latestPresence = presence
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
                    captureExpiredUnreadCount(recent, userId)
                    // Keep the optimistic placeholder until the remote
                    // snapshot confirms the deleted flag, so a slow listener
                    // cannot briefly restore the original message.
                    optimisticallyDeletedMessageIds.removeAll { messageId ->
                        recent.any { it.id == messageId && it.deleted }
                    }
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

    private fun captureExpiredUnreadCount(messages: List<ChatMessage>, userId: String) {
        val count = messages.count { message ->
            message.senderId != userId &&
                !message.read &&
                !message.deleted &&
                message.type != MessageType.SESSION &&
                message.type != MessageType.SYSTEM &&
                message.unseenExpiresAt?.let(MessageExpiryUtil::isExpired) == true
        }
        if (count > 0) {
            _uiState.update { it.copy(expiredUnreadCount = maxOf(it.expiredUnreadCount, count)) }
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
        presenceRefreshJob?.cancel()
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

    private fun publishMessages(immediate: Boolean = false) {
        publishJob?.cancel()
        publishJob = viewModelScope.launch {
            if (!immediate) delay(PUBLISH_DEBOUNCE_MS)
            val merged = mergeMessages(olderMessages, recentMessages)
            evictExpiredMedia(merged)
            val visible = merged
                .map { message ->
                    if (message.id in optimisticallyDeletedMessageIds) {
                        message.copy(deleted = true, content = "")
                    } else {
                        message
                    }
                }
                .filterNot { message -> isHiddenFromUi(message) }
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
        // Background uploads are intentionally not tied to this ViewModel.
        // Leaving a chat must not cancel the process-global transfer.
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
        private const val PRESENCE_REFRESH_INTERVAL_MS = 5_000L
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

/**
 * A media attachment the user is preparing to send. It is not uploaded until
 * the user presses send in the input bar (draft, never auto-sent).
 */
data class DraftMedia(
    val localId: String,
    val type: MessageType,
    val localUri: Uri,
    val fileName: String? = null,
    val fileSize: Long? = null,
)

data class ChatUiState(
    val currentUserId: String? = null,
    val otherUserId: String? = null,
    val otherDisplayName: String = "",
    val otherAvatarUrl: String? = null,
    val otherAvatarFullUrl: String? = null,
    val otherAvatarSharedUrl: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val likedMessageIds: Set<String> = emptySet(),
    val listEntries: List<ChatListEntry> = emptyList(),
    val pendingUploads: List<PendingUpload> = emptyList(),
    val draftMedia: List<DraftMedia> = emptyList(),
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
    val chatWallpaperEnabled: Boolean = true,
    val chatWallpaperOverlay: Int = com.codeshell.app.data.preferences.AppPreferences.CHAT_WALLPAPER_OVERLAY_DEFAULT,
    val wallpaperSource: String = com.codeshell.app.data.preferences.AppPreferences.WALLPAPER_SOURCE_AVATAR,
    val wallpaperVaultId: String? = null,
    val wallpaperVaultImageUri: Uri? = null,
    val isBlocking: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val hasMoreOlderMessages: Boolean = true,
    val errorMessage: String? = null,
    val sendErrorMessage: String? = null,
    val videoTooLargeMessage: String? = null,
    val expiredUnreadCount: Int = 0,
)
