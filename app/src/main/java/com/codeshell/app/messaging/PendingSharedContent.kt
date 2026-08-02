package com.codeshell.app.messaging

import android.net.Uri

data class SharedContent(
    val text: String? = null,
    val videoUris: List<Uri> = emptyList(),
    val imageUris: List<Uri> = emptyList(),
    val fileUris: List<Uri> = emptyList(),
) {
    /** First video, if any. Convenience for single-item consumers. */
    val videoUri: Uri? get() = videoUris.firstOrNull()

    /** First image, if any. Convenience for single-item consumers. */
    val imageUri: Uri? get() = imageUris.firstOrNull()

    /** First file, if any. Convenience for single-item consumers. */
    val fileUri: Uri? get() = fileUris.firstOrNull()

    /** True when any media (video/image/file) is attached. */
    val hasMedia: Boolean
        get() = videoUris.isNotEmpty() || imageUris.isNotEmpty() || fileUris.isNotEmpty()

    /** Total number of attached media items. */
    val mediaCount: Int
        get() = videoUris.size + imageUris.size + fileUris.size

    /** Every attached media URI in share order: videos, images, then files. */
    val allUris: List<Uri>
        get() = videoUris + imageUris + fileUris
}

object PendingSharedContent {

    @Volatile
    private var content: SharedContent? = null

    fun set(sharedContent: SharedContent) {
        content = sharedContent
    }

    fun peek(): SharedContent? = content

    fun consume(): SharedContent? {
        val pending = content
        content = null
        return pending
    }

    fun clear() {
        content = null
    }
}
