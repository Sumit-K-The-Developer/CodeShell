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
