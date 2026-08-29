package com.codeshell.app.ui.vault

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.codeshell.app.R
import com.codeshell.app.data.model.ChatInterfaceStyle
import com.codeshell.app.data.model.ChatSummary
import com.codeshell.app.data.model.VaultItem
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.ui.chat.ChatAccessLockOverlay
import com.codeshell.app.ui.chat.rememberAdaptiveChatTheme
import com.codeshell.app.ui.components.MediaCropDialog
import com.codeshell.app.ui.components.ProfilePhotoCropDialog
import com.codeshell.app.ui.home.HomeViewModel
import com.codeshell.app.ui.settings.SettingsViewModel
import com.codeshell.app.util.findActivity
import com.codeshell.app.util.ChatAccessCodeHasher
import com.codeshell.app.ui.util.TerminalInactivityEffect
import com.codeshell.app.util.formatFileSize
import com.codeshell.app.util.mimeTypeForFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val EditorBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background

private val EditorDefaultText: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

private val TopBarBackground: Color
    @Composable get() = MaterialTheme.colorScheme.surface

private fun vaultDateGroupKey(timestamp: Long): Pair<Int, Int> {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    return calendar.get(Calendar.YEAR) to calendar.get(Calendar.DAY_OF_YEAR)
}

private fun vaultDateGroupLabel(timestamp: Long): String {
    val itemCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    fun sameDay(left: Calendar, right: Calendar): Boolean =
        left.get(Calendar.ERA) == right.get(Calendar.ERA) &&
            left.get(Calendar.YEAR) == right.get(Calendar.YEAR) &&
            left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR)

    return when {
        sameDay(itemCalendar, today) -> "Today"
        sameDay(itemCalendar, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onNavigateToHomeForVaultSend: (() -> Unit)? = null,
    onInactivityTimeout: () -> Unit = onNavigateBack,
) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel: VaultViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(app) }
    val homeViewModel: HomeViewModel = viewModel { HomeViewModel(app) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val accessCodeEnabled by appPreferences.chatAccessCodeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val selectedChatStyle by appPreferences.chatInterfaceStyle.collectAsStateWithLifecycle(
        initialValue = ChatInterfaceStyle.DEFAULT,
    )
    val vaultGridColumns by appPreferences.vaultGridColumns.collectAsStateWithLifecycle(initialValue = 3)
    val vaultTheme = rememberAdaptiveChatTheme(
        if (selectedChatStyle == ChatInterfaceStyle.IDE_LIGHT) ChatInterfaceStyle.IDE_LIGHT else ChatInterfaceStyle.DEFAULT
    )
    val vaultAccessUnlocked by VaultAccessSession.unlocked.collectAsStateWithLifecycle()
    var vaultAccessDigits by remember { mutableStateOf("") }
    var vaultAccessError by remember { mutableStateOf<String?>(null) }
    var profilePhotoUpdating by remember { mutableStateOf(false) }
    var profilePhotoSaveStarted by remember { mutableStateOf(false) }
    // Resolve the host activity so we can suppress the secure-exit (hide-on-home)
    // feature while an external media picker is showing. Without this, opening
    // the system photo picker triggers onUserLeaveHint() which finishes the
    // activity — and the process crashes when the picker result is delivered.
    val activity = context.findActivity() as? com.codeshell.app.MainActivity

    fun setSuppressHideOnHome(suppress: Boolean) {
        activity?.applySuppressHideOnHome(suppress)
    }

    var showSelectionMenu by remember { mutableStateOf(false) }
    var showGridMenu by remember { mutableStateOf(false) }
    var showSendConfirmationDialog by remember { mutableStateOf(false) }
    var pendingSendItem by remember { mutableStateOf<VaultItem?>(null) }
    var pendingExtractZipItem by remember { mutableStateOf<VaultItem?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<VaultItem?>(null) }
    var showDeleteSelectionConfirmation by remember { mutableStateOf(false) }
    var pendingMoveItem by remember { mutableStateOf<VaultItem?>(null) }
    var showMoveSelectionConfirmation by remember { mutableStateOf(false) }
    var pendingCopyItem by remember { mutableStateOf<VaultItem?>(null) }
    var showCopySelectionConfirmation by remember { mutableStateOf(false) }
    var editingImageItem by remember { mutableStateOf<VaultItem?>(null) }
    var editingImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingProfilePhotoUri by remember { mutableStateOf<Uri?>(null) }
    var wallpaperTargetItem by remember { mutableStateOf<VaultItem?>(null) }
    var activityGeneration by remember { mutableIntStateOf(0) }
    var vaultViewerVideoPlaying by remember { mutableStateOf(false) }

    val touchActivityModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.changedToDown() }) {
                    activityGeneration++
                }
            }
        }
    }

    TerminalInactivityEffect(
        onInactivityTimeout,
        activityGeneration,
        uiState.isLoading,
        uiState.isSaving,
        uiState.isExporting,
        enabled = !vaultViewerVideoPlaying,
    )

    // Use PickMultipleVisualMedia so the system photo/video gallery opens
    // (not a generic file manager).  This contract returns content URIs that
    // already have read permission for the duration of the app process.
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        // Always re-allow hide-on-home the moment the picker returns.
        setSuppressHideOnHome(false)
        if (uris.isNotEmpty()) {
            // Take persistable permission so background threads can read the files
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Some providers don't support persistable permission
                }
            }
            viewModel.saveToVaultFromUris(uris, deleteSource = false) { summary ->
                scope.launch {
                    snackbarHostState.showSnackbar(vaultImportSummaryMessage(summary))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadItems()
    }

    LaunchedEffect(
        profilePhotoUpdating,
        settingsUiState.isSavingProfilePhoto,
        settingsUiState.successMessage,
        settingsUiState.errorMessage,
    ) {
        if (profilePhotoUpdating && settingsUiState.isSavingProfilePhoto) {
            profilePhotoSaveStarted = true
        }
        if (profilePhotoUpdating && profilePhotoSaveStarted && !settingsUiState.isSavingProfilePhoto) {
            profilePhotoUpdating = false
            profilePhotoSaveStarted = false
            val message = settingsUiState.successMessage ?: settingsUiState.errorMessage
            message?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    LaunchedEffect(editingImageItem?.id) {
        editingImageUri = editingImageItem?.let { item ->
            withContext(Dispatchers.IO) {
                viewModel.getVaultFile(item)?.let(Uri::fromFile)
            }
        }
    }

    DisposableEffect(Unit) {
        // Safety net: if the screen leaves while suppressHideOnHome was set
        // (e.g. process went away) make sure the flag is cleared so the
        // chat interface's secure exit keeps working elsewhere.
        onDispose {
            setSuppressHideOnHome(false)
            VaultAccessSession.lock()
        }
    }

    BackHandler(enabled = uiState.selectionMode) {
        viewModel.clearItemSelection()
    }

    fun performDelete(item: VaultItem) {
        val deletionToken = viewModel.deleteItemWithUndo(item)
        scope.launch {
            val dismissJob = launch {
                delay(5_000L)
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.deleted_item, item.fileName),
                actionLabel = context.getString(R.string.undo),
            )
            dismissJob.cancel()
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoPendingDelete(deletionToken)
            }
        }
    }

    fun performDeleteSelection() {
        val count = uiState.selectedItems.size
        val deletionToken = viewModel.deleteSelectedItems()
        scope.launch {
            val dismissJob = launch {
                delay(5_000L)
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            val result = snackbarHostState.showSnackbar(
                message = if (count == 1) {
                    context.getString(R.string.deleted_one_item)
                } else {
                    context.getString(R.string.deleted_count_items, count)
                },
                actionLabel = context.getString(R.string.undo),
            )
            dismissJob.cancel()
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoPendingDelete(deletionToken)
            }
        }
    }

    fun sendSelectedItemsToChat() {
        val selectedIds = uiState.selectedItems.toList()
        if (selectedIds.isEmpty()) return
        PendingVaultSend.setAll(selectedIds)
        onNavigateToHomeForVaultSend?.invoke()
    }

    Scaffold(
        modifier = touchActivityModifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            uiState.selectionMode && uiState.selectedItems.isNotEmpty() ->
                                stringResource(R.string.selected_count, uiState.selectedItems.size)
                            uiState.selectionMode -> stringResource(R.string.select_items)
                            else -> stringResource(R.string.vault_title)
                        },
                        modifier = if (uiState.selectionMode) {
                            Modifier.clickable { viewModel.toggleSelectAllItems() }
                        } else {
                            Modifier
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.selectionMode) {
                            viewModel.clearItemSelection()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showGridMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = stringResource(R.string.vault_view_grid),
                                tint = EditorDefaultText,
                            )
                        }
                        DropdownMenu(
                            expanded = showGridMenu,
                            onDismissRequest = { showGridMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.vault_view_grid_3)) },
                                onClick = {
                                    showGridMenu = false
                                    scope.launch { appPreferences.setVaultGridColumns(3) }
                                },
                                leadingIcon = {
                                    if (vaultGridColumns == 3) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.vault_view_grid_4)) },
                                onClick = {
                                    showGridMenu = false
                                    scope.launch { appPreferences.setVaultGridColumns(4) }
                                },
                                leadingIcon = {
                                    if (vaultGridColumns == 4) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                    if (uiState.selectionMode && uiState.selectedItems.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showSelectionMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_options),
                                    tint = EditorDefaultText
                                )
                            }
                            DropdownMenu(
                                expanded = showSelectionMenu,
                                onDismissRequest = { showSelectionMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.send_to_chat)) },
                                    onClick = {
                                        showSelectionMenu = false
                                        sendSelectedItemsToChat()
                                    },
                                    enabled = !uiState.isSending
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.move_to_gallery)) },
                                    onClick = {
                                        showSelectionMenu = false
                                        showMoveSelectionConfirmation = true
                                    },
                                    enabled = !uiState.isExporting
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_to_gallery)) },
                                    onClick = {
                                        showSelectionMenu = false
                                        showCopySelectionConfirmation = true
                                    },
                                    enabled = !uiState.isExporting
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_as_zip)) },
                                    onClick = {
                                        showSelectionMenu = false
                                        viewModel.exportSelectedAsZip()
                                    },
                                    enabled = !uiState.isExporting,
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = {
                                        showSelectionMenu = false
                                        showDeleteSelectionConfirmation = true
                                    },
                                    enabled = !uiState.isExporting
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.cancel)) },
                                    onClick = {
                                        showSelectionMenu = false
                                        viewModel.clearItemSelection()
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = EditorDefaultText,
                    navigationIconContentColor = EditorDefaultText,
                ),
            )
        },
        floatingActionButton = {
            if (!uiState.selectionMode) {
                FloatingActionButton(
                    onClick = {
                        // Suppress the hide-on-home secure exit for as long as the
                        // system gallery is in front, otherwise MainActivity would
                        // be finished() and crash when the picker returns.
                        setSuppressHideOnHome(true)
                        galleryPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    containerColor = EditorDefaultText.copy(alpha = 0.9f),
                    contentColor = EditorBackground
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_to_vault),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.saveProgress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LinearProgressIndicator(
                        progress = { uiState.saveProgress!!.first.toFloat() / uiState.saveProgress!!.second },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                    )
                }
            }
            uiState.extractionProgress?.let { (completed, total) ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.extracting_zip_progress, completed, total),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    LinearProgressIndicator(
                        progress = {
                            if (total > 0) completed.toFloat() / total else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
            }

            val gridState = rememberLazyGridState()

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.loadError != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.vault_load_failed),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = uiState.loadError.orEmpty(),
                                color = EditorDefaultText.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Button(
                                onClick = viewModel::loadItems,
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }

                uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = EditorDefaultText.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp),
                            )
                            Text(
                                text = stringResource(R.string.no_media_saved_yet),
                                color = EditorDefaultText.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                text = stringResource(R.string.vault_empty_hint),
                                color = EditorDefaultText.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

            else -> {
                    val groupedItems = uiState.items.groupBy { vaultDateGroupKey(it.dateSaved) }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyVerticalGrid(
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                run {
                                    var dragSelectionMode: Boolean? = null
                                    var lastDraggedItemId: String? = null

                                    fun itemAt(position: Offset): VaultItem? {
                                        val layoutItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                            position.x >= info.offset.x &&
                                                position.x < info.offset.x + info.size.width &&
                                                position.y >= info.offset.y &&
                                                position.y < info.offset.y + info.size.height
                                        } ?: return null
                                        val itemId = layoutItem.key as? String ?: return null
                                        return uiState.items.firstOrNull { it.id == itemId }
                                    }

                                    awaitEachGesture {
                                        val down = awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial,
                                        )
                                        var latestPosition = down.position
                                        val selectionWasActive = viewModel.uiState.value.selectionMode

                                        if (selectionWasActive) {
                                            var dragging = false
                                            var dragSelectionMode: Boolean? = null
                                            var startDraggedItemId: String? = null

                                            fun itemsInSelectionRect(currentPosition: Offset): List<VaultItem> {
                                                val startId = startDraggedItemId ?: return emptyList()
                                                val startInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                                                    it.key == startId
                                                } ?: return emptyList()
                                                val currentItem = itemAt(currentPosition) ?: return emptyList()
                                                val currentInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                                                    it.key == currentItem.id
                                                } ?: return emptyList()
                                                val left = minOf(startInfo.offset.x, currentInfo.offset.x)
                                                val top = minOf(startInfo.offset.y, currentInfo.offset.y)
                                                val right = maxOf(
                                                    startInfo.offset.x + startInfo.size.width,
                                                    currentInfo.offset.x + currentInfo.size.width,
                                                )
                                                val bottom = maxOf(
                                                    startInfo.offset.y + startInfo.size.height,
                                                    currentInfo.offset.y + currentInfo.size.height,
                                                )
                                                return gridState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                                                    val itemId = info.key as? String ?: return@mapNotNull null
                                                    val item = uiState.items.firstOrNull { it.id == itemId }
                                                        ?: return@mapNotNull null
                                                    val intersects =
                                                        info.offset.x < right &&
                                                            info.offset.x + info.size.width > left &&
                                                            info.offset.y < bottom &&
                                                            info.offset.y + info.size.height > top
                                                    item.takeIf { intersects }
                                                }
                                            }

                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                    ?: break
                                                if (!change.pressed) break
                                                latestPosition = change.position

                                                if (!dragging &&
                                                    (change.position - down.position).getDistance() > 16f
                                                ) {
                                                    val startItem = itemAt(down.position)
                                                        ?: return@awaitEachGesture
                                                    val currentState = viewModel.uiState.value
                                                    dragSelectionMode = startItem.id !in currentState.selectedItems
                                                    startDraggedItemId = startItem.id
                                                    dragging = true
                                                }

                                                if (dragging) {
                                                    viewModel.setItemsSelection(
                                                        itemsInSelectionRect(latestPosition),
                                                        dragSelectionMode == true,
                                                    )
                                                }
                                            }
                                            return@awaitEachGesture
                                        }

                                        val longPressPosition: Offset? = try {
                                            withTimeout<Offset?>(500L) {
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                        ?: return@withTimeout null
                                                    if (!change.pressed) return@withTimeout null
                                                    latestPosition = change.position
                                                    if ((change.position - down.position).getDistance() > 24f) {
                                                        return@withTimeout null
                                                    }
                                                }
                                                latestPosition
                                            }
                                        } catch (_: TimeoutCancellationException) {
                                            latestPosition
                                        }
                                        val startPosition = longPressPosition ?: return@awaitEachGesture
                                        val startItem = itemAt(startPosition)
                                            ?: return@awaitEachGesture
                                        viewModel.startItemSelection(startItem)
                                        val dragSelectionMode = true

                                        fun itemsInSelectionRect(currentPosition: Offset): List<VaultItem> {
                                            val startInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                                                it.key == startItem.id
                                            } ?: return emptyList()
                                            val currentItem = itemAt(currentPosition) ?: return emptyList()
                                            val currentInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                                                it.key == currentItem.id
                                            } ?: return emptyList()
                                            val left = minOf(startInfo.offset.x, currentInfo.offset.x)
                                            val top = minOf(startInfo.offset.y, currentInfo.offset.y)
                                            val right = maxOf(
                                                startInfo.offset.x + startInfo.size.width,
                                                currentInfo.offset.x + currentInfo.size.width,
                                            )
                                            val bottom = maxOf(
                                                startInfo.offset.y + startInfo.size.height,
                                                currentInfo.offset.y + currentInfo.size.height,
                                            )
                                            return gridState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                                                val itemId = info.key as? String ?: return@mapNotNull null
                                                val item = uiState.items.firstOrNull { it.id == itemId }
                                                    ?: return@mapNotNull null
                                                val intersects =
                                                    info.offset.x < right &&
                                                        info.offset.x + info.size.width > left &&
                                                        info.offset.y < bottom &&
                                                        info.offset.y + info.size.height > top
                                                item.takeIf { intersects }
                                            }
                                        }

                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                                ?: break
                                            if (!change.pressed) break
                                            viewModel.setItemsSelection(
                                                itemsInSelectionRect(change.position),
                                                dragSelectionMode,
                                            )
                                        }
                                    }
                                }
                            },
                            columns = GridCells.Fixed(vaultGridColumns),
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            userScrollEnabled = true,
                        ) {
                            groupedItems.forEach { (_, itemsForDate) ->
                                item(
                                    key = "date-${vaultDateGroupKey(itemsForDate.first().dateSaved)}",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    Text(
                                        text = vaultDateGroupLabel(itemsForDate.first().dateSaved),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EditorDefaultText.copy(alpha = 0.72f),
                                        modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 6.dp),
                                    )
                                }
                                items(itemsForDate, key = { it.id }) { item ->
                                    VaultItemTile(
                                        item = item,
                                        viewModel = viewModel,
                                        selectionMode = uiState.selectionMode,
                                         selected = item.id in uiState.selectedItems,
                                         modifier = Modifier.aspectRatio(1f),
                                         onClick = {
                                             if (item.isZip) pendingExtractZipItem = item
                                             else viewModel.selectItem(item)
                                         },
                                         onToggleSelection = { viewModel.toggleItemSelection(item) },
                                        onLongPress = {
                                            if (!uiState.selectionMode) {
                                                viewModel.startItemSelection(item)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.selectionMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { showDeleteSelectionConfirmation = true },
                            enabled = uiState.selectedItems.isNotEmpty() && !uiState.isExporting,
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                        TextButton(
                            onClick = ::sendSelectedItemsToChat,
                            enabled = uiState.selectedItems.isNotEmpty() && !uiState.isSending,
                        ) {
                            Text(stringResource(R.string.send))
                        }
                        TextButton(onClick = viewModel::clearItemSelection) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            }
        }
    }

    uiState.selectedItem?.let { item ->
        VaultItemViewerDialog(
            item = item,
            items = uiState.items,
            viewModel = viewModel,
            onSelectItem = {
                vaultViewerVideoPlaying = false
                viewModel.selectItem(it)
            },
            onUserActivity = { activityGeneration++ },
            onVideoPlaybackChanged = { vaultViewerVideoPlaying = it },
            onDismiss = {
                vaultViewerVideoPlaying = false
                viewModel.selectItem(null)
            },
            onDelete = {
                pendingDeleteItem = item
            },
            onSend = {
                viewModel.selectItem(null)
                pendingSendItem = item
                showSendConfirmationDialog = true
            },
            onMoveToGallery = {
                pendingMoveItem = item
            },
            onCopyToGallery = {
                pendingCopyItem = item
            },
            onEditImage = {
                viewModel.selectItem(null)
                editingImageItem = item
            },
            onSetProfilePhoto = {
                scope.launch { snackbarHostState.showSnackbar("Profile picture setup started") }
                scope.launch {
                    pendingProfilePhotoUri = withContext(Dispatchers.IO) {
                        viewModel.getVaultFile(item)?.let(Uri::fromFile)
                    }
                }
            },
            onSetWallpaper = {
                scope.launch { snackbarHostState.showSnackbar("Wallpaper setup started") }
                homeViewModel.loadChatsBypassingAccessCode()
                wallpaperTargetItem = item
            },
        )
    }

    pendingExtractZipItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingExtractZipItem = null },
            title = { Text(stringResource(R.string.extract_zip)) },
            text = { Text(stringResource(R.string.extract_zip_confirm, item.fileName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingExtractZipItem = null
                        viewModel.extractZip(item)
                    },
                ) {
                    Text(stringResource(R.string.extract_zip))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingExtractZipItem = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingProfilePhotoUri?.let { sourceUri ->
        ProfilePhotoCropDialog(
            sourceUri = sourceUri,
            onDismiss = { pendingProfilePhotoUri = null },
            onCropped = { photoUris ->
                pendingProfilePhotoUri = null
                profilePhotoUpdating = true
                settingsViewModel.uploadProfilePhoto(
                    photoUris.croppedUri,
                    photoUris.fullUri,
                    photoUris.useCroppedForSharing,
                )
            },
            onTooLarge = { pendingProfilePhotoUri = null },
        )
    }

    if (profilePhotoUpdating) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 72.dp)
                        .background(
                            Color.Black.copy(alpha = 0.78f),
                            RoundedCornerShape(24.dp),
                        )
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("Updating", color = Color.White)
                }
            }
            if (uiState.exportProgress != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Exporting ZIP ${uiState.exportProgress!!.first}/${uiState.exportProgress!!.second}",
                        modifier = Modifier.padding(horizontal = 32.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    LinearProgressIndicator(
                        progress = {
                            uiState.exportProgress!!.first.toFloat() /
                                uiState.exportProgress!!.second.coerceAtLeast(1)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                    )
                }
            }
        }
    }

    wallpaperTargetItem?.let { item ->
        WallpaperTargetPickerDialog(
            chats = homeViewModel.uiState.collectAsStateWithLifecycle().value.chats,
            onSelect = { chat ->
                wallpaperTargetItem = null
                scope.launch {
                    appPreferences.setChatWallpaperVaultId(chat.chatId, item.id)
                    appPreferences.setChatWallpaperSource(chat.chatId, AppPreferences.WALLPAPER_SOURCE_VAULT)
                    appPreferences.setChatWallpaperEnabled(true)
                    Toast.makeText(context, "Wallpaper set", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { wallpaperTargetItem = null },
        )
    }

    pendingDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text(stringResource(R.string.delete_vault_item_title)) },
            text = { Text(stringResource(R.string.delete_vault_item_message, item.fileName)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteItem = null
                    val deletedIndex = uiState.items.indexOfFirst { it.id == item.id }
                    val replacement = uiState.items
                        .getOrNull(deletedIndex + 1)
                        ?: uiState.items.getOrNull(deletedIndex - 1)
                    performDelete(item)
                    if (replacement != null) {
                        viewModel.selectItem(replacement)
                    } else {
                        viewModel.selectItem(null)
                    }
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDeleteSelectionConfirmation) {
        val count = uiState.selectedItems.size
        AlertDialog(
            onDismissRequest = { showDeleteSelectionConfirmation = false },
            title = { Text(stringResource(R.string.delete_vault_item_title)) },
            text = { Text(stringResource(R.string.delete_vault_items_message, count)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSelectionConfirmation = false
                    performDeleteSelection()
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectionConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingMoveItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingMoveItem = null },
            title = { Text(stringResource(R.string.move_vault_item_title)) },
            text = { Text(stringResource(R.string.move_vault_item_message, item.fileName)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingMoveItem = null
                    viewModel.moveToGallery(item)
                }) {
                    Text(stringResource(R.string.move_to_gallery))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMoveItem = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showMoveSelectionConfirmation) {
        val count = uiState.selectedItems.size
        AlertDialog(
            onDismissRequest = { showMoveSelectionConfirmation = false },
            title = { Text(stringResource(R.string.move_vault_item_title)) },
            text = { Text(stringResource(R.string.move_vault_items_message, count)) },
            confirmButton = {
                TextButton(onClick = {
                    showMoveSelectionConfirmation = false
                    viewModel.moveSelectedToGallery()
                }) {
                    Text(stringResource(R.string.move_to_gallery))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveSelectionConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingCopyItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingCopyItem = null },
            title = { Text(stringResource(R.string.copy_to_gallery)) },
            text = { Text(stringResource(R.string.copy_vault_item_message, item.fileName)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingCopyItem = null
                    viewModel.copyToGallery(item)
                }) {
                    Text(stringResource(R.string.copy_to_gallery))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCopyItem = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showCopySelectionConfirmation) {
        val count = uiState.selectedItems.size
        AlertDialog(
            onDismissRequest = { showCopySelectionConfirmation = false },
            title = { Text(stringResource(R.string.copy_to_gallery)) },
            text = { Text(stringResource(R.string.copy_vault_items_message, count)) },
            confirmButton = {
                TextButton(onClick = {
                    showCopySelectionConfirmation = false
                    viewModel.copySelectedToGallery()
                }) {
                    Text(stringResource(R.string.copy_to_gallery))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCopySelectionConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val editItem = editingImageItem
    val editUri = editingImageUri
    if (editItem != null && editUri != null) {
        MediaCropDialog(
            sourceUri = editUri,
            allowRotate = true,
            onDismiss = {
                editingImageItem = null
                editingImageUri = null
            },
            onCropped = { croppedUri ->
                viewModel.replaceImage(editItem, croppedUri)
                editingImageItem = null
                editingImageUri = null
            },
        )
    }

    if (accessCodeEnabled && !vaultAccessUnlocked) {
        ChatAccessLockOverlay(
            digits = vaultAccessDigits,
            onDigitsChange = {
                vaultAccessDigits = it
                vaultAccessError = null
            },
            onComplete = { code ->
                scope.launch {
                    val storedHash = appPreferences.chatAccessCodeHash.first()
                    if (ChatAccessCodeHasher.verify(code, storedHash)) {
                        VaultAccessSession.unlock()
                        vaultAccessDigits = ""
                    } else {
                        vaultAccessDigits = ""
                        vaultAccessError = "Incorrect access code"
                    }
                }
            },
            errorMessage = vaultAccessError,
            theme = vaultTheme,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showSendConfirmationDialog && pendingSendItem != null) {
        AlertDialog(
            onDismissRequest = {
                showSendConfirmationDialog = false
                pendingSendItem = null
            },
            title = { Text(stringResource(R.string.send_to_chat)) },
            text = {
                Text(stringResource(R.string.send_file_confirmation, pendingSendItem?.fileName ?: ""))
            },
            confirmButton = {
                TextButton(onClick = {
                    showSendConfirmationDialog = false
                    PendingVaultSend.set(pendingSendItem?.id ?: return@TextButton)
                    pendingSendItem = null
                    onNavigateToHomeForVaultSend?.invoke()
                }) {
                    Text(stringResource(R.string.send))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSendConfirmationDialog = false
                    pendingSendItem = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    uiState.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.save_failed)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(R.string.done))
                }
            },
        )
    }

    if (uiState.exportSuccess) {
        LaunchedEffect(uiState.exportSuccessMessage) {
            Toast.makeText(
                context,
                uiState.exportSuccessMessage ?: context.getString(R.string.moved_to_gallery),
                Toast.LENGTH_SHORT,
            ).show()
            viewModel.clearError()
        }
    }

    uiState.pendingDuplicateImports.firstOrNull()?.let { duplicate ->
        VaultDuplicateImportDialog(
            duplicate = duplicate,
            onReplace = { viewModel.resolveDuplicateImport(replace = true) },
            onSkip = { viewModel.resolveDuplicateImport(replace = false) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultItemTile(
    item: VaultItem,
    viewModel: VaultViewModel,
    selectionMode: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
) {
    var thumbnail by remember(item.id, item.contentVersion) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(item.id, item.contentVersion) { mutableStateOf(true) }

    LaunchedEffect(item.id, item.contentVersion) {
        loading = true
        val bytesResult = withContext(Dispatchers.IO) {
            viewModel.getThumbnailBytes(item)
        }
        // runCatching so a thrown Error (e.g. OOM) while decoding the thumbnail
        // can never escape the LaunchedEffect and crash the process; a failed
        // tile simply falls back to the placeholder icon.
        thumbnail = bytesResult.getOrNull()?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
        loading = false
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onToggleSelection()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (!selectionMode) onLongPress()
                },
            )
            .background(
                when {
                    selected -> EditorDefaultText.copy(alpha = 0.3f)
                    else -> EditorDefaultText.copy(alpha = 0.08f)
                }
            ),
    ) {
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            thumbnail != null -> {
                androidx.compose.foundation.Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = item.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
                else -> {
                    if (item.isImage || item.isVideo) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = EditorDefaultText.copy(alpha = 0.4f),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = EditorDefaultText.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.fileName,
                                style = MaterialTheme.typography.labelSmall,
                                color = EditorDefaultText.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatFileSize(item.fileSize),
                                style = MaterialTheme.typography.labelSmall,
                                color = EditorDefaultText.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
        }
        if (item.importedFromGallery) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(14.dp),
            ) {
                val marker = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(marker, Color(0xFF35C759))
            }
        }
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.video_badge),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (selected) {
            // Translucent overlay + checkmark so selected tiles are obvious.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.selected),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultItemViewerDialog(
    item: VaultItem,
    items: List<VaultItem>,
    viewModel: VaultViewModel,
    onSelectItem: (VaultItem) -> Unit,
    onUserActivity: () -> Unit,
    onVideoPlaybackChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    onMoveToGallery: () -> Unit,
    onCopyToGallery: () -> Unit,
    onEditImage: () -> Unit,
    onSetProfilePhoto: () -> Unit,
    onSetWallpaper: () -> Unit,
) {
    var loading by remember(item.id) { mutableStateOf(true) }
    var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var videoFile by remember(item.id) { mutableStateOf<java.io.File?>(null) }
    var filePath by remember(item.id) { mutableStateOf<String?>(null) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showImageUseMenu by remember { mutableStateOf(false) }
    var showChrome by remember(item.id) { mutableStateOf(true) }
    var slideDirection by remember { mutableIntStateOf(1) }
    var edgeDirection by remember { mutableIntStateOf(0) }
    var edgePulse by remember { mutableIntStateOf(0) }
    val boundaryNudge = remember { Animatable(0f) }
    val currentIndex = items.indexOfFirst { it.id == item.id }

    fun selectAdjacent(offset: Int) {
        val targetIndex = currentIndex + offset
        items.getOrNull(targetIndex)?.let {
            slideDirection = offset
            onSelectItem(it)
        } ?: run {
            edgeDirection = offset
            edgePulse++
        }
    }

    LaunchedEffect(edgePulse) {
        if (edgePulse == 0) return@LaunchedEffect
        boundaryNudge.snapTo(0f)
        boundaryNudge.animateTo(
            targetValue = if (edgeDirection > 0) -52f else 52f,
            animationSpec = tween(durationMillis = 120),
        )
        boundaryNudge.animateTo(
            0f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 520f),
        )
    }

    // Resolve the on-disk vault file once per item. For images we decode a
    // sampled bitmap straight from the file (avoids loading the whole file
    // into RAM); for videos we hand the file path straight to ExoPlayer — no
    // bytearray copy, no stale cache file, no OOM on large clips. This is the
    // fix for the "video preview with play button is not shown" bug. Other
    // files (e.g. PDFs) keep their on-disk path so the viewer can render them.
    LaunchedEffect(item.id) {
        loading = true
        val file = withContext(Dispatchers.IO) { viewModel.getVaultFile(item) }
        if (file == null) {
            loading = false
            return@LaunchedEffect
        }
        when {
            item.isImage -> {
                val decoded = withContext(Dispatchers.IO) {
                    runCatching {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, opts)
                        opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, 2048, 2048)
                        opts.inJustDecodeBounds = false
                        BitmapFactory.decodeFile(file.absolutePath, opts)
                    }.getOrNull()
                }
                bitmap = decoded?.let { source ->
                    val orientation = runCatching {
                        ExifInterface(file.absolutePath).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL,
                        )
                    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                    orientBitmap(source, orientation)
                }
            }
            item.isVideo -> {
                videoFile = file
            }
            else -> {
                filePath = file.absolutePath
            }
        }
        loading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
                modifier = Modifier
                    .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.changedToDown() }) {
                                onUserActivity()
                            }
                        }
                    }
                }
                .then(
                        if (!item.isImage) {
                            Modifier.pointerInput(item.id, items) {
                                var horizontalDistance = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { horizontalDistance = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        horizontalDistance += dragAmount
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        when {
                                            horizontalDistance < -80f -> selectAdjacent(1)
                                            horizontalDistance > 80f -> selectAdjacent(-1)
                                        }
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = item.id,
                    transitionSpec = {
                        if (slideDirection > 0) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "vault_item_transition",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = boundaryNudge.value },
                ) {
                    when {
                        loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                        item.isImage && bitmap != null -> {
                            ZoomableVaultImage(
                                bitmap = bitmap!!,
                                contentDescription = item.fileName,
                                onTap = { showChrome = !showChrome },
                                onSwipe = ::selectAdjacent,
                            )
                        }
                        item.isVideo && videoFile != null -> {
                            VaultVideoPlayer(
                                videoUri = videoFile!!.absolutePath,
                                onTap = { showChrome = !showChrome },
                                onPlaybackChanged = onVideoPlaybackChanged,
                            )
                        }
                        !item.isImage && !item.isVideo && filePath != null -> {
                            if (isPdfItem(item)) {
                                VaultPdfViewer(
                                    pdfFile = File(filePath!!),
                                    chromeVisible = showChrome,
                                    onTap = { showChrome = !showChrome },
                                )
                            } else {
                                VaultFileInfoView(
                                    item = item,
                                    filePath = filePath!!,
                                    onTap = { showChrome = !showChrome },
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(R.string.could_not_load_media), color = Color.White)
                            }
                        }
                    }
                }
            }

            if (showChrome) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                    )
                }

                if (!item.isImage) Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    IconButton(
                        onClick = { showOptionsMenu = true },
                        modifier = Modifier.background(
                            Color.Black.copy(alpha = 0.5f),
                            CircleShape,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            tint = Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_to_gallery)) },
                            onClick = {
                                showOptionsMenu = false
                                onMoveToGallery()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.send_to_chat)) },
                            onClick = {
                                showOptionsMenu = false
                                onSend()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_to_gallery)) },
                            onClick = {
                                showOptionsMenu = false
                                onCopyToGallery()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                showOptionsMenu = false
                                onDelete()
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = item.fileName,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = formatFileSize(item.fileSize),
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (item.isImage) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = onMoveToGallery,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DriveFileMove,
                                        contentDescription = stringResource(R.string.move_to_gallery),
                                        tint = Color.White,
                                    )
                                }
                                IconButton(
                                    onClick = onCopyToGallery,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.copy_to_gallery),
                                        tint = Color.White,
                                    )
                                }
                                IconButton(
                                    onClick = onEditImage,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.edit),
                                        tint = Color.White,
                                    )
                                }
                                IconButton(
                                    onClick = onDelete,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = Color.White,
                                    )
                                }
                                Box {
                                    IconButton(
                                        onClick = { showImageUseMenu = true },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Use",
                                            tint = Color.White,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showImageUseMenu,
                                        onDismissRequest = { showImageUseMenu = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.send_to_chat)) },
                                            onClick = { showImageUseMenu = false; onSend() },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Set as profile picture") },
                                            onClick = { showImageUseMenu = false; onSetProfilePhoto() },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Set as wallpaper") },
                                            onClick = { showImageUseMenu = false; onSetWallpaper() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun WallpaperTargetPickerDialog(
    chats: List<ChatSummary>,
    onSelect: (ChatSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set wallpaper for") },
        text = {
            if (chats.isEmpty()) {
                Text("No chats available")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    chats.forEach { chat ->
                        TextButton(
                            onClick = { onSelect(chat) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = chat.otherUsername.ifBlank { chat.otherUserId },
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

object VaultAccessSession {
    private val _unlocked = MutableStateFlow(false)
    val unlocked = _unlocked.asStateFlow()

    fun unlock() { _unlocked.value = true }
    fun lock() { _unlocked.value = false }
}

@Composable
private fun ZoomableVaultImage(
    bitmap: Bitmap,
    contentDescription: String,
    onTap: () -> Unit = {},
    onSwipe: (Int) -> Unit = {},
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = Offset(
            x = (offset.x + panChange.x).coerceIn(
                -(bitmap.width * (scale - 1f)) / 2f,
                (bitmap.width * (scale - 1f)) / 2f
            ),
            y = (offset.y + panChange.y).coerceIn(
                -(bitmap.height * (scale - 1f)) / 2f,
                (bitmap.height * (scale - 1f)) / 2f
            ),
        )
    }

    androidx.compose.foundation.Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .transformable(state = transformState)
            .pointerInput(bitmap) {
                awaitEachGesture {
                    val first = awaitPointerEvent().changes.firstOrNull()
                        ?: return@awaitEachGesture
                    var lastPosition = first.position
                    var totalDrag = Offset.Zero
                    var multiTouch = false
                    var released = false

                    while (!released) {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) multiTouch = true
                        val change = event.changes.firstOrNull { it.id == first.id }
                            ?: break
                        val delta = change.position - lastPosition
                        lastPosition = change.position
                        if (!multiTouch) totalDrag += delta
                        released = !change.pressed
                    }

                    if (!multiTouch &&
                        abs(totalDrag.x) > 80f &&
                        abs(totalDrag.x) > abs(totalDrag.y)
                    ) {
                        onSwipe(if (totalDrag.x < 0f) 1 else -1)
                    } else if (!multiTouch && totalDrag.getDistance() < 24f) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTapTime < 300L) {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                            lastTapTime = 0
                        } else {
                            onTap()
                            lastTapTime = now
                        }
                    }
                }
            },
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VaultVideoPlayer(
    videoUri: String,
    onTap: () -> Unit = {},
    onPlaybackChanged: (Boolean) -> Unit = {},
) {    val context = LocalContext.current
    var showBuffering by remember(videoUri) { mutableStateOf(true) }

    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            // Start paused so the user controls playback with the on-screen
            // play button; this matches the "video preview with play button"
            // expectation instead of silently auto-playing.
            playWhenReady = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                showBuffering = playbackState == Player.STATE_BUFFERING ||
                    playbackState == Player.STATE_IDLE
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlaybackChanged(isPlaying)
            }
        }
        player.addListener(listener)
        onPlaybackChanged(player.isPlaying)
        showBuffering = player.playbackState == Player.STATE_BUFFERING ||
            player.playbackState == Player.STATE_IDLE
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    // Keep the controller visible so the play button is shown
                    // (it auto-hides only once playback starts and on touch).
                    useController = true
                    controllerAutoShow = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { playerView ->
                playerView.setOnClickListener { onTap() }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (showBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = Color.White,
            )
        }
    }
}

private fun isPdfItem(item: VaultItem): Boolean =
    item.mimeType.equals("application/pdf", ignoreCase = true) ||
        item.fileName.substringAfterLast('.', "").equals("pdf", ignoreCase = true)

/**
 * In-app PDF viewer built on [android.graphics.pdf.PdfRenderer]. Pages are
 * rendered lazily to full-width bitmaps (off the main thread, serialized by a
 * mutex) so large documents never block the UI or load every page into RAM.
 */
@Composable
private fun VaultPdfViewer(
    pdfFile: File,
    chromeVisible: Boolean = true,
    onTap: () -> Unit = {},
) {
    val renderMutex = remember { Mutex() }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(pdfFile) {
        val instance = runCatching {
            PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY))
        }.getOrNull()
        renderer = instance
        pageCount = instance?.pageCount ?: 0
        failed = instance == null
        onDispose { instance?.close() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            failed -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.could_not_view_pdf), color = Color.White)
                }
            }
            pageCount == 0 -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onTap() })
                        }
                        .padding(
                            top = if (chromeVisible) 56.dp else 0.dp,
                            bottom = if (chromeVisible) 72.dp else 0.dp,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.pdf_page_count, pageCount),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(pageCount, key = { it }) { index ->
                        VaultPdfPage(
                            renderer = renderer,
                            pageIndex = index,
                            mutex = renderMutex,
                            density = density,
                            screenWidthDp = configuration.screenWidthDp,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultPdfPage(
    renderer: PdfRenderer?,
    pageIndex: Int,
    mutex: Mutex,
    density: Density,
    screenWidthDp: Int,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(renderer, pageIndex) {
        val r = renderer ?: return@LaunchedEffect
        val widthPx = with(density) { screenWidthDp.dp.roundToPx() }
        val rendered = withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val page = r.openPage(pageIndex)
                    try {
                        val heightPx = (page.height.toLong() * widthPx / page.width)
                            .toInt()
                            .coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    } finally {
                        page.close()
                    }
                }.getOrNull()
            }
        }
        bitmap = rendered
    }

    val bmp = bitmap
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = stringResource(R.string.pdf_page, pageIndex + 1),
            contentScale = ContentScale.FillWidth,
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Fallback for vault items that have no in-app viewer (non-image/video/PDF
 * documents). Shows file info and lets the user open the file externally.
 */
@Composable
private fun VaultFileInfoView(
    item: VaultItem,
    filePath: String,
    onTap: () -> Unit = {},
) {
    val context = LocalContext.current

    fun openExternally() {
        val file = File(filePath)
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeTypeForFileName(item.fileName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_with)))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = item.fileName,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = formatFileSize(item.fileSize),
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.no_in_app_preview),
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(
            onClick = { openExternally() },
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Text(stringResource(R.string.open_with_another_app))
        }
    }
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    var sampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
            sampleSize *= 2
        }
    }
    return sampleSize
}

private fun orientBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    val oriented = Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true,
    )
    if (oriented !== bitmap) bitmap.recycle()
    return oriented
}

object PendingVaultSend {
    private var itemIds: List<String> = emptyList()

    fun set(id: String) {
        itemIds = listOf(id)
    }

    fun setAll(ids: List<String>) {
        itemIds = ids
    }

    /** Single-item accessor preserved for single-send callers. */
    fun consume(): String? {
        val id = itemIds.firstOrNull()
        itemIds = emptyList()
        return id
    }

    /** Multi-item accessor used by the vault multiselect send flow. */
    fun consumeAll(): List<String> {
        val ids = itemIds
        itemIds = emptyList()
        return ids
    }
}
