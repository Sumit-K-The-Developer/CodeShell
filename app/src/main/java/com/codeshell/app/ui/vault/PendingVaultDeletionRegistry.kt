package com.codeshell.app.ui.vault

import com.codeshell.app.data.model.VaultItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps undo-pending deletions visible across VaultViewModel recreation. This
 * prevents a reload from bringing an item back while its deletion is waiting
 * for the undo window to expire.
 */
object PendingVaultDeletionRegistry {

    private val pending = ConcurrentHashMap<String, VaultItem>()
    private var generation = 0L

    @Synchronized
    fun stage(items: Collection<VaultItem>): Long {
        items.forEach { pending[it.id] = it }
        generation++
        return generation
    }

    @Synchronized
    fun pendingIds(): Set<String> = pending.keys.toSet()

    @Synchronized
    fun flush(token: Long): List<VaultItem> {
        if (token != generation) return emptyList()
        val result = pending.values.toList()
        pending.clear()
        return result
    }

    @Synchronized
    fun undo(): List<VaultItem> {
        val result = pending.values.toList()
        pending.clear()
        generation++
        return result
    }
}
