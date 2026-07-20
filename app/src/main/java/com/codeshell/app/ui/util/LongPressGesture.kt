package com.codeshell.app.ui.util

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.customLongPress(
    durationMs: Long,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = pointerInput(durationMs) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull {
                it.pressed && !it.previousPressed
            } ?: continue
            val pointerId = down.id
            val start = down.uptimeMillis
            var longPressFired = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) {
                    if (!longPressFired) onTap()
                    break
                }
                if (!longPressFired && change.uptimeMillis - start >= durationMs) {
                    longPressFired = true
                    onLongPress()
                }
            }
        }
    }
}
