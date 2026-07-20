package com.codeshell.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.codeshell.app.data.model.ChatInterfaceStyle
import com.codeshell.app.util.ChatInactivityTimeout
import com.codeshell.app.util.ChatMessageFontScale
import com.codeshell.app.util.SecretEntryResetTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "codeshell_prefs")

class AppPreferences(private val context: Context) {

    val hideOnHomePress: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HIDE_ON_HOME_PRESS] ?: true
    }

    val terminateOnLock: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TERMINATE_ON_LOCK] ?: true
    }

    val closeChatOnInactivity: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CLOSE_CHAT_ON_INACTIVITY] ?: true
    }

    val chatInactivityTimeoutSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        ChatInactivityTimeout.clampSeconds(
            prefs[CHAT_INACTIVITY_TIMEOUT_SECONDS] ?: ChatInactivityTimeout.DEFAULT_SECONDS,
        )
    }

    val newTerminalAlert: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[NEW_TERMINAL_ALERT] ?: false
    }

    val profilePicturesEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PROFILE_PICTURES_ENABLED] ?: false
    }

    val sharePanelEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHARE_PANEL_ENABLED] ?: false
    }

    val chatInterfaceStyle: Flow<ChatInterfaceStyle> = context.dataStore.data.map { prefs ->
        ChatInterfaceStyle.fromStorageKey(prefs[CHAT_INTERFACE_STYLE])
    }

    val secretDialerCode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SECRET_DIALER_CODE] ?: DEFAULT_DIALER_CODE
    }

    val secureTerminalOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SECURE_TERMINAL_ONLY] ?: false
    }

    val secretChipDoubleTapEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SECRET_CHIP_DOUBLE_TAP_ENABLED] ?: false
    }

    val chatAccessCodeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CHAT_ACCESS_CODE_ENABLED] ?: false
    }

    val chatAccessCodeHash: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[CHAT_ACCESS_CODE_HASH]
    }

    suspend fun setHideOnHomePress(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HIDE_ON_HOME_PRESS] = enabled
        }
    }

    suspend fun setTerminateOnLock(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[TERMINATE_ON_LOCK] = enabled
        }
    }

    suspend fun setCloseChatOnInactivity(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CLOSE_CHAT_ON_INACTIVITY] = enabled
        }
    }

    suspend fun setChatInactivityTimeoutSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_INACTIVITY_TIMEOUT_SECONDS] = ChatInactivityTimeout.clampSeconds(seconds)
        }
    }

    suspend fun setNewTerminalAlert(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[NEW_TERMINAL_ALERT] = enabled
        }
    }

    suspend fun setProfilePicturesEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PROFILE_PICTURES_ENABLED] = enabled
        }
    }

    suspend fun setSharePanelEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHARE_PANEL_ENABLED] = enabled
        }
    }

    suspend fun setChatInterfaceStyle(style: ChatInterfaceStyle) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_INTERFACE_STYLE] = style.storageKey
        }
    }

    suspend fun setSecretDialerCode(code: String) {
        context.dataStore.edit { prefs ->
            prefs[SECRET_DIALER_CODE] = code
        }
    }

    suspend fun setSecureTerminalOnly(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SECURE_TERMINAL_ONLY] = enabled
        }
    }

    suspend fun setSecretChipDoubleTapEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SECRET_CHIP_DOUBLE_TAP_ENABLED] = enabled
        }
    }

    suspend fun recordSecretChipToggle(enabled: Boolean) {
        setSecretChipDoubleTapEnabled(enabled)
    }

    suspend fun applyDailySecretEntryResetIfDue() {
        val latestBoundary = SecretEntryResetTime.latestResetBoundaryBefore()
        context.dataStore.edit { prefs ->
            val lastResetAt = prefs[SECRET_CHIP_LAST_RESET_AT] ?: 0L
            if (lastResetAt < latestBoundary) {
                prefs[SECRET_CHIP_DOUBLE_TAP_ENABLED] = false
                prefs[SECRET_CHIP_LAST_RESET_AT] = latestBoundary
            }
        }
    }

    suspend fun setChatAccessCodeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_ACCESS_CODE_ENABLED] = enabled
        }
    }

    suspend fun setChatAccessCodeHash(hash: String) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_ACCESS_CODE_HASH] = hash
        }
    }

    val chatMessageFontScale: Flow<Float> = context.dataStore.data.map { prefs ->
        ChatMessageFontScale.clamp(prefs[CHAT_MESSAGE_FONT_SCALE] ?: ChatMessageFontScale.DEFAULT)
    }

    suspend fun setChatMessageFontScale(scale: Float) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_MESSAGE_FONT_SCALE] = ChatMessageFontScale.clamp(scale)
        }
    }

    suspend fun clearChatAccessCode() {
        context.dataStore.edit { prefs ->
            prefs.remove(CHAT_ACCESS_CODE_HASH)
            prefs[CHAT_ACCESS_CODE_ENABLED] = false
        }
    }

    suspend fun getHiddenMessageIds(chatId: String): Set<String> {
        val key = hiddenMessagesKey(chatId)
        return context.dataStore.data.first()[key] ?: emptySet()
    }

    suspend fun addHiddenMessageIds(chatId: String, messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        val key = hiddenMessagesKey(chatId)
        context.dataStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            prefs[key] = current + messageIds
        }
    }

    suspend fun getChatDraft(chatId: String): String {
        return context.dataStore.data.first()[chatDraftKey(chatId)].orEmpty()
    }

    suspend fun setChatDraft(chatId: String, text: String) {
        val key = chatDraftKey(chatId)
        context.dataStore.edit { prefs ->
            if (text.isBlank()) {
                prefs.remove(key)
            } else {
                prefs[key] = text
            }
        }
    }

    suspend fun clearChatDraft(chatId: String) {
        context.dataStore.edit { prefs ->
            prefs.remove(chatDraftKey(chatId))
        }
    }

    private fun hiddenMessagesKey(chatId: String) = stringSetPreferencesKey("hidden_messages_$chatId")

    private fun chatDraftKey(chatId: String) = stringPreferencesKey("chat_draft_$chatId")

    companion object {
        const val DEFAULT_DIALER_CODE = "7777"

        private val HIDE_ON_HOME_PRESS = booleanPreferencesKey("hide_on_home_press")
        private val TERMINATE_ON_LOCK = booleanPreferencesKey("terminate_on_lock")
        private val CLOSE_CHAT_ON_INACTIVITY = booleanPreferencesKey("close_chat_on_inactivity")
        private val CHAT_INACTIVITY_TIMEOUT_SECONDS = intPreferencesKey("chat_inactivity_timeout_seconds")
        private val NEW_TERMINAL_ALERT = booleanPreferencesKey("new_terminal_alert")
        private val PROFILE_PICTURES_ENABLED = booleanPreferencesKey("profile_pictures_enabled")
        private val SHARE_PANEL_ENABLED = booleanPreferencesKey("share_panel_enabled")
        private val CHAT_INTERFACE_STYLE = stringPreferencesKey("chat_interface_style")
        private val SECRET_DIALER_CODE = stringPreferencesKey("secret_dialer_code")
        private val SECURE_TERMINAL_ONLY = booleanPreferencesKey("secure_terminal_only")
        private val SECRET_CHIP_DOUBLE_TAP_ENABLED = booleanPreferencesKey("secret_chip_double_tap_enabled")
        private val SECRET_CHIP_LAST_RESET_AT = longPreferencesKey("secret_chip_last_reset_at")
        private val CHAT_ACCESS_CODE_ENABLED = booleanPreferencesKey("chat_access_code_enabled")
        private val CHAT_ACCESS_CODE_HASH = stringPreferencesKey("chat_access_code_hash")
        private val CHAT_MESSAGE_FONT_SCALE = floatPreferencesKey("chat_message_font_scale")
    }
}
