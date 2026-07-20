package com.codeshell.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeshell.app.R
import com.codeshell.app.data.model.CallType
import com.codeshell.app.data.model.ChatInterfaceStyle
import com.codeshell.app.data.model.ChatSummary
import com.codeshell.app.data.model.UserProfile
import com.codeshell.app.data.model.UserSearchResult
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.data.repository.AuthRepository
import com.codeshell.app.data.repository.BotAccountRepository
import com.codeshell.app.data.repository.CallRepository
import com.codeshell.app.data.repository.ChatRepository
import com.codeshell.app.data.repository.MessageRepository
import com.codeshell.app.data.repository.PresenceRepository
import com.codeshell.app.data.repository.PresenceState
import com.codeshell.app.data.repository.StorageRepository
import com.codeshell.app.data.repository.UserRepository
import com.codeshell.app.data.auth.BotAccountConstants
import com.codeshell.app.messaging.LocalActiveChatStore
import com.codeshell.app.messaging.MessageReceiptCoordinator
import com.codeshell.app.ui.components.filterDigitUsernameInput
import com.codeshell.app.util.ChatAccessCodeHasher
import com.codeshell.app.util.ChatIdUtil
import com.codeshell.app.util.NetworkConnectivity
import com.codeshell.app.util.UsernameValidator
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    application: Application,
    private val appPreferences: AppPreferences = AppPreferences(application),
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val chatRepository: ChatRepository = ChatRepository(),
    private val callRepository: CallRepository = CallRepository(),
    private val messageRepository: MessageRepository = MessageRepository(),
    private val botAccountRepository: BotAccountRepository = BotAccountRepository(),
    private val presenceRepository: PresenceRepository = PresenceRepository(),
    private val storageRepository: StorageRepository = StorageRepository(),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val profileCache = mutableMapOf<String, UserProfile>()
    private val profileJobs = mutableMapOf<String, Job>()
    private var searchJob: Job? = null
    private var rawChats: List<ChatSummary> = emptyList()
    private var homeSessionStarted = false
    private var failedAccessCodeAttempts = 0
    private val presenceJobs = mutableMapOf<String, Job>()
    private val presenceCache = mutableMapOf<String, PresenceState>()
    private var deliveryReceiptJob: Job? = null
    private var incomingCallObserverJob: Job? = null

    private val currentUserId: String?
        get() = authRepository.currentUser?.uid

    init {
        val userId = currentUserId
        if (userId == null) {
            _uiState.update { it.copy(isLoggedIn = false) }
        } else {
            _uiState.update { it.copy(isLoggedIn = true) }
            viewModelScope.launch {
                var accessCodeWasEnabled = false
                appPreferences.chatAccessCodeEnabled.collect { enabled ->
                    if (enabled && !accessCodeWasEnabled) {
                        _uiState.update {
                            it.copy(isAccessUnlocked = false, isAccessLocked = true)
                        }
                    } else {
                        _uiState.update { state ->
                            state.copy(isAccessLocked = enabled && !state.isAccessUnlocked)
                        }
                    }
                    accessCodeWasEnabled = enabled
                    if (!enabled && !homeSessionStarted) {
                        startHomeSession(userId)
                    }
                }
            }
            observeChatInterfaceStyle()
            observeProfilePicturePreference()
        }
    }

    private fun startHomeSession(userId: String) {
        if (homeSessionStarted) return
        homeSessionStarted = true
        publishChats()
        observeProfile(userId)
        observeChats(userId)
        observeIncomingCalls(userId)
        syncFcmToken(userId)
    }

    private fun observeIncomingCalls(userId: String) {
        incomingCallObserverJob?.cancel()
        incomingCallObserverJob = viewModelScope.launch {
            callRepository.observeIncomingRingingForCallee(userId)
                .catch { /* ignore listener errors */ }
                .collect { incomingByChat ->
                    _uiState.update { it.copy(incomingCallsByChatId = incomingByChat) }
                }
        }
    }

    fun ensureBotAccountInBackground() {
        if (_uiState.value.botUserId != null) return
        viewModelScope.launch {
            botAccountRepository.ensureBotAccount()
                .onSuccess { botUserId ->
                    profileCache[botUserId] = UserProfile(userId = botUserId, username = BotAccountConstants.USERNAME)
                    _uiState.update { it.copy(botUserId = botUserId) }
                    val userId = currentUserId
                    if (userId != null && userId != botUserId) {
                        chatRepository.getOrCreateChat(userId, botUserId)
                    }
                    publishChats()
                }
        }
    }

    fun prepareBotAssistant(onReady: () -> Unit) {
        if (_uiState.value.isPreparingBot) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPreparingBot = true, errorMessage = null) }
            botAccountRepository.ensureBotAccount()
                .onSuccess { botUserId ->
                    profileCache[botUserId] = UserProfile(userId = botUserId, username = BotAccountConstants.USERNAME)
                    _uiState.update { it.copy(botUserId = botUserId, isPreparingBot = false) }
                    val currentUserId = currentUserId
                    if (currentUserId != null && currentUserId != botUserId) {
                        chatRepository.getOrCreateChat(currentUserId, botUserId)
                    }
                    publishChats()
                    onReady()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isPreparingBot = false,
                            errorMessage = error.message ?: "Could not prepare learning assistant",
                        )
                    }
                }
        }
    }

    fun isBotChat(chat: ChatSummary): Boolean {
        if (chat.chatId == BotAccountConstants.LOCAL_ASSISTANT_CHAT_ID) return true
        val botUserId = _uiState.value.botUserId
        if (!botUserId.isNullOrBlank() && chat.otherUserId == botUserId) return true
        return BotAccountConstants.isBotUsername(chat.otherUsername)
    }

    fun isProtectedBotChatId(chatId: String): Boolean {
        if (chatId == BotAccountConstants.LOCAL_ASSISTANT_CHAT_ID) return true
        val chat = rawChats.find { it.chatId == chatId } ?: return false
        return isBotChat(chat)
    }

    private suspend fun deleteNonBotChatsForUser(userId: String) {
        val botUserId = _uiState.value.botUserId
            ?: botAccountRepository.ensureBotAccount().getOrNull()
        val botChatId = botUserId?.let { ChatIdUtil.create(userId, it) }
        val chatIds = chatRepository.listChatIdsForUser(userId).getOrElse { return }
        val toDelete = chatIds.filter { chatId ->
            chatId != BotAccountConstants.LOCAL_ASSISTANT_CHAT_ID && chatId != botChatId
        }
        if (toDelete.isNotEmpty()) {
            chatRepository.deleteChats(toDelete)
        }
    }

    fun onAccessCodeInputChange(digits: String) {
        _uiState.update { it.copy(accessCodeInput = digits) }
    }

    fun submitAccessCode(onTerminalRevoked: () -> Unit) {
        val code = _uiState.value.accessCodeInput
        if (code.length != 4) return
        viewModelScope.launch {
            val storedHash = appPreferences.chatAccessCodeHash.first()
            if (ChatAccessCodeHasher.verify(code, storedHash)) {
                failedAccessCodeAttempts = 0
                _uiState.update {
                    it.copy(
                        isAccessUnlocked = true,
                        isAccessLocked = false,
                        accessCodeInput = "",
                    )
                }
                currentUserId?.let { startHomeSession(it) }
            } else {
                failedAccessCodeAttempts++
                if (failedAccessCodeAttempts >= 3) {
                    currentUserId?.let { deleteNonBotChatsForUser(it) }
                    authRepository.signOut()
                    _uiState.update {
                        it.copy(
                            isAccessLocked = false,
                            accessCodeInput = "",
                        )
                    }
                    onTerminalRevoked()
                } else {
                    _uiState.update { it.copy(accessCodeInput = "") }
                }
            }
        }
    }

    private fun observeChatInterfaceStyle() {
        viewModelScope.launch {
            appPreferences.chatInterfaceStyle.collect { style ->
                _uiState.update { it.copy(chatInterfaceStyle = style) }
            }
        }
    }

    private fun offlineMessage(): String =
        NetworkConnectivity.message(
            getApplication(),
            R.string.no_internet_action_blocked,
        )

    private fun isOffline(): Boolean =
        NetworkConnectivity.isOffline(getApplication())

    private fun syncFcmToken(userId: String) {
        viewModelScope.launch {
            val alertsEnabled = appPreferences.newTerminalAlert.first()
            if (!alertsEnabled) return@launch
            runCatching {
                FirebaseMessaging.getInstance().token.await()
            }.onSuccess { token ->
                userRepository.updateFcmToken(userId, token)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        val digits = filterDigitUsernameInput(query)
        _uiState.update {
            it.copy(
                searchQuery = digits,
                searchResult = null,
                searchMessage = null
            )
        }
        searchJob?.cancel()

        val validation = UsernameValidator.validate(digits)
        if (digits.isBlank() || validation is UsernameValidator.Validation.Invalid) {
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            searchUser(digits)
        }
    }

    fun searchUser(query: String = _uiState.value.searchQuery) {
        val userId = currentUserId ?: return
        if (isOffline()) {
            _uiState.update {
                it.copy(isSearching = false, searchMessage = offlineMessage())
            }
            return
        }
        val blocked = _uiState.value.profile?.blockedUsers.orEmpty()

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchMessage = null, searchResult = null) }
            val result = userRepository.findUserByExactUsername(query, userId, blocked)
            _uiState.update { state ->
                result.fold(
                    onSuccess = { outcome ->
                        val app = getApplication<Application>()
                        when (outcome) {
                            is UserSearchResult.Found -> state.copy(
                                isSearching = false,
                                searchResult = outcome.profile,
                                searchMessage = null,
                            )
                            UserSearchResult.InBlockList -> state.copy(
                                isSearching = false,
                                searchResult = null,
                                searchMessage = app.getString(R.string.search_id_in_block_list),
                            )
                            UserSearchResult.NotFound -> state.copy(
                                isSearching = false,
                                searchResult = null,
                                searchMessage = app.getString(R.string.no_user_found),
                            )
                        }
                    },
                    onFailure = { error ->
                        state.copy(
                            isSearching = false,
                            searchMessage = error.message
                                ?: getApplication<Application>().getString(R.string.lookup_failed)
                        )
                    }
                )
            }
        }
    }

    fun startChatWithUser(
        otherUserId: String,
        onChatReady: (chatId: String) -> Unit
    ) {
        val userId = currentUserId ?: return
        if (isOffline()) {
            _uiState.update { it.copy(errorMessage = offlineMessage()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isOpeningChat = true, errorMessage = null) }
            val result = chatRepository.getOrCreateChat(userId, otherUserId)
            _uiState.update { it.copy(isOpeningChat = false) }
            result.fold(
                onSuccess = { chatId ->
                    _uiState.update { it.copy(searchQuery = "", searchResult = null) }
                    onChatReady(chatId)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not open terminal session")
                    }
                }
            )
        }
    }

    fun openChat(chatId: String, onChatReady: (String) -> Unit) {
        onChatReady(chatId)
    }

    private fun observeProfile(userId: String) {
        viewModelScope.launch {
            userRepository.observeCurrentUserProfile(userId)
                .catch { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not load profile")
                    }
                }
                .collect { profile ->
                    _uiState.update { it.copy(profile = profile, errorMessage = null) }
                    publishChats()
                }
        }
    }

    private fun observeChats(userId: String) {
        viewModelScope.launch {
            chatRepository.observeChats(userId)
                .catch { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not load terminal history")
                    }
                }
                .collect { chats ->
                    rawChats = chats
                    publishChats()
                }
        }
    }

    private fun observeProfilePicturePreference() {
        viewModelScope.launch {
            appPreferences.profilePicturesEnabled.collect { enabled ->
                _uiState.update { it.copy(profilePicturesEnabled = enabled) }
                publishChats()
            }
        }
    }

    private fun publishChats() {
        val blocked = _uiState.value.profile?.blockedUsers.orEmpty()
        val botUserId = _uiState.value.botUserId
        val currentId = currentUserId
        val loggedInAsBot = BotAccountConstants.isBotUsername(
            _uiState.value.profile?.username.orEmpty(),
        ) || (botUserId != null && currentId == botUserId)

        var enriched = rawChats
            .filterNot { it.otherUserId in blocked }
            .map { chat ->
                val profile = profileCache[chat.otherUserId]
                val username = profile?.username.orEmpty()
                val resolvedUsername = when {
                    username.isNotBlank() -> username
                    botUserId != null && chat.otherUserId == botUserId -> BotAccountConstants.USERNAME
                    else -> ""
                }
                chat.copy(
                    otherUsername = resolvedUsername,
                    otherAvatarUrl = profile?.avatarUrl,
                )
            }

        if (currentId != null) {
            val botBlocked = botUserId != null && botUserId in blocked
            val hasBotRow = enriched.any { chat ->
                chat.chatId == BotAccountConstants.LOCAL_ASSISTANT_CHAT_ID ||
                    BotAccountConstants.isBotUsername(chat.otherUsername) ||
                    (botUserId != null && chat.otherUserId == botUserId)
            }
            if (!botBlocked && !hasBotRow) {
                val synthetic = if (loggedInAsBot) {
                    ChatSummary(
                        chatId = BotAccountConstants.LOCAL_ASSISTANT_CHAT_ID,
                        otherUserId = botUserId.orEmpty(),
                        otherUsername = BotAccountConstants.USERNAME,
                    )
                } else {
                    ChatSummary(
                        chatId = if (botUserId != null) {
                            ChatIdUtil.create(currentId, botUserId)
                        } else {
                            BotAccountConstants.LOCAL_ASSISTANT_CHAT_ID
                        },
                        otherUserId = botUserId.orEmpty(),
                        otherUsername = BotAccountConstants.USERNAME,
                    )
                }
                enriched = listOf(synthetic) + enriched
            }
        }

        enriched = enriched.sortedByDescending { chat ->
            chat.otherUserId == botUserId ||
                chat.chatId == BotAccountConstants.LOCAL_ASSISTANT_CHAT_ID ||
                BotAccountConstants.isBotUsername(chat.otherUsername)
        }
        _uiState.update { it.copy(chats = enriched) }
        syncProfileObservers(enriched)
        syncPresenceObservers(enriched)
        scheduleHomeDeliveryReceipts(currentId, enriched)
    }

    private fun syncProfileObservers(chats: List<ChatSummary>) {
        val botUserId = _uiState.value.botUserId
        val targetUserIds = chats
            .map { it.otherUserId }
            .filter { it.isNotBlank() }
            .filter { userId ->
                userId != botUserId && !BotAccountConstants.isBotUsername(
                    chats.find { it.otherUserId == userId }?.otherUsername.orEmpty(),
                )
            }
            .toSet()

        val stale = profileJobs.keys - targetUserIds
        stale.forEach { userId ->
            profileJobs.remove(userId)?.cancel()
            profileCache.remove(userId)
        }

        targetUserIds.forEach { userId ->
            if (userId in profileJobs) return@forEach
            profileJobs[userId] = viewModelScope.launch {
                userRepository.observeUserProfile(userId)
                    .catch { /* ignore */ }
                    .collect { profile ->
                        if (profile == null) {
                            profileCache.remove(userId)
                        } else {
                            profileCache[userId] = profile
                        }
                        publishChats()
                    }
            }
        }
    }

    private fun scheduleHomeDeliveryReceipts(userId: String?, chats: List<ChatSummary>) {
        if (userId.isNullOrBlank()) return
        deliveryReceiptJob?.cancel()
        deliveryReceiptJob = viewModelScope.launch {
            delay(HOME_DELIVERY_DEBOUNCE_MS)
            chats.filter { chat ->
                chat.unreadCount > 0 &&
                    !LocalActiveChatStore.isViewingChat(chat.chatId) &&
                    chat.chatId != BotAccountConstants.LOCAL_ASSISTANT_CHAT_ID
            }.forEach { chat ->
                messageRepository.getRecentMessages(chat.chatId)
                    .onSuccess { messages ->
                        MessageReceiptCoordinator.processIncomingDeliveries(
                            chatId = chat.chatId,
                            currentUserId = userId,
                            messages = messages,
                            messageRepository = messageRepository,
                        )
                    }
            }
        }
    }

    fun touchPresence() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            presenceRepository.touchPresence(userId)
        }
    }

    private fun syncPresenceObservers(chats: List<ChatSummary>) {
        val botUserId = _uiState.value.botUserId
        val targetUserIds = chats
            .map { it.otherUserId }
            .filter { it.isNotBlank() }
            .filter { userId ->
                userId != botUserId && !BotAccountConstants.isBotUsername(
                    chats.find { it.otherUserId == userId }?.otherUsername.orEmpty(),
                )
            }
            .toSet()

        val stale = presenceJobs.keys - targetUserIds
        stale.forEach { userId ->
            presenceJobs.remove(userId)?.cancel()
            presenceCache.remove(userId)
        }

        targetUserIds.forEach { userId ->
            if (userId in presenceJobs) return@forEach
            presenceJobs[userId] = viewModelScope.launch {
                presenceRepository.observePresence(userId)
                    .catch { /* ignore */ }
                    .collect { presence ->
                        presenceCache[userId] = presence
                        recomputeOnlineUserIds()
                    }
            }
        }
        recomputeOnlineUserIds()
    }

    private fun recomputeOnlineUserIds() {
        val online = presenceCache
            .filter { (userId, presence) ->
                userId in presenceJobs && PresenceRepository.isOnline(presence)
            }
            .keys
            .toSet()
        _uiState.update { it.copy(onlineUserIds = online) }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResult = null,
                searchMessage = null,
                isSearching = false,
            )
        }
    }

    fun enterSelectionModeWithChat(chatId: String) {
        if (isProtectedBotChatId(chatId)) return
        _uiState.update {
            it.copy(selectionMode = true, selectedChatIds = setOf(chatId))
        }
    }

    fun enterSelectionMode() {
        _uiState.update { it.copy(selectionMode = true, selectedChatIds = emptySet()) }
    }

    fun cancelSelection() {
        _uiState.update { it.copy(selectionMode = false, selectedChatIds = emptySet()) }
    }

    fun toggleChatSelection(chatId: String) {
        if (isProtectedBotChatId(chatId)) return
        _uiState.update { state ->
            val updated = state.selectedChatIds.toMutableSet()
            if (chatId in updated) updated.remove(chatId) else updated.add(chatId)
            state.copy(selectedChatIds = updated)
        }
    }

    fun deleteSelectedChats() {
        val chatIds = _uiState.value.selectedChatIds
            .filter { !isProtectedBotChatId(it) }
        if (chatIds.isEmpty()) return
        if (isOffline()) {
            _uiState.update { it.copy(errorMessage = offlineMessage()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
            val result = chatRepository.deleteChats(chatIds)
            _uiState.update {
                it.copy(
                    isDeleting = false,
                    selectionMode = false,
                    selectedChatIds = emptySet(),
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun blockSelectedChats() {
        val userId = currentUserId ?: return
        val chatIds = _uiState.value.selectedChatIds
        if (chatIds.isEmpty()) return
        if (isOffline()) {
            _uiState.update { it.copy(errorMessage = offlineMessage()) }
            return
        }
        val otherUserIds = chatIds.mapNotNull { chatId ->
            if (isProtectedBotChatId(chatId)) return@mapNotNull null
            rawChats.find { it.chatId == chatId }?.otherUserId
        }.distinct()
        if (otherUserIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isBlocking = true, errorMessage = null) }
            var lastError: String? = null
            otherUserIds.forEach { targetId ->
                userRepository.blockUser(userId, targetId)
                    .onFailure { lastError = it.message }
            }
            _uiState.update {
                it.copy(
                    isBlocking = false,
                    selectionMode = false,
                    selectedChatIds = emptySet(),
                    errorMessage = lastError
                )
            }
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        deliveryReceiptJob?.cancel()
        incomingCallObserverJob?.cancel()
        profileJobs.values.forEach { it.cancel() }
        profileJobs.clear()
        profileCache.clear()
        presenceJobs.values.forEach { it.cancel() }
        presenceJobs.clear()
        presenceCache.clear()
        super.onCleared()
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 400L
        private const val HOME_DELIVERY_DEBOUNCE_MS = 300L
    }
}

data class HomeUiState(
    val isLoggedIn: Boolean = true,
    val profile: UserProfile? = null,
    val chats: List<ChatSummary> = emptyList(),
    val searchQuery: String = "",
    val searchResult: UserProfile? = null,
    val searchMessage: String? = null,
    val isSearching: Boolean = false,
    val isOpeningChat: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedChatIds: Set<String> = emptySet(),
    val isDeleting: Boolean = false,
    val isBlocking: Boolean = false,
    val chatInterfaceStyle: ChatInterfaceStyle = ChatInterfaceStyle.DEFAULT,
    val errorMessage: String? = null,
    val isAccessLocked: Boolean = false,
    val isAccessUnlocked: Boolean = false,
    val accessCodeInput: String = "",
    val botUserId: String? = null,
    val isPreparingBot: Boolean = false,
    val onlineUserIds: Set<String> = emptySet(),
    val incomingCallsByChatId: Map<String, CallType> = emptyMap(),
    val profilePicturesEnabled: Boolean = false,
)
