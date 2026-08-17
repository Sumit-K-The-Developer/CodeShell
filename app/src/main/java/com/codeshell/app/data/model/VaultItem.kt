package com.codeshell.app.data.model

import kotlinx.serialization.Serializable
import com.codeshell.app.util.mimeTypeForFileName

@Serializable
data class VaultItem(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val dateSaved: Long,
    val fileSize: Long,
    val contentVersion: Long = 0L,
    val sourceChatId: String? = null,
    val sourceMessageId: String? = null,
    val importedFromGallery: Boolean = false,
) {
    val isVideo: Boolean
        get() = mimeType.startsWith("video/") || mimeTypeForFileName(fileName).startsWith("video/")
    val isImage: Boolean
        get() = mimeType.startsWith("image/") || mimeTypeForFileName(fileName).startsWith("image/")

    val extension: String get() {
        val dot = fileName.lastIndexOf('.')
        return if (dot >= 0) fileName.substring(dot) else ""
    }
}
