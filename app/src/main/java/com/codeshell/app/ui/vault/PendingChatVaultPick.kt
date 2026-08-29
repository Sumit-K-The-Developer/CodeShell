package com.codeshell.app.ui.vault

/**
 * Static holder used to pass the selected vault item IDs from the
 * Settings-opened chat vault picker back to the originating ChatScreen.
 * The chat screen consumes these on resume (via [consumeAll]) and converts
 * them into pending media drafts (never auto-sent).
 */
object PendingChatVaultPick {
    private var itemIds: List<String> = emptyList()

    fun setAll(ids: List<String>) {
        itemIds = ids
    }

    fun consumeAll(): List<String> {
        val ids = itemIds
        itemIds = emptyList()
        return ids
    }
}