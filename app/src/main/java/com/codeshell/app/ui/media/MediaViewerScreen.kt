package com.codeshell.app.ui.media

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.codeshell.app.R
import com.codeshell.app.data.model.MessageType
import com.codeshell.app.data.repository.AuthRepository
import com.codeshell.app.data.repository.MessageRepository
import com.codeshell.app.ui.theme.EditorBackground
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.TopBarBackground
import com.codeshell.app.ui.vault.VaultSaveState
import com.codeshell.app.ui.vault.VaultSaveTracker
import com.codeshell.app.ui.vault.VaultViewModel
import com.codeshell.app.util.MediaImageLoader
import com.codeshell.app.util.MediaPreloadCache
import com.codeshell.app.util.formatFileSize
import com.codeshell.app.util.mimeTypeForFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    mediaType: String,
    mediaUrl: String,
    fileName: String?,
    chatId: String? = null,
    messageId: String? = null,
    onNavigateBack: () -> Unit
) {
    val type = MessageType.from(mediaType)
    val context = LocalContext.current
    val imageLoader = remember { MediaImageLoader.get(context) }
    val app = context.applicationContext as android.app.Application
    val vaultViewModel: VaultViewModel = viewModel(
        factory = AndroidViewModelFactory(app)
    )

    // Observe the persistent save tracker (updated by the background vault save)
    val saveState by VaultSaveTracker.state.collectAsStateWithLifecycle()

    LaunchedEffect(chatId, messageId) {
        val resolvedChatId = chatId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val resolvedMessageId = messageId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val userId = AuthRepository().currentUser?.uid ?: return@LaunchedEffect
        MessageRepository().markSeenBy(resolvedChatId, resolvedMessageId, userId)
    }

    fun saveMedia() {
        if (mediaUrl.isBlank()) {
            return
        }
        val safeName = fileName ?: when (type) {
            MessageType.IMAGE -> "image.jpg"
            MessageType.AUDIO -> "voice.m4a"
            MessageType.VIDEO -> "video.mp4"
            else -> "attachment.bin"
        }
        val mime = when (type) {
            MessageType.IMAGE -> "image/jpeg"
            MessageType.AUDIO -> "audio/mp4"
            MessageType.VIDEO -> "video/mp4"
            // Derive from the file extension so documents (e.g. PDFs) keep their
            // real MIME type in the vault, which enables the in-app PDF viewer.
            else -> mimeTypeForFileName(safeName)
        }
        vaultViewModel.saveToVault(mediaUrl, safeName, mime, chatId, messageId)
    }

    // Clear the tracker when the screen is first shown so stale state doesn't persist
    LaunchedEffect(Unit) {
        VaultSaveTracker.reset()
    }

    Scaffold(
        containerColor = EditorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (type) {
                            MessageType.IMAGE -> stringResource(R.string.image_output)
                            MessageType.VIDEO -> stringResource(R.string.video_output)
                            MessageType.AUDIO -> stringResource(R.string.voice_output)
                            else -> stringResource(R.string.file_attachment)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (mediaUrl.isNotBlank()) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            when (val state = saveState) {
                                is VaultSaveState.Saving -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                is VaultSaveState.Success -> {
                                    // Show a brief success indicator
                                    LaunchedEffect(state) {
                                        delay(1500)
                                        VaultSaveTracker.reset()
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = stringResource(R.string.saved_to_vault),
                                        tint = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                    )
                                }
                                is VaultSaveState.Error -> {
                                    LaunchedEffect(state) {
                                        delay(2000)
                                        VaultSaveTracker.reset()
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = stringResource(R.string.save_failed),
                                        tint = androidx.compose.ui.graphics.Color(0xFFF44336)
                                    )
                                }
                                else -> {
                                    IconButton(onClick = { saveMedia() }) {
                                        Icon(
                                            imageVector = Icons.Default.Save,
                                            contentDescription = stringResource(R.string.save_to_vault)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TopBarBackground,
                    titleContentColor = EditorDefaultText,
                    navigationIconContentColor = EditorDefaultText
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (mediaUrl.isBlank()) {
                Text(text = stringResource(R.string.media_unavailable), color = EditorDefaultText)
            } else {
                when (type) {
                    MessageType.IMAGE -> ZoomableImage(url = mediaUrl, imageLoader = imageLoader)
                    MessageType.VIDEO -> VideoPlayer(url = mediaUrl)
                    MessageType.AUDIO -> AudioPlayer(url = mediaUrl)
                    MessageType.FILE -> RemoteFileViewer(url = mediaUrl, fileName = fileName)
                    MessageType.TEXT -> Text(text = mediaUrl, color = EditorDefaultText)
                    MessageType.SESSION,
                    MessageType.SYSTEM -> Unit
                }
            }

            // Determinate progress bar at the bottom during saving
            if (saveState is VaultSaveState.Saving) {
                val progress = (saveState as VaultSaveState.Saving).progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = EditorDefaultText,
                        trackColor = EditorDefaultText.copy(alpha = 0.3f)
                    )
                }
            }

            // Status message
            val statusMessage = when (val state = saveState) {
                is VaultSaveState.Saving -> stringResource(R.string.saving)
                is VaultSaveState.Success -> stringResource(R.string.saved_to_vault)
                is VaultSaveState.Error -> stringResource(R.string.save_failed_with_message, state.message)
                else -> null
            }
            statusMessage?.let { message ->
                Text(
                    text = message,
                    color = EditorDefaultText,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(url: String, imageLoader: coil.ImageLoader) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += panChange
    }

    SubcomposeAsyncImage(
        model = url,
        imageLoader = imageLoader,
        contentDescription = stringResource(R.string.image_output),
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
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
            else -> SubcomposeAsyncImageContent()
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(url: String) {
    val context = LocalContext.current
    var isLoading by remember(url) { mutableStateOf(true) }
    val player = remember(url) {
        MediaPreloadCache.buildPlayer(context, url).apply {
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = playbackState == Player.STATE_BUFFERING ||
                    playbackState == Player.STATE_IDLE
            }
        }
        player.addListener(listener)
        isLoading = player.playbackState != Player.STATE_READY
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
            )
        }
    }
}

@Composable
private fun AudioPlayer(url: String) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(1L)
            delay(250)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(TopBarBackground, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = {
            if (player.isPlaying) player.pause() else player.play()
        }) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.play_audio),
                tint = EditorDefaultText
            )
        }
        Slider(
            value = positionMs.toFloat(),
            onValueChange = { value ->
                positionMs = value.toLong()
                player.seekTo(positionMs)
            },
            valueRange = 0f..durationMs.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "${formatMs(positionMs)} / ${formatMs(durationMs)}",
            color = EditorDefaultText.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun RemoteFileViewer(
    url: String,
    fileName: String?,
) {
    val context = LocalContext.current
    val isPdf = fileName?.substringAfterLast('.', "")
        .equals("pdf", ignoreCase = true) ||
        mimeTypeForFileName(fileName).equals("application/pdf", ignoreCase = true)
    var localFile by remember { mutableStateOf<File?>(null) }
    var downloading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(url, fileName) {
        downloading = true
        failed = false
        val file = withContext(Dispatchers.IO) {
            downloadToCache(context, url, fileName)
        }
        localFile = file
        failed = file == null
        downloading = false
    }

    when {
        downloading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        localFile != null && isPdf -> {
            PdfViewer(file = localFile!!)
        }
        localFile != null -> {
            RemoteFileInfoView(fileName = fileName, file = localFile!!)
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.media_unavailable), color = EditorDefaultText)
            }
        }
    }
}

/**
 * Downloads [url] into the app cache so local renderers (PdfRenderer) and the
 * external "open with" intent can work with a real on-disk file. Returns null
 * on any failure so the caller can surface an error instead of crashing.
 */
private fun downloadToCache(context: Context, url: String, fileName: String?): File? {
    return try {
        val safeName = (fileName ?: "download")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .takeLast(120)
        val file = File(context.cacheDir, "preview_${System.currentTimeMillis()}_$safeName")
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.connect()
            if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            if (file.length() == 0L) {
                file.delete()
                return null
            }
            file
        } finally {
            connection.disconnect()
        }
    } catch (e: Throwable) {
        null
    }
}

/**
 * In-app PDF viewer built on [android.graphics.pdf.PdfRenderer]. Pages render
 * lazily to full-width bitmaps off the main thread (serialized via a mutex),
 * so documents open without loading every page into memory at once.
 */
@Composable
private fun PdfViewer(file: File) {
    val renderMutex = remember { Mutex() }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(file) {
        val instance = runCatching {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
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
                    modifier = Modifier.fillMaxSize(),
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
                        PdfPageImage(
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
private fun PdfPageImage(
    renderer: PdfRenderer?,
    pageIndex: Int,
    mutex: Mutex,
    density: Density,
    screenWidthDp: Int,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

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
                        val bmp = android.graphics.Bitmap.createBitmap(
                            widthPx,
                            heightPx,
                            android.graphics.Bitmap.Config.ARGB_8888,
                        )
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
                .height(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Info view for non-PDF files: shows name/size and offers an external opener. */
@Composable
private fun RemoteFileInfoView(
    fileName: String?,
    file: File,
) {
    val context = LocalContext.current

    fun openExternally() {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeTypeForFileName(fileName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_with)))
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
            tint = EditorDefaultText.copy(alpha = 0.7f),
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = fileName ?: stringResource(R.string.file_attachment),
            color = EditorDefaultText,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = formatFileSize(file.length()),
            color = EditorDefaultText.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = stringResource(R.string.save_to_device_hint),
            color = EditorDefaultText.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 12.dp)
        )
        Button(
            onClick = { openExternally() },
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Text(stringResource(R.string.open_with_another_app))
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
