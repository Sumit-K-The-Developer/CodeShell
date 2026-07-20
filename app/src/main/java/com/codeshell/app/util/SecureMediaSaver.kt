package com.codeshell.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.net.URL

object SecureMediaSaver {

    suspend fun saveToDownloads(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String
    ): Result<Uri> {
        return try {
            val bytes = URL(url).openStream().use { it.readBytes() }
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: return Result.failure(Exception("Could not save file"))
            resolver.openOutputStream(uri)?.use { output -> output.write(bytes) }
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
}
