package com.codeshell.app.ui.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

fun Modifier.tapOrDoubleTap(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    enabled: Boolean = true,
    doubleTapEnabled: Boolean = true,
): Modifier = composed {
    if (!enabled) return@composed this
    this.then(
        Modifier.pointerInput(doubleTapEnabled) {
            if (doubleTapEnabled) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap() },
                )
            } else {
                detectTapGestures(
                    onTap = { onTap() },
                )
            }
        },
    )
}

/**
 * Reliable tap inside scrollable lists ([LazyColumn]) plus horizontal swipe for a secondary action.
 * Uses [clickable] for taps so scroll parents do not swallow short presses.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.clickableWithHorizontalSwipe(
    onClick: () -> Unit,
    onSwipe: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    minSwipeDistance: Dp = 48.dp,
    enabled: Boolean = true,
): Modifier = composed {
    if (!enabled) return@composed this

    val minDistancePx = with(LocalDensity.current) { minSwipeDistance.toPx() }
    var dragDistance by remember { mutableFloatStateOf(0f) }

    this
        .pointerInput(minDistancePx) {
            detectHorizontalDragGestures(
                onDragStart = { dragDistance = 0f },
                onHorizontalDrag = { _, amount -> dragDistance += amount },
                onDragEnd = {
                    if (abs(dragDistance) >= minDistancePx) {
                        onSwipe()
                    }
                    dragDistance = 0f
                },
                onDragCancel = { dragDistance = 0f },
            )
        }
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            } else {
                Modifier.clickable(enabled = enabled, onClick = onClick)
            }
        )
}
