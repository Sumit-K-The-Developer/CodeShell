package com.codeshell.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.codeshell.app.data.model.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class VaultRepository(private val context: Context) {

    private val vaultDir: File
        get() = File(context.filesDir, "vault").also { it.mkdirs() }

    private val thumbnailsDir: File
        get() = File(vaultDir, "thumbnails").also { it.mkdirs() }

    private val metadataFile: File
        get() = File(vaultDir, "vault_metadata.json")

    private val json = Json { ignoreUnknownKeys = true }

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
        try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.connect()

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

            val file = File(vaultDir, "$id$extension")
            var downloadedBytes = 0L

            file.outputStream().use { output ->
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

            val fileSize = file.length()
            val thumbnailFile = File(thumbnailsDir, "$id.jpg")
            try {
                generateThumbnailStreaming(file, mimeType, thumbnailFile)
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
            Result.failure(Exception("Failed to download media: ${e.message}", e))
        }
    }

    suspend fun saveFromUri(uri: Uri, fileName: String, mimeType: String, sourceChatId: String? = null, sourceMessageId: String? = null): Result<VaultItem> = withContext(Dispatchers.IO) {
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

            val file = File(vaultDir, "$id$extension")
            inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val fileSize = file.length()
            val thumbnailFile = File(thumbnailsDir, "$id.jpg")
            // Generate thumbnail using streaming to avoid OOM on large files
            try {
                generateThumbnailStreaming(file, mimeType, thumbnailFile)
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
            when {
                mimeType.startsWith("image/") -> {
                    // Decode directly from file with sampling to avoid OOM on large images
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath, this)
                        inSampleSize = calculateSampleSize(outWidth, outHeight, 100, 100)
                        inJustDecodeBounds = false
                    }
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                    bitmap?.let {
                        outputFile.outputStream().use { out ->
                            it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        it.recycle()
                    }
                }
                mimeType.startsWith("video/") -> {
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
                            outputFile.outputStream().use { out ->
                                it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                            }
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

    suspend fun saveFromUris(uris: List<Uri>, onProgress: (completed: Int, total: Int) -> Unit): Result<List<VaultItem>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VaultItem>()
        val errors = mutableListOf<String>()
        uris.forEachIndexed { index, uri ->
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val fileName = getFileNameFromUri(uri) ?: "file_${index + 1}"
                saveFromUri(uri, fileName, mimeType).onSuccess { item ->
                    results.add(item)
                }.onFailure { error ->
                    errors.add("Item ${index + 1}: ${error.message}")
                }
            } catch (e: Throwable) {
                errors.add("Item ${index + 1}: ${e.message}")
            }
            onProgress(index + 1, uris.size)
        }
        if (results.isEmpty() && errors.isNotEmpty()) {
            Result.failure(Exception(errors.joinToString("; ")))
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

    suspend fun moveToGallery(item: VaultItem): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val file = findEncryptedFile(item)
            if (file == null || !file.exists()) {
                return@withContext Result.failure(Exception("File not found"))
            }

            val bytes = file.readBytes()
            val collection = when {
                item.isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                item.isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values)
                ?: return@withContext Result.failure(Exception("Could not save to gallery"))

            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: return@withContext Result.failure(Exception("Could not write to gallery"))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllItems(): List<VaultItem> = withContext(Dispatchers.IO) {
        try {
            if (!metadataFile.exists()) return@withContext emptyList()
            val text = metadataFile.readText()
            json.decodeFromString<List<VaultItem>>(text).sortedByDescending { it.dateSaved }
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteItem(item: VaultItem) = withContext(Dispatchers.IO) {
        try {
            findEncryptedFile(item)?.delete()
            File(thumbnailsDir, "${item.id}.jpg").delete()
            removeFromMetadata(item.id)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun findEncryptedFile(item: VaultItem): File? {
        // Look for file starting with the item's id
        return vaultDir.listFiles()?.firstOrNull { it.name.startsWith(item.id) }
    }

    private fun appendToMetadata(item: VaultItem) {
        val items = try {
            if (metadataFile.exists()) {
                json.decodeFromString<List<VaultItem>>(metadataFile.readText()).toMutableList()
            } else mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
        items.add(item)
        metadataFile.writeText(json.encodeToString(items))
    }

    private fun removeFromMetadata(id: String) {
        val items = try {
            if (metadataFile.exists()) {
                json.decodeFromString<List<VaultItem>>(metadataFile.readText()).toMutableList()
            } else mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
        items.removeAll { it.id == id }
        metadataFile.writeText(json.encodeToString(items))
    }
}
