package com.codeshell.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class VaultItem(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val dateSaved: Long,
    val fileSize: Long,
    val sourceChatId: String? = null,
    val sourceMessageId: String? = null,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")

    val extension: String get() {
        val dot = fileName.lastIndexOf('.')
        return if (dot >= 0) fileName.substring(dot) else ""
    }
}