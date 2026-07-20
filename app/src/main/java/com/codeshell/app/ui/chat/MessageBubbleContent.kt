package com.codeshell.app.ui.chat

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import com.codeshell.app.R
import com.codeshell.app.data.model.ChatMessage
import com.codeshell.app.data.model.MessageReply
import com.codeshell.app.data.model.MessageType
import com.codeshell.app.data.model.PendingUpload
import com.codeshell.app.data.model.UploadStage
import com.codeshell.app.util.MediaImageLoader
import com.codeshell.app.util.VideoThumbnailLoader
import com.codeshell.app.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleContent(
    message: ChatMessage,
    isMine: Boolean,
    onOpenMedia: (MessageType, String, String?, String) -> Unit,
    theme: ChatTheme,
    mediaClickEnabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onOpenLink: ((url: String, title: String) -> Unit)? = null,
) {
    val colors = theme.colors
    if (message.deleted) {
        Text(
            text = stringResource(R.string.message_removed_for_everyone),
            color = colors.text,
            fontStyle = FontStyle.Italic,
            style = messageTextStyle(theme),
            modifier = modifier
        )
        return
    }

    val imageLoader = MediaImageLoader.get(LocalContext.current)

    Column(modifier = modifier) {
        message.reply?.let { reply ->
            ReplyQuoteBlock(
                reply = reply,
                isMine = isMine,
                colors = colors,
                modifier = Modifier.padding(bottom = if (message.type == MessageType.IMAGE || message.type == MessageType.VIDEO) 4.dp else 0.dp)
            )
        }
    MessageBody(
        message = message,
        isMine = isMine,
        onOpenMedia = onOpenMedia,
        theme = theme,
        imageLoader = imageLoader,
        mediaClickEnabled = mediaClickEnabled,
        onLongPress = onLongPress,
        onOpenLink = onOpenLink,
    )
    }
}

@Composable
private fun messageTextStyle(theme: ChatTheme): TextStyle {
    val base = if (theme.useMonospaceText) {
        MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    } else {
        MaterialTheme.typography.bodyMedium
    }
    return base.copy(fontSize = base.fontSize * theme.messageFontScale)
}

@Composable
private fun MessageBody(
    message: ChatMessage,
    isMine: Boolean,
    onOpenMedia: (MessageType, String, String?, String) -> Unit,
    theme: ChatTheme,
    imageLoader: ImageLoader,
    mediaClickEnabled: Boolean,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onOpenLink: ((url: String, title: String) -> Unit)? = null,
) {
    val colors = theme.colors
    when (message.type) {
        MessageType.IMAGE -> {
            val url = message.mediaUrl
            if (url != null) {
                ChatMediaPreview(
                    model = url,
                    imageLoader = imageLoader,
                    contentDescription = stringResource(R.string.image_output),
                    borderColor = colors.mutedText.copy(alpha = 0.35f),
                    fillWidth = false,
                    showPlayOverlay = false,
                    useLocalVideoThumbnail = false,
                    previewHeight = theme.mediaPreviewHeight,
                    previewCorner = theme.mediaPreviewCorner,
                    borderWidth = theme.mediaBorderWidth,
                    compact = theme.compactMediaAttachments,
                    onClick = if (mediaClickEnabled) {
                        { onOpenMedia(MessageType.IMAGE, url, message.fileName ?: "image.jpg", message.id) }
                    } else {
                        null
                    },
                    onLongClick = onLongPress,
                )
            } else {
                MediaPlaceholderRow(
                    icon = { Icon(Icons.Default.Image, contentDescription = null, tint = colors.accent) },
                    label = stringResource(R.string.image_output),
                    textColor = colors.text,
                )
            }
        }

        MessageType.VIDEO -> {
            val url = message.mediaUrl
            if (url != null) {
                ChatMediaPreview(
                    model = url,
                    imageLoader = imageLoader,
                    contentDescription = stringResource(R.string.video_output),
                    borderColor = colors.mutedText.copy(alpha = 0.35f),
                    fillWidth = false,
                    showPlayOverlay = true,
                    useLocalVideoThumbnail = false,
                    previewHeight = theme.mediaPreviewHeight,
                    previewCorner = theme.mediaPreviewCorner,
                    borderWidth = theme.mediaBorderWidth,
                    compact = theme.compactMediaAttachments,
                    onClick = if (mediaClickEnabled) {
                        { onOpenMedia(MessageType.VIDEO, url, message.fileName ?: "video.mp4", message.id) }
                    } else {
                        null
                    },
                    onLongClick = onLongPress,
                )
            } else {
                MediaPlaceholderRow(
                    icon = {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    label = stringResource(R.string.video_output),
                    textColor = colors.text,
                )
            }
        }

        MessageType.AUDIO -> {
            val url = message.mediaUrl
            MediaPlaceholderRow(
                icon = {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.play_audio),
                        tint = colors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                },
                label = stringResource(R.string.voice_output),
                textColor = colors.text,
                modifier = mediaInteractionModifier(
                    enabled = url != null,
                    onClick = if (mediaClickEnabled && url != null) {
                        {
                            url.let {
                                onOpenMedia(MessageType.AUDIO, it, message.fileName ?: "voice.m4a", message.id)
                            }
                        }
                    } else {
                        null
                    },
                    onLongClick = onLongPress,
                )
            )
        }

        MessageType.FILE -> {
            val url = message.mediaUrl
            Column(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .then(
                        mediaInteractionModifier(
                            enabled = url != null,
                            onClick = if (mediaClickEnabled && url != null) {
                                {
                                    url.let {
                                        onOpenMedia(
                                            MessageType.FILE,
                                            it,
                                            message.fileName ?: "attachment",
                                            message.id
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            onLongClick = onLongPress,
                        )
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = colors.accent)
                    Column(modifier = Modifier.padding(start = 6.dp)) {
                        Text(
                            text = message.fileName ?: stringResource(R.string.file_attachment),
                            color = colors.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        message.fileSize?.let { size ->
                            Text(
                                text = formatFileSize(size),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.mutedText
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.tap_to_view),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        MessageType.TEXT -> {
            LinkableMessageText(
                text = message.displayText,
                color = colors.text,
                linkColor = colors.accent,
                style = messageTextStyle(theme),
                onOpenLink = onOpenLink,
            )
        }

        MessageType.SESSION -> Unit
        MessageType.SYSTEM -> Unit
    }
}

@Composable
private fun LinkableMessageText(
    text: String,
    color: Color,
    linkColor: Color,
    style: TextStyle,
    onOpenLink: ((url: String, title: String) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val matches = remember(text) { URL_PATTERN.findAll(text).toList() }
    if (matches.isEmpty()) {
        Text(
            text = text,
            color = color,
            style = style,
        )
        return
    }

    val annotated = remember(text, matches, color, linkColor) {
        buildAnnotatedString {
            var cursor = 0
            matches.forEach { match ->
                append(text.substring(cursor, match.range.first))
                val rawUrl = match.value
                val url = rawUrl.withScheme()
                pushStringAnnotation(tag = URL_TAG, annotation = url)
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    )
                ) {
                    append(rawUrl)
                }
                pop()
                cursor = match.range.last + 1
            }
            append(text.substring(cursor))
        }
    }

    ClickableText(
        text = annotated,
        style = style.copy(color = color),
        onClick = { offset ->
            annotated
                .getStringAnnotations(URL_TAG, offset, offset)
                .firstOrNull()
                ?.let { annotation ->
                    val url = annotation.item
                    if (onOpenLink != null) {
                        onOpenLink(url, url)
                    } else {
                        uriHandler.openUri(url)
                    }
                }
        },
    )
}

private fun String.withScheme(): String =
    if (startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)) {
        this
    } else {
        "https://$this"
    }

private fun String.toEmbeddedVideoUrl(): String? {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return null
    val host = uri.host.orEmpty().lowercase()
    return when {
        host == "youtu.be" -> {
            uri.lastPathSegment
                ?.takeIf { it.isNotBlank() }
                ?.let { "https://www.youtube.com/embed/$it?autoplay=1&playsinline=1" }
        }
        host.endsWith("youtube.com") -> {
            val videoId = when {
                uri.path.orEmpty().startsWith("/shorts/") -> uri.pathSegments.getOrNull(1)
                uri.path.orEmpty().startsWith("/embed/") -> uri.pathSegments.getOrNull(1)
                else -> uri.getQueryParameter("v")
            }
            videoId
                ?.takeIf { it.isNotBlank() }
                ?.let { "https://www.youtube.com/embed/$it?autoplay=1&playsinline=1" }
        }
        host.endsWith("instagram.com") &&
            uri.pathSegments.any { it.equals("reel", ignoreCase = true) || it.equals("p", ignoreCase = true) } -> this
        else -> null
    }
}

@Composable
private fun EmbeddedLinkPlayerDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webViewClient = WebViewClient()
                        loadUrl(url)
                    }
                },
                update = { webView ->
                    if (webView.url != url) {
                        webView.loadUrl(url)
                    }
                },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ReplyQuoteBlock(
    reply: MessageReply,
    isMine: Boolean,
    colors: ChatThemeColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widthIn(max = 220.dp)
            .padding(horizontal = if (isMine) 0.dp else 0.dp)
            .background(colors.accent.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .background(colors.accent, RoundedCornerShape(2.dp))
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = reply.preview,
                style = MaterialTheme.typography.bodySmall,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun mediaInteractionModifier(
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
): Modifier = when {
    onClick != null && onLongClick != null ->
        Modifier.combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    onClick != null ->
        Modifier.clickable(enabled = enabled, onClick = onClick)
    onLongClick != null ->
        Modifier.combinedClickable(
            enabled = enabled,
            onClick = {},
            onLongClick = onLongClick,
        )
    else -> Modifier
}

@Composable
private fun ChatMediaPreview(
    model: Any?,
    imageLoader: ImageLoader,
    contentDescription: String,
    borderColor: Color,
    fillWidth: Boolean = false,
    showPlayOverlay: Boolean = false,
    useLocalVideoThumbnail: Boolean = false,
    previewHeight: androidx.compose.ui.unit.Dp = 120.dp,
    previewCorner: androidx.compose.ui.unit.Dp = 6.dp,
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp,
    compact: Boolean = false,
    maxPreviewWidth: androidx.compose.ui.unit.Dp = 220.dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(previewCorner)
    val minWidth = if (compact) 120.dp else 160.dp
    val maxWidth = if (compact) 180.dp else maxPreviewWidth
    var adaptiveAspectRatio by remember(model) { mutableFloatStateOf(if (compact) 1.15f else 1.33f) }
    val boundedAspectRatio = adaptiveAspectRatio.coerceIn(0.62f, 1.78f)
    val minPreviewHeight = if (compact) 96.dp else 112.dp
    val maxPreviewHeight = if (compact) 220.dp else 280.dp
    val sizeModifier = if (fillWidth) {
        Modifier
            .fillMaxWidth()
            .heightIn(min = minPreviewHeight, max = maxPreviewHeight)
            .aspectRatio(boundedAspectRatio)
    } else {
        Modifier
            .widthIn(min = minWidth, max = maxWidth)
            .defaultMinSize(minWidth = minWidth, minHeight = previewHeight)
            .heightIn(min = minPreviewHeight, max = maxPreviewHeight)
            .aspectRatio(boundedAspectRatio)
    }
    Box(
        modifier = modifier
            .then(sizeModifier)
            .clip(shape)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(width = borderWidth, color = borderColor, shape = shape)
                } else {
                    Modifier
                }
            )
            .then(
                mediaInteractionModifier(
                    enabled = true,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            )
    ) {
        when {
            model is Uri && useLocalVideoThumbnail -> LocalVideoThumbnailImage(
                uri = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                onAspectRatio = { adaptiveAspectRatio = it },
            )
            else -> {
                val context = LocalContext.current
                val request = remember(model) {
                    ImageRequest.Builder(context)
                        .data(model)
                        .size(Size(MediaImageLoader.CHAT_THUMBNAIL_MAX_PX, MediaImageLoader.CHAT_THUMBNAIL_MAX_PX))
                        .crossfade(true)
                        .build()
                }
                SubcomposeAsyncImage(
                    model = request,
                    imageLoader = imageLoader,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    is AsyncImagePainter.State.Error -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                    }
                    else -> {
                        val intrinsicSize = painter.intrinsicSize
                        LaunchedEffect(intrinsicSize) {
                            if (intrinsicSize.width.isFinite() &&
                                intrinsicSize.height.isFinite() &&
                                intrinsicSize.width > 0f &&
                                intrinsicSize.height > 0f
                            ) {
                                adaptiveAspectRatio = intrinsicSize.width / intrinsicSize.height
                            }
                        }
                        SubcomposeAsyncImageContent()
                    }
                }
            }
            }
        }
        if (showPlayOverlay) {
            PlayOverlay()
        }
    }
}

@Composable
private fun LocalVideoThumbnailImage(
    uri: Uri,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onAspectRatio: (Float) -> Unit = {},
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            VideoThumbnailLoader.loadFrame(context, uri)
        }
    }
    when (val frame = bitmap) {
        null -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        else -> {
            LaunchedEffect(frame.width, frame.height) {
                if (frame.width > 0 && frame.height > 0) {
                    onAspectRatio(frame.width.toFloat() / frame.height.toFloat())
                }
            }
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun PlayOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.video_output),
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

private const val URL_TAG = "url"

private val URL_PATTERN = Regex(
    """(?i)\b((https?://)?([a-z0-9-]+\.)+[a-z]{2,63}(/[^\s]*)?)"""
)

@Composable
private fun MediaPlaceholderRow(
    icon: @Composable () -> Unit,
    label: String,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(
            text = label,
            color = textColor,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
fun PendingUploadBubble(
    upload: PendingUpload,
    isMine: Boolean,
    theme: ChatTheme,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = theme.colors
    val imageLoader = MediaImageLoader.get(LocalContext.current)
    val bubbleColor = if (isMine) colors.bubbleMine else colors.bubbleOther
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier.widthIn(max = 220.dp)
    ) {
        when (upload.type) {
            MessageType.IMAGE -> {
                ChatMediaPreview(
                    model = upload.localUri,
                    imageLoader = imageLoader,
                    contentDescription = stringResource(R.string.image_output),
                    borderColor = colors.mutedText.copy(alpha = 0.35f),
                    showPlayOverlay = false,
                    useLocalVideoThumbnail = false,
                    previewHeight = theme.mediaPreviewHeight,
                    previewCorner = theme.mediaPreviewCorner,
                    borderWidth = theme.mediaBorderWidth,
                    compact = theme.compactMediaAttachments,
                )
            }
            MessageType.VIDEO -> {
                ChatMediaPreview(
                    model = upload.localUri,
                    imageLoader = imageLoader,
                    contentDescription = stringResource(R.string.video_output),
                    borderColor = colors.mutedText.copy(alpha = 0.35f),
                    showPlayOverlay = true,
                    useLocalVideoThumbnail = true,
                    previewHeight = theme.mediaPreviewHeight,
                    previewCorner = theme.mediaPreviewCorner,
                    borderWidth = theme.mediaBorderWidth,
                    compact = theme.compactMediaAttachments,
                )
            }
            else -> Column(
                modifier = Modifier
                    .background(bubbleColor, shape)
                    .padding(6.dp)
            ) {
                when (upload.type) {
                    MessageType.AUDIO -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = colors.accent)
                        Text(
                            text = stringResource(R.string.voice_output),
                            color = colors.text,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    MessageType.FILE -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = colors.accent)
                        Text(
                            text = upload.fileName ?: stringResource(R.string.file_attachment),
                            color = colors.text,
                            modifier = Modifier.padding(start = 6.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    MessageType.TEXT,
                    MessageType.IMAGE,
                    MessageType.VIDEO,
                    MessageType.SESSION,
                    MessageType.SYSTEM -> Unit
                }
            }
        }
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cancel_upload),
                tint = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(4.dp)
                    .size(16.dp)
            )
        }
        if (upload.stage == UploadStage.PREPARING || upload.progress < 1f) {
            if (upload.stage == UploadStage.PREPARING) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(20.dp)
                )
            } else {
                CircularProgressIndicator(
                    progress = { upload.progress.coerceIn(0f, 1f) },
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(16.dp)
                )
            }
        }
    }
}
