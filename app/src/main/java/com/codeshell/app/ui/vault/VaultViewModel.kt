package com.codeshell.app.ui.vault

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeshell.app.data.model.MessageType
import com.codeshell.app.data.model.VaultItem
import com.codeshell.app.data.repository.AuthRepository
import com.codeshell.app.data.repository.VaultRepository
import com.codeshell.app.data.repository.MessageRepository
import com.codeshell.app.data.repository.StorageRepository
import com.codeshell.app.util.BackgroundWorkCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val vaultRepository = VaultRepository(application)
    private val messageRepository = MessageRepository()
    private val authRepository = AuthRepository()
    private val storageRepository = StorageRepository()

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    // Detached background jobs that must outlive this VM (vault saves, export).
    // Each is manually cancellable from the UI.
    private var urlSaveJob: Job? = null
    private var urisSaveJob: Job? = null
    private var sendJob: Job? = null

    // Items staged for deletion that can still be restored via the Undo action.
    // The real disk deletion is deferred until the undo window elapses.
    private var pendingDeleteJob: Job? = null

    fun loadItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            vaultRepository.getAllItemsResult().fold(
                onSuccess = { items ->
                    val pendingIds = PendingVaultDeletionRegistry.pendingIds()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = items.filterNot { item -> item.id in pendingIds },
                            loadError = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, loadError = error.message ?: "Could not load Vault")
                    }
                },
            )
        }
    }

    fun deleteItem(item: VaultItem) {
        stageDeletion(listOf(item))
    }

    /**
     * Save media sourced from a chat URL into the vault. Runs on the
     * process-global [BackgroundWorkCoordinator] so it persists after the
     * originating screen / ViewModel is destroyed and after the app is closed,
     * with a neutral foreground notification. In-app progress is published via
     * [VaultSaveTracker] (observed by [MediaViewerScreen]).
     */
    fun saveToVault(
        url: String,
        fileName: String,
        mimeType: String,
        sourceChatId: String? = null,
        sourceMessageId: String? = null,
    ): Job {
        // Cancel any in-flight URL save before starting a new one.
        urlSaveJob?.cancel()
        VaultSaveTracker.reset()
        _uiState.update { it.copy(isSaving = true, saveProgress = null, errorMessage = null) }

        val job = BackgroundWorkCoordinator.launch(getApplication<Application>()) {
            try {
                vaultRepository.saveFromUrlWithProgress(
                    url = url,
                    fileName = fileName,
                    mimeType = mimeType,
                    sourceChatId = sourceChatId,
                    sourceMessageId = sourceMessageId,
                    onProgress = { completed, total ->
                        val progress = if (total > 0) {
                            (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        VaultSaveTracker.saving(progress)
                    },
                ).onSuccess {
                    VaultSaveTracker.success(fileName)
                }.onFailure { error ->
                    VaultSaveTracker.error(error.message ?: "Save failed")
                }
            } catch (e: Throwable) {
                VaultSaveTracker.error(e.message ?: "Save failed")
            }
        }
        urlSaveJob = job
        return job
    }

    /** Cancels an in-flight URL save started by [saveToVault], if any. */
    fun cancelUrlSave() {
        urlSaveJob?.cancel()
        urlSaveJob = null
        VaultSaveTracker.reset()
        _uiState.update { it.copy(isSaving = false, saveProgress = null) }
    }

    fun saveToVaultFromUris(uris: List<Uri>, deleteSource: Boolean = true, onAllComplete: () -> Unit) {
        urisSaveJob?.cancel()
        _uiState.update { it.copy(isSaving = true, saveProgress = null, errorMessage = null) }

        urisSaveJob = BackgroundWorkCoordinator.launch(getApplication<Application>()) {
            try {
                vaultRepository.saveFromUris(uris, deleteSource = deleteSource) { completed, total ->
                    _uiState.update { it.copy(saveProgress = completed to total) }
                }.onSuccess {
                    loadItems()
                    onAllComplete()
                }.onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
            } catch (e: Throwable) {
                // A thrown exception must never escape and crash the process;
                // surface it as an in-app error instead.
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to save media") }
            } finally {
                _uiState.update { it.copy(isSaving = false, saveProgress = null) }
            }
        }
    }

    /** Cancels an in-flight gallery import, if any. */
    fun cancelUrisSave() {
        urisSaveJob?.cancel()
        urisSaveJob = null
        _uiState.update { it.copy(isSaving = false, saveProgress = null) }
    }

    fun saveToVaultFromUri(
        uri: Uri,
        fileName: String,
        mimeType: String,
        sourceChatId: String? = null,
        sourceMessageId: String? = null,
        deleteSource: Boolean = true,
        onComplete: (Result<Unit>) -> Unit = {},
    ) {
        urisSaveJob?.cancel()
        _uiState.update { it.copy(isSaving = true) }
        urisSaveJob = BackgroundWorkCoordinator.launch(getApplication<Application>()) {
            try {
                vaultRepository.saveFromUri(uri, fileName, mimeType, sourceChatId, sourceMessageId, deleteSource = deleteSource)
                    .onSuccess {
                        loadItems()
                        onComplete(Result.success(Unit))
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(errorMessage = error.message) }
                        onComplete(Result.failure(error))
                    }
            } catch (e: Throwable) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to save media") }
                onComplete(Result.failure(e))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    suspend fun getDecryptedBytes(item: VaultItem): Result<ByteArray> {
        return vaultRepository.getDecryptedBytes(item)
    }

    suspend fun getThumbnailBytes(item: VaultItem): Result<ByteArray> {
        return vaultRepository.getThumbnailBytes(item)
    }

    /**
     * Resolves the on-disk file for [item]. Must be called off the main thread
     * (it touches the filesystem). Returns null when the backing file is gone.
     */
    suspend fun getVaultFile(item: VaultItem): File? = withContext(Dispatchers.IO) {
        vaultRepository.getVaultFile(item)?.takeIf { it.exists() }
    }

    fun replaceImage(item: VaultItem, sourceUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            vaultRepository.replaceImage(item, sourceUri)
                .onSuccess { updated ->
                    _uiState.update { state ->
                        state.copy(
                            isSaving = false,
                            items = state.items
                                .map { if (it.id == updated.id) updated else it }
                                .sortedByDescending { it.dateSaved },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = error.message ?: "Could not update image")
                    }
                }
        }
    }

    fun moveToGallery(item: VaultItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            vaultRepository.moveToGallery(item)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            exportSuccess = true,
                            isExporting = false,
                            items = state.items.filterNot { it.id == item.id },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message, isExporting = false) }
            }
        }
    }

    fun copyToGallery(item: VaultItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, errorMessage = null) }
            vaultRepository.copyToGallery(item)
                .onSuccess {
                    _uiState.update { it.copy(exportSuccess = true, isExporting = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message, isExporting = false)
                    }
                }
        }
    }

    fun sendToChat(vaultItem: VaultItem, chatId: String) {
        sendJob?.cancel()
        _uiState.update { it.copy(isSending = true, sendProgress = null) }

        sendJob = BackgroundWorkCoordinator.launch(getApplication<Application>()) {
            try {
                sendVaultItemInternal(vaultItem, chatId)
                _uiState.update { it.copy(sendProgress = 1 to 1, isSending = false, sendSuccess = true) }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isSending = false, errorMessage = e.message ?: "Failed to send") }
            }
        }
    }

    /** Cancels an in-flight send-to-chat, if any. */
    fun cancelSend() {
        sendJob?.cancel()
        sendJob = null
        _uiState.update { it.copy(isSending = false, sendProgress = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, sendSuccess = false, exportSuccess = false) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItem = null, showChatPicker = false, selectionMode = false, selectedItems = emptySet()) }
    }

    fun selectItem(item: VaultItem?) {
        _uiState.update { it.copy(selectedItem = item) }
    }

    fun showChatPickerForItem(item: VaultItem) {
        _uiState.update { it.copy(selectedItem = item, showChatPicker = true) }
    }

    // Multi-select support
    fun toggleSelectionMode() {
        _uiState.update { it.copy(selectionMode = !it.selectionMode, selectedItems = if (it.selectionMode) emptySet() else it.selectedItems) }
    }

    fun toggleItemSelection(item: VaultItem) {
        _uiState.update { state ->
            val updated = state.selectedItems.toMutableSet()
            if (item.id in updated) {
                updated.remove(item.id)
            } else {
                updated.add(item.id)
            }
            state.copy(selectedItems = updated)
        }
    }

    fun clearItemSelection() {
        _uiState.update { it.copy(selectionMode = false, selectedItems = emptySet()) }
    }

    fun moveSelectedToGallery() {
        val itemsToMove = _uiState.value.items.filter { it.id in _uiState.value.selectedItems }
        if (itemsToMove.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            var lastError: String? = null
            val movedIds = mutableSetOf<String>()
            itemsToMove.forEach { item ->
                vaultRepository.moveToGallery(item)
                    .onSuccess { movedIds += item.id }
                    .onFailure { error ->
                        lastError = error.message
                    }
            }
            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportSuccess = movedIds.isNotEmpty(),
                    items = it.items.filterNot { item -> item.id in movedIds },
                    selectionMode = false,
                    selectedItems = emptySet(),
                    errorMessage = lastError
                )
            }
        }
    }

    fun toggleSelectAllItems() {
        _uiState.update { state ->
            val allIds = state.items.mapTo(mutableSetOf()) { it.id }
            val nextSelection = if (allIds.isNotEmpty() && state.selectedItems.size == allIds.size) {
                emptySet()
            } else {
                allIds
            }
            state.copy(selectedItems = nextSelection)
        }
    }

    fun copySelectedToGallery() {
        val itemsToCopy = _uiState.value.items.filter { it.id in _uiState.value.selectedItems }
        if (itemsToCopy.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, errorMessage = null) }
            var lastError: String? = null
            itemsToCopy.forEach { item ->
                vaultRepository.copyToGallery(item).onFailure { error ->
                    lastError = error.message
                }
            }
            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportSuccess = lastError == null,
                    selectionMode = false,
                    selectedItems = emptySet(),
                    errorMessage = lastError,
                )
            }
        }
    }

    fun deleteItemWithUndo(item: VaultItem) {
        stageDeletion(listOf(item))
    }

    fun deleteSelectedItems() {
        val itemsToDelete = _uiState.value.items.filter { it.id in _uiState.value.selectedItems }
        if (itemsToDelete.isEmpty()) return
        stageDeletion(itemsToDelete)
    }

    /**
     * Removes [items] from the UI immediately but defers the real disk
     * deletion until the undo window elapses, so the Undo action can restore
     * them. Any previously staged (not yet flushed) batch is deleted first so
     * repeated deletes never leak files back into the vault on restart.
     */
    private fun stageDeletion(items: List<VaultItem>) {
        pendingDeleteJob?.cancel()
        val stagedIds = items.mapTo(mutableSetOf()) { it.id }
        val token = PendingVaultDeletionRegistry.stage(items)
        _uiState.update { state ->
            state.copy(
                items = state.items.filterNot {
                    it.id in stagedIds || it.id in PendingVaultDeletionRegistry.pendingIds()
                },
                selectionMode = false,
                selectedItems = emptySet(),
            )
        }
        pendingDeleteJob = BackgroundWorkCoordinator.launch(getApplication<Application>()) {
            delay(DELETE_UNDO_WINDOW_MS)
            val toDelete = PendingVaultDeletionRegistry.flush(token)
            toDelete.forEach { item ->
                vaultRepository.deleteItem(item).onFailure { error ->
                    _uiState.update { state ->
                        state.copy(errorMessage = error.message ?: "Could not delete ${item.fileName}")
                    }
                }
            }
        }
    }

    /** Restores the most recently staged (undo-pending) deletion, if any. */
    fun undoPendingDelete() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        val restored = PendingVaultDeletionRegistry.undo()
        if (restored.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                items = (restored + state.items)
                    .distinctBy { it.id }
                    .sortedByDescending { it.dateSaved },
            )
        }
    }

    // Send vault item to chat (used when navigating from vault)
    fun sendVaultItemToChat(itemId: String, chatId: String) {
        val item = _uiState.value.items.find { it.id == itemId } ?: return
        sendToChat(vaultItem = item, chatId = chatId)
    }

    /**
     * Sends multiple vault items to the same chat in sequence. Used by the
     * vault multiselect "Send to Chat" flow: the user picks several items, then
     * selects a single chat; each item is loaded and uploaded on the
     * background coordinator (persists after leaving the screen / closing the
     * app, no notification). Clears the multiselect state once dispatched.
     */
    fun sendVaultItemsToChat(itemIds: List<String>, chatId: String) {
        val itemsToSend = itemIds.mapNotNull { id ->
            _uiState.value.items.find { it.id == id }
        }.ifEmpty { return }
        sendJob?.cancel()
        _uiState.update { it.copy(isSending = true, sendProgress = 0 to itemsToSend.size, errorMessage = null) }
        sendJob = BackgroundWorkCoordinator.launch(getApplication<Application>()) {
            var sent = 0
            var lastError: String? = null
            itemsToSend.forEach { item ->
                runCatching { sendVaultItemInternal(item, chatId) }
                    .onSuccess {
                        sent++
                        _uiState.update { it.copy(sendProgress = sent to itemsToSend.size) }
                    }
                    .onFailure { lastError = it.message }
            }
            _uiState.update {
                it.copy(
                    isSending = false,
                    sendSuccess = lastError == null,
                    errorMessage = lastError,
                )
            }
        }
        _uiState.update { it.copy(selectionMode = false, selectedItems = emptySet()) }
    }

    private suspend fun sendVaultItemInternal(vaultItem: VaultItem, chatId: String) {
        val bytes = vaultRepository.getDecryptedBytes(vaultItem).getOrThrow()
        val currentUserId = authRepository.currentUser?.uid ?: error("Not signed in")
        val participantIds = messageRepository.getParticipantIds(chatId).getOrThrow()
        val recipientId = participantIds.firstOrNull { it != currentUserId } ?: error("Recipient not found")
        val messageId = UUID.randomUUID().toString()
        val file = File(getApplication<Application>().cacheDir, "vault_send_$messageId${vaultItem.extension}")
        try {
            file.writeBytes(bytes)
            val uploadResult = storageRepository.uploadChatMedia(
                chatId = chatId,
                messageId = messageId,
                localUri = Uri.fromFile(file),
                contentResolver = getApplication<Application>().contentResolver,
                onProgress = {},
            )
            val url = uploadResult.getOrThrow()
            messageRepository.sendMediaMessage(
                chatId = chatId,
                senderId = currentUserId,
                recipientId = recipientId,
                type = if (vaultItem.isVideo) MessageType.VIDEO else MessageType.IMAGE,
                contentUrl = url,
                fileName = vaultItem.fileName,
                messageId = messageId,
            ).getOrThrow()
        } finally {
            runCatching { file.delete() }
        }
    }

    companion object {
        // Give the Snackbar's Undo action a chance to run before the real
        // disk deletion is performed (SnackbarDuration.Short is ~4s).
        private const val DELETE_UNDO_WINDOW_MS = 5_000L
    }
}

data class VaultUiState(
    val items: List<VaultItem> = emptyList(),
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val isSaving: Boolean = false,
    val saveProgress: Pair<Int, Int>? = null, // completed/total
    val isSending: Boolean = false,
    val sendProgress: Pair<Int, Int>? = null, // completed/total
    val isExporting: Boolean = false,
    val selectedItem: VaultItem? = null,
    val showChatPicker: Boolean = false,
    val sendSuccess: Boolean = false,
    val exportSuccess: Boolean = false,
    val errorMessage: String? = null,
    val selectionMode: Boolean = false,
    val selectedItems: Set<String> = emptySet(),
)
