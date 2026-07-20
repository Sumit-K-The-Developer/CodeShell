package com.codeshell.app.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) {
            return cursor.getString(index)
        }
    }
    return null
}

fun queryFileSize(contentResolver: ContentResolver, uri: Uri): Long? {
    contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst() && index >= 0) {
            return cursor.getLong(index).takeIf { it > 0L }
        }
    }
    return null
}

fun isVideoUri(context: Context, contentResolver: ContentResolver, uri: Uri): Boolean {
    contentResolver.getType(uri)?.startsWith("video/")?.let { if (it) return true }
    queryDisplayName(contentResolver, uri)?.lowercase()?.let { name ->
        if (name.endsWith(".mp4") ||
            name.endsWith(".mov") ||
            name.endsWith(".3gp") ||
            name.endsWith(".mkv") ||
            name.endsWith(".webm")
        ) {
            return true
        }
    }
    return runCatching {
        android.media.MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        }
    }.getOrDefault(false)
}

fun downloadFileToCache(context: Context, url: String, fileName: String): Result<java.io.File> {
    return try {
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destination = java.io.File(context.cacheDir, safeName)
        java.net.URL(url).openStream().use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        Result.success(destination)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
