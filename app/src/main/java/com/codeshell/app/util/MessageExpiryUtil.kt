package com.codeshell.app.util

import com.google.firebase.Timestamp

object MessageExpiryUtil {
    const val EXPIRY_HOURS = 12L
    const val UNSEEN_HOLD_HOURS = 24L
    const val EXPIRING_SOON_HOURS = 1L

    fun expiresAt(from: Timestamp = Timestamp.now()): Timestamp {
        return Timestamp(from.seconds + EXPIRY_HOURS * 60 * 60, from.nanoseconds)
    }

    fun unseenExpiresAt(from: Timestamp = Timestamp.now()): Timestamp {
        return Timestamp(from.seconds + UNSEEN_HOLD_HOURS * 60 * 60, from.nanoseconds)
    }

    fun isExpired(expiresAt: Timestamp?): Boolean {
        if (expiresAt == null) return false
        val now = Timestamp.now()
        return expiresAt.seconds <= now.seconds
    }

    fun isExpiringSoon(expiresAt: Timestamp?): Boolean {
        if (expiresAt == null || isExpired(expiresAt)) return false
        val now = Timestamp.now()
        val soonThreshold = now.seconds + EXPIRING_SOON_HOURS * 60 * 60
        return expiresAt.seconds <= soonThreshold
    }

    fun shouldHideFromClient(
        expiresAt: Timestamp?,
        unseenExpiresAt: Timestamp?,
        read: Boolean = false,
    ): Boolean {
        if (expiresAt != null) return isExpired(expiresAt)
        // Recipient has read (seen) but the expiry timer was not written yet — keep visible.
        if (read) return false
        return isExpired(unseenExpiresAt)
    }

    fun isBelowPolicyWindow(expiresAt: Timestamp, messageTimestamp: Timestamp?): Boolean {
        if (messageTimestamp == null) return false
        val policySpanSeconds = EXPIRY_HOURS * 60 * 60
        return expiresAt.seconds - messageTimestamp.seconds < policySpanSeconds
    }

    fun hasStartedExpiry(seenBy: Set<String>, participantIds: Collection<String>): Boolean {
        return participantIds.isNotEmpty() && participantIds.all { it in seenBy }
    }
}
