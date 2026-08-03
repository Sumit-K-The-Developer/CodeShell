package com.codeshell.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codeshell.app.R
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Full-screen free-form rectangular crop tool used for draft-attachment images.
 * Unlike the square-locked profile photo cropper, this preserves the selected
 * rectangular region at (near) original resolution so it can be sent as-is.
 */
@Composable
fun MediaCropDialog(
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onCropped: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var isCropping by remember { mutableStateOf(false) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var cropFrame by remember(sourceUri) { mutableStateOf<CropRect?>(null) }

    LaunchedEffect(sourceUri) {
        bitmap = loadBitmap(context, sourceUri)
        cropFrame = null
    }

    val currentBitmap = bitmap
    val imageBounds = remember(currentBitmap, previewSize) {
        if (currentBitmap == null || previewSize.width == 0 || previewSize.height == 0) {
            null
        } else {
            imageDisplayBounds(currentBitmap, previewSize)
        }
    }

    LaunchedEffect(imageBounds) {
        val bounds = imageBounds ?: return@LaunchedEffect
        if (cropFrame == null) {
            val wide = bounds.width * MEDIA_CROP_INITIAL_FRACTION
            val tall = bounds.height * MEDIA_CROP_INITIAL_FRACTION
            cropFrame = CropRect(
                left = bounds.left + (bounds.width - wide) / 2f,
                top = bounds.top + (bounds.height - tall) / 2f,
                right = bounds.left + (bounds.width + wide) / 2f,
                bottom = bounds.top + (bounds.height + tall) / 2f,
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF071014)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .onSizeChanged { previewSize = it },
                contentAlignment = Alignment.Center,
            ) {
                if (currentBitmap == null) {
                    CircularProgressIndicator()
                } else {
                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    val frame = cropFrame
                    val bounds = imageBounds
                    if (frame != null && bounds != null) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(bounds) {
                                    awaitEachGesture {
                                        var activeMode = RectDragMode.None
                                        var previousCentroid: Offset? = null
                                        var previousDistance: Float? = null
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            if (pressed.isEmpty()) break

                                            val centroid = pressed.centroid()
                                            val distance = pressed.averageDistanceFrom(centroid)
                                            val pan = previousCentroid
                                                ?.let { centroid - it }
                                                ?: Offset.Zero
                                            val activeFrame = cropFrame
                                            if (activeFrame != null && activeMode == RectDragMode.None) {
                                                activeMode = resolveRectDragMode(centroid, activeFrame)
                                            }
                                            if (activeFrame != null) {
                                                cropFrame = if (pressed.size >= 2) {
                                                    val zoom = previousDistance
                                                        ?.takeIf { it > 0f && distance > 0f }
                                                        ?.let { distance / it }
                                                        ?: 1f
                                                    scaleCropRect(activeFrame, bounds, zoom, centroid)
                                                } else {
                                                    updateCropRect(
                                                        frame = activeFrame,
                                                        bounds = bounds,
                                                        mode = activeMode,
                                                        drag = pan,
                                                    )
                                                }
                                                event.changes.forEach { it.consume() }
                                            }

                                            previousCentroid = centroid
                                            previousDistance = distance
                                        }
                                    }
                                },
                        ) {
                            drawCropOverlay(frame)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !isCropping) {
                    Text(stringResource(R.string.cancel), color = Color(0xFF24D366))
                }
                Button(
                    enabled = currentBitmap != null && cropFrame != null && imageBounds != null && !isCropping,
                    onClick = {
                        val source = currentBitmap ?: return@Button
                        val frame = cropFrame ?: return@Button
                        val bounds = imageBounds ?: return@Button
                        isCropping = true
                        runCatching {
                            cropRectToCache(context, source, bounds, frame)
                        }.onSuccess { croppedUri ->
                            isCropping = false
                            onCropped(croppedUri)
                        }.onFailure {
                            isCropping = false
                        }
                    },
                ) {
                    if (isCropping) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.done), color = Color(0xFF06120C))
                    }
                }
            }
        }
    }
}
private fun loadBitmap(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input)
        }
    } ?: error("Could not decode image")
}

private fun imageDisplayBounds(bitmap: Bitmap, previewSize: IntSize): Rect {
    val scale = min(
        previewSize.width.toFloat() / bitmap.width.toFloat(),
        previewSize.height.toFloat() / bitmap.height.toFloat(),
    )
    val width = bitmap.width * scale
    val height = bitmap.height * scale
    val left = (previewSize.width - width) / 2f
    val top = (previewSize.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCropOverlay(frame: CropRect) {
    val overlay = Color.Black.copy(alpha = 0.48f)
    val line = Color.White.copy(alpha = 0.92f)
    val grid = Color.White.copy(alpha = 0.38f)
    drawRect(
        color = overlay,
        topLeft = Offset.Zero,
        size = androidx.compose.ui.geometry.Size(size.width, frame.top),
    )
    drawRect(
        color = overlay,
        topLeft = Offset(0f, frame.bottom),
        size = androidx.compose.ui.geometry.Size(size.width, size.height - frame.bottom),
    )
    drawRect(
        color = overlay,
        topLeft = Offset(0f, frame.top),
        size = androidx.compose.ui.geometry.Size(frame.left, frame.height),
    )
    drawRect(
        color = overlay,
        topLeft = Offset(frame.right, frame.top),
        size = androidx.compose.ui.geometry.Size(size.width - frame.right, frame.height),
    )

    val left = frame.left
    val top = frame.top
    val right = frame.right
    val bottom = frame.bottom
    val thirdX = frame.width / 3f
    val thirdY = frame.height / 3f
    val stroke = 1.2.dp.toPx()
    val borderStroke = 2.dp.toPx()
    val cornerStroke = 3.dp.toPx()
    val corner = min(34.dp.toPx(), min(frame.width, frame.height) * 0.16f)

    listOf(left + thirdX, left + thirdX * 2f).forEach { x ->
        drawLine(grid, Offset(x, top), Offset(x, bottom), stroke)
    }
    listOf(top + thirdY, top + thirdY * 2f).forEach { y ->
        drawLine(grid, Offset(left, y), Offset(right, y), stroke)
    }
    drawLine(line, Offset(left, top), Offset(right, top), borderStroke)
    drawLine(line, Offset(left, bottom), Offset(right, bottom), borderStroke)
    drawLine(line, Offset(left, top), Offset(left, bottom), borderStroke)
    drawLine(line, Offset(right, top), Offset(right, bottom), borderStroke)

    drawLine(line, Offset(left, top), Offset(left + corner, top), cornerStroke)
    drawLine(line, Offset(left, top), Offset(left, top + corner), cornerStroke)
    drawLine(line, Offset(right, top), Offset(right - corner, top), cornerStroke)
    drawLine(line, Offset(right, top), Offset(right, top + corner), cornerStroke)
    drawLine(line, Offset(left, bottom), Offset(left + corner, bottom), cornerStroke)
    drawLine(line, Offset(left, bottom), Offset(left, bottom - corner), cornerStroke)
    drawLine(line, Offset(right, bottom), Offset(right - corner, bottom), cornerStroke)
    drawLine(line, Offset(right, bottom), Offset(right, bottom - corner), cornerStroke)
}

private fun resolveRectDragMode(start: Offset, frame: CropRect): RectDragMode {
    val handle = CROP_HANDLE_TOUCH_PX
    val nearLeft = start.x in (frame.left - handle)..(frame.left + handle)
    val nearTop = start.y in (frame.top - handle)..(frame.top + handle)
    val nearRight = start.x in (frame.right - handle)..(frame.right + handle)
    val nearBottom = start.y in (frame.bottom - handle)..(frame.bottom + handle)
    return when {
        nearLeft && nearTop -> RectDragMode.TopLeft
        nearRight && nearTop -> RectDragMode.TopRight
        nearLeft && nearBottom -> RectDragMode.BottomLeft
        nearRight && nearBottom -> RectDragMode.BottomRight
        nearLeft && start.y in frame.top..frame.bottom -> RectDragMode.Left
        nearRight && start.y in frame.top..frame.bottom -> RectDragMode.Right
        nearTop && start.x in frame.left..frame.right -> RectDragMode.Top
        nearBottom && start.x in frame.left..frame.right -> RectDragMode.Bottom
        start.x in frame.left..frame.right && start.y in frame.top..frame.bottom -> RectDragMode.Move
        else -> RectDragMode.Move
    }
}

private fun updateCropRect(
    frame: CropRect,
    bounds: Rect,
    mode: RectDragMode,
    drag: Offset,
): CropRect {
    val minW = CROP_MIN_SIZE_PX.coerceAtMost(bounds.width)
    val minH = CROP_MIN_SIZE_PX.coerceAtMost(bounds.height)
    return when (mode) {
        RectDragMode.Move -> frame.copy(
            left = (frame.left + drag.x).coerceIn(bounds.left, bounds.right - frame.width),
            top = (frame.top + drag.y).coerceIn(bounds.top, bounds.bottom - frame.height),
            right = (frame.right + drag.x).coerceIn(bounds.left + frame.width, bounds.right),
            bottom = (frame.bottom + drag.y).coerceIn(bounds.top + frame.height, bounds.bottom),
        )
        RectDragMode.TopLeft -> sanitizeRect(
            CropRect(
                left = frame.left + drag.x,
                top = frame.top + drag.y,
                right = frame.right,
                bottom = frame.bottom,
            ),
            bounds,
            minW,
            minH,
        )
        RectDragMode.TopRight -> sanitizeRect(
            CropRect(
                left = frame.left,
                top = frame.top + drag.y,
                right = frame.right + drag.x,
                bottom = frame.bottom,
            ),
            bounds,
            minW,
            minH,
        )
        RectDragMode.BottomLeft -> sanitizeRect(
            CropRect(
                left = frame.left + drag.x,
                top = frame.top,
                right = frame.right,
                bottom = frame.bottom + drag.y,
            ),
            bounds,
            minW,
            minH,
        )
        RectDragMode.BottomRight -> sanitizeRect(
            CropRect(
                left = frame.left,
                top = frame.top,
                right = frame.right + drag.x,
                bottom = frame.bottom + drag.y,
            ),
            bounds,
            minW,
            minH,
        )
        RectDragMode.Left -> sanitizeRect(
            frame.copy(left = frame.left + drag.x),
            bounds,
            minW,
            minH,
        )
        RectDragMode.Right -> sanitizeRect(
            frame.copy(right = frame.right + drag.x),
            bounds,
            minW,
            minH,
        )
        RectDragMode.Top -> sanitizeRect(
            frame.copy(top = frame.top + drag.y),
            bounds,
            minW,
            minH,
        )
        RectDragMode.Bottom -> sanitizeRect(
            frame.copy(bottom = frame.bottom + drag.y),
            bounds,
            minW,
            minH,
        )
        RectDragMode.None -> frame
    }
}

private fun scaleCropRect(
    frame: CropRect,
    bounds: Rect,
    zoom: Float,
    centroid: Offset,
): CropRect {
    if (zoom == 1f) return frame
    val minW = CROP_MIN_SIZE_PX.coerceAtMost(bounds.width)
    val minH = CROP_MIN_SIZE_PX.coerceAtMost(bounds.height)
    val newW = (frame.width * zoom).coerceIn(minW, bounds.width)
    val newH = (frame.height * zoom).coerceIn(minH, bounds.height)
    val anchorX = centroid.x.coerceIn(frame.left, frame.right)
    val anchorY = centroid.y.coerceIn(frame.top, frame.bottom)
    val rx = if (frame.width > 0f) (anchorX - frame.left) / frame.width else 0.5f
    val ry = if (frame.height > 0f) (anchorY - frame.top) / frame.height else 0.5f
    return sanitizeRect(
        CropRect(
            left = anchorX - newW * rx,
            top = anchorY - newH * ry,
            right = anchorX + newW * (1f - rx),
            bottom = anchorY + newH * (1f - ry),
        ),
        bounds,
        minW,
        minH,
    )
}

private fun sanitizeRect(
    frame: CropRect,
    bounds: Rect,
    minW: Float,
    minH: Float,
): CropRect {
    var left = frame.left
    var top = frame.top
    var right = frame.right
    var bottom = frame.bottom

    // Clamp every edge inside the image bounds first.
    if (left > right) { val t = left; left = right; right = t }
    if (top > bottom) { val t = top; top = bottom; bottom = t }
    left = left.coerceIn(bounds.left, bounds.right)
    right = right.coerceIn(bounds.left, bounds.right)
    top = top.coerceIn(bounds.top, bounds.bottom)
    bottom = bottom.coerceIn(bounds.top, bounds.bottom)

    // Enforce the minimum size by expanding around the current rectangle's
    // center, without exceeding the image bounds or flipping edges.
    var w = right - left
    var h = bottom - top
    if (w < minW) {
        val cx = (left + right) / 2f
        left = (cx - minW / 2f).coerceIn(bounds.left, bounds.right - minW)
        right = left + minW
    }
    if (h < minH) {
        val cy = (top + bottom) / 2f
        top = (cy - minH / 2f).coerceIn(bounds.top, bounds.bottom - minH)
        bottom = top + minH
    }

    // Keep within bounds after any expansion.
    left = left.coerceIn(bounds.left, bounds.right - minW)
    top = top.coerceIn(bounds.top, bounds.bottom - minH)
    right = right.coerceIn(bounds.left + minW, bounds.right)
    bottom = bottom.coerceIn(bounds.top + minH, bounds.bottom)

    return CropRect(left, top, right, bottom)
}

private fun cropRectToCache(
    context: Context,
    bitmap: Bitmap,
    imageBounds: Rect,
    cropRect: CropRect,
): Uri {
    val scaleX = bitmap.width.toFloat() / imageBounds.width
    val scaleY = bitmap.height.toFloat() / imageBounds.height
    val x = ((cropRect.left - imageBounds.left) * scaleX)
        .roundToInt()
        .coerceIn(0, bitmap.width - 1)
    val y = ((cropRect.top - imageBounds.top) * scaleY)
        .roundToInt()
        .coerceIn(0, bitmap.height - 1)
    val w = ((cropRect.right - cropRect.left) * scaleX)
        .roundToInt()
        .coerceIn(1, bitmap.width - x)
    val h = ((cropRect.bottom - cropRect.top) * scaleY)
        .roundToInt()
        .coerceIn(1, bitmap.height - y)

    val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
    val file = File(context.cacheDir, "draft_crop_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { stream ->
        cropped.compress(Bitmap.CompressFormat.JPEG, MEDIA_CROP_JPEG_QUALITY, stream)
    }
    cropped.recycle()
    return Uri.fromFile(file)
}

private data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

private enum class RectDragMode {
    None,
    Move,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
    Left,
    Right,
    Top,
    Bottom,
}

private fun List<androidx.compose.ui.input.pointer.PointerInputChange>.centroid(): Offset {
    val x = sumOf { it.position.x.toDouble() }.toFloat() / size
    val y = sumOf { it.position.y.toDouble() }.toFloat() / size
    return Offset(x, y)
}

private fun List<androidx.compose.ui.input.pointer.PointerInputChange>.averageDistanceFrom(
    centroid: Offset,
): Float {
    if (size < 2) return 0f
    return sumOf { change ->
        val dx = change.position.x - centroid.x
        val dy = change.position.y - centroid.y
        sqrt(dx * dx + dy * dy).toDouble()
    }.toFloat() / size
}

private const val MEDIA_CROP_JPEG_QUALITY = 92
private const val MEDIA_CROP_INITIAL_FRACTION = 0.82f
private const val CROP_MIN_SIZE_PX = 96f
private const val CROP_HANDLE_TOUCH_PX = 84f
