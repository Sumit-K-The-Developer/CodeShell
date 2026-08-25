package com.codeshell.app.ui.home

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeshell.app.R
import com.codeshell.app.BuildConfig
import com.codeshell.app.ui.chat.ChatAccessLockOverlay
import com.codeshell.app.ui.chat.ChatThemeColors
import com.codeshell.app.ui.chat.ChatUserIdBadge
import com.codeshell.app.ui.util.DeviceCaptureState
import com.codeshell.app.ui.chat.rememberAdaptiveChatTheme
import com.codeshell.app.ui.util.AdaptiveWidthContainer
import com.codeshell.app.ui.util.WindowLayout
import com.codeshell.app.ui.components.OfflineBanner
import com.codeshell.app.CodeShellApplication
import com.codeshell.app.MainActivity
import com.codeshell.app.data.auth.BotAccountConstants
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.messaging.PendingChatDraft
import com.codeshell.app.messaging.PendingSharedContent
import com.codeshell.app.util.MediaPreloadCache
import com.codeshell.app.util.UsernameFormatter
import com.codeshell.app.util.findActivity
import com.codeshell.app.ui.vault.VaultViewModel
import com.codeshell.app.ui.vault.VaultSaveState
import com.codeshell.app.ui.vault.VaultSaveTracker
import com.codeshell.app.data.repository.VaultRepository
import com.codeshell.app.ui.util.TerminalInactivityEffect
import com.codeshell.app.ui.util.rememberTerminalActivity
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun HomeScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (chatId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenAiTutor: (topicId: String, topicLabel: String, welcomeDigits: String, entrySource: String) -> Unit,
    onTerminalRevoked: () -> Unit,
    vaultSendItemIds: List<String> = emptyList(),
) {
    val vaultViewModel: VaultViewModel = viewModel()
    val app = LocalContext.current.applicationContext as CodeShellApplication
    val context = LocalContext.current
    val activity = context.findActivity() as? MainActivity
    val focusManager = LocalFocusManager.current
    val viewModel: HomeViewModel = viewModel { HomeViewModel(app) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accessCodeEnabled by remember { AppPreferences(app) }.chatAccessCodeEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val vaultUiState by vaultViewModel.uiState.collectAsStateWithLifecycle()
    val deviceCaptureEnabled by DeviceCaptureState.enabled.collectAsStateWithLifecycle()
    val chatTheme = rememberAdaptiveChatTheme(uiState.chatInterfaceStyle)
    val listColors = chatTheme.colors
    val profileDigits = UsernameFormatter.parseRawInput(uiState.profile?.username.orEmpty())
        .takeIf { it.isNotEmpty() }
    fun toggleDeviceCapture() {
        val enabled = DeviceCaptureState.toggle()
        if (!enabled) {
            Toast.makeText(context, "Ensure the captured content is moved to Vault.", Toast.LENGTH_LONG).show()
        }
    }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSelectionMenu by remember { mutableStateOf(false) }
    var searchActivated by remember { mutableStateOf(false) }
    var expandedProfilePhotoUrl by remember { mutableStateOf<String?>(null) }
    var pendingSharedContent by remember { mutableStateOf(PendingSharedContent.peek()) }
    var multiSendInProgress by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val searchActive = searchActivated || uiState.searchQuery.isNotEmpty()
    val gridState = rememberLazyGridState()
    val (bumpActivity, activityGeneration) = rememberTerminalActivity()

    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) bumpActivity()
    }

    LaunchedEffect(activity?.shareOpenTick) {
        pendingSharedContent = PendingSharedContent.peek()
    }

    LaunchedEffect(vaultUiState.errorMessage) {
        val message = vaultUiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vaultViewModel.clearError()
    }

    // Handle vault send flow - load vault items and enter selection mode when coming from vault
    LaunchedEffect(vaultSendItemIds) {
        if (vaultSendItemIds.isNotEmpty()) {
            vaultViewModel.loadItems()
            viewModel.enterSelectionModeForVaultSend()
        }
    }

    TerminalInactivityEffect(
        onNavigateBack,
        activityGeneration,
        uiState.searchQuery,
        uiState.chats.size,
        uiState.selectedChatIds,
        uiState.accessCodeInput,
        enabled = !uiState.isAccessLocked,
        blocked = uiState.selectionMode ||
            showBlockDialog ||
            showDeleteDialog ||
            gridState.isScrollInProgress ||
            uiState.isOpeningChat,
    )

    fun dismissSearch() {
        searchActivated = false
        viewModel.clearSearch()
        focusManager.clearFocus()
    }

    fun handleTerminalRevoked() {
        Toast.makeText(context, context.getString(R.string.terminal_revoked), Toast.LENGTH_SHORT).show()
        onTerminalRevoked()
    }

    fun clearPendingSharedContent() {
        PendingSharedContent.clear()
        pendingSharedContent = null
    }

    fun savePendingSharedContentToVault() {
        val content = pendingSharedContent ?: return
        val mediaUris = content.allUris
        if (mediaUris.isEmpty()) {
            clearPendingSharedContent()
            return
        }
        vaultViewModel.saveToVaultFromUris(
            uris = mediaUris,
            onAllComplete = {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.saved_to_vault),
                        actionLabel = context.getString(R.string.view),
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onOpenVault()
                    }
                }
                clearPendingSharedContent()
            },
            deleteSource = false,
        )
    }

    fun sendPendingSharedContentToSelectedChats() {
        val content = pendingSharedContent ?: return
        val selectedChatIds = uiState.selectedChatIds
        if (selectedChatIds.isEmpty() || multiSendInProgress) return
        viewModel.sendSharedContentToChats(content, selectedChatIds)
        PendingSharedContent.clear()
        pendingSharedContent = null
        multiSendInProgress = false
        viewModel.cancelSelection()
        scope.launch {
            snackbarHostState.showSnackbar("Sent")
        }
    }

    fun sendPendingVaultItemsToSelectedChats() {
        val selectedChatIds = uiState.selectedChatIds
        if (vaultSendItemIds.isEmpty() || selectedChatIds.isEmpty()) return
        vaultViewModel.sendVaultItemsToChats(vaultSendItemIds, selectedChatIds)
        viewModel.clearVaultSendFlag()
        viewModel.cancelSelection()
        scope.launch { snackbarHostState.showSnackbar("Sent") }
    }

    BackHandler(enabled = uiState.selectionMode) {
        viewModel.cancelSelection()
    }

    BackHandler(enabled = !uiState.selectionMode && searchActive) {
        dismissSearch()
    }

    BackHandler(enabled = expandedProfilePhotoUrl != null) {
        expandedProfilePhotoUrl = null
    }

    fun openDisguiseAiTutor(welcomeDigits: String) {
        onOpenAiTutor(
            BotAccountConstants.GENERIC_TOPIC_ID,
            "",
            welcomeDigits.ifBlank { BotAccountConstants.WELCOME_DIGITS },
            "disguise",
        )
        viewModel.ensureBotAccountInBackground()
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text(stringResource(R.string.block_user)) },
            text = {
                Text(
                    stringResource(
                        R.string.block_selected_confirm,
                        uiState.selectedChatIds.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBlockDialog = false
                    viewModel.blockSelectedChats()
                }) {
                    Text(stringResource(R.string.block_user_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_sessions)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_sessions_confirm,
                        uiState.selectedChatIds.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteSelectedChats()
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    AdaptiveWidthContainer {
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = listColors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!uiState.selectionMode && uiState.profilePicturesEnabled &&
                            !uiState.profile?.avatarUrl.isNullOrBlank()
                        ) {
                            AsyncImage(
                                model = uiState.profile?.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(34.dp)
                                    .clip(CircleShape),
                            )
                        }
                        Text(
                            text = if (uiState.selectionMode) {
                                if (uiState.selectedChatIds.isEmpty()) {
                                    stringResource(R.string.select_sessions)
                                } else {
                                    stringResource(R.string.selected_sessions_count, uiState.selectedChatIds.size)
                                }
                            } else {
                                if (BuildConfig.SHELL_FLAVOR == "news") "InLine" else "EnglishShell"
                            },
                            color = listColors.text,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = if (BuildConfig.SHELL_FLAVOR == "news") {
                                    FontFamily.SansSerif
                                } else {
                                    FontFamily.Cursive
                                },
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        if (!uiState.selectionMode) {
                            Text(
                                text = "♥",
                                color = Color(0xFFB84A58),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        if (!uiState.selectionMode && pendingSharedContent == null && !profileDigits.isNullOrEmpty()) {
                            Text(
                                text = profileDigits,
                                color = listColors.text.copy(alpha = 0.52f),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .pointerInput(deviceCaptureEnabled) {
                                        detectTapGestures(onLongPress = { toggleDeviceCapture() })
                                    },
                            )
                            if (deviceCaptureEnabled) {
                                Text(
                                    text = "⚠",
                                    color = Color(0xFFFFC107),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(start = 3.dp),
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (uiState.selectionMode) {
                        IconButton(onClick = { viewModel.cancelSelection() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = listColors.text,
                            )
                        }
                    }
                },
                actions = {
                    if (pendingSharedContent != null && !uiState.selectionMode) {
                        if (pendingSharedContent?.hasMedia == true) {
                            TextButton(onClick = ::savePendingSharedContentToVault) {
                                Text(
                                    text = "→ Vault",
                                    color = listColors.accent,
                                )
                            }
                        }
                        Surface(
                            color = listColors.text.copy(alpha = 0.12f),
                            shape = CircleShape,
                        ) {
                            IconButton(onClick = ::clearPendingSharedContent) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cancel),
                                    tint = listColors.text,
                                )
                            }
                        }
                    }
                    if (uiState.selectionMode) {
                        Box {
                            IconButton(onClick = { showSelectionMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_options),
                                    tint = listColors.text
                                )
                            }
                            DropdownMenu(
                                expanded = showSelectionMenu,
                                onDismissRequest = { showSelectionMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.block_user_action)) },
                                    onClick = {
                                        showSelectionMenu = false
                                        showBlockDialog = true
                                    },
                                    enabled = uiState.selectedChatIds.isNotEmpty() && !uiState.isBlocking
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.cancel)) },
                                    onClick = {
                                        showSelectionMenu = false
                                        viewModel.cancelSelection()
                                    }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .pointerInput(accessCodeEnabled) {
                                    detectTapGestures(
                                        onTap = {
                                            if (!accessCodeEnabled) onOpenSettings()
                                        },
                                        onDoubleTap = { onOpenSettings() },
                                    )
                                },
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                                tint = listColors.text,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = listColors.topBar,
                    titleContentColor = listColors.text,
                    navigationIconContentColor = listColors.text
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OfflineBanner(colors = listColors)
            if (!uiState.selectionMode) {
                SearchBar(
                    query = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    activated = searchActivated,
                    onActivatedChange = { searchActivated = it },
                    onDismiss = ::dismissSearch,
                    isSearching = uiState.isSearching,
                    colors = listColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )

                if (pendingSharedContent != null) {
                    Text(
                        text = "Select a tile to send",
                        color = listColors.accent,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 6.dp),
                    )
                }

                uiState.searchMessage?.let { message ->
                    Text(
                        text = message,
                        color = listColors.mutedText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                uiState.searchResult?.let { user ->
                    UserProfileCard(
                        user = user,
                        onClick = {
                            viewModel.startChatWithUser(user.userId, onOpenChat)
                        },
                        colors = listColors,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (uiState.isOpeningChat) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            HorizontalDivider(color = listColors.mutedText.copy(alpha = 0.15f))

            if (uiState.chats.isEmpty() && uiState.profile != null && uiState.isLoggedIn) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .then(
                            if (uiState.selectionMode &&
                                !uiState.pendingVaultSend &&
                                !uiState.pendingSharedSend
                            ) {
                                Modifier.pointerInput(uiState.selectionMode) {
                                    detectTapGestures(onTap = { viewModel.cancelSelection() })
                                }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(
                            if (uiState.selectionMode) {
                                R.string.no_sessions_to_delete
                            } else {
                                R.string.no_chats_yet
                            }
                        ),
                        color = listColors.mutedText
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(WindowLayout.homeGridColumns()),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (uiState.selectionMode) {
                                Modifier.padding(bottom = 88.dp)
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (uiState.selectionMode &&
                                !uiState.pendingVaultSend &&
                                !uiState.pendingSharedSend
                            ) {
                                Modifier.pointerInput(uiState.selectionMode) {
                                    detectTapGestures(onTap = { viewModel.cancelSelection() })
                                }
                            } else {
                                Modifier
                            }
                        ),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.chats, key = { it.chatId }) { chat ->
                        ChatListItem(
                            chat = chat,
                            colors = listColors,
                            selectionMode = uiState.selectionMode,
                            selected = chat.chatId in uiState.selectedChatIds,
                            isOnline = chat.otherUserId in uiState.onlineUserIds,
                            incomingCallType = uiState.incomingCallsByChatId[chat.chatId],
                            profilePicturesEnabled = uiState.profilePicturesEnabled,
                            onToggleSelection = { viewModel.toggleChatSelection(chat.chatId) },
                            onLongPress = {
                                if (!uiState.selectionMode) {
                                    viewModel.enterSelectionModeWithChat(chat.chatId)
                                }
                            },
                            onDisguiseTap = {
                                if (uiState.selectionMode) {
                                    viewModel.toggleChatSelection(chat.chatId)
                                } else if (pendingSharedContent != null) {
                                    if (!viewModel.isBotChat(chat)) {
                                        onOpenChat(chat.chatId)
                                        pendingSharedContent = null
                                    }
                                } else {
                                    val digits = UsernameFormatter.parseRawInput(chat.otherUsername)
                                    openDisguiseAiTutor(digits)
                                }
                            },
                            onOpenSession = {
                                if (!uiState.selectionMode) {
                                    if (pendingSharedContent != null) {
                                        if (!viewModel.isBotChat(chat)) {
                                            onOpenChat(chat.chatId)
                                            pendingSharedContent = null
                                        }
                                    } else {
                                        viewModel.openChat(chat.chatId, onOpenChat)
                                    }
                                }
                            },
                            onIncomingCallTap = {
                                if (!uiState.selectionMode) {
                                    onOpenChat(chat.chatId)
                                }
                            },
                            onViewProfilePhoto = {
                                if (!uiState.selectionMode && !chat.otherAvatarUrl.isNullOrBlank()) {
                                    expandedProfilePhotoUrl = chat.otherAvatarSharedUrl ?: chat.otherAvatarUrl
                                }
                            },
                            swipeToSelect = pendingSharedContent != null,
                            interactionDisabled = pendingSharedContent != null && viewModel.isBotChat(chat),
                        )
                    }
                }
            }
        }
    }
        AnimatedVisibility(
            visible = uiState.selectionMode && pendingSharedContent == null,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SessionSelectionBar(
                selectedCount = uiState.selectedChatIds.size,
                enabled = uiState.selectedChatIds.isNotEmpty() && !uiState.isDeleting,
                colors = listColors,
                isVaultSendMode = uiState.pendingVaultSend || uiState.pendingSharedSend,
                onSend = {
                    // Already handled in LaunchedEffect
                },
                onDelete = { showDeleteDialog = true },
            )
        }
        if (uiState.selectionMode &&
            (pendingSharedContent != null || uiState.pendingVaultSend) &&
            uiState.selectedChatIds.isNotEmpty()
        ) {
            FloatingActionButton(
                onClick = {
                    if (uiState.pendingVaultSend) {
                        sendPendingVaultItemsToSelectedChats()
                    } else {
                        sendPendingSharedContentToSelectedChats()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp),
                containerColor = listColors.accent,
                contentColor = listColors.background,
            ) {
                if (multiSendInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = listColors.background,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.send),
                    )
                }
            }
        }
        if (uiState.isAccessLocked) {
            ChatAccessLockOverlay(
                digits = uiState.accessCodeInput,
                onDigitsChange = viewModel::onAccessCodeInputChange,
                onComplete = { viewModel.submitAccessCode(::handleTerminalRevoked) },
                theme = chatTheme,
            )
        }
        expandedProfilePhotoUrl?.let { photoUrl ->
            ProfilePhotoViewer(
                url = photoUrl,
                vaultViewModel = vaultViewModel,
                onOpenVault = onOpenVault,
                onDismiss = { expandedProfilePhotoUrl = null },
            )
        }
    }
    }
}

@Composable
private fun PendingShareBanner(
    description: String,
    showSaveToVault: Boolean,
    colors: ChatThemeColors,
    onSendToChat: () -> Unit,
    onSaveToVault: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = colors.accent.copy(alpha = 0.18f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pending_share_title),
                        color = colors.text,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = description,
                        color = colors.mutedText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = colors.text,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onSendToChat,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.send_to_chat),
                        color = colors.accent,
                    )
                }
                if (showSaveToVault) {
                    TextButton(
                        onClick = onSaveToVault,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.save_to_vault),
                            color = colors.accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SendModeHintBar(
    text: String,
    colors: ChatThemeColors,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = colors.topBar,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = colors.text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ProfilePhotoViewer(
    url: String,
    vaultViewModel: VaultViewModel,
    onOpenVault: () -> Unit,
    onDismiss: () -> Unit,
) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var imageLoading by remember(url) { mutableStateOf(true) }
    var saveInProgress by remember(url) { mutableStateOf(false) }
    var showSaveConfirmation by remember(url) { mutableStateOf(false) }
    val saveState by VaultSaveTracker.state.collectAsStateWithLifecycle()

    LaunchedEffect(saveState) {
        when (saveState) {
            is VaultSaveState.Saving -> saveInProgress = true
            is VaultSaveState.Success -> {
                saveInProgress = false
                showSaveConfirmation = true
                delay(2_000L)
                showSaveConfirmation = false
            }
            is VaultSaveState.Error -> saveInProgress = false
            VaultSaveState.Idle -> Unit
        }
    }
    val saveStatusText = when {
        saveInProgress || saveState is VaultSaveState.Saving -> "Saving..."
        saveState is VaultSaveState.Success && showSaveConfirmation ->
            stringResource(R.string.saved_to_vault)
        else -> null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(onDismiss) {
                    detectTapGestures(onTap = { onDismiss() })
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onLoading = { imageLoading = true },
                onSuccess = { imageLoading = false },
                onError = { imageLoading = false },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(url) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val longPressReached = withTimeoutOrNull(300L) {
                                waitForUpOrCancellation()
                                false
                            } ?: true
                            if (longPressReached) {
                                // Prevent the parent full-screen tap handler from
                                // dismissing the viewer when this gesture ends.
                                down.consume()
                                if (!saveInProgress && saveState !is VaultSaveState.Saving) {
                                    saveInProgress = true
                                    vaultViewModel.saveToVault(
                                        url = url,
                                        fileName = "profile_picture.jpg",
                                        mimeType = "image/jpeg",
                                    )
                                }
                                waitForUpOrCancellation()?.consume()
                            }
                        }
                    }
                    .pointerInput(url) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = nextScale
                            offset = if (nextScale == 1f) {
                                Offset.Zero
                            } else {
                                offset + pan
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            if (imageLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(42.dp),
                )
            }
            if (saveStatusText != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = saveStatusText, color = Color.White)
                    if (saveState is VaultSaveState.Success && showSaveConfirmation) {
                        TextButton(onClick = onOpenVault) {
                            Text(text = stringResource(R.string.view), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionSelectionBar(
    selectedCount: Int,
    enabled: Boolean,
    colors: ChatThemeColors,
    onSend: () -> Unit = {},
    onDelete: () -> Unit,
    isVaultSendMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 20.dp),
        shape = RoundedCornerShape(50),
        color = colors.topBar,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.selected_sessions_count, selectedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
            )
            Spacer(modifier = Modifier.width(16.dp))
            if (isVaultSendMode) {
                Text(
                    text = stringResource(R.string.send),
                    color = if (enabled) {
                        colors.accent
                    } else {
                        colors.mutedText
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                TextButton(
                    onClick = onDelete,
                    enabled = enabled,
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = if (enabled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            colors.mutedText
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onValueChange: (String) -> Unit,
    activated: Boolean,
    onActivatedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    isSearching: Boolean,
    colors: ChatThemeColors,
    modifier: Modifier = Modifier
) {
    val transparent = Color.Transparent
    val focusRequester = remember { FocusRequester() }
    val searchInputActive = activated || query.isNotEmpty()

    LaunchedEffect(activated) {
        if (activated) {
            focusRequester.requestFocus()
        }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focus ->
                    if (!focus.hasFocus && query.isEmpty()) {
                        onActivatedChange(false)
                    }
                },
            readOnly = !(activated || query.isNotEmpty()),
            prefix = {
                if (searchInputActive) {
                    Text(
                        text = UsernameFormatter.PREFIX,
                        color = colors.text,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
            },
            trailingIcon = {
                if (searchInputActive) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_search),
                            tint = colors.mutedText,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent,
                    )
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Search,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = transparent,
                unfocusedBorderColor = transparent,
                disabledBorderColor = transparent,
                errorBorderColor = transparent,
                focusedContainerColor = transparent,
                unfocusedContainerColor = transparent,
                disabledContainerColor = transparent,
                cursorColor = colors.accent,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
            ),
        )
        if (!searchInputActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { onActivatedChange(true) })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.search_username),
                    color = colors.mutedText.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
