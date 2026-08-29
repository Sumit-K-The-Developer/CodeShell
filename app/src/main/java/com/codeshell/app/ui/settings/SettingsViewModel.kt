package com.codeshell.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeshell.app.util.BackgroundWorkCoordinator
import com.codeshell.app.R
import com.codeshell.app.SharePanelController
import com.codeshell.app.data.model.ChatInterfaceStyle
import com.codeshell.app.data.model.UserProfile
import com.codeshell.app.data.model.VaultItem
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.data.repository.AccountRepository
import com.codeshell.app.data.repository.AuthRepository
import com.codeshell.app.data.repository.UserRepository
import com.codeshell.app.data.repository.StorageRepository
import com.codeshell.app.data.repository.VaultRepository
import com.codeshell.app.util.ChatAccessCodeHasher
import com.codeshell.app.util.ChatInactivityTimeout
import com.codeshell.app.util.ChatMessageFontScale
import com.codeshell.app.util.NetworkConnectivity
import com.codeshell.app.util.UsernameFormatter
import com.codeshell.app.messaging.TerminalNotificationManager
import com.codeshell.app.ui.util.DeviceCaptureState
import com.codeshell.app.util.filterFourDigitCode
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    private val chatId: String? = null,
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val storageRepository: StorageRepository = StorageRepository(),
    private val accountRepository: AccountRepository = AccountRepository(),
    private val vaultRepository: VaultRepository = VaultRepository(application),
    private val appPreferences: AppPreferences = AppPreferences(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val currentUserId: String?
        get() = authRepository.currentUser?.uid

    init {
        currentUserId?.let { userId ->
            viewModelScope.launch {
                userRepository.observeCurrentUserProfile(userId)
                    .catch { error ->
                        _uiState.update { it.copy(errorMessage = error.message) }
                    }
                    .collect { profile ->
                        val blockedIds = profile?.blockedUsers.orEmpty()
                        val blockedLabels = resolveBlockedLabels(blockedIds)
                        _uiState.update {
                            it.copy(
                                profile = profile,
                                displayId = profile?.username?.let { name ->
                                    UsernameFormatter.display(name)
                                }.orEmpty(),
                                blockedUsers = blockedIds,
                                blockedUserLabels = blockedLabels,
                            )
                        }
                    }
            }
        }
        viewModelScope.launch {
            appPreferences.hideOnHomePress.collect { enabled ->
                _uiState.update { it.copy(hideOnHomePress = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.terminateOnLock.collect { enabled ->
                _uiState.update { it.copy(terminateOnLock = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.disconnectOnDeviceLock.collect { enabled ->
                _uiState.update { it.copy(disconnectOnDeviceLock = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.vaultGridColumns.collect { columns ->
                _uiState.update { it.copy(vaultGridColumns = columns) }
            }
        }
        viewModelScope.launch {
            appPreferences.closeChatOnInactivity.collect { enabled ->
                _uiState.update { it.copy(closeChatOnInactivity = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.chatInactivityTimeoutSeconds.collect { seconds ->
                _uiState.update { it.copy(chatInactivityTimeoutSeconds = seconds) }
            }
        }
        viewModelScope.launch {
            appPreferences.newTerminalAlert.collect { enabled ->
                _uiState.update { it.copy(newTerminalAlert = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.callNotificationControls.collect { enabled ->
                _uiState.update { it.copy(callNotificationControls = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.profilePicturesEnabled.collect { enabled ->
                _uiState.update { it.copy(profilePicturesEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.chatWallpaperEnabled.collect { enabled ->
                _uiState.update { it.copy(chatWallpaperEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.chatWallpaperOverlay.collect { overlay ->
                _uiState.update { it.copy(chatWallpaperOverlay = overlay) }
            }
        }
        chatId?.let { currentChat ->
            viewModelScope.launch {
                appPreferences.chatWallpaperSource(currentChat).collect { source ->
                    _uiState.update { it.copy(wallpaperSource = source) }
                }
            }
            viewModelScope.launch {
                appPreferences.chatWallpaperVaultId(currentChat).collect { vaultId ->
                    _uiState.update { it.copy(wallpaperVaultId = vaultId) }
                }
            }
        }
        if (chatId != null) {
            loadWallpaperVaultItems()
        }
        viewModelScope.launch {
            appPreferences.sharePanelEnabled.collect { enabled ->
                _uiState.update { it.copy(sharePanelEnabled = enabled) }
                setShareAliasEnabled(enabled)
            }
        }
        viewModelScope.launch {
            appPreferences.chatInterfaceStyle.collect { style ->
                _uiState.update { it.copy(chatInterfaceStyle = style) }
            }
        }
        viewModelScope.launch {
            appPreferences.chatMessageFontScale.collect { scale ->
                _uiState.update { it.copy(chatMessageFontScale = scale) }
            }
        }
        viewModelScope.launch {
            appPreferences.secureTerminalOnly.collect { enabled ->
                _uiState.update { it.copy(secureTerminalOnly = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.chatTitleDoubleTapVaultCapture.collect { enabled ->
                _uiState.update { it.copy(chatTitleDoubleTapVaultCapture = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.chatAccessCodeEnabled.collect { enabled ->
                _uiState.update { it.copy(chatAccessCodeEnabled = enabled) }
            }
        }
    }

    fun setHideOnHomePress(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setHideOnHomePress(enabled)
            _uiState.update { it.copy(successMessage = "Setting updated") }
        }
    }

    fun setSecureTerminalOnly(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setSecureTerminalOnly(enabled)
            _uiState.update { it.copy(successMessage = "Setting updated") }
        }
    }

    fun setChatTitleDoubleTapVaultCapture(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setChatTitleDoubleTapVaultCapture(enabled)
            _uiState.update { it.copy(successMessage = "Setting updated") }
        }
    }

    fun setTerminateOnLock(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setTerminateOnLock(enabled)
            _uiState.update { it.copy(successMessage = "Setting updated") }
        }
    }

    fun setDisconnectOnDeviceLock(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setDisconnectOnDeviceLock(enabled)
            _uiState.update { it.copy(successMessage = "Setting updated") }
        }
    }

    fun setVaultGridColumns(columns: Int) {
        viewModelScope.launch {
            appPreferences.setVaultGridColumns(columns)
            _uiState.update { it.copy(vaultGridColumns = if (columns == 4) 4 else 3) }
        }
    }

    fun setCloseChatOnInactivity(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setCloseChatOnInactivity(enabled)
            _uiState.update { it.copy(successMessage = "Setting updated") }
        }
    }

    fun setChatInactivityTimeoutSeconds(seconds: Int) {
        viewModelScope.launch {
            appPreferences.setChatInactivityTimeoutSeconds(seconds)
        }
    }

    fun setNewTerminalAlert(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setNewTerminalAlert(enabled)
            if (enabled) {
                syncFcmToken()
            } else {
                clearFcmToken()
            }
            _uiState.update { it.copy(successMessage = "Setting updated") }
        }
    }

    fun setCallNotificationControls(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setCallNotificationControls(enabled)
            _uiState.update { it.copy(successMessage = "Setting updated") }
        }
    }

    fun ensureCallNotificationControlsDefault(notificationPermissionGranted: Boolean) {
        viewModelScope.launch {
            appPreferences.ensureCallNotificationControlsDefault(notificationPermissionGranted)
        }
    }

    fun setProfilePicturesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setProfilePicturesEnabled(enabled)
            _uiState.update { it.copy(successMessage = "Setting updated") }
        }
    }

    fun setChatWallpaperEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setChatWallpaperEnabled(enabled)
            _uiState.update { it.copy(successMessage = "Setting updated") }
        }
    }

    fun setChatWallpaperOverlay(value: Int) {
        viewModelScope.launch {
            appPreferences.setChatWallpaperOverlay(value)
        }
    }

    fun setChatWallpaperSource(source: String) {
        val currentChat = chatId ?: return
        viewModelScope.launch {
            appPreferences.setChatWallpaperSource(currentChat, source)
            if (source != com.codeshell.app.data.preferences.AppPreferences.WALLPAPER_SOURCE_VAULT) {
                // Avatar source: no vault image required.
                _uiState.update { it.copy(wallpaperSource = source) }
            }
        }
    }

    fun setChatWallpaperVaultItem(item: VaultItem?) {
        val currentChat = chatId ?: return
        if (item == null) {
            viewModelScope.launch {
                appPreferences.setChatWallpaperVaultId(currentChat, null)
                appPreferences.setChatWallpaperSource(currentChat, com.codeshell.app.data.preferences.AppPreferences.WALLPAPER_SOURCE_AVATAR)
            }
            return
        }
        viewModelScope.launch {
            appPreferences.setChatWallpaperVaultId(currentChat, item.id)
            appPreferences.setChatWallpaperSource(currentChat, com.codeshell.app.data.preferences.AppPreferences.WALLPAPER_SOURCE_VAULT)
            _uiState.update {
                it.copy(
                    wallpaperSource = com.codeshell.app.data.preferences.AppPreferences.WALLPAPER_SOURCE_VAULT,
                    wallpaperVaultId = item.id,
                    successMessage = getApplication<Application>().getString(
                        R.string.chat_wallpaper_vault_success,
                    ),
                    errorMessage = null,
                )
            }
        }
    }

    fun loadWallpaperVaultItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(wallpaperVaultLoading = true) }
            val items = vaultRepository.getAllItems()
                .filter { it.isImage }
                .sortedByDescending { it.dateSaved }
            _uiState.update { it.copy(wallpaperVaultItems = items, wallpaperVaultLoading = false) }
        }
    }

    suspend fun getWallpaperThumbnailBytes(item: VaultItem): Result<ByteArray> =
        vaultRepository.getThumbnailBytes(item)

    fun setSharePanelEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setSharePanelEnabled(enabled)
            setShareAliasEnabled(enabled)
            val app = getApplication<Application>()
            val componentOn = SharePanelController.isEnabled(app)
            _uiState.update {
                it.copy(
                    successMessage = if (enabled) {
                        if (componentOn) {
                            "Share panel ON. If the app still doesn't appear, force-stop or restart the app once."
                        } else {
                            "Share panel: the share component did not enable. Restart the app and try again."
                        }
                    } else {
                        "Share panel OFF. The app is hidden from the share sheet."
                    }
                )
            }
        }
    }

    private fun setShareAliasEnabled(enabled: Boolean) {
        SharePanelController.apply(getApplication(), enabled)
    }

    fun uploadProfilePhoto(
        uri: Uri,
        fullUri: Uri? = null,
        useCroppedForSharing: Boolean = false,
    ) {
        val userId = currentUserId ?: return
        if (isOffline()) {
            _uiState.update { it.copy(errorMessage = offlineMessage()) }
            return
        }
        BackgroundWorkCoordinator.launch(getApplication<Application>()) {
            _uiState.update { it.copy(isSavingProfilePhoto = true, errorMessage = null, successMessage = null) }
            storageRepository.uploadProfilePhoto(userId, uri, fullUri)
                .onSuccess { (downloadUrl, fullDownloadUrl) ->
                    userRepository.updateAvatarUrls(
                        userId,
                        downloadUrl.withAvatarCacheVersion(),
                        fullDownloadUrl?.withAvatarCacheVersion(),
                        if (useCroppedForSharing) {
                            downloadUrl.withAvatarCacheVersion()
                        } else {
                            (fullDownloadUrl ?: downloadUrl).withAvatarCacheVersion()
                        },
                    )
                        .onSuccess {
                            _uiState.update {
                                it.copy(
                                    isSavingProfilePhoto = false,
                                    successMessage = getApplication<Application>().getString(R.string.profile_picture_saved),
                                )
                            }
                        }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    isSavingProfilePhoto = false,
                                    errorMessage = error.message ?: getApplication<Application>().getString(R.string.profile_picture_save_failed),
                                )
                            }
                        }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSavingProfilePhoto = false,
                            errorMessage = error.message ?: getApplication<Application>().getString(R.string.profile_picture_save_failed),
                        )
                    }
                }
        }
    }

    fun removeProfilePhoto() {
        val userId = currentUserId ?: return
        if (isOffline()) {
            _uiState.update { it.copy(errorMessage = offlineMessage()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProfilePhoto = true, errorMessage = null, successMessage = null) }
            val deleteResult = storageRepository.deleteProfilePhoto(userId)
            if (deleteResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isSavingProfilePhoto = false,
                        errorMessage = deleteResult.exceptionOrNull()?.message
                            ?: getApplication<Application>().getString(R.string.profile_picture_save_failed),
                    )
                }
                return@launch
            }

            userRepository.updateAvatarUrl(userId, null)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSavingProfilePhoto = false,
                            successMessage = getApplication<Application>().getString(R.string.profile_picture_removed),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSavingProfilePhoto = false,
                            errorMessage = error.message ?: getApplication<Application>().getString(R.string.profile_picture_save_failed),
                        )
                    }
                }
        }
    }

    fun setChatInterfaceStyle(style: ChatInterfaceStyle) {
        viewModelScope.launch {
            appPreferences.setChatInterfaceStyle(style)
        }
    }

    fun setChatMessageFontScale(scale: Float) {
        viewModelScope.launch {
            appPreferences.setChatMessageFontScale(scale)
        }
    }

    fun enableChatAccessCode(pin: String, confirm: String, onResult: (String?) -> Unit) {
        if (pin.length != 4) {
            onResult("Enter a 4-digit code")
            return
        }
        if (pin != confirm) {
            onResult("Codes do not match")
            return
        }
        viewModelScope.launch {
            appPreferences.setChatAccessCodeHash(ChatAccessCodeHasher.hash(pin))
            appPreferences.setChatAccessCodeEnabled(true)
            onResult(null)
        }
    }

    fun disableChatAccessCode(pin: String, onResult: (String?) -> Unit) {
        if (pin.length != 4) {
            onResult("Enter your current code")
            return
        }
        viewModelScope.launch {
            val storedHash = appPreferences.chatAccessCodeHash.first()
            if (!ChatAccessCodeHasher.verify(pin, storedHash)) {
                onResult("Incorrect code")
                return@launch
            }
            appPreferences.clearChatAccessCode()
            onResult(null)
        }
    }

    fun changeChatAccessCode(current: String, newPin: String, confirm: String, onResult: (String?) -> Unit) {
        if (current.length != 4 || newPin.length != 4) {
            onResult("Enter a 4-digit code")
            return
        }
        if (newPin != confirm) {
            onResult("Codes do not match")
            return
        }
        viewModelScope.launch {
            val storedHash = appPreferences.chatAccessCodeHash.first()
            if (!ChatAccessCodeHasher.verify(current, storedHash)) {
                onResult("Incorrect code")
                return@launch
            }
            appPreferences.setChatAccessCodeHash(ChatAccessCodeHasher.hash(newPin))
            onResult(null)
        }
    }

    fun onNewTerminalAlertPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            if (granted) {
                appPreferences.setNewTerminalAlert(true)
                syncFcmToken()
            }
        }
    }

    private suspend fun syncFcmToken() {
        val userId = currentUserId ?: return
        runCatching {
            FirebaseMessaging.getInstance().token.await()
        }.onSuccess { token ->
            userRepository.updateFcmToken(userId, token)
        }
    }

    private suspend fun clearFcmToken() {
        val userId = currentUserId ?: return
        userRepository.updateFcmToken(userId, "")
    }

    private fun offlineMessage(): String =
        NetworkConnectivity.message(getApplication())

    private fun isOffline(): Boolean =
        NetworkConnectivity.isOffline(getApplication())

    private suspend fun resolveBlockedLabels(userIds: List<String>): Map<String, String> {
        return userIds.associateWith { userId ->
            userRepository.getUsername(userId).getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { UsernameFormatter.display(it) }
                ?: userId.take(8) + "…"
        }
    }

    fun unblockUser(targetUserId: String) {
        val userId = currentUserId ?: return
        if (isOffline()) {
            _uiState.update { it.copy(errorMessage = offlineMessage()) }
            return
        }
        viewModelScope.launch {
            userRepository.unblockUser(userId, targetUserId)
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    fun onDeleteAccountPasswordChange(value: String) {
        _uiState.update {
            it.copy(deleteAccountPassword = value, errorMessage = null, successMessage = null)
        }
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        val password = _uiState.value.deleteAccountPassword
        if (password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your access key to continue") }
            return
        }
        if (isOffline()) {
            _uiState.update { it.copy(errorMessage = offlineMessage()) }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDeletingAccount = true,
                    isSaving = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            val reauthResult = authRepository.reauthenticate(password)
            if (reauthResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isDeletingAccount = false,
                        isSaving = false,
                        errorMessage = reauthResult.exceptionOrNull()?.message
                    )
                }
                return@launch
            }
            val deleteResult = accountRepository.deleteAccount()
            if (deleteResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isDeletingAccount = false,
                        isSaving = false,
                        errorMessage = deleteResult.exceptionOrNull()?.message
                    )
                }
                return@launch
            }
            currentUserId?.let { userId ->
                appPreferences.suspendNewTerminalAlertForAccount(userId)
            }
            TerminalNotificationManager.clearAll(getApplication())
            DeviceCaptureState.disable()
            authRepository.signOut()
            onDeleted()
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            currentUserId?.let { userId ->
                appPreferences.suspendNewTerminalAlertForAccount(userId)
            }
            TerminalNotificationManager.clearAll(getApplication())
            DeviceCaptureState.disable()
            authRepository.signOut()
            onSignedOut()
        }
    }
}

private fun String.withAvatarCacheVersion(): String {
    val separator = if (contains("?")) "&" else "?"
    return "$this${separator}v=${System.currentTimeMillis()}"
}

data class SettingsUiState(
    val profile: UserProfile? = null,
    val displayId: String = "",
    val hideOnHomePress: Boolean = true,
    val terminateOnLock: Boolean = true,
    val disconnectOnDeviceLock: Boolean = true,
    val vaultGridColumns: Int = 3,
    val secureTerminalOnly: Boolean = true,
    val chatTitleDoubleTapVaultCapture: Boolean = false,
    val closeChatOnInactivity: Boolean = false,
    val chatInactivityTimeoutSeconds: Int = ChatInactivityTimeout.DEFAULT_SECONDS,
    val newTerminalAlert: Boolean = true,
    val callNotificationControls: Boolean = false,
    val profilePicturesEnabled: Boolean = false,
    val chatWallpaperEnabled: Boolean = true,
    val chatWallpaperOverlay: Int = com.codeshell.app.data.preferences.AppPreferences.CHAT_WALLPAPER_OVERLAY_DEFAULT,
    val wallpaperSource: String = com.codeshell.app.data.preferences.AppPreferences.WALLPAPER_SOURCE_AVATAR,
    val wallpaperVaultId: String? = null,
    val wallpaperVaultItems: List<VaultItem> = emptyList(),
    val wallpaperVaultLoading: Boolean = false,
    val sharePanelEnabled: Boolean = true,
    val chatInterfaceStyle: ChatInterfaceStyle = ChatInterfaceStyle.DEFAULT,
    val chatMessageFontScale: Float = ChatMessageFontScale.DEFAULT,
    val chatAccessCodeEnabled: Boolean = false,
    val deleteAccountPassword: String = "",
    val blockedUsers: List<String> = emptyList(),
    val blockedUserLabels: Map<String, String> = emptyMap(),
    val isSaving: Boolean = false,
    val isSavingProfilePhoto: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
