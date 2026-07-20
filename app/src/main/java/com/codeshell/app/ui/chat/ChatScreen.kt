package com.codeshell.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codeshell.app.BuildConfig
import com.codeshell.app.MainActivity
import com.codeshell.app.R
import com.codeshell.app.data.model.CallType
import com.codeshell.app.data.model.ChatMessage
import com.codeshell.app.data.model.MessageType
import com.codeshell.app.data.model.sessionLogAlignAsMine
import com.codeshell.app.data.model.PendingUpload
import com.codeshell.app.messaging.PendingSharedContent
import com.codeshell.app.ui.components.CodeShellDialog
import com.codeshell.app.ui.components.CodeShellDialogAction
import com.codeshell.app.ui.components.CodeShellDialogChoice
import com.codeshell.app.ui.components.CodeShellChoiceDivider
import com.codeshell.app.ui.components.OfflineBanner
import com.codeshell.app.ui.call.CallOverlay
import com.codeshell.app.ui.call.CallUiPhase
import com.codeshell.app.ui.call.CallViewModel
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.OnlineGreen
import com.codeshell.app.ui.disguise.LearningWebView
import com.codeshell.app.ui.util.AdaptiveWidthContainer
import com.codeshell.app.ui.util.TerminalInactivityEffect
import com.codeshell.app.util.MediaImageLoader
import com.codeshell.app.util.MediaPreloadCache
import com.codeshell.app.util.formatMessageTime
import com.codeshell.app.util.findActivity
import android.app.Application
import android.app.Activity
import android.Manifest
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.util.Date
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    onNavigateBack: () -> Unit,
    onNavigateToLearning: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMedia: (type: String, url: String, fileName: String?, messageId: String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(key = chatId) { ChatViewModel(app, chatId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = context.findActivity() as MainActivity
    val callViewModel: CallViewModel = viewModel(viewModelStoreOwner = activity) { CallViewModel(app) }
    val callUiState by callViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val imageLoader = remember { MediaImageLoader.get(context) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var videoCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var externalMediaFlowActive by remember { mutableStateOf(false) }
    var activityGeneration by remember { mutableIntStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var webViewUrl by remember { mutableStateOf<String?>(null) }
    var webViewTitle by remember { mutableStateOf<String?>(null) }
    val chatTheme = rememberAdaptiveChatTheme(
        style = uiState.chatInterfaceStyle,
        messageFontScale = uiState.chatMessageFontScale,
    )
    val chatColors = chatTheme.colors
    val canEditSelection = viewModel.editableSelectedMessage() != null
    var pendingDeniedMessage by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    LaunchedEffect(Unit) {
        callViewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun setSuppressHideOnHome(suppress: Boolean) {
        activity.applySuppressHideOnHome(suppress)
    }

    LaunchedEffect(chatId, uiState.currentUserId, uiState.otherUserId, uiState.otherDisplayName) {
        val userId = uiState.currentUserId
        val otherId = uiState.otherUserId
        if (userId != null && otherId != null) {
            callViewModel.bindChat(
                chatId = chatId,
                currentUserId = userId,
                otherUserId = otherId,
                otherDisplayName = uiState.otherDisplayName,
            )
        }
    }

    LaunchedEffect(chatId, uiState.currentUserId, uiState.otherUserId) {
        if (uiState.currentUserId == null || uiState.otherUserId == null) return@LaunchedEffect
        val sharedContent = PendingSharedContent.consume() ?: return@LaunchedEffect
        sharedContent.text?.takeIf { it.isNotBlank() }?.let(viewModel::onInputChange)
        sharedContent.videoUri?.let(viewModel::sendVideo)
    }

    val isCallActive = callUiState.phase != CallUiPhase.Hidden && callUiState.phase != CallUiPhase.StatusMessage
    val keepVideoCallAwake = isCallActive && callUiState.callType == CallType.VIDEO

    DisposableEffect(keepVideoCallAwake) {
        if (keepVideoCallAwake) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(callUiState.isScreenSharing) {
        if (callUiState.isScreenSharing) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else if (!BuildConfig.ALLOW_DEMO_CAPTURE) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        onDispose { }
    }

    LaunchedEffect(isCallActive) {
        activity.suppressTerminateOnLock = isCallActive
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) activityGeneration++
    }

    BackHandler(enabled = uiState.messageSelectionMode || isCallActive) {
        when {
            isCallActive -> callViewModel.minimizeCall()
            uiState.messageSelectionMode -> viewModel.cancelMessageSelection()
        }
    }

    // Activity signals: scroll, input text, pending uploads, selection changes, typing indication, new messages
    val messagesCount by remember {
        derivedStateOf { uiState.messages.size }
    }
    val lastMessageId by remember {
        derivedStateOf { uiState.messages.lastOrNull()?.id }
    }

    TerminalInactivityEffect(
        onNavigateToLearning,
        activityGeneration,
        uiState.inputText,
        uiState.pendingUploads.size,
        uiState.selectedMessageIds,
        uiState.isOtherTyping,
        messagesCount,
        lastMessageId,
        blocked = uiState.messageSelectionMode ||
            isCallActive ||
            pendingAction != null ||
            showClearDialog ||
            showBlockDialog ||
            showCameraDialog ||
            externalMediaFlowActive ||
            listState.isScrollInProgress,
    )

    val visualMediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        externalMediaFlowActive = false
        setSuppressHideOnHome(false)
        uri?.let(viewModel::sendPickedMedia)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        externalMediaFlowActive = false
        setSuppressHideOnHome(false)
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.sendFile(it)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        externalMediaFlowActive = false
        setSuppressHideOnHome(false)
        if (success) cameraUri?.let(viewModel::sendImage)
        cameraUri = null
    }
    val videoCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        externalMediaFlowActive = false
        setSuppressHideOnHome(false)
        if (success) videoCaptureUri?.let(viewModel::sendVideo)
        videoCaptureUri = null
    }

    val galleryPermissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val cameraPermissions = listOf(Manifest.permission.CAMERA)
    val videoCapturePermissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )
    val audioPermissions = listOf(Manifest.permission.RECORD_AUDIO)
    val screenShareNotificationPermissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        setSuppressHideOnHome(false)
        if (results.values.all { it }) {
            pendingAction?.invoke()
        } else {
            if (isCallActive) {
                callViewModel.reportPermissionDenied()
            }
            pendingDeniedMessage?.let { message ->
                scope.launch {
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
        pendingAction = null
        pendingDeniedMessage = null
    }

    fun runWithPermissions(
        required: List<String>,
        deniedMessage: String? = null,
        action: () -> Unit,
    ) {
        val missing = required.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingAction = action
            pendingDeniedMessage = deniedMessage
            setSuppressHideOnHome(true)
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    val screenShareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        setSuppressHideOnHome(false)
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            callViewModel.startScreenShare(result.resultCode, result.data!!)
        } else {
            if (!BuildConfig.ALLOW_DEMO_CAPTURE) {
                activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE,
                )
            }
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.call_screen_share_denied))
            }
        }
    }

    fun startVoiceCall() {
        runWithPermissions(
            required = audioPermissions,
            deniedMessage = context.getString(R.string.call_permission_denied),
        ) {
            callViewModel.startOutgoingCall(CallType.VOICE)
        }
    }

    fun startVideoCall() {
        runWithPermissions(
            required = videoCapturePermissions,
            deniedMessage = context.getString(R.string.call_camera_permission_denied),
        ) {
            callViewModel.startOutgoingCall(CallType.VIDEO)
        }
    }

    fun acceptIncomingCall() {
        if (callUiState.callType == CallType.VIDEO) {
            runWithPermissions(
                required = videoCapturePermissions,
                deniedMessage = context.getString(R.string.call_camera_permission_denied),
            ) {
                callViewModel.acceptIncomingCall()
            }
        } else {
            runWithPermissions(
                required = audioPermissions,
                deniedMessage = context.getString(R.string.call_permission_denied),
            ) {
                callViewModel.acceptIncomingCall()
            }
        }
    }

    fun requestScreenShare() {
        runWithPermissions(
            required = screenShareNotificationPermissions,
            deniedMessage = context.getString(R.string.call_screen_share_notification_permission),
        ) {
            setSuppressHideOnHome(true)
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenShareLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    val chatListEntries = uiState.listEntries
    var animatedMessageIds by remember(chatId) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(chatId) {
        animatedMessageIds = uiState.messages.map { it.id }.toSet()
    }
    LaunchedEffect(uiState.messages.lastOrNull()?.id) {
        val latestId = uiState.messages.lastOrNull()?.id ?: return@LaunchedEffect
        if (latestId !in animatedMessageIds) {
            animatedMessageIds = animatedMessageIds + latestId
        }
    }

    val bottomScrollTrigger by remember {
        derivedStateOf {
            uiState.messages.lastOrNull()?.id to uiState.isOtherTyping
        }
    }

    var userScrolledUp by remember(chatId) { mutableStateOf(false) }
    var needsInitialScroll by remember(chatId) { mutableStateOf(true) }
    var lastAutoScrolledMessageId by remember(chatId) { mutableStateOf<String?>(null) }

    LaunchedEffect(listState, chatId) {
        snapshotFlow {
            listState.firstVisibleItemIndex > 2 ||
                (listState.firstVisibleItemIndex == 2 && listState.firstVisibleItemScrollOffset > 32)
        }
            .distinctUntilChanged()
            .collect { scrolledUp -> userScrolledUp = scrolledUp }
    }

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val loadingExtra = if (uiState.isLoadingOlderMessages) 1 else 0
    val typingExtra = 1
    val listItemCount = uiState.pendingUploads.size + chatListEntries.size + loadingExtra + typingExtra

    LaunchedEffect(bottomScrollTrigger, listItemCount) {
        if (listItemCount > 0 && !userScrolledUp) {
            val latestMessageId = uiState.messages.lastOrNull()?.id
            if (needsInitialScroll) {
                listState.scrollToItem(0)
                needsInitialScroll = false
                lastAutoScrolledMessageId = latestMessageId
            } else if (latestMessageId != null && latestMessageId != lastAutoScrolledMessageId) {
                lastAutoScrolledMessageId = latestMessageId
                val latestMessage = uiState.messages.lastOrNull()
                if (latestMessage?.senderId == uiState.currentUserId) {
                    listState.animateScrollToItem(0)
                } else {
                    listState.scrollToItem(0)
                }
            } else if (uiState.isOtherTyping) {
                listState.scrollToItem(0)
            }
        }
    }

    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && listItemCount > 0 && !userScrolledUp) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(listState, uiState.hasMoreOlderMessages) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastIndex = layoutInfo.totalItemsCount - 1
            if (lastIndex < 0) {
                false
            } else {
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= lastIndex - 1
            }
        }
            .distinctUntilChanged()
            .collect { atTop ->
                if (atTop && uiState.hasMoreOlderMessages && !uiState.isLoadingOlderMessages) {
                    viewModel.loadOlderMessages()
                }
            }
    }

    DisposableEffect(Unit) {
        viewModel.setActiveInChat()
        onDispose {
            callViewModel.onChatScreenClosed()
            viewModel.clearTyping()
            viewModel.clearActiveInChat()
            setSuppressHideOnHome(false)
        }
    }

    if (showBlockDialog) {
        CodeShellDialog(
            onDismiss = { showBlockDialog = false },
            title = stringResource(R.string.block_user),
            content = {
                Text(
                    text = stringResource(R.string.block_user_confirm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EditorDefaultText.copy(alpha = 0.85f),
                )
            },
            actions = {
                CodeShellDialogAction(
                    label = stringResource(R.string.cancel),
                    onClick = { showBlockDialog = false },
                )
                CodeShellDialogAction(
                    label = stringResource(R.string.block_user_action),
                    onClick = {
                        showBlockDialog = false
                        viewModel.blockOtherUser(onNavigateBack)
                    },
                )
            },
        )
    } 

    if (showCameraDialog) {
        AlertDialog(
            onDismissRequest = { showCameraDialog = false },
            title = { Text(stringResource(R.string.camera)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showCameraDialog = false
                            runWithPermissions(cameraPermissions) {
                                val photoFile = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    photoFile
                                )
                                cameraUri = uri
                                externalMediaFlowActive = true
                                setSuppressHideOnHome(true)
                                cameraLauncher.launch(uri)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.take_photo))
                    }
                    TextButton(
                        onClick = {
                            showCameraDialog = false
                            runWithPermissions(videoCapturePermissions) {
                                val videoFile = File(context.cacheDir, "capture_${System.currentTimeMillis()}.mp4")
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    videoFile
                                )
                                videoCaptureUri = uri
                                externalMediaFlowActive = true
                                setSuppressHideOnHome(true)
                                videoCaptureLauncher.launch(uri)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.record_video))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCameraDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showClearDialog) {
        DeleteChoiceDialog(
            title = stringResource(R.string.clear_terminal),
            message = stringResource(R.string.clear_terminal_confirm),
            onDismiss = { showClearDialog = false },
            onForMeOnly = {
                showClearDialog = false
                viewModel.clearTerminalForMeOnly()
            },
            onForEveryone = {
                showClearDialog = false
                viewModel.clearTerminalForEveryone()
            },
        )
    }

    if (showRemoveDialog) {
        DeleteChoiceDialog(
            title = stringResource(R.string.remove),
            message = stringResource(R.string.delete_messages_confirm),
            showForEveryone = viewModel.canDeleteSelectedForEveryone(),
            onDismiss = { showRemoveDialog = false },
            onForMeOnly = {
                showRemoveDialog = false
                viewModel.removeSelectedMessagesForMeOnly()
            },
            onForEveryone = {
                showRemoveDialog = false
                viewModel.removeSelectedMessagesForEveryone()
            },
        )
    }

    AdaptiveWidthContainer {
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = chatColors.background,
        contentWindowInsets = WindowInsets.statusBars,
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedContent(
                targetState = uiState.messageSelectionMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) + slideInVertically { it / 3 } togetherWith
                        fadeOut(animationSpec = tween(160)) + slideOutVertically { -it / 3 }
                },
                label = "chatTopBar",
            ) { selectionMode ->
            if (selectionMode) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.selected_count, uiState.selectedMessageIds.size))
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::cancelMessageSelection) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = viewModel::startEditingSelectedMessage,
                            enabled = canEditSelection,
                        ) {
                            Text(stringResource(R.string.edit))
                        }
                        TextButton(
                            onClick = { showRemoveDialog = true },
                            enabled = uiState.selectedMessageIds.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.remove))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = chatColors.topBar,
                        titleContentColor = chatColors.text,
                        navigationIconContentColor = chatColors.text
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val showTopBarAvatar = uiState.profilePicturesEnabled && !uiState.otherAvatarUrl.isNullOrBlank()
                            if (showTopBarAvatar) {
                                AsyncImage(
                                    model = uiState.otherAvatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(chatColors.topBar, CircleShape)
                                        .clip(CircleShape),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (uiState.isOtherOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = uiState.otherDisplayName.ifBlank {
                                    stringResource(R.string.terminal_session_title)
                                }
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        ChatTopBarTrailingActions(
                            callEnabled = callUiState.phase == CallUiPhase.Hidden,
                            showMenu = showMenu,
                            onOpenMenu = { showMenu = true },
                            onDismissMenu = { showMenu = false },
                            onVoiceConnect = { startVoiceCall() },
                            onLiveConnect = { startVideoCall() },
                            onClearTerminal = { showClearDialog = true },
                            onBlockUser = { showBlockDialog = true },
                            onOpenSettings = onOpenSettings,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = chatColors.topBar,
                        titleContentColor = chatColors.text,
                        navigationIconContentColor = chatColors.text
                    )
                )
            }
            }
        },
        bottomBar = {
            if (uiState.messageSelectionMode) return@Scaffold
            ChatInputBar(
                inputText = uiState.inputText,
                onInputChange = viewModel::onInputChange,
                onSend = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.sendMessage()
                },
                isSending = uiState.isSending,
                theme = chatTheme,
                replyTarget = uiState.replyTarget,
                replyAuthorLabel = when (uiState.replyTarget?.senderId) {
                    uiState.currentUserId -> stringResource(R.string.reply_preview_you)
                    else -> uiState.otherDisplayName.ifBlank { "User" }
                },
                onClearReply = viewModel::clearReplyTarget,
                isEditing = uiState.editingMessageId != null,
                onClearEdit = viewModel::cancelEditing,
                onPickImage = {
                    runWithPermissions(galleryPermissions) {
                        externalMediaFlowActive = true
                        setSuppressHideOnHome(true)
                        visualMediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                },
                onTakePhoto = {
                    runWithPermissions(cameraPermissions) {
                        showCameraDialog = true
                    }
                },
                onPickFile = {
                    externalMediaFlowActive = true
                    setSuppressHideOnHome(true)
                    filePicker.launch(arrayOf("*/*"))
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OfflineBanner(colors = chatColors)

            if (uiState.messages.isEmpty() && uiState.pendingUploads.isEmpty() && !uiState.isOtherTyping) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (uiState.messageSelectionMode) {
                                Modifier.pointerInput(uiState.messageSelectionMode) {
                                    detectTapGestures(onTap = { viewModel.cancelMessageSelection() })
                                }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoadingSession) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                            color = chatColors.accent,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.no_session_output),
                            color = chatColors.mutedText
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (uiState.messageSelectionMode) {
                                Modifier.pointerInput(uiState.messageSelectionMode) {
                                    detectTapGestures(onTap = { viewModel.cancelMessageSelection() })
                                }
                            } else {
                                Modifier
                            }
                        ),
                    state = listState,
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    item(key = "typing_indicator") {
                        ChatSlideInVisibility(visible = uiState.isOtherTyping) {
                            ChatTypingIndicator(theme = chatTheme)
                        }
                    }
                    items(
                        uiState.pendingUploads.asReversed(),
                        key = { "pending-${it.localId}" },
                        contentType = { "pending" },
                    ) { upload ->
                        PendingUploadRow(
                            upload = upload,
                            isMine = true,
                            theme = chatTheme,
                            onCancel = { viewModel.cancelPendingUpload(upload.localId) },
                        )
                    }
                    items(
                        chatListEntries,
                        key = { entry ->
                            when (entry) {
                                is ChatListEntry.DateSeparator -> "date-${entry.dayKey}"
                                is ChatListEntry.Message -> entry.message.id
                            }
                        },
                        contentType = { entry -> chatListEntryContentType(entry) },
                    ) { entry ->
                        when (entry) {
                            is ChatListEntry.DateSeparator -> {
                                ChatDateSeparator(label = entry.label, theme = chatTheme)
                            }
                            is ChatListEntry.Message -> {
                                val message = entry.message
                                val isMine = message.senderId == uiState.currentUserId
                                val isVisible by remember {
                                    derivedStateOf {
                                        listState.layoutInfo.visibleItemsInfo.any { item ->
                                            item.key == message.id
                                        }
                                    }
                                }
                                LaunchedEffect(isVisible, message.id) {
                                    if (isVisible && !isMine && !message.deleted && !message.read) {
                                        viewModel.markMessageSeen(message.id)
                                    }
                                }
                                ChatMessageRemoveVisibility(
                                    visible = message.id !in uiState.removingMessageIds,
                                ) {
                                    ChatSlideInItem(
                                        itemKey = message.id,
                                        animate = message.id !in animatedMessageIds,
                                    ) {
                                        when (message.type) {
                                            MessageType.SESSION -> SessionLogCard(
                                                message = message,
                                                isMine = message.sessionLogAlignAsMine(uiState.currentUserId.orEmpty()),
                                                theme = chatTheme,
                                            )
                                            MessageType.SYSTEM -> SystemNoticeCard(message = message, theme = chatTheme)
                                            else -> MessageBubble(
                                                message = message,
                                                isMine = isMine,
                                                theme = chatTheme,
                                                selectionMode = uiState.messageSelectionMode,
                                                selected = message.id in uiState.selectedMessageIds,
                                                onLongPress = {
                                                    if (!message.deleted) {
                                                        viewModel.startMessageSelection(message.id)
                                                    }
                                                },
                                                onToggleSelection = { viewModel.toggleMessageSelection(message.id) },
                                                onReply = { viewModel.setReplyTarget(message) },
                                                onDoubleTap = onNavigateToLearning,
                                                onOpenMedia = { type, url, fileName, messageId ->
                                                    if ((type == MessageType.IMAGE || type == MessageType.VIDEO) && url.isNotBlank()) {
                                                        imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
                                                    }
                                                    if (type == MessageType.VIDEO && url.isNotBlank()) {
                                                        MediaPreloadCache.preload(context, url)
                                                    }
                                                    onOpenMedia(type.name.lowercase(), url, fileName, messageId)
                                                },
                                                onOpenLink = { url, title ->
                                                    webViewUrl = url
                                                    webViewTitle = title
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (uiState.isLoadingOlderMessages) {
                        item(key = "loading_older") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
        }
        callUiState.otherIncomingCallType?.let { incomingType ->
            OtherConversationIncomingCallBanner(
                callType = incomingType,
                onClick = onNavigateBack,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp, start = 14.dp, end = 14.dp),
            )
        }
        if (callUiState.phase == CallUiPhase.StatusMessage) {
            CallOverlay(
                uiState = callUiState,
                colors = chatColors,
                onAccept = { acceptIncomingCall() },
                onDecline = callViewModel::declineIncomingCall,
                onEndCall = callViewModel::endActiveCall,
                onMinimize = callViewModel::minimizeCall,
                onExpand = callViewModel::expandCall,
                onToggleMute = callViewModel::toggleMute,
                onRefreshAudioRoutes = callViewModel::refreshAudioRoutes,
                onSelectAudioRoute = callViewModel::selectAudioRoute,
                onToggleVideo = callViewModel::toggleVideo,
                onSwitchCamera = callViewModel::switchCamera,
                onRequestScreenShare = { requestScreenShare() },
                onStopScreenShare = callViewModel::stopScreenShare,
                onBindLocalRenderer = callViewModel::bindLocalRenderer,
                onBindRemoteRenderer = callViewModel::bindRemoteRenderer,
                onDismissStatus = callViewModel::dismissStatus,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (callUiState.phase != CallUiPhase.Hidden) {
            CallOverlay(
                uiState = callUiState,
                colors = chatColors,
                onAccept = { acceptIncomingCall() },
                onDecline = callViewModel::declineIncomingCall,
                onEndCall = callViewModel::endActiveCall,
                onMinimize = callViewModel::minimizeCall,
                onExpand = callViewModel::expandCall,
                onToggleMute = callViewModel::toggleMute,
                onRefreshAudioRoutes = callViewModel::refreshAudioRoutes,
                onSelectAudioRoute = callViewModel::selectAudioRoute,
                onToggleVideo = callViewModel::toggleVideo,
                onSwitchCamera = callViewModel::switchCamera,
                onRequestScreenShare = { requestScreenShare() },
                onStopScreenShare = callViewModel::stopScreenShare,
                onBindLocalRenderer = callViewModel::bindLocalRenderer,
                onBindRemoteRenderer = callViewModel::bindRemoteRenderer,
                onDismissStatus = callViewModel::dismissStatus,
                modifier = if (callUiState.isMinimized) {
                    Modifier
                        .align(
                            if (callUiState.callType == CallType.VIDEO) {
                                Alignment.TopEnd
                            } else {
                                Alignment.TopCenter
                            }
                        )
                        .statusBarsPadding()
                        .padding(top = 64.dp)
                } else {
                    Modifier
                },
            )
        }
    }
        webViewUrl?.let { url ->
            LearningWebView(
                url = url,
                title = webViewTitle.orEmpty(),
                onClose = {
                    webViewUrl = null
                    webViewTitle = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun OtherConversationIncomingCallBanner(
    callType: CallType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick),
        color = Color(0xFF1FA855),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.18f),
                shape = CircleShape,
                modifier = Modifier.size(30.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = when (callType) {
                    CallType.VIDEO -> Icons.Default.Videocam
                    CallType.VOICE -> Icons.Default.Phone
                },
                contentDescription = null,
                tint = Color.White,
                        modifier = Modifier.size(17.dp),
            )
                }
            }
            Text(
                text = when (callType) {
                    CallType.VIDEO -> stringResource(R.string.video_call_incoming_other_chat)
                    CallType.VOICE -> stringResource(R.string.call_incoming_other_chat)
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChatTopBarTrailingActions(
    callEnabled: Boolean,
    showMenu: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onVoiceConnect: () -> Unit,
    onLiveConnect: () -> Unit,
    onClearTerminal: () -> Unit,
    onBlockUser: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box {
        IconButton(onClick = onOpenMenu) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.voice_connect)) },
                onClick = {
                    onDismissMenu()
                    if (callEnabled) onVoiceConnect()
                },
                enabled = callEnabled,
                leadingIcon = {
                    Icon(Icons.Default.Phone, contentDescription = null)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.live_connect)) },
                onClick = {
                    onDismissMenu()
                    if (callEnabled) onLiveConnect()
                },
                enabled = callEnabled,
                leadingIcon = {
                    Icon(Icons.Default.Videocam, contentDescription = null)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.clear_terminal)) },
                onClick = {
                    onDismissMenu()
                    onClearTerminal()
                },
                leadingIcon = {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.block_user_action)) },
                onClick = {
                    onDismissMenu()
                    onBlockUser()
                },
                leadingIcon = {
                    Icon(Icons.Default.Block, contentDescription = null)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                onClick = {
                    onDismissMenu()
                    onOpenSettings()
                },
                leadingIcon = {
                    Icon(Icons.Default.Settings, contentDescription = null)
                },
            )
        }
    }
}

@Composable
private fun PendingUploadRow(
    upload: PendingUpload,
    isMine: Boolean,
    theme: ChatTheme,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        PendingUploadBubble(
            upload = upload,
            isMine = isMine,
            theme = theme,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    theme: ChatTheme,
    selectionMode: Boolean,
    selected: Boolean,
    onLongPress: () -> Unit,
    onToggleSelection: () -> Unit,
    onReply: () -> Unit,
    onDoubleTap: () -> Unit,
    onOpenMedia: (MessageType, String, String?, String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: ((url: String, title: String) -> Unit)? = null,
) {
    val colors = theme.colors
    val bubbleColor = if (isMine) colors.bubbleMine else colors.bubbleOther
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (theme.useAsymmetricBubbleTail) {
        RoundedCornerShape(
            topStart = theme.bubbleCornerLarge,
            topEnd = theme.bubbleCornerLarge,
            bottomStart = if (isMine) theme.bubbleCornerLarge else theme.bubbleCornerSmall,
            bottomEnd = if (isMine) theme.bubbleCornerSmall else theme.bubbleCornerLarge,
        )
    } else {
        RoundedCornerShape(theme.bubbleCornerLarge)
    }
    val selectionBorderColor by animateColorAsState(
        targetValue = if (selected) colors.accent else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "selectionBorderColor",
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "selectionBorderWidth",
    )
    val bubbleScale by animateFloatAsState(
        targetValue = if (selected) 0.985f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "selectionScale",
    )
    val bubbleAlpha by animateFloatAsState(
        targetValue = when {
            selected -> 1f
            selectionMode -> 0.9f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 200),
        label = "selectionAlpha",
    )
    val isVisualMediaMessage = !message.deleted &&
        (message.type == MessageType.IMAGE || message.type == MessageType.VIDEO) &&
        message.mediaUrl != null
    val contentPadding = if (isVisualMediaMessage) {
        Modifier.padding(horizontal = 0.dp, vertical = 0.dp)
    } else {
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    }
    val timestampPadding = if (isVisualMediaMessage) {
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    } else {
        Modifier
    }

    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 48.dp.toPx() }
    val maxSwipePx = with(density) { 56.dp.toPx() }
    var rawDragOffset by remember(message.id) { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = rawDragOffset,
        animationSpec = tween(durationMillis = 200),
        label = "bubbleSwipe",
    )

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .widthIn(max = theme.bubbleMaxWidth)
                .wrapContentWidth(align = if (isMine) Alignment.End else Alignment.Start)
                .graphicsLayer {
                    scaleX = bubbleScale
                    scaleY = bubbleScale
                    alpha = bubbleAlpha
                }
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .border(selectionBorderWidth, selectionBorderColor, shape)
                .background(bubbleColor, shape)
                .then(
                    if (!selectionMode && !message.deleted) {
                        Modifier.pointerInput(message.id) {
                            var dragDistance = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragDistance = 0f
                                    rawDragOffset = 0f
                                },
                                onHorizontalDrag = { _, amount ->
                                    dragDistance += amount
                                    rawDragOffset = dragDistance.coerceIn(-maxSwipePx, maxSwipePx)
                                },
                                onDragEnd = {
                                    if (abs(dragDistance) >= swipeThresholdPx) {
                                        onReply()
                                    }
                                    dragDistance = 0f
                                    rawDragOffset = 0f
                                },
                                onDragCancel = {
                                    dragDistance = 0f
                                    rawDragOffset = 0f
                                },
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (selectionMode && !message.deleted) {
                        Modifier.clickable { onToggleSelection() }
                    } else if (!message.deleted) {
                        Modifier.pointerInput(message.id) {
                            detectTapGestures(
                                onLongPress = { onLongPress() },
                                onDoubleTap = { onDoubleTap() },
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .then(contentPadding)
        ) {
            Crossfade(
                targetState = message.deleted,
                animationSpec = tween(durationMillis = 200),
                label = "messageDeleted",
            ) { deleted ->
                if (deleted) {
                    Text(
                        text = ChatMessage.DELETED_PLACEHOLDER,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.mutedText,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    )
                } else {
                    MessageBubbleContent(
                        message = message,
                        isMine = isMine,
                        theme = theme,
                        onOpenMedia = onOpenMedia,
                        mediaClickEnabled = !selectionMode,
                        onLongPress = if (!selectionMode) onLongPress else null,
                        onOpenLink = onOpenLink,
                    )
                }
            }
            if (!message.deleted) {
                MessageBadges(message = message, colors = colors)
            }
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .align(Alignment.End)
                    .then(timestampPadding)
                    .then(if (!isVisualMediaMessage) Modifier.padding(top = 4.dp) else Modifier),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                message.timestamp?.let { ts ->
                    Text(
                        text = formatMessageTime(Date(ts.seconds * 1000)),
                        style = if (theme.useMonospaceText) {
                            MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        } else {
                            MaterialTheme.typography.labelSmall
                        },
                        color = colors.mutedText
                    )
                }
                if (message.editedAt != null && !message.deleted) {
                    Text(
                        text = stringResource(R.string.message_edited),
                        modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        ),
                        color = colors.mutedText,
                    )
                }
                if (isMine && !message.deleted) {
                    MessageReceipt(message = message, theme = theme)
                }
            }
        }
    }
}

@Composable
private fun MessageReceipt(message: ChatMessage, theme: ChatTheme) {
    val colors = theme.colors
    val (label, color) = when {
        message.read -> "✓✓" to OnlineGreen
        message.delivered -> "✓✓" to colors.mutedText
        else -> "✓" to colors.text.copy(alpha = 0.8f)
    }
    Text(
        text = label,
        modifier = Modifier.padding(start = 6.dp),
        style = if (theme.useMonospaceText) {
            MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        } else {
            MaterialTheme.typography.labelSmall
        },
        color = color,
        textAlign = TextAlign.End,
    )
}

@Composable
private fun DeleteChoiceDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onForMeOnly: () -> Unit,
    onForEveryone: () -> Unit,
    showForEveryone: Boolean = true,
) {
    CodeShellDialog(
        onDismiss = onDismiss,
        title = title,
        content = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = EditorDefaultText.copy(alpha = 0.85f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            CodeShellDialogChoice(
                label = stringResource(R.string.delete_for_me_only),
                onClick = onForMeOnly,
            )
            if (showForEveryone) {
                CodeShellChoiceDivider()
                CodeShellDialogChoice(
                    label = stringResource(R.string.delete_for_everyone),
                    onClick = onForEveryone,
                )
            }
        },
        actions = {
            CodeShellDialogAction(
                label = stringResource(R.string.cancel),
                onClick = onDismiss,
            )
        },
    )
}
