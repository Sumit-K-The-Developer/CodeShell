package com.codeshell.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codeshell.app.R

private val HiddenSettingsHitSize = 56.dp

@Composable
fun HiddenSettingsIconButton(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    hitSize: Dp = HiddenSettingsHitSize,
) {
    Box(
        modifier = modifier
            .size(hitSize)
            .alpha(0f)
            .pointerInput(onOpenSettings) {
                detectTapGestures(
                    onTap = { },
                    onDoubleTap = { onOpenSettings() },
                    onLongPress = { onOpenSettings() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = stringResource(R.string.settings_title),
            modifier = Modifier.size(24.dp),
        )
    }
}
