package com.codeshell.app.util

import java.time.LocalDateTime
import java.time.ZoneId

object SecretEntryResetTime {

    private const val RESET_HOUR = 3

    fun latestResetBoundaryBefore(nowMillis: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val todayReset = now.toLocalDate().atTime(RESET_HOUR, 0)
        val boundary = if (now.isBefore(todayReset)) {
            todayReset.minusDays(1)
        } else {
            todayReset
        }
        return boundary.atZone(zone).toInstant().toEpochMilli()
    }

    fun nextResetBoundaryAfter(nowMillis: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val todayReset = now.toLocalDate().atTime(RESET_HOUR, 0)
        val boundary = if (now.isBefore(todayReset)) {
            todayReset
        } else {
            todayReset.plusDays(1)
        }
        return boundary.atZone(zone).toInstant().toEpochMilli()
    }
}
