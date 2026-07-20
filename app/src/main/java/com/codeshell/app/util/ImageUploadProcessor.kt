package com.codeshell.app.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object ImageUploadProcessor {

    private const val MAX_LONG_EDGE = 1600
    private const val JPEG_QUALITY = 80

    data class PreparedImage(
        val uri: Uri,
        val fileSize: Long?,
    )

    fun prepare(
        context: Context,
        contentResolver: ContentResolver,
        sourceUri: Uri,
    ): PreparedImage {
        val originalSize = queryFileSize(contentResolver, sourceUri)

        return runCatching {
            val bounds = decodeBounds(contentResolver, sourceUri)
            val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
            val decoded = decodeBitmap(contentResolver, sourceUri, sampleSize)
                ?: return PreparedImage(sourceUri, originalSize)

            val oriented = applyExifOrientation(contentResolver, sourceUri, decoded)
            val scaled = scaleToMaxLongEdge(oriented, MAX_LONG_EDGE)

            val outputFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outputFile).use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            }
            if (scaled !== oriented) {
                scaled.recycle()
            }
            oriented.recycle()

            PreparedImage(
                uri = Uri.fromFile(outputFile),
                fileSize = outputFile.length().takeIf { it > 0L }
            )
        }.getOrElse {
            PreparedImage(sourceUri, originalSize)
        }
    }

    private fun scaleToMaxLongEdge(bitmap: Bitmap, maxLongEdge: Int): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxLongEdge) return bitmap

        val scale = maxLongEdge.toFloat() / longEdge.toFloat()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun decodeBounds(contentResolver: ContentResolver, uri: Uri): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return options
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sampleSize = 1
        val maxDimension = maxOf(width, height)
        while (maxDimension / sampleSize > MAX_LONG_EDGE * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun decodeBitmap(contentResolver: ContentResolver, uri: Uri, sampleSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    private fun applyExifOrientation(contentResolver: ContentResolver, uri: Uri, bitmap: Bitmap): Bitmap {
        val rotation = contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0

        if (rotation == 0) return bitmap

        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
}
