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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.codeshell.app.ui.chat.ChatAccessLockOverlay
import com.codeshell.app.ui.chat.ChatThemeColors
import com.codeshell.app.ui.chat.ChatUserIdBadge
import com.codeshell.app.ui.chat.rememberAdaptiveChatTheme
import com.codeshell.app.ui.util.AdaptiveWidthContainer
import com.codeshell.app.ui.components.HiddenSettingsIconButton
import com.codeshell.app.ui.components.OfflineBanner
import com.codeshell.app.CodeShellApplication
import com.codeshell.app.MainActivity
import com.codeshell.app.data.auth.BotAccountConstants
import com.codeshell.app.messaging.PendingSharedContent
import com.codeshell.app.util.MediaPreloadCache
import com.codeshell.app.util.UsernameFormatter
import com.codeshell.app.util.findActivity
import com.codeshell.app.ui.util.TerminalInactivityEffect
import com.codeshell.app.ui.util.rememberTerminalActivity
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (chatId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiTutor: (topicId: String, topicLabel: String, welcomeDigits: String, entrySource: String) -> Unit,
    onTerminalRevoked: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as CodeShellApplication
    val context = LocalContext.current
    val activity = context.findActivity() as? MainActivity
    val focusManager = LocalFocusManager.current
    val viewModel: HomeViewModel = viewModel { HomeViewModel(app) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chatTheme = rememberAdaptiveChatTheme(uiState.chatInterfaceStyle)
    val listColors = chatTheme.colors
    val profileDigits = UsernameFormatter.parseRawInput(uiState.profile?.username.orEmpty())
        .takeIf { it.isNotEmpty() }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSelectionMenu by remember { mutableStateOf(false) }
    var searchActivated by remember { mutableStateOf(false) }
    var expandedProfilePhotoUrl by remember { mutableStateOf<String?>(null) }
    var pendingSharedContent by remember { mutableStateOf(PendingSharedContent.peek()) }
    val searchActive = searchActivated || uiState.searchQuery.isNotEmpty()
    val gridState = rememberLazyGridState()
    val (bumpActivity, activityGeneration) = rememberTerminalActivity()

    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) bumpActivity()
    }

    LaunchedEffect(activity?.shareOpenTick) {
        pendingSharedContent = PendingSharedContent.peek()
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.selectionMode) {
                            if (uiState.selectedChatIds.isEmpty()) {
                                stringResource(R.string.select_sessions)
                            } else {
                                stringResource(R.string.selected_sessions_count, uiState.selectedChatIds.size)
                            }
                        } else {
                            stringResource(R.string.home_title)
                        },
                        color = listColors.text
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                uiState.selectionMode -> viewModel.cancelSelection()
                                searchActive -> dismissSearch()
                                else -> onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = listColors.text
                        )
                    }
                },
                actions = {
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
                        ChatUserIdBadge(
                            digits = profileDigits,
                            colors = listColors,
                        )
                        HiddenSettingsIconButton(onOpenSettings = onOpenSettings)
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

            pendingSharedContent?.let { content ->
                PendingShareBanner(
                    hasVideo = content.videoUri != null,
                    text = content.text,
                    colors = listColors,
                    onDismiss = {
                        PendingSharedContent.clear()
                        pendingSharedContent = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (uiState.chats.isEmpty() && uiState.profile != null && uiState.isLoggedIn) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .then(
                            if (uiState.selectionMode) {
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
                    columns = GridCells.Fixed(2),
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
                            if (uiState.selectionMode) {
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
                                } else {
                                    val digits = UsernameFormatter.parseRawInput(chat.otherUsername)
                                    openDisguiseAiTutor(digits)
                                }
                            },
                            onOpenSession = {
                                if (!uiState.selectionMode) {
                                    viewModel.openChat(chat.chatId, onOpenChat)
                                }
                            },
                            onViewProfilePhoto = {
                                if (!uiState.selectionMode && !chat.otherAvatarUrl.isNullOrBlank()) {
                                    expandedProfilePhotoUrl = chat.otherAvatarUrl
                                }
                            },
                        )
                    }
                }
            }
        }
    }
        AnimatedVisibility(
            visible = uiState.selectionMode,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SessionSelectionBar(
                selectedCount = uiState.selectedChatIds.size,
                enabled = uiState.selectedChatIds.isNotEmpty() && !uiState.isDeleting,
                colors = listColors,
                onDelete = { showDeleteDialog = true },
            )
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
                onDismiss = { expandedProfilePhotoUrl = null },
            )
        }
    }
    }
}

@Composable
private fun PendingShareBanner(
    hasVideo: Boolean,
    text: String?,
    colors: ChatThemeColors,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = colors.accent.copy(alpha = 0.18f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                    text = when {
                        hasVideo -> stringResource(R.string.pending_share_video_desc)
                        !text.isNullOrBlank() -> text
                        else -> stringResource(R.string.pending_share_text_desc)
                    },
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
    }
}

@Composable
private fun ProfilePhotoViewer(
    url: String,
    onDismiss: () -> Unit,
) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }

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
                modifier = Modifier
                    .fillMaxSize()
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
        }
    }
}

@Composable
private fun SessionSelectionBar(
    selectedCount: Int,
    enabled: Boolean,
    colors: ChatThemeColors,
    onDelete: () -> Unit,
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
