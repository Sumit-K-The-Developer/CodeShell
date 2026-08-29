package com.codeshell.app.ui.vault

import com.codeshell.app.data.model.VaultItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps undo-pending deletions visible across VaultViewModel recreation. This
 * prevents a reload from bringing an item back while its deletion is waiting
 * for the undo window to expire.
 */
object PendingVaultDeletionRegistry {

    private val pending = ConcurrentHashMap<Long, Map<String, VaultItem>>()
    private var nextToken = 0L

    @Synchronized
    fun stage(items: Collection<VaultItem>): Long {
        val token = ++nextToken
        pending[token] = items.associateBy { it.id }
        return token
    }

    @Synchronized
    fun pendingIds(): Set<String> = pending.values.flatMapTo(mutableSetOf()) { it.keys }

    @Synchronized
    fun flush(token: Long): List<VaultItem> {
        return pending.remove(token)?.values?.toList().orEmpty()
    }

    @Synchronized
    fun undo(token: Long): List<VaultItem> = pending.remove(token)?.values?.toList().orEmpty()
}
