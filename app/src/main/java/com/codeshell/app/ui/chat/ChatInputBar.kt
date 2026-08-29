package com.codeshell.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.codeshell.app.R
import com.codeshell.app.data.model.MessageType
import com.codeshell.app.util.MediaImageLoader

@Composable
private fun DraftThumb(
    draft: DraftMedia,
    theme: ChatTheme,
    caption: String?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val imageLoader = remember { MediaImageLoader.get(context) }
    val colors = theme.colors
    val corner = RoundedCornerShape(theme.inputBarCorner)
    Surface(
        modifier = Modifier
            .clip(corner)
            .clickable(onClick = onClick),
        color = colors.topBar.copy(alpha = 0.96f),
        shape = corner,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .height(64.dp)
                .padding(start = 6.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.text.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                if (draft.type == MessageType.FILE) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(draft.localUri)
                            .allowHardware(false)
                            .crossfade(true)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = draft.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(modifier = Modifier.widthIn(max = 150.dp)) {
                Text(
                    text = draft.fileName
                        ?: when (draft.type) {
                            MessageType.VIDEO -> stringResource(R.string.draft_type_video)
                            MessageType.FILE -> stringResource(R.string.draft_type_file)
                            else -> stringResource(R.string.draft_type_image)
                        },
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.draft_ready_to_send),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                )
                caption?.let { text ->
                    Text(
                        text = text,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = colors.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = colors.mutedText,
                )
            }
        }
    }
}

@Composable
private fun AttachIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    tint: Color,
    icon: ImageVector,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFile: () -> Unit,
    onPickVault: () -> Unit,
    theme: ChatTheme,
    hasDrafts: Boolean = false,
    drafts: List<DraftMedia> = emptyList(),
    onEditDraft: (DraftMedia) -> Unit = {},
    onRemoveDraft: (String) -> Unit = {},
    captionForDraft: (DraftMedia) -> String? = { null },
    replyTarget: ReplyTarget? = null,
    replyAuthorLabel: String = "",
    onClearReply: () -> Unit = {},
    isEditing: Boolean = false,
    onClearEdit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = theme.colors
    var attachExpanded by remember { mutableStateOf(false) }
    val fieldShape = RoundedCornerShape(28.dp)
    val sendButtonSize = 40.dp
    val inputTextStyle = if (theme.useMonospaceText) {
        MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    } else {
        MaterialTheme.typography.bodyMedium
    }
    val labelTextStyle = if (theme.useMonospaceText) {
        MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
    } else {
        MaterialTheme.typography.labelSmall
    }
    val bodyTextStyle = if (theme.useMonospaceText) {
        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    } else {
        MaterialTheme.typography.bodySmall
    }
    val isMultiline = inputText.contains('\n') || inputText.length > 48
    var inputFocused by remember { mutableStateOf(false) }
    val inputBorderColor by animateColorAsState(
        targetValue = if (inputFocused) colors.accent.copy(alpha = 0.7f) else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "inputFocusBorder",
    )

    fun sendAndCollapse() {
        attachExpanded = false
        onSend()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        AnimatedVisibility(
            visible = replyTarget != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            replyTarget?.let { target ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .background(colors.bubbleOther, RoundedCornerShape(theme.inputBarCorner))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.replying_to, replyAuthorLabel),
                            style = labelTextStyle,
                            color = colors.accent
                        )
                        Text(
                            text = target.preview,
                            style = bodyTextStyle,
                            color = colors.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onClearReply),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel_reply),
                            tint = colors.mutedText
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isEditing,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .background(colors.bubbleOther, RoundedCornerShape(theme.inputBarCorner))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editing_output),
                    style = labelTextStyle,
                    color = colors.accent,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onClearEdit),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = colors.mutedText
                    )
                }
            }
        }

        if (drafts.isNotEmpty() || attachExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
        AnimatedVisibility(visible = drafts.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 4.dp),
            ) {
                items(
                    drafts,
                    key = { it.localId },
                ) { draft ->
                    DraftThumb(
                        draft = draft,
                        theme = theme,
                        caption = captionForDraft(draft),
                        onClick = { if (draft.type == MessageType.IMAGE) onEditDraft(draft) },
                        onRemove = { onRemoveDraft(draft.localId) },
                    )
                }
            }
        }

        AnimatedVisibility(visible = attachExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AttachIconButton(
                    onClick = onPickImage,
                    contentDescription = stringResource(R.string.attach_gallery),
                    tint = colors.accent,
                    icon = Icons.Default.Image,
                )
                AttachIconButton(
                    onClick = onTakePhoto,
                    contentDescription = stringResource(R.string.camera),
                    tint = colors.accent,
                    icon = Icons.Default.CameraAlt,
                )
                AttachIconButton(
                    onClick = onPickFile,
                    contentDescription = stringResource(R.string.attach_file),
                    tint = colors.accent,
                    icon = Icons.Default.AttachFile,
                )
                AttachIconButton(
                    onClick = onPickVault,
                    contentDescription = stringResource(R.string.attach_vault),
                    tint = colors.accent,
                    icon = Icons.Default.Folder,
                )
            }
        }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = fieldShape,
            color = colors.topBar.copy(alpha = 0.96f),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val canSend = (inputText.isNotBlank() || hasDrafts) && !isSending
            val sendFilled = inputText.isNotBlank() || hasDrafts || isSending
            val inputRowModifier = if (isMultiline) {
                Modifier
                    .weight(1f)
                    .heightIn(min = sendButtonSize, max = 120.dp)
            } else {
                Modifier
                    .weight(1f)
                    .height(sendButtonSize)
            }

            Row(
                modifier = inputRowModifier
                    .clip(fieldShape)
                    .background(colors.text.copy(alpha = 0.06f))
                    .border(1.dp, inputBorderColor, fieldShape)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { attachExpanded = !attachExpanded },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (attachExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = stringResource(R.string.attach_media),
                        tint = colors.accent,
                    )
                }

                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { inputFocused = it.hasFocus }
                        .padding(vertical = if (isMultiline) 8.dp else 0.dp),
                    textStyle = inputTextStyle.copy(color = colors.text),
                    cursorBrush = SolidColor(Color.White),
                    maxLines = 5,
                    singleLine = !isMultiline,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (!isMultiline) ImeAction.Send else ImeAction.Default,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if ((inputText.isNotBlank() || hasDrafts) && !isSending) sendAndCollapse()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.type_output),
                                    style = inputTextStyle,
                                    color = colors.mutedText,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            Box(
                modifier = Modifier
                    .size(sendButtonSize)
                    .clip(CircleShape)
                    .background(
                        if (sendFilled) colors.accent else colors.text.copy(alpha = 0.15f),
                    )
                    .clickable(
                        enabled = canSend,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { sendAndCollapse() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(
                            if (isEditing) R.string.save_edit else R.string.send_output
                        ),
                        tint = if (sendFilled) Color.White else colors.mutedText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        }
    }
}
