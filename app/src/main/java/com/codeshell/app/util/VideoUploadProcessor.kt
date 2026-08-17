package com.codeshell.app.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
object VideoUploadProcessor {

    private const val TAG = "VideoUploadProcessor"
    const val MAX_UPLOAD_BYTES = 500L * 1024 * 1024

    data class PreparedVideo(
        val uri: Uri,
        val fileSize: Long?,
    )

    class VideoTooLargeException : Exception("Video exceeds maximum upload size")

    class VideoPrepareException(message: String, cause: Throwable? = null) : Exception(message, cause)

    class VideoCancelledException : Exception("Video processing cancelled")

    private data class CompressionTier(
        val maxHeight: Int,
        val bitrate: Int,
        val scaleVideo: Boolean,
    )

    private val compressionTiers = listOf(
        CompressionTier(maxHeight = 1280, bitrate = 2_500_000, scaleVideo = false),
        CompressionTier(maxHeight = 720, bitrate = 1_500_000, scaleVideo = true),
        CompressionTier(maxHeight = 480, bitrate = 800_000, scaleVideo = true),
    )

    private val activeTransformer = AtomicReference<Transformer?>(null)
    private val cancelRequested = AtomicReference(false)

    fun cancelActive() {
        cancelRequested.set(true)
        activeTransformer.getAndSet(null)?.cancel()
    }

    fun prepare(
        context: Context,
        contentResolver: ContentResolver,
        sourceUri: Uri,
    ): PreparedVideo {
        val appContext = context.applicationContext
        val localFile = materializeToCache(appContext, contentResolver, sourceUri)
        val localUri = Uri.fromFile(localFile)
        val localSize = localFile.length()

        if (localSize > MAX_UPLOAD_BYTES) {
            localFile.delete()
            throw VideoTooLargeException()
        }

        var lastError: Exception? = null
        for (tier in compressionTiers) {
            val outputFile = File(appContext.cacheDir, "upload_${System.currentTimeMillis()}.mp4")
            val result = runCatching {
                transcode(appContext, localUri, outputFile, tier)
            }
            result.onFailure { error ->
                when (error) {
                    is VideoCancelledException -> {
                        localFile.delete()
                        throw error
                    }
                    else -> {
                        Log.w(TAG, "Transcode tier ${tier.maxHeight} failed", error)
                        lastError = error as? Exception
                            ?: VideoPrepareException(error.message ?: "Transcode failed", error)
                        outputFile.delete()
                    }
                }
            }
            if (result.isSuccess) {
                val size = outputFile.length()
                if (size in 1..MAX_UPLOAD_BYTES) {
                    localFile.delete()
                    return PreparedVideo(Uri.fromFile(outputFile), size)
                }
                outputFile.delete()
            }
        }

        if (localSize in 1..MAX_UPLOAD_BYTES) {
            Log.w(TAG, "Transcode failed; uploading original materialized copy", lastError)
            return PreparedVideo(localUri, localSize)
        }

        localFile.delete()
        throw lastError ?: VideoTooLargeException()
    }

    private fun materializeToCache(
        context: Context,
        contentResolver: ContentResolver,
        sourceUri: Uri,
    ): File {
        if (sourceUri.scheme == "file") {
            val path = sourceUri.path
            if (!path.isNullOrBlank()) {
                val existing = File(path)
                if (existing.exists() && existing.length() > 0L) {
                    return existing
                }
            }
        }

        val extension = when (contentResolver.getType(sourceUri)) {
            "video/3gpp" -> "3gp"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            else -> "mp4"
        }
        val outputFile = File(context.cacheDir, "source_${System.currentTimeMillis()}.$extension")
        val copied = contentResolver.openInputStream(sourceUri)?.use { input ->
            outputFile.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
            true
        } ?: false

        if (!copied || outputFile.length() <= 0L) {
            outputFile.delete()
            throw VideoPrepareException("Cannot read selected video")
        }
        return outputFile
    }

    private fun buildTransformer(context: Context, tier: CompressionTier): Transformer {
        val decoderFactory = DefaultDecoderFactory.Builder(context)
            .setEnableDecoderFallback(true)
            .build()
        val assetLoaderFactory = DefaultAssetLoaderFactory(
            context,
            decoderFactory,
            Clock.DEFAULT,
        )
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(tier.bitrate)
                    .build()
            )
            .build()

        return Transformer.Builder(context)
            .setAssetLoaderFactory(assetLoaderFactory)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .build()
    }

    private fun buildEditedMediaItem(sourceUri: Uri, tier: CompressionTier): EditedMediaItem {
        val mediaItem = MediaItem.fromUri(sourceUri)
        if (!tier.scaleVideo) {
            return EditedMediaItem.Builder(mediaItem).build()
        }
        val presentation = Presentation.createForHeight(tier.maxHeight)
        val effects = Effects(emptyList(), listOf(presentation))
        return EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .build()
    }

    private fun transcode(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        tier: CompressionTier,
    ) {
        val transformer = buildTransformer(context, tier)
        val editedMediaItem = buildEditedMediaItem(sourceUri, tier)

        activeTransformer.set(transformer)
        cancelRequested.set(false)

        val errorRef = AtomicReference<ExportException?>(null)
        val latch = CountDownLatch(1)
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                latch.countDown()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                errorRef.set(exportException)
                latch.countDown()
            }
        }

        transformer.addListener(listener)
        transformer.start(editedMediaItem, outputFile.absolutePath)
        if (!latch.await(10, TimeUnit.MINUTES)) {
            transformer.cancel()
            activeTransformer.compareAndSet(transformer, null)
            throw VideoPrepareException("Video processing timed out")
        }

        activeTransformer.compareAndSet(transformer, null)

        if (cancelRequested.get()) {
            outputFile.delete()
            throw VideoCancelledException()
        }

        errorRef.get()?.let { throw VideoPrepareException(it.message ?: "Video transcode failed", it) }
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            throw VideoPrepareException("Video output file is empty")
        }
    }
}
