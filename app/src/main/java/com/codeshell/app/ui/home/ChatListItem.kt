package com.codeshell.app.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.codeshell.app.R
import com.codeshell.app.data.model.CallType
import com.codeshell.app.data.model.ChatSummary
import com.codeshell.app.ui.chat.ChatThemeColors
import com.codeshell.app.ui.util.clickableWithHorizontalSwipe
import com.codeshell.app.util.UsernameFormatter
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ChatListItem(
    chat: ChatSummary,
    colors: ChatThemeColors,
    selectionMode: Boolean,
    selected: Boolean,
    isOnline: Boolean = false,
    incomingCallType: CallType? = null,
    profilePicturesEnabled: Boolean = false,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
    onDisguiseTap: () -> Unit,
    onOpenSession: () -> Unit,
    onViewProfilePhoto: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val digits = UsernameFormatter.parseRawInput(chat.otherUsername)
    val hashId = if (digits.isEmpty()) "#…" else "#$digits"
    val unreadPrefix = if (!selectionMode) UsernameFormatter.unreadPrefix(chat.unreadCount) else null
    val idLine = buildAnnotatedString {
        if (unreadPrefix != null) {
            withStyle(SpanStyle(color = colors.accent)) {
                append(unreadPrefix)
            }
        }
        withStyle(SpanStyle(color = colors.text)) {
            append(hashId)
        }
    }
    val tileShape = RoundedCornerShape(10.dp)
    val showProfilePicture = profilePicturesEnabled && !chat.otherAvatarUrl.isNullOrBlank()
    val context = LocalContext.current
    val avatarRequest = remember(chat.otherAvatarUrl) {
        chat.otherAvatarUrl?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey("conversation-avatar:$url")
                .diskCacheKey("conversation-avatar:$url")
                .crossfade(false)
                .build()
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(tileShape)
            .background(
                when {
                    selected -> colors.accent.copy(alpha = 0.14f)
                    else -> colors.topBar.copy(alpha = 0.55f)
                },
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.accent else colors.mutedText.copy(alpha = 0.22f),
                shape = tileShape,
            )
            .then(
                if (selectionMode) {
                    Modifier.clickable(onClick = onToggleSelection)
                } else {
                    Modifier.clickableWithHorizontalSwipe(
                        onClick = onDisguiseTap,
                        onSwipe = onOpenSession,
                        onLongClick = onLongPress,
                    )
                },
            ),
    ) {
        if (showProfilePicture) {
            AsyncImage(
                model = avatarRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(colors.topBar.copy(alpha = 0.9f))
                    .then(
                        if (!selectionMode) {
                            Modifier.pointerInput(onViewProfilePhoto) {
                                detectTapGestures(
                                    onDoubleTap = { onViewProfilePhoto() },
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = idLine,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "[>_]",
                    color = colors.mutedText,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "CODESHELL",
                    color = colors.mutedText.copy(alpha = 0.85f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = idLine,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (isOnline && !selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(8.dp)
                    .background(Color.White, CircleShape),
            )
        }

        if (incomingCallType != null && !selectionMode) {
            IncomingCallPulseIcon(
                callType = incomingCallType,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun IncomingCallPulseIcon(
    callType: CallType,
    modifier: Modifier = Modifier,
) {
    val pulseColor = Color(0xFF43A047)
    val transition = rememberInfiniteTransition(label = "incoming_call_pulse")
    val pulse1Scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse1_scale",
    )
    val pulse1Alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse1_alpha",
    )
    val pulse2Scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, delayMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse2_scale",
    )
    val pulse2Alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, delayMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse2_alpha",
    )

    Box(
        modifier = modifier.size(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = pulse1Scale
                    scaleY = pulse1Scale
                    alpha = pulse1Alpha
                }
                .background(pulseColor, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = pulse2Scale
                    scaleY = pulse2Scale
                    alpha = pulse2Alpha
                }
                .background(pulseColor, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(pulseColor.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (callType) {
                    CallType.VIDEO -> Icons.Default.Videocam
                    CallType.VOICE -> Icons.Default.Phone
                },
                contentDescription = when (callType) {
                    CallType.VIDEO -> stringResource(R.string.video_call_incoming_indicator)
                    CallType.VOICE -> stringResource(R.string.call_incoming_indicator)
                },
                tint = pulseColor,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
