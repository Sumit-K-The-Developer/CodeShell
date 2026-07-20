package com.codeshell.app.util

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@UnstableApi
object MediaPreloadCache {

    private const val CACHE_SIZE_BYTES = 100L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preloadingUrls = ConcurrentHashMap.newKeySet<String>()

    private fun getCache(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: run {
                val cacheDir = File(context.applicationContext.cacheDir, "media_playback_cache")
                SimpleCache(
                    cacheDir,
                    LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES),
                    StandaloneDatabaseProvider(context.applicationContext)
                ).also { cache = it }
            }
        }
    }

    private fun cacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val appContext = context.applicationContext
        val upstreamFactory = DefaultDataSource.Factory(appContext)
        return CacheDataSource.Factory()
            .setCache(getCache(appContext))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun preload(context: Context, url: String) {
        if (url.isBlank() || !preloadingUrls.add(url)) return
        preloadScope.launch {
            runCatching {
                val appContext = context.applicationContext
                val player = ExoPlayer.Builder(appContext)
                    .setMediaSourceFactory(
                        DefaultMediaSourceFactory(cacheDataSourceFactory(appContext))
                    )
                    .build()
                try {
                    player.setMediaItem(MediaItem.fromUri(url))
                    player.prepare()
                } finally {
                    player.release()
                }
            }
            preloadingUrls.remove(url)
        }
    }

    fun evictUrl(context: Context, url: String) {
        if (url.isBlank()) return
        runCatching {
            val appContext = context.applicationContext
            val cache = getCache(appContext)
            val dataSpec = DataSpec.Builder().setUri(url).build()
            val key = CacheKeyFactory.DEFAULT.buildCacheKey(dataSpec)
            cache.removeResource(key)
        }
        preloadingUrls.remove(url)
    }

    fun buildPlayer(context: Context, url: String): ExoPlayer {
        val appContext = context.applicationContext
        return ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(cacheDataSourceFactory(appContext))
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
            }
    }
}
