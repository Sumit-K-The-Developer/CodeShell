package com.codeshell.app.messaging

/**
 * Holds the set of vault item IDs the user wants to drop into a chat as a
 * draft. The Home (conversation tile) page writes the IDs here after the user
 * picks a tile; the chat screen consumes them and turns each vault item into a
 * pending attachment (draft) above the input bar instead of auto-sending.
 */
object PendingChatDraft {

    @Volatile
    private var vaultItemIds: List<String> = emptyList()

    fun set(ids: List<String>) {
        vaultItemIds = ids
    }

    fun peek(): List<String> = vaultItemIds

    fun consume(): List<String> {
        val ids = vaultItemIds
        vaultItemIds = emptyList()
        return ids
    }

    fun clear() {
        vaultItemIds = emptyList()
    }
}
