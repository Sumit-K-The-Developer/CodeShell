package com.codeshell.app.call

import android.content.Context
import android.content.SharedPreferences

object PendingCallStore {
    private const val PREFS = "pending_calls"
    private const val KEY_CHAT_ID = "chat_id"
    private const val KEY_CALL_ID = "call_id"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, chatId: String, callId: String) {
        prefs(context).edit()
            .putString(KEY_CHAT_ID, chatId)
            .putString(KEY_CALL_ID, callId)
            .apply()
    }

    fun peek(context: Context): Pair<String, String>? {
        val chatId = prefs(context).getString(KEY_CHAT_ID, null) ?: return null
        val callId = prefs(context).getString(KEY_CALL_ID, null) ?: return null
        return chatId to callId
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
