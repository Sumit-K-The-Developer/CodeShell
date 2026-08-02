package com.codeshell.app.ui.vault

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.codeshell.app.R
import com.codeshell.app.data.model.VaultItem
import com.codeshell.app.ui.theme.EditorBackground
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.TopBarBackground
import com.codeshell.app.util.findActivity
import com.codeshell.app.util.formatFileSize
import com.codeshell.app.util.mimeTypeForFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onNavigateToHomeForVaultSend: (() -> Unit)? = null,
) {
    val viewModel: VaultViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Resolve the host activity so we can suppress the secure-exit (hide-on-home)
    // feature while an external media picker is showing. Without this, opening
    // the system photo picker triggers onUserLeaveHint() which finishes the
    // activity — and the process crashes when the picker result is delivered.
    val activity = context.findActivity() as? com.codeshell.app.MainActivity

    fun setSuppressHideOnHome(suppress: Boolean) {
        activity?.applySuppressHideOnHome(suppress)
    }

    var showSelectionMenu by remember { mutableStateOf(false) }
    var showSendConfirmationDialog by remember { mutableStateOf(false) }
    var pendingSendItem by remember { mutableStateOf<VaultItem?>(null) }

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
            viewModel.saveToVaultFromUris(uris) {
                // All items processed
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadItems()
    }

    DisposableEffect(Unit) {
        // Safety net: if the screen leaves while suppressHideOnHome was set
        // (e.g. process went away) make sure the flag is cleared so the
        // chat interface's secure exit keeps working elsewhere.
        onDispose { setSuppressHideOnHome(false) }
    }

    BackHandler(enabled = uiState.selectionMode) {
        viewModel.clearItemSelection()
    }

    fun performDelete(item: VaultItem) {
        viewModel.deleteItemWithUndo(item)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Deleted ${item.fileName}",
                actionLabel = "Undo",
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoPendingDelete()
            }
        }
    }

    Scaffold(
        containerColor = EditorBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            uiState.selectionMode && uiState.selectedItems.isNotEmpty() ->
                                "${uiState.selectedItems.size} selected"
                            uiState.selectionMode -> "Select items"
                            else -> "Vault"
                        }
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
                    if (uiState.selectionMode && uiState.selectedItems.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showSelectionMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = EditorDefaultText
                                )
                            }
                            DropdownMenu(
                                expanded = showSelectionMenu,
                                onDismissRequest = { showSelectionMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Send to Chat") },
                                    onClick = {
                                        showSelectionMenu = false
                                        val selectedIds = uiState.selectedItems.toList()
                                        PendingVaultSend.setAll(selectedIds)
                                        onNavigateToHomeForVaultSend?.invoke()
                                    },
                                    enabled = !uiState.isSending
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to Gallery") },
                                    onClick = {
                                        showSelectionMenu = false
                                        viewModel.moveSelectedToGallery()
                                    },
                                    enabled = !uiState.isExporting
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showSelectionMenu = false
                                        val count = uiState.selectedItems.size
                                        viewModel.deleteSelectedItems()
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = if (count == 1) {
                                                    "Deleted 1 item"
                                                } else {
                                                    "Deleted $count items"
                                                },
                                                actionLabel = "Undo",
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.undoPendingDelete()
                                            }
                                        }
                                    },
                                    enabled = !uiState.isExporting
                                )
                                DropdownMenuItem(
                                    text = { Text("Cancel") },
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
                    containerColor = TopBarBackground,
                    titleContentColor = EditorDefaultText,
                    navigationIconContentColor = EditorDefaultText,
                ),
            )
        },
        floatingActionButton = {
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
                    contentDescription = "Add to Vault",
                    modifier = Modifier.size(24.dp)
                )
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

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
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
                                text = "No media saved yet",
                                color = EditorDefaultText.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                text = "Tap + to add from gallery, or save media from chats",
                                color = EditorDefaultText.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            VaultItemTile(
                                item = item,
                                viewModel = viewModel,
                                selectionMode = uiState.selectionMode,
                                selected = item.id in uiState.selectedItems,
                                modifier = Modifier.aspectRatio(1f),
                                onToggleSelection = { viewModel.toggleItemSelection(item) },
                                onLongPress = {
                                    if (!uiState.selectionMode) {
                                        viewModel.toggleSelectionMode()
                                        viewModel.toggleItemSelection(item)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.selectedItem?.let { item ->
        VaultItemViewerDialog(
            item = item,
            viewModel = viewModel,
            onDismiss = { viewModel.selectItem(null) },
            onDelete = {
                viewModel.selectItem(null)
                performDelete(item)
            },
            onSend = {
                viewModel.selectItem(null)
                pendingSendItem = item
                showSendConfirmationDialog = true
            },
            onMoveToGallery = {
                viewModel.selectItem(null)
                viewModel.moveToGallery(item)
            }
        )
    }

    if (showSendConfirmationDialog && pendingSendItem != null) {
        AlertDialog(
            onDismissRequest = {
                showSendConfirmationDialog = false
                pendingSendItem = null
            },
            title = { Text("Send to Chat") },
            text = {
                Text("Send \"${pendingSendItem?.fileName}\" to a conversation?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showSendConfirmationDialog = false
                    PendingVaultSend.set(pendingSendItem?.id ?: return@TextButton)
                    pendingSendItem = null
                    onNavigateToHomeForVaultSend?.invoke()
                }) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSendConfirmationDialog = false
                    pendingSendItem = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    uiState.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            },
        )
    }

    if (uiState.exportSuccess) {
        LaunchedEffect(Unit) {
            snackbarHostState.showSnackbar("Moved to gallery")
            viewModel.clearError()
        }
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
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
) {
    var thumbnail by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(item.id) { mutableStateOf(true) }

    LaunchedEffect(item.id) {
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
                        viewModel.selectItem(item)
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (item.isImage || item.isVideo) {
                                Icons.Default.Image
                            } else {
                                Icons.Default.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = EditorDefaultText.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
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
                    contentDescription = "Video",
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
                        contentDescription = "Selected",
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
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    onMoveToGallery: () -> Unit,
) {
    var loading by remember(item.id) { mutableStateOf(true) }
    var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var videoFile by remember(item.id) { mutableStateOf<java.io.File?>(null) }
    var filePath by remember(item.id) { mutableStateOf<String?>(null) }
    var showOptionsMenu by remember { mutableStateOf(false) }

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
                bitmap = decoded
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
                .background(Color.Black),
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
                    )
                }
                item.isVideo && videoFile != null -> {
                    VaultVideoPlayer(
                        videoUri = videoFile!!.absolutePath,
                    )
                }
                !item.isImage && !item.isVideo && filePath != null -> {
                    if (isPdfItem(item)) {
                        VaultPdfViewer(pdfFile = File(filePath!!))
                    } else {
                        VaultFileInfoView(
                            item = item,
                            filePath = filePath!!,
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Could not load media", color = Color.White)
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                IconButton(onClick = { showOptionsMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                    )
                }
                DropdownMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Move to Gallery") },
                        onClick = {
                            showOptionsMenu = false
                            onMoveToGallery()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Send to Chat") },
                        onClick = {
                            showOptionsMenu = false
                            onSend()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
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
                // The viewer's actions live only in the overflow menu (top-right),
                // so the bottom bar is just the filename. This removes the duplicate
                // Send/Delete icon buttons that previously sat here.
                Text(
                    text = item.fileName,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun ZoomableVaultImage(
    bitmap: Bitmap,
    contentDescription: String,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

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
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            },
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VaultVideoPlayer(
    videoUri: String,
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
        }
        player.addListener(listener)
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
                    Text("Could not view this PDF", color = Color.White)
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
                        .padding(top = 56.dp, bottom = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
            contentDescription = "Page ${pageIndex + 1}",
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
            context.startActivity(Intent.createChooser(intent, "Open with"))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            text = "No in-app preview for this file type",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(
            onClick = { openExternally() },
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Text("Open with another app")
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
