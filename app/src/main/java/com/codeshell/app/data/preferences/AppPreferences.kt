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

    val disconnectOnDeviceLock: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DISCONNECT_ON_DEVICE_LOCK] ?: true
    }

    val vaultGridColumns: Flow<Int> = context.dataStore.data.map { prefs ->
        if ((prefs[VAULT_GRID_COLUMNS] ?: 3) == 4) 4 else 3
    }

    val closeChatOnInactivity: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CLOSE_CHAT_ON_INACTIVITY] ?: false
    }

    val chatInactivityTimeoutSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        ChatInactivityTimeout.clampSeconds(
            prefs[CHAT_INACTIVITY_TIMEOUT_SECONDS] ?: ChatInactivityTimeout.DEFAULT_SECONDS,
        )
    }

    val newTerminalAlert: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[NEW_TERMINAL_ALERT] ?: true
    }

    val callNotificationControls: Flow<Boolean> = context.dataStore.data.map { prefs ->
        (prefs[NEW_TERMINAL_ALERT] ?: true) &&
            !(prefs[CHAT_ACCESS_CODE_ENABLED] ?: false) &&
            (prefs[CALL_NOTIFICATION_CONTROLS] ?: false)
    }

    val profilePicturesEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PROFILE_PICTURES_ENABLED] ?: false
    }

    val chatWallpaperEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CHAT_WALLPAPER_ENABLED] ?: true
    }

    val chatWallpaperOverlay: Flow<Int> = context.dataStore.data.map { prefs ->
        ClampOverlay(prefs[CHAT_WALLPAPER_OVERLAY] ?: CHAT_WALLPAPER_OVERLAY_DEFAULT)
    }

    val sharePanelEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHARE_PANEL_ENABLED] ?: true
    }

    val chatInterfaceStyle: Flow<ChatInterfaceStyle> = context.dataStore.data.map { prefs ->
        ChatInterfaceStyle.fromStorageKey(prefs[CHAT_INTERFACE_STYLE])
    }

    val secretDialerCode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SECRET_DIALER_CODE] ?: DEFAULT_DIALER_CODE
    }

    val secureTerminalOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SECURE_TERMINAL_ONLY] ?: true
    }

    val chatTitleDoubleTapVaultCapture: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CHAT_TITLE_DOUBLE_TAP_VAULT_CAPTURE] ?: false
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

    suspend fun setDisconnectOnDeviceLock(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DISCONNECT_ON_DEVICE_LOCK] = enabled
        }
    }

    suspend fun setVaultGridColumns(columns: Int) {
        context.dataStore.edit { prefs ->
            prefs[VAULT_GRID_COLUMNS] = if (columns == 4) 4 else 3
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

    /** Disables alerts while signed out while preserving this account's choice. */
    suspend fun suspendNewTerminalAlertForAccount(userId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[NEW_TERMINAL_ALERT] ?: true
            prefs[newTerminalAlertAccountKey(userId)] = current
            prefs[NEW_TERMINAL_ALERT] = false
        }
    }

    /** Restores the saved alert choice after this account signs in again. */
    suspend fun restoreNewTerminalAlertForAccount(userId: String) {
        context.dataStore.edit { prefs ->
            prefs[NEW_TERMINAL_ALERT] = prefs[newTerminalAlertAccountKey(userId)] ?: true
        }
    }

    suspend fun setCallNotificationControls(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CALL_NOTIFICATION_CONTROLS] = enabled && !(prefs[CHAT_ACCESS_CODE_ENABLED] ?: false)
        }
    }

    suspend fun ensureCallNotificationControlsDefault(notificationPermissionGranted: Boolean) {
        if (!notificationPermissionGranted) return
        context.dataStore.edit { prefs ->
            if ((prefs[CHAT_ACCESS_CODE_ENABLED] ?: false) || CALL_NOTIFICATION_CONTROLS !in prefs) {
                prefs[CALL_NOTIFICATION_CONTROLS] = false
            }
        }
    }

    suspend fun setProfilePicturesEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PROFILE_PICTURES_ENABLED] = enabled
        }
    }

    suspend fun setChatWallpaperEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_WALLPAPER_ENABLED] = enabled
        }
    }

    suspend fun setChatWallpaperOverlay(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_WALLPAPER_OVERLAY] = ClampOverlay(value)
        }
    }

    fun chatWallpaperSource(chatId: String): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[wallpaperSourceKey(chatId)] ?: WALLPAPER_SOURCE_AVATAR
    }

    suspend fun setChatWallpaperSource(chatId: String, source: String) {
        val clamped = if (source == WALLPAPER_SOURCE_AVATAR || source == WALLPAPER_SOURCE_VAULT) {
            source
        } else {
            WALLPAPER_SOURCE_AVATAR
        }
        context.dataStore.edit { prefs ->
            prefs[wallpaperSourceKey(chatId)] = clamped
        }
    }

    fun chatWallpaperVaultId(chatId: String): Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[wallpaperVaultKey(chatId)]
    }

    suspend fun setChatWallpaperVaultId(chatId: String, vaultId: String?) {
        val key = wallpaperVaultKey(chatId)
        context.dataStore.edit { prefs ->
            if (vaultId.isNullOrBlank()) {
                prefs.remove(key)
            } else {
                prefs[key] = vaultId
            }
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

    suspend fun setChatTitleDoubleTapVaultCapture(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_TITLE_DOUBLE_TAP_VAULT_CAPTURE] = enabled
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
            if (enabled) {
                prefs[CALL_NOTIFICATION_CONTROLS] = false
            }
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
            prefs[CALL_NOTIFICATION_CONTROLS] = false
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

    suspend fun getLikedMessageIds(chatId: String): Set<String> {
        return context.dataStore.data.first()[likedMessagesKey(chatId)] ?: emptySet()
    }

    suspend fun setMessageLiked(chatId: String, messageId: String, liked: Boolean) {
        val key = likedMessagesKey(chatId)
        context.dataStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            prefs[key] = if (liked) current + messageId else current - messageId
        }
    }

    suspend fun getHiddenChatIdsForAccount(userId: String): Set<String> {
        return context.dataStore.data.first()[hiddenChatsKey(userId)] ?: emptySet()
    }

    suspend fun addHiddenChatIdsForAccount(userId: String, chatIds: Collection<String>) {
        if (chatIds.isEmpty()) return
        val key = hiddenChatsKey(userId)
        context.dataStore.edit { prefs ->
            prefs[key] = (prefs[key] ?: emptySet()) + chatIds
        }
    }

    suspend fun removeHiddenChatIdForAccount(userId: String, chatId: String) {
        val key = hiddenChatsKey(userId)
        context.dataStore.edit { prefs ->
            val updated = (prefs[key] ?: emptySet()) - chatId
            if (updated.isEmpty()) prefs.remove(key) else prefs[key] = updated
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

    private fun hiddenChatsKey(userId: String) = stringSetPreferencesKey(
        "hidden_chats_${userId.replace(Regex("[^A-Za-z0-9_-]"), "_")}"
    )

    private fun likedMessagesKey(chatId: String) = stringSetPreferencesKey("liked_messages_$chatId")

    private fun chatDraftKey(chatId: String) = stringPreferencesKey("chat_draft_$chatId")

    private fun wallpaperSourceKey(chatId: String) = stringPreferencesKey("chat_wallpaper_source_$chatId")

    private fun wallpaperVaultKey(chatId: String) = stringPreferencesKey("chat_wallpaper_vault_$chatId")

    private fun newTerminalAlertAccountKey(userId: String) =
        booleanPreferencesKey("new_terminal_alert_account_${userId.replace(Regex("[^A-Za-z0-9_-]"), "_")}")

    companion object {
        const val DEFAULT_DIALER_CODE = "7777"

        const val WALLPAPER_SOURCE_AVATAR = "avatar"
        const val WALLPAPER_SOURCE_VAULT = "vault"
        const val CHAT_WALLPAPER_OVERLAY_DEFAULT = 40
        const val CHAT_WALLPAPER_OVERLAY_MIN = 0
        const val CHAT_WALLPAPER_OVERLAY_MAX = 100

        private fun ClampOverlay(value: Int): Int =
            value.coerceIn(CHAT_WALLPAPER_OVERLAY_MIN, CHAT_WALLPAPER_OVERLAY_MAX)

        private val HIDE_ON_HOME_PRESS = booleanPreferencesKey("hide_on_home_press")
        private val TERMINATE_ON_LOCK = booleanPreferencesKey("terminate_on_lock")
        private val DISCONNECT_ON_DEVICE_LOCK = booleanPreferencesKey("disconnect_on_device_lock")
        private val VAULT_GRID_COLUMNS = intPreferencesKey("vault_grid_columns")
        private val CLOSE_CHAT_ON_INACTIVITY = booleanPreferencesKey("close_chat_on_inactivity")
        private val CHAT_INACTIVITY_TIMEOUT_SECONDS = intPreferencesKey("chat_inactivity_timeout_seconds")
        private val NEW_TERMINAL_ALERT = booleanPreferencesKey("new_terminal_alert")
        private val CALL_NOTIFICATION_CONTROLS = booleanPreferencesKey("call_notification_controls")
        private val PROFILE_PICTURES_ENABLED = booleanPreferencesKey("profile_pictures_enabled")
        private val CHAT_WALLPAPER_ENABLED = booleanPreferencesKey("chat_wallpaper_enabled")
        private val CHAT_WALLPAPER_OVERLAY = intPreferencesKey("chat_wallpaper_overlay")
        private val SHARE_PANEL_ENABLED = booleanPreferencesKey("share_panel_enabled")
        private val CHAT_INTERFACE_STYLE = stringPreferencesKey("chat_interface_style")
        private val SECRET_DIALER_CODE = stringPreferencesKey("secret_dialer_code")
        private val SECURE_TERMINAL_ONLY = booleanPreferencesKey("secure_terminal_only")
        private val CHAT_TITLE_DOUBLE_TAP_VAULT_CAPTURE =
            booleanPreferencesKey("chat_title_double_tap_vault_capture")
        private val SECRET_CHIP_DOUBLE_TAP_ENABLED = booleanPreferencesKey("secret_chip_double_tap_enabled")
        private val SECRET_CHIP_LAST_RESET_AT = longPreferencesKey("secret_chip_last_reset_at")
        private val CHAT_ACCESS_CODE_ENABLED = booleanPreferencesKey("chat_access_code_enabled")
        private val CHAT_ACCESS_CODE_HASH = stringPreferencesKey("chat_access_code_hash")
        private val CHAT_MESSAGE_FONT_SCALE = floatPreferencesKey("chat_message_font_scale")
    }
}
