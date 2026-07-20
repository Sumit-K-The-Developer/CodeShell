package com.codeshell.app.util

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatChatTimestamp(date: Date): String {
    val now = Calendar.getInstance()
    val messageCal = Calendar.getInstance().apply { time = date }
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    val isToday = now.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == messageCal.get(Calendar.DAY_OF_YEAR)

    if (isToday) {
        return timeFormat.format(date)
    }

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == messageCal.get(Calendar.DAY_OF_YEAR)

    if (isYesterday) {
        return "Yesterday, ${timeFormat.format(date)}"
    }

    val dateFormat = if (now.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR)) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    } else {
        SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault())
    }
    return dateFormat.format(date)
}

fun formatMessageTime(date: Date): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)

fun formatDateSeparator(date: Date): String {
    val now = Calendar.getInstance()
    val messageCal = Calendar.getInstance().apply { time = date }

    val isToday = now.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == messageCal.get(Calendar.DAY_OF_YEAR)
    if (isToday) return "Today"

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == messageCal.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "Yesterday"

    return if (now.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR)) {
        SimpleDateFormat("MMMM d", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
    }
}

fun dayKey(timestamp: Timestamp?): Int {
    if (timestamp == null) return Int.MIN_VALUE
    val cal = Calendar.getInstance().apply { time = Date(timestamp.seconds * 1000) }
    return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
}

fun dayKey(date: Date): Int {
    val cal = Calendar.getInstance().apply { time = date }
    return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
}
