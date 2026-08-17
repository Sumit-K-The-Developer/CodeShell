package com.codeshell.app.ui.chat

import com.codeshell.app.data.model.PendingUpload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-global view of active chat uploads. The transfer itself is owned by
 * BackgroundWorkCoordinator; this registry lets a newly-created ChatViewModel
 * restore the pending row after the previous chat screen was popped.
 */
object BackgroundUploadRegistry {

    private data class Entry(
        val chatId: String,
        val upload: PendingUpload,
    )

    private val uploads = MutableStateFlow<Map<String, Entry>>(emptyMap())
    private val cancelers = ConcurrentHashMap<String, () -> Unit>()

    fun observe(chatId: String): Flow<List<PendingUpload>> = uploads
        .map { entries ->
            entries.values
                .filter { it.chatId == chatId }
                .map { it.upload }
                .sortedBy { it.localId }
        }
        .distinctUntilChanged()

    fun upsert(chatId: String, upload: PendingUpload) {
        uploads.update { it + (upload.localId to Entry(chatId, upload)) }
    }

    fun update(chatId: String, localId: String, transform: (PendingUpload) -> PendingUpload) {
        uploads.update { entries ->
            val entry = entries[localId]
            if (entry == null || entry.chatId != chatId) entries
            else entries + (localId to entry.copy(upload = transform(entry.upload)))
        }
    }

    fun remove(localId: String) {
        cancelers.remove(localId)
        uploads.update { it - localId }
    }

    fun setCanceler(localId: String, cancel: () -> Unit) {
        cancelers[localId] = cancel
    }

    fun cancel(localId: String) {
        cancelers[localId]?.invoke()
    }
}
