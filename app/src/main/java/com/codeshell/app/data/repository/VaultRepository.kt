package com.codeshell.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.codeshell.app.data.model.VaultItem
import com.codeshell.app.util.FALLBACK_MIME
import com.codeshell.app.util.mimeTypeForFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.ByteArrayInputStream
import java.util.UUID

class VaultRepository(private val context: Context) {

    private val vaultDir: File
        get() = File(context.filesDir, "vault").also { it.mkdirs() }

    private val thumbnailsDir: File
        get() = File(vaultDir, "thumbnails").also { it.mkdirs() }

    private val metadataFile: File
        get() = File(vaultDir, "vault_metadata.json")

    private val json = Json { ignoreUnknownKeys = true }
    private val metadataLock = Any()

    suspend fun saveFromUrl(url: String, fileName: String, mimeType: String, sourceChatId: String? = null, sourceMessageId: String? = null): Result<VaultItem> =
        saveFromUrlWithProgress(url, fileName, mimeType, sourceChatId, sourceMessageId) { _, _ -> }

    /**
     * Downloads [url] in chunks and reports progress via [onProgress].
     *
     * The download is performed as a streaming chunked copy so that the entire
     * file is never held in memory at once.  This is important for large media
     * files and also allows the caller (e.g. a foreground service) to display a
     * progress bar.
     */
    suspend fun saveFromUrlWithProgress(
        url: String,
        fileName: String,
        mimeType: String,
        sourceChatId: String? = null,
        sourceMessageId: String? = null,
        onProgress: (completed: Long, total: Long) -> Unit,
    ): Result<VaultItem> = withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        var file: java.io.File? = null
        try {
            connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.connect()
            val code = connection.responseCode
            if (code != java.net.HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext Result.failure(Exception("Download failed (HTTP $code)"))
            }

            val totalBytes = connection.contentLength.toLong()
            val inputStream = connection.inputStream

            val id = UUID.randomUUID().toString()
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val extension = if (fileName.contains('.')) ".${fileName.substringAfterLast('.')}" else ""
            if (extension.length > 20) {
                connection.disconnect()
                return@withContext Result.failure(Exception("File extension too long"))
            }

            vaultDir.mkdirs()
            thumbnailsDir.mkdirs()

            file = File(vaultDir, "$id$extension")
            val targetFile = file
            var downloadedBytes = 0L

            targetFile.outputStream().use { output ->
                inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        onProgress(downloadedBytes, totalBytes)
                    }
                }
            }
            connection.disconnect()

            val fileSize = targetFile.length()
            val thumbnailFile = File(thumbnailsDir, "$id.jpg")
            try {
                generateThumbnailStreaming(targetFile, mimeType, thumbnailFile)
            } catch (e: Throwable) {
                // Thumbnail generation failed - skip silently
            }

            val item = VaultItem(
                id = id,
                fileName = safeName,
                mimeType = mimeType,
                dateSaved = System.currentTimeMillis(),
                fileSize = fileSize,
                sourceChatId = sourceChatId,
                sourceMessageId = sourceMessageId,
            )
            appendToMetadata(item)
            Result.success(item)
        } catch (e: Throwable) {
            // Don't leave a partial download lying around in the vault.
            file?.let { partial -> if (partial.exists()) runCatching { partial.delete() } }
            Result.failure(Exception("Failed to download media: ${e.message}", e))
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Copies a content URI into the vault. When [deleteSource] is true (the
     * default for gallery imports) the source URI is removed afterwards on a
     * best-effort basis, turning the copy into a move — see [deleteSourceUri]
     * for permission caveats on Android 13+ photo-picker URIs.
     */
    suspend fun saveFromUri(
        uri: Uri,
        fileName: String,
        mimeType: String,
        sourceChatId: String? = null,
        sourceMessageId: String? = null,
        deleteSource: Boolean = true,
        importedFromGallery: Boolean = false,
    ): Result<VaultItem> = withContext(Dispatchers.IO) {
        var file: java.io.File? = null
        try {
            val id = UUID.randomUUID().toString()
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val extension = if (fileName.contains('.')) ".${fileName.substringAfterLast('.')}" else ""
            if (extension.length > 20) {
                return@withContext Result.failure(Exception("File extension too long"))
            }

            vaultDir.mkdirs()
            thumbnailsDir.mkdirs()

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Could not open URI for reading"))

            file = File(vaultDir, "$id$extension")
            val targetFile = file
            inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val fileSize = targetFile.length()
            val thumbnailFile = File(thumbnailsDir, "$id.jpg")
            // Generate thumbnail using streaming to avoid OOM on large files
            try {
                generateThumbnailStreaming(targetFile, mimeType, thumbnailFile)
            } catch (e: Throwable) {
                // Thumbnail generation failed - skip silently
            }

            val item = VaultItem(
                id = id,
                fileName = safeName,
                mimeType = mimeType,
                dateSaved = System.currentTimeMillis(),
                fileSize = fileSize,
                sourceChatId = sourceChatId,
                sourceMessageId = sourceMessageId,
                importedFromGallery = importedFromGallery,
            )
            appendToMetadata(item)

            // True move: drop the source gallery entry after the vault copy is
            // safely committed. Never blocking — see [deleteSourceUri].
            if (deleteSource) {
                deleteSourceUri(uri)
            }
            Result.success(item)
        } catch (e: Throwable) {
            // Clean up a half-written partial file so the vault doesn't leak bytes.
            file?.let { partial -> if (partial.exists()) runCatching { partial.delete() } }
            Result.failure(Exception("Failed to save media: ${e.message}", e))
        }
    }

    private suspend fun saveBytes(bytes: ByteArray, fileName: String, mimeType: String, sourceChatId: String? = null, sourceMessageId: String? = null): Result<VaultItem> = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val extension = if (fileName.contains('.')) ".${fileName.substringAfterLast('.')}" else ""
            if (extension.length > 20) {
                return@withContext Result.failure(Exception("File extension too long"))
            }

            vaultDir.mkdirs()
            thumbnailsDir.mkdirs()

            val file = File(vaultDir, "$id$extension")
            file.writeBytes(bytes)

            val thumbnailFile = File(thumbnailsDir, "$id.jpg")
            generateThumbnail(bytes, mimeType, thumbnailFile)

            val item = VaultItem(
                id = id,
                fileName = safeName,
                mimeType = mimeType,
                dateSaved = System.currentTimeMillis(),
                fileSize = bytes.size.toLong(),
                sourceChatId = sourceChatId,
                sourceMessageId = sourceMessageId,
            )
            appendToMetadata(item)
            Result.success(item)
        } catch (e: Throwable) {
            Result.failure(Exception("Failed to save media: ${e.message}", e))
        }
    }

    private fun generateThumbnailStreaming(file: File, mimeType: String, outputFile: File) {
        try {
            val effectiveMimeType = if (mimeType.contains("/") && mimeType != FALLBACK_MIME) {
                mimeType
            } else {
                mimeTypeForFileName(file.name)
            }
            when {
                effectiveMimeType.startsWith("image/") -> {
                    // Decode directly from file with sampling to avoid OOM on large images
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath, this)
                        inSampleSize = calculateSampleSize(outWidth, outHeight, 100, 100)
                        inJustDecodeBounds = false
                    }
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                    bitmap?.let {
                        val oriented = applyExifOrientation(
                            it,
                            runCatching {
                                ExifInterface(file.absolutePath).getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL,
                                )
                            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL),
                        )
                        outputFile.outputStream().use { out ->
                            oriented.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        if (oriented !== it) oriented.recycle()
                        it.recycle()
                    }
                }
                effectiveMimeType.startsWith("video/") -> {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        val bitmap = retriever.frameAtTime
                        bitmap?.let {
                            outputFile.outputStream().use { out ->
                                it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                            }
                            it.recycle()
                        }
                    } catch (e: Exception) {
                    } finally {
                        retriever.release()
                    }
                }
            }
        } catch (e: Throwable) {
        }
    }

    private fun generateThumbnail(bytes: ByteArray, mimeType: String, outputFile: File) {
        try {
            when {
                mimeType.startsWith("image/") -> {
                    // For images, decode and scale down
                    android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
                        inSampleSize = calculateSampleSize(outWidth, outHeight, 100, 100)
                        inJustDecodeBounds = false
                    }.let { options ->
                         val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                         bitmap?.let {
                             val oriented = applyExifOrientation(
                                 it,
                                 runCatching {
                                     ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                                         ExifInterface.TAG_ORIENTATION,
                                         ExifInterface.ORIENTATION_NORMAL,
                                     )
                                 }.getOrDefault(ExifInterface.ORIENTATION_NORMAL),
                             )
                             outputFile.outputStream().use { out ->
                                 oriented.compress(Bitmap.CompressFormat.JPEG, 80, out)
                             }
                             if (oriented !== it) oriented.recycle()
                             it.recycle()
                        }
                    }
                }
                mimeType.startsWith("video/") -> {
                    // For videos, extract keyframe using MediaMetadataRetriever
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        // Create a temporary file for the video to read from
                        val tempVideo = File(context.cacheDir, "temp_thumb_${System.currentTimeMillis()}.tmp")
                        tempVideo.writeBytes(bytes)
                        retriever.setDataSource(tempVideo.absolutePath)
                        val bitmap = retriever.frameAtTime
                        bitmap?.let {
                            outputFile.outputStream().use { out ->
                                it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                            }
                            it.recycle()
                        }
                    } catch (e: Exception) {
                        // Video thumbnail generation failed, file will be created empty
                    } finally {
                        retriever.release()
                    }
                }
            }
        } catch (e: Exception) {
            // Thumbnail generation failed silently - file won't have a preview
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    suspend fun saveFromUris(uris: List<Uri>, deleteSource: Boolean = true, onProgress: (completed: Int, total: Int) -> Unit): Result<List<VaultItem>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VaultItem>()
        val errors = mutableListOf<String>()
        uris.forEachIndexed { index, uri ->
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val fileName = getFileNameFromUri(uri) ?: "file_${index + 1}"
                saveFromUri(
                    uri,
                    fileName,
                    mimeType,
                    deleteSource = deleteSource,
                    importedFromGallery = true,
                ).onSuccess { item ->
                    results.add(item)
                }.onFailure { error ->
                    errors.add("Item ${index + 1}: ${error.message}")
                }
            } catch (e: Throwable) {
                errors.add("Item ${index + 1}: ${e.message}")
            }
            onProgress(index + 1, uris.size)
        }
        if (errors.isNotEmpty()) {
            val savedCount = results.size
            Result.failure(Exception("Saved $savedCount of ${uris.size} items. ${errors.joinToString("; ")}"))
        } else {
            Result.success(results)
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else null
                }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Moves [item] out of the vault into the system gallery via [MediaStore].
     *
     * This is a true **move**: after the MediaStore insert succeeds the vault
     * file, its thumbnail and its metadata entry are deleted, so the media is
     * no longer accessible from the vault page. If the gallery insert fails the
     * vault item is left intact.
     */
    suspend fun moveToGallery(item: VaultItem): Result<Uri> =
        copyItemToGallery(item, deleteSource = true)

    suspend fun copyToGallery(item: VaultItem): Result<Uri> =
        copyItemToGallery(item, deleteSource = false)

    private suspend fun copyItemToGallery(
        item: VaultItem,
        deleteSource: Boolean,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        var insertedUri: Uri? = null
        try {
            val vaultFile = findEncryptedFile(item)
            if (vaultFile == null || !vaultFile.exists()) {
                return@withContext Result.failure(Exception("File not found"))
            }

            val bytes = vaultFile.readBytes()
            val collection = when {
                item.isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                item.isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    item.mimeType.takeIf { it.contains("/") && it != "application/octet-stream" }
                        ?: mimeTypeForFileName(item.fileName),
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            insertedUri = resolver.insert(collection, values)
                ?: return@withContext Result.failure(Exception("Could not save to gallery"))

            resolver.openOutputStream(insertedUri)?.use { it.write(bytes) }
                ?: return@withContext Result.failure(Exception("Could not write to gallery"))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(insertedUri, values, null, null)
            }

            if (deleteSource) {
                // Move completed: drop the vault copy so the item is no longer
                // accessible from the vault (it now lives in the gallery only).
                vaultFile.delete()
                File(thumbnailsDir, "${item.id}.jpg").delete()
                removeFromMetadata(item.id)
            }

            Result.success(insertedUri)
        } catch (e: Throwable) {
            // Don't leave an orphaned IS_PENDING=1 MediaStore row if writing failed.
            insertedUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            // Never delete the vault source on failure — the user can retry.
            Result.failure(e)
        }
    }

    suspend fun getAllItems(): List<VaultItem> = withContext(Dispatchers.IO) {
        try {
            synchronized(metadataLock) {
                if (!metadataFile.exists()) return@withContext emptyList()
                val text = metadataFile.readText()
                json.decodeFromString<List<VaultItem>>(text).sortedByDescending { it.dateSaved }
            }
        } catch (e: Throwable) {
            // Catch Throwable (incl. Error) so a malformed/corrupt metadata
            // file can never crash loadItems() (which runs right after a save).
            emptyList()
        }
    }

    suspend fun getDecryptedBytes(item: VaultItem): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val file = findEncryptedFile(item)
            if (file == null || !file.exists()) {
                return@withContext Result.failure(Exception("File not found"))
            }
            // No decryption needed - files are stored as-is
            Result.success(file.readBytes())
        } catch (e: Throwable) {
            // Catch Throwable (incl. OutOfMemoryError) so a malformed/large file
            // surfaces as a Dialog error instead of crashing the whole app.
            Result.failure(e)
        }
    }

    suspend fun getThumbnailBytes(item: VaultItem): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val thumbnailFile = File(thumbnailsDir, "${item.id}.jpg")
            if (thumbnailFile.exists()) {
                Result.success(thumbnailFile.readBytes())
            } else {
                Result.failure(Exception("No thumbnail"))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun deleteItem(item: VaultItem): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val vaultFile = findEncryptedFile(item)
            if (vaultFile?.exists() == true && !vaultFile.delete()) {
                return@withContext Result.failure(Exception("Could not delete ${item.fileName}"))
            }
            val thumbnail = File(thumbnailsDir, "${item.id}.jpg")
            if (thumbnail.exists() && !thumbnail.delete()) {
                return@withContext Result.failure(Exception("Could not delete ${item.fileName} thumbnail"))
            }
            removeFromMetadata(item.id)
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setRotate(180f).also { matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> matrix.setRotate(90f).also { matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> matrix.setRotate(-90f).also { matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun findEncryptedFile(item: VaultItem): File? {
        // Look for file starting with the item's id
        return vaultDir.listFiles()?.firstOrNull { it.name.startsWith(item.id) }
    }

    /**
     * Best-effort deletion of a content URI's backing document. Used to turn a
     * gallery→vault copy into a true move.
     *
     * On Android 13+ the photo picker returns read-only URIs that the system
     * will not let an arbitrary app delete; in that case this is a silent
     * no-op and the source stays in the gallery (the vault still gets its
     * copy). Where the granted URI *does* allow write (e.g. SAF / OpenDocument
     * fallbacks, or media the app owns), the original is removed.
     */
    private fun deleteSourceUri(uri: Uri): Boolean {
        return try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Throwable) {
            // SecurityException / UnsupportedOperationException on read-only
            // picker URIs — expected on Android 13+ photo picker. Swallow.
            false
        }
    }

    /**
     * Returns the on-disk vault file backing [item], or null if it cannot be
     * located. Exposed so the UI can hand the path directly to ExoPlayer /
     * BitmapFactory instead of loading the whole file into memory.
     */
fun getVaultFile(item: VaultItem): File? = findEncryptedFile(item)

    /** Resolves the on-disk vault file for an item by its id (e.g. a stored chat wallpaper id). */
    fun getVaultFileById(id: String): File? =
        vaultDir.listFiles()?.firstOrNull { it.name.startsWith(id) }

    suspend fun replaceImage(item: VaultItem, sourceUri: Uri): Result<VaultItem> =
        withContext(Dispatchers.IO) {
            try {
                val target = findEncryptedFile(item)
                    ?: return@withContext Result.failure(Exception("File not found"))
                val source = context.contentResolver.openInputStream(sourceUri)
                    ?: return@withContext Result.failure(Exception("Could not read edited image"))
                val temporary = File(vaultDir, ".${item.id}_edited.tmp")
                source.use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
                target.outputStream().use { output -> temporary.inputStream().use { input -> input.copyTo(output) } }
                temporary.delete()

                val thumbnail = File(thumbnailsDir, "${item.id}.jpg")
                runCatching { generateThumbnailStreaming(target, item.mimeType, thumbnail) }
                val updated = item.copy(
                    fileSize = target.length(),
                    contentVersion = System.currentTimeMillis(),
                )
                replaceInMetadata(updated)
                Result.success(updated)
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }

    private fun appendToMetadata(item: VaultItem) = synchronized(metadataLock) {
        val items = try {
            if (metadataFile.exists()) {
                json.decodeFromString<List<VaultItem>>(metadataFile.readText()).toMutableList()
            } else mutableListOf()
        } catch (e: Throwable) {
            mutableListOf()
        }
        items.add(item)
        metadataFile.writeText(json.encodeToString(items))
    }

    private fun removeFromMetadata(id: String) = synchronized(metadataLock) {
        val items = try {
            if (metadataFile.exists()) {
                json.decodeFromString<List<VaultItem>>(metadataFile.readText()).toMutableList()
            } else mutableListOf()
        } catch (e: Throwable) {
            mutableListOf()
        }
        items.removeAll { it.id == id }
        metadataFile.writeText(json.encodeToString(items))
    }

    private fun replaceInMetadata(updated: VaultItem) = synchronized(metadataLock) {
        val items = if (metadataFile.exists()) {
            json.decodeFromString<List<VaultItem>>(metadataFile.readText()).toMutableList()
        } else {
            mutableListOf()
        }
        val index = items.indexOfFirst { it.id == updated.id }
        if (index >= 0) items[index] = updated
        metadataFile.writeText(json.encodeToString(items))
    }
}
