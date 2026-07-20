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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun ProfilePhotoCropDialog(
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onCropped: (Uri) -> Unit,
    onTooLarge: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var isCropping by remember { mutableStateOf(false) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var cropFrame by remember(sourceUri) { mutableStateOf<CropFrame?>(null) }

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
            val side = min(bounds.width, bounds.height) * 0.78f
            cropFrame = CropFrame(
                left = bounds.left + (bounds.width - side) / 2f,
                top = bounds.top + (bounds.height - side) / 2f,
                size = side,
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
                                        var activeDragMode = DragMode.None
                                        var previousCentroid: Offset? = null
                                        var previousDistance: Float? = null
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            if (pressed.isEmpty()) break

                                            val centroid = pressed.centroid()
                                            val distance = pressed.averageDistanceFrom(centroid)
                                            val pan = previousCentroid?.let { centroid - it } ?: Offset.Zero
                                            val activeFrame = cropFrame
                                            if (activeFrame != null && activeDragMode == DragMode.None) {
                                                activeDragMode = resolveDragMode(centroid, activeFrame)
                                            }
                                            if (activeFrame != null) {
                                                cropFrame = if (pressed.size >= 2) {
                                                    val zoom = previousDistance
                                                        ?.takeIf { it > 0f && distance > 0f }
                                                        ?.let { distance / it }
                                                        ?: 1f
                                                    scaleCropFrame(activeFrame, bounds, zoom, centroid)
                                                } else {
                                                    updateCropFrame(
                                                        frame = activeFrame,
                                                        bounds = bounds,
                                                        mode = activeDragMode,
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
                            cropSquareToCache(context, source, bounds, frame)
                        }.onSuccess { croppedUri ->
                            val length = File(croppedUri.path.orEmpty()).length()
                            isCropping = false
                            if (length > PROFILE_PHOTO_MAX_BYTES) {
                                onTooLarge()
                            } else {
                                onCropped(croppedUri)
                            }
                        }.onFailure {
                            isCropping = false
                            onTooLarge()
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCropOverlay(frame: CropFrame) {
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
        topLeft = Offset(0f, frame.top + frame.size),
        size = androidx.compose.ui.geometry.Size(size.width, size.height - frame.top - frame.size),
    )
    drawRect(
        color = overlay,
        topLeft = Offset(0f, frame.top),
        size = androidx.compose.ui.geometry.Size(frame.left, frame.size),
    )
    drawRect(
        color = overlay,
        topLeft = Offset(frame.left + frame.size, frame.top),
        size = androidx.compose.ui.geometry.Size(size.width - frame.left - frame.size, frame.size),
    )

    val left = frame.left
    val top = frame.top
    val right = frame.left + frame.size
    val bottom = frame.top + frame.size
    val third = frame.size / 3f
    val stroke = 1.2.dp.toPx()
    val borderStroke = 2.dp.toPx()
    val cornerStroke = 3.dp.toPx()
    val corner = min(34.dp.toPx(), frame.size * 0.16f)

    listOf(left + third, left + third * 2f).forEach { x ->
        drawLine(grid, Offset(x, top), Offset(x, bottom), stroke)
    }
    listOf(top + third, top + third * 2f).forEach { y ->
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

private fun resolveDragMode(start: Offset, frame: CropFrame): DragMode {
    val handle = CROP_HANDLE_TOUCH_PX
    val nearLeft = start.x in (frame.left - handle)..(frame.left + handle)
    val nearTop = start.y in (frame.top - handle)..(frame.top + handle)
    val nearRight = start.x in (frame.left + frame.size - handle)..(frame.left + frame.size + handle)
    val nearBottom = start.y in (frame.top + frame.size - handle)..(frame.top + frame.size + handle)
    return when {
        nearLeft && nearTop -> DragMode.TopLeft
        nearRight && nearTop -> DragMode.TopRight
        nearLeft && nearBottom -> DragMode.BottomLeft
        nearRight && nearBottom -> DragMode.BottomRight
        start.x in frame.left..(frame.left + frame.size) &&
            start.y in frame.top..(frame.top + frame.size) -> DragMode.Move
        else -> DragMode.Move
    }
}

private fun updateCropFrame(
    frame: CropFrame,
    bounds: Rect,
    mode: DragMode,
    drag: Offset,
): CropFrame {
    val minSide = min(CROP_MIN_SIZE_PX, min(bounds.width, bounds.height))
    val maxSide = min(bounds.width, bounds.height)
    return when (mode) {
        DragMode.Move -> frame.copy(
            left = (frame.left + drag.x).coerceIn(bounds.left, bounds.right - frame.size),
            top = (frame.top + drag.y).coerceIn(bounds.top, bounds.bottom - frame.size),
        )
        DragMode.TopLeft -> {
            val delta = max(drag.x, drag.y)
            val right = frame.left + frame.size
            val bottom = frame.top + frame.size
            val size = (frame.size - delta).coerceIn(minSide, maxSide)
            sanitizeFrame(CropFrame(right - size, bottom - size, size), bounds)
        }
        DragMode.TopRight -> {
            val delta = max(drag.x, -drag.y)
            val left = frame.left
            val bottom = frame.top + frame.size
            val size = (frame.size + delta).coerceIn(minSide, maxSide)
            sanitizeFrame(CropFrame(left, bottom - size, size), bounds)
        }
        DragMode.BottomLeft -> {
            val delta = max(-drag.x, drag.y)
            val right = frame.left + frame.size
            val top = frame.top
            val size = (frame.size + delta).coerceIn(minSide, maxSide)
            sanitizeFrame(CropFrame(right - size, top, size), bounds)
        }
        DragMode.BottomRight -> {
            val delta = max(drag.x, drag.y)
            val size = (frame.size + delta).coerceIn(minSide, maxSide)
            sanitizeFrame(frame.copy(size = size), bounds)
        }
        DragMode.None -> frame
    }
}

private fun scaleCropFrame(
    frame: CropFrame,
    bounds: Rect,
    zoom: Float,
    centroid: Offset,
): CropFrame {
    if (zoom == 1f) return frame
    val minSide = min(CROP_MIN_SIZE_PX, min(bounds.width, bounds.height))
    val maxSide = min(bounds.width, bounds.height)
    val newSize = (frame.size * zoom).coerceIn(minSide, maxSide)
    val anchorX = centroid.x.coerceIn(frame.left, frame.left + frame.size)
    val anchorY = centroid.y.coerceIn(frame.top, frame.top + frame.size)
    val anchorRatioX = (anchorX - frame.left) / frame.size
    val anchorRatioY = (anchorY - frame.top) / frame.size
    return sanitizeFrame(
        CropFrame(
            left = anchorX - newSize * anchorRatioX,
            top = anchorY - newSize * anchorRatioY,
            size = newSize,
        ),
        bounds,
    )
}

private fun sanitizeFrame(frame: CropFrame, bounds: Rect): CropFrame {
    val size = frame.size.coerceIn(
        min(CROP_MIN_SIZE_PX, min(bounds.width, bounds.height)),
        min(bounds.width, bounds.height),
    )
    return CropFrame(
        left = frame.left.coerceIn(bounds.left, bounds.right - size),
        top = frame.top.coerceIn(bounds.top, bounds.bottom - size),
        size = size,
    )
}

private fun cropSquareToCache(
    context: Context,
    bitmap: Bitmap,
    imageBounds: Rect,
    cropFrame: CropFrame,
): Uri {
    val scale = imageBounds.width / bitmap.width.toFloat()
    val x = ((cropFrame.left - imageBounds.left) / scale)
        .roundToInt()
        .coerceIn(0, bitmap.width - 1)
    val y = ((cropFrame.top - imageBounds.top) / scale)
        .roundToInt()
        .coerceIn(0, bitmap.height - 1)
    val side = (cropFrame.size / scale)
        .roundToInt()
        .coerceIn(1, min(bitmap.width - x, bitmap.height - y))

    val cropped = Bitmap.createBitmap(bitmap, x, y, side, side)
    val output = if (side > PROFILE_PHOTO_OUTPUT_SIZE) {
        Bitmap.createScaledBitmap(cropped, PROFILE_PHOTO_OUTPUT_SIZE, PROFILE_PHOTO_OUTPUT_SIZE, true)
    } else {
        cropped
    }
    val file = File(context.cacheDir, "profile_photo_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { stream ->
        output.compress(Bitmap.CompressFormat.JPEG, PROFILE_PHOTO_JPEG_QUALITY, stream)
    }
    if (output !== cropped) cropped.recycle()
    return Uri.fromFile(file)
}

private data class CropFrame(
    val left: Float,
    val top: Float,
    val size: Float,
)

private enum class DragMode {
    None,
    Move,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
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

private const val PROFILE_PHOTO_OUTPUT_SIZE = 1024
private const val PROFILE_PHOTO_JPEG_QUALITY = 92
private const val PROFILE_PHOTO_MAX_BYTES = 5L * 1024L * 1024L
private const val CROP_MIN_SIZE_PX = 96f
private const val CROP_HANDLE_TOUCH_PX = 84f
