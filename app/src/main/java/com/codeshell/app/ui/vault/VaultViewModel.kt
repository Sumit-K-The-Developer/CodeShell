package com.codeshell.app.ui.vault

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeshell.app.data.model.MessageType
import com.codeshell.app.data.model.VaultItem
import com.codeshell.app.data.repository.AuthRepository
import com.codeshell.app.data.repository.ChatRepository
import com.codeshell.app.data.repository.VaultRepository
import com.codeshell.app.data.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val vaultRepository = VaultRepository(application)
    private val chatRepository = ChatRepository()
    private val messageRepository = MessageRepository()
    private val authRepository = AuthRepository()

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    fun loadItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val items = vaultRepository.getAllItems()
            _uiState.update { it.copy(isLoading = false, items = items) }
        }
    }

    fun deleteItem(item: VaultItem) {
        viewModelScope.launch {
            vaultRepository.deleteItem(item)
            loadItems()
        }
    }

    fun saveToVault(url: String, fileName: String, mimeType: String, sourceChatId: String? = null, sourceMessageId: String? = null) {
        // Reset tracker state before starting a new save
        VaultSaveTracker.reset()
        _uiState.update { it.copy(isSaving = true, saveProgress = null, errorMessage = null) }

        // Start a foreground service so the download survives the ViewModel
        // being cleared (e.g. when the user navigates away from the media viewer).
        val context = getApplication<Application>()
        val intent = Intent(context, VaultSaveService::class.java).apply {
            putExtra(VaultSaveService.EXTRA_URL, url)
            putExtra(VaultSaveService.EXTRA_FILE_NAME, fileName)
            putExtra(VaultSaveService.EXTRA_MIME_TYPE, mimeType)
            putExtra(VaultSaveService.EXTRA_SOURCE_CHAT_ID, sourceChatId)
            putExtra(VaultSaveService.EXTRA_SOURCE_MESSAGE_ID, sourceMessageId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun saveToVaultFromUris(uris: List<Uri>, onAllComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            vaultRepository.saveFromUris(uris) { completed, total ->
                _uiState.update { it.copy(saveProgress = completed to total) }
            }.onSuccess { items ->
                loadItems()
                onAllComplete()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message) }
            }
            _uiState.update { it.copy(isSaving = false, saveProgress = null) }
        }
    }

    fun saveToVaultFromUri(uri: Uri, fileName: String, mimeType: String, sourceChatId: String? = null, sourceMessageId: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = vaultRepository.saveFromUri(uri, fileName, mimeType, sourceChatId, sourceMessageId)
            result.onSuccess {
                loadItems()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message) }
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    suspend fun getDecryptedBytes(item: VaultItem): Result<ByteArray> {
        return vaultRepository.getDecryptedBytes(item)
    }

    suspend fun getThumbnailBytes(item: VaultItem): Result<ByteArray> {
        return vaultRepository.getThumbnailBytes(item)
    }

    fun moveToGallery(item: VaultItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            vaultRepository.moveToGallery(item)
                .onSuccess {
                    _uiState.update { it.copy(exportSuccess = true, isExporting = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message, isExporting = false) }
                }
        }
    }

    fun sendToChat(vaultItem: VaultItem, chatId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, sendProgress = null) }
            val bytesResult = vaultRepository.getDecryptedBytes(vaultItem)
            bytesResult.onSuccess { bytes ->
                val extension = vaultItem.extension
                val mimeType = vaultItem.mimeType
                val tempFile = File(
                    getApplication<Application>().cacheDir,
                    "vault_send_${vaultItem.id}$extension"
                )
                tempFile.writeBytes(bytes)
                val uri = Uri.fromFile(tempFile)
                val currentUserId = authRepository.currentUser?.uid ?: return@launch
                val participantResult = messageRepository.getParticipantIds(chatId)
                val participantIds = participantResult.getOrNull() ?: return@launch
                val recipientId = participantIds.firstOrNull { it != currentUserId } ?: return@launch

                val type = if (vaultItem.isVideo) MessageType.VIDEO else MessageType.IMAGE

                messageRepository.sendMediaMessage(
                    chatId = chatId,
                    senderId = currentUserId,
                    recipientId = recipientId,
                    type = type,
                    contentUrl = uri.toString(),
                    fileName = vaultItem.fileName,
                )
                tempFile.deleteOnExit()
                _uiState.update { it.copy(sendProgress = 1 to 1, isSending = false, sendSuccess = true) }
            }.onFailure { error ->
                _uiState.update { it.copy(isSending = false, errorMessage = error.message) }
            }
        }
    }

    fun getApplicationCacheDir(): java.io.File {
        return getApplication<Application>().cacheDir
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
            itemsToMove.forEach { item ->
                vaultRepository.moveToGallery(item).onFailure { error ->
                    lastError = error.message
                }
            }
            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportSuccess = true,
                    selectionMode = false,
                    selectedItems = emptySet(),
                    errorMessage = lastError
                )
            }
        }
    }

    fun deleteSelectedItems() {
        val itemsToDelete = _uiState.value.items.filter { it.id in _uiState.value.selectedItems }
        if (itemsToDelete.isEmpty()) return

        viewModelScope.launch {
            itemsToDelete.forEach { item ->
                vaultRepository.deleteItem(item)
            }
            loadItems()
            _uiState.update { it.copy(selectionMode = false, selectedItems = emptySet()) }
        }
    }

    // Send vault item to chat (used when navigating from vault)
    fun sendVaultItemToChat(itemId: String, chatId: String) {
        val item = _uiState.value.items.find { it.id == itemId } ?: return
        sendToChat(vaultItem = item, chatId = chatId)
    }
}

data class VaultUiState(
    val items: List<VaultItem> = emptyList(),
    val isLoading: Boolean = false,
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
