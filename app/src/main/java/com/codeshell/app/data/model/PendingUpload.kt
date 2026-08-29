package com.codeshell.app.data.model

import android.net.Uri

enum class UploadStage {
    PREPARING,
    UPLOADING,
    FAILED,
}

data class PendingUpload(
    val localId: String,
    val type: MessageType,
    val localUri: Uri,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val caption: String? = null,
    val stage: UploadStage = UploadStage.PREPARING,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val uploadedMessageId: String? = null,
    val uploadedUrl: String? = null,
)
