package com.codeshell.app.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.UploadTask
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await

class StorageRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    private val activeUploadTasks = ConcurrentHashMap<String, UploadTask>()

    fun cancelUpload(messageId: String) {
        activeUploadTasks.remove(messageId)?.cancel()
    }

    suspend fun uploadChatMedia(
        chatId: String,
        messageId: String,
        localUri: Uri,
        contentResolver: ContentResolver,
        onProgress: (Float) -> Unit
    ): Result<String> {
        return try {
            val fileName = resolveFileName(contentResolver, localUri, messageId)
            val ref = storage.reference.child("chats/$chatId/$messageId/$fileName")
            val uploadTask = ref.putFile(localUri)
            activeUploadTasks[messageId] = uploadTask
            while (!uploadTask.isComplete) {
                if (!currentCoroutineContext().isActive) {
                    uploadTask.cancel()
                    activeUploadTasks.remove(messageId)
                    return Result.failure(CancellationException("Upload cancelled"))
                }
                val snapshot = uploadTask.snapshot
                val total = snapshot.totalByteCount
                if (total > 0L) {
                    onProgress(snapshot.bytesTransferred.toFloat() / total.toFloat())
                }
                delay(PROGRESS_POLL_MS)
            }
            activeUploadTasks.remove(messageId)
            uploadTask.await()
            if (uploadTask.isCanceled) {
                return Result.failure(CancellationException("Upload cancelled"))
            }
            onProgress(1f)
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: CancellationException) {
            activeUploadTasks.remove(messageId)
            Result.failure(e)
        } catch (e: Exception) {
            activeUploadTasks.remove(messageId)
            Result.failure(e)
        }
    }

    suspend fun uploadProfilePhoto(
        userId: String,
        localUri: Uri
    ): Result<String> {
        return try {
            val ref = storage.reference.child("users/$userId/profile/avatar.jpg")
            ref.putFile(localUri).await()
            Result.success(ref.downloadUrl.await().toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProfilePhoto(userId: String): Result<Unit> {
        return try {
            storage.reference.child("users/$userId/profile/avatar.jpg").delete().await()
            Result.success(Unit)
        } catch (e: StorageException) {
            if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                Result.success(Unit)
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessageMedia(chatId: String, messageId: String): Result<Unit> {
        return try {
            val folderRef = storage.reference.child("chats/$chatId/$messageId")
            val items = folderRef.listAll().await()
            items.items.forEach { it.delete().await() }
            items.prefixes.forEach { prefix ->
                prefix.listAll().await().items.forEach { it.delete().await() }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChatMedia(chatId: String): Result<Unit> {
        return try {
            val chatRef = storage.reference.child("chats/$chatId")
            deleteFolderRecursive(chatRef)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun deleteFolderRecursive(folderRef: com.google.firebase.storage.StorageReference) {
        val listing = folderRef.listAll().await()
        listing.items.forEach { it.delete().await() }
        listing.prefixes.forEach { deleteFolderRecursive(it) }
    }

    private fun resolveFileName(
        contentResolver: ContentResolver,
        uri: Uri,
        messageId: String
    ): String {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) return sanitizeFileName(name)
                }
            }
        return "$messageId.bin"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)
    }

    companion object {
        private const val PROGRESS_POLL_MS = 100L
    }
}
