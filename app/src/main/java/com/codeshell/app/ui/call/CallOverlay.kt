package com.codeshell.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.codeshell.app.R
import com.codeshell.app.call.CallAudioRouteType
import com.codeshell.app.data.model.CallType
import com.codeshell.app.data.model.formatSessionDuration
import com.codeshell.app.ui.chat.ChatThemeColors
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallOverlay(
    uiState: CallUiState,
    colors: ChatThemeColors,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEndCall: () -> Unit,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onToggleMute: () -> Unit,
    onRefreshAudioRoutes: () -> Unit,
    onSelectAudioRoute: (CallAudioRouteType) -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onRequestScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit,
    onBindLocalRenderer: (SurfaceViewRenderer) -> Unit,
    onBindRemoteRenderer: (SurfaceViewRenderer) -> Unit,
    onDismissStatus: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when (uiState.phase) {
        CallUiPhase.Hidden -> Unit
        CallUiPhase.StatusMessage -> {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = colors.topBar,
                shadowElevation = 6.dp,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = uiState.statusMessage.orEmpty(),
                        color = colors.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissStatus) {
                        Text(stringResource(R.string.dismiss), color = colors.accent)
                    }
                    onRetry?.let {
                        TextButton(onClick = it) {
                            Text("Try again", color = colors.accent)
                        }
                    }
                }
            }
        }
        else -> {
            if (uiState.isMinimized) {
                MiniCallTile(
                    uiState = uiState,
                    colors = colors,
                    onExpand = onExpand,
                    onEndCall = onEndCall,
                    onBindRemoteRenderer = onBindRemoteRenderer,
                    modifier = modifier,
                )
                return
            }
            val isVideo = uiState.callType == CallType.VIDEO
            val showVideoSurfaces = isVideo && uiState.phase in VIDEO_SURFACE_PHASES
            val localPreviewOnly = isVideo && uiState.phase != CallUiPhase.InCall

            Surface(
                modifier = modifier.fillMaxSize(),
                color = if (showVideoSurfaces) Color.Black else colors.background.copy(alpha = 0.96f),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (showVideoSurfaces) {
                        VideoCallSurfaces(
                            localPreviewOnly = localPreviewOnly,
                            onBindLocalRenderer = onBindLocalRenderer,
                            onBindRemoteRenderer = onBindRemoteRenderer,
                        )
                    }

                    if (showVideoSurfaces) {
                        if (uiState.phase == CallUiPhase.InCall) {
                            FilledIconButton(
                                onClick = if (uiState.isScreenSharing) onStopScreenShare else onRequestScreenShare,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .statusBarsPadding()
                                    .padding(16.dp)
                                    .size(44.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (uiState.isScreenSharing) {
                                        colors.accent.copy(alpha = 0.45f)
                                    } else {
                                        Color.Black.copy(alpha = 0.45f)
                                    },
                                ),
                            ) {
                                Icon(
                                    imageVector = if (uiState.isScreenSharing) {
                                        Icons.Default.StopScreenShare
                                    } else {
                                        Icons.Default.ScreenShare
                                    },
                                    contentDescription = stringResource(R.string.call_toggle_screen_share),
                                    tint = Color.White,
                                )
                            }
                        }
                        FilledIconButton(
                            onClick = onMinimize,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp)
                                .size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.45f),
                            ),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.call_minimize),
                                tint = Color.White,
                            )
                        }
                        CallStatusDisplay(
                            uiState = uiState,
                            colors = colors,
                            onDarkBackground = true,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding(),
                        )
                    }
                    if (!showVideoSurfaces) {
                        FilledIconButton(
                            onClick = onMinimize,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp)
                                .size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.bubbleOther),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.call_minimize),
                                tint = colors.text,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (showVideoSurfaces) {
                            Arrangement.Bottom
                        } else {
                            Arrangement.Center
                        },
                    ) {
                        if (!showVideoSurfaces || uiState.phase != CallUiPhase.InCall) {
                            Text(
                                text = uiState.otherDisplayName.ifBlank {
                                    stringResource(R.string.terminal_session_title)
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (showVideoSurfaces) Color.White else colors.text,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (!showVideoSurfaces) {
                                CallStatusDisplay(
                                    uiState = uiState,
                                    colors = colors,
                                    onDarkBackground = false,
                                )
                                Spacer(modifier = Modifier.height(48.dp))
                            } else {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        CallControlRow(
                            uiState = uiState,
                            colors = colors,
                            onAccept = onAccept,
                            onDecline = onDecline,
                            onEndCall = onEndCall,
                            onToggleMute = onToggleMute,
                            onRefreshAudioRoutes = onRefreshAudioRoutes,
                            onSelectAudioRoute = onSelectAudioRoute,
                            onToggleVideo = onToggleVideo,
                            onSwitchCamera = onSwitchCamera,
                            onRequestScreenShare = onRequestScreenShare,
                            onStopScreenShare = onStopScreenShare,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniCallTile(
    uiState: CallUiState,
    colors: ChatThemeColors,
    onExpand: () -> Unit,
    onEndCall: () -> Unit,
    onBindRemoteRenderer: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVideo = uiState.callType == CallType.VIDEO && uiState.phase == CallUiPhase.InCall
    if (!isVideo) {
        MinimizedAudioBanner(
            uiState = uiState,
            onExpand = onExpand,
            onEndCall = onEndCall,
            modifier = modifier,
        )
        return
    }
    Surface(
        modifier = modifier
            .width(140.dp)
            .height(220.dp)
            .padding(8.dp)
            .clickable(onClick = onExpand),
        color = Color.Black,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isVideo) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context -> SurfaceViewRenderer(context).also(onBindRemoteRenderer) },
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = uiState.otherDisplayName.ifBlank { stringResource(R.string.terminal_session_title) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    text = if (uiState.phase == CallUiPhase.InCall) {
                        formatSessionDuration(uiState.callDurationSec)
                    } else {
                        phaseStatusLabel(uiState)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                )
            }
            FilledIconButton(
                onClick = onEndCall,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.call_end_action))
            }
        }
    }
}

@Composable
private fun MinimizedAudioBanner(
    uiState: CallUiState,
    onExpand: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(46.dp)
            .clickable(onClick = onExpand),
        color = Color(0xFF1FA855),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.otherDisplayName.ifBlank { stringResource(R.string.terminal_session_title) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    text = if (uiState.phase == CallUiPhase.InCall) {
                        formatSessionDuration(uiState.callDurationSec)
                    } else {
                        phaseStatusLabel(uiState)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                )
            }
            FilledIconButton(
                onClick = onEndCall,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.call_end_action))
            }
        }
    }
}

@Composable
private fun CallStatusDisplay(
    uiState: CallUiState,
    colors: ChatThemeColors,
    onDarkBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    val titleColor = if (onDarkBackground) Color.White else colors.text
    val mutedColor = if (onDarkBackground) Color.White.copy(alpha = 0.85f) else colors.mutedText
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = phaseStatusLabel(uiState),
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            textAlign = TextAlign.Center,
        )
        if (uiState.phase == CallUiPhase.InCall) {
            Text(
                text = formatSessionDuration(uiState.callDurationSec),
                style = MaterialTheme.typography.bodyLarge,
                color = mutedColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun VideoCallSurfaces(
    localPreviewOnly: Boolean,
    onBindLocalRenderer: (SurfaceViewRenderer) -> Unit,
    onBindRemoteRenderer: (SurfaceViewRenderer) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (localPreviewOnly) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    SurfaceViewRenderer(context).also(onBindLocalRenderer)
                },
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    SurfaceViewRenderer(context).also(onBindRemoteRenderer)
                },
            )
            AndroidView(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(width = 108.dp, height = 152.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.35f)),
                factory = { context ->
                    SurfaceViewRenderer(context).also(onBindLocalRenderer)
                },
            )
        }
    }
}

@Composable
private fun CallControlRow(
    uiState: CallUiState,
    colors: ChatThemeColors,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onRefreshAudioRoutes: () -> Unit,
    onSelectAudioRoute: (CallAudioRouteType) -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onRequestScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit,
) {
    val isVideo = uiState.callType == CallType.VIDEO
    val controlTint = if (isVideo && uiState.phase in VIDEO_SURFACE_PHASES) Color.White else colors.text
    val controlBg = if (isVideo && uiState.phase in VIDEO_SURFACE_PHASES) {
        Color.Black.copy(alpha = 0.45f)
    } else {
        colors.bubbleOther
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (uiState.phase) {
            CallUiPhase.IncomingRinging -> {
                FilledIconButton(
                    onClick = onDecline,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.call_decline_action))
                }
                FilledIconButton(
                    onClick = onAccept,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.accent,
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.call_accept_action))
                }
            }
            CallUiPhase.InCall -> {
                FilledIconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = controlBg),
                ) {
                    Icon(
                        imageVector = if (uiState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = stringResource(R.string.call_toggle_mute),
                        tint = if (uiState.isMuted) controlTint.copy(alpha = 0.45f) else controlTint,
                    )
                }
                if (isVideo) {
                    FilledIconButton(
                        onClick = onToggleVideo,
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = controlBg),
                    ) {
                        Icon(
                            imageVector = if (uiState.isVideoEnabled && !uiState.isScreenSharing) {
                                Icons.Default.Videocam
                            } else {
                                Icons.Default.VideocamOff
                            },
                            contentDescription = stringResource(R.string.call_toggle_video),
                            tint = if (uiState.isVideoEnabled && !uiState.isScreenSharing) {
                                controlTint
                            } else {
                                controlTint.copy(alpha = 0.45f)
                            },
                        )
                    }
                    FilledIconButton(
                        onClick = onSwitchCamera,
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = controlBg),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = stringResource(R.string.call_switch_camera),
                            tint = if (uiState.isScreenSharing) controlTint.copy(alpha = 0.45f) else controlTint,
                        )
                    }
                }
                AudioRouteButton(
                    uiState = uiState,
                    colors = colors,
                    controlBg = controlBg,
                    controlTint = controlTint,
                    onRefreshAudioRoutes = onRefreshAudioRoutes,
                    onSelectAudioRoute = onSelectAudioRoute,
                )
                FilledIconButton(
                    onClick = onEndCall,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.call_end_action))
                }
            }
            else -> {
                FilledIconButton(
                    onClick = onEndCall,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.call_end_action))
                }
            }
        }
    }
}

@Composable
private fun AudioRouteButton(
    uiState: CallUiState,
    colors: ChatThemeColors,
    controlBg: Color,
    controlTint: Color,
    onRefreshAudioRoutes: () -> Unit,
    onSelectAudioRoute: (CallAudioRouteType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledIconButton(
            onClick = {
                onRefreshAudioRoutes()
                if (uiState.hasExternalAudioRoute) {
                    expanded = true
                } else {
                    val nextRoute = if (uiState.selectedAudioRoute == CallAudioRouteType.SPEAKER) {
                        CallAudioRouteType.EARPIECE
                    } else {
                        CallAudioRouteType.SPEAKER
                    }
                    onSelectAudioRoute(nextRoute)
                }
            },
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (uiState.isSpeakerOn) {
                    colors.accent.copy(alpha = 0.35f)
                } else {
                    controlBg
                },
            ),
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = stringResource(R.string.call_audio_route),
                tint = controlTint,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            uiState.audioRoutes.forEach { route ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (route.type == uiState.selectedAudioRoute) {
                                stringResource(R.string.call_audio_route_selected, route.label)
                            } else {
                                route.label
                            },
                        )
                    },
                    onClick = {
                        onSelectAudioRoute(route.type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun phaseStatusLabel(uiState: CallUiState): String {
    uiState.statusMessage?.let { return it }
    return when (uiState.phase) {
        CallUiPhase.OutgoingRinging,
        CallUiPhase.IncomingRinging,
        -> stringResource(R.string.call_status_ringing)
        CallUiPhase.Connecting -> stringResource(R.string.call_status_connecting)
        CallUiPhase.InCall -> stringResource(R.string.call_status_connected)
        else -> ""
    }
}

private val VIDEO_SURFACE_PHASES = setOf(
    CallUiPhase.OutgoingRinging,
    CallUiPhase.IncomingRinging,
    CallUiPhase.Connecting,
    CallUiPhase.InCall,
)
