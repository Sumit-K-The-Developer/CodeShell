package com.codeshell.app.util

fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${bytes} ${units[unitIndex]}"
    } else {
        String.format("%.1f %s", size, units[unitIndex])
    }
}

/**
 * Best-effort MIME type for a file name, derived from its extension. Falls
 * back to [FALLBACK_MIME] for unknown extensions so vault saves / external
 * opens still work for arbitrary shared documents.
 */
fun mimeTypeForFileName(fileName: String?): String {
    if (fileName.isNullOrBlank()) return FALLBACK_MIME
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "bmp" -> "image/bmp"
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "html", "htm" -> "text/html"
        "xml" -> "application/xml"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        else -> FALLBACK_MIME
    }
}

const val FALLBACK_MIME = "application/octet-stream"
