package com.codeshell.app.data.repository

import android.content.Context
import android.util.Log
import com.codeshell.app.BuildConfig
import com.codeshell.app.ui.disguise.snippets.LearnManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

class LessonRepository(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _manifest = MutableStateFlow<LearnManifest?>(null)
    val manifest: StateFlow<LearnManifest?> = _manifest.asStateFlow()

    init {
        scope.launch {
            loadManifest()
        }
    }

    suspend fun loadManifest() {
        _manifest.value = loadBundledManifest()
    }

    private fun loadBundledManifest(): LearnManifest? {
        val assetPath = BuildConfig.LESSON_ASSET
        val cached = readCachedManifest()
        if (cached != null) return cached
        return try {
            val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            parseManifest(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bundled manifest from $assetPath", e)
            null
        }
    }

    private fun readCachedManifest(): LearnManifest? {
        val file = cacheFile()
        if (!file.exists()) return null
        return runCatching { parseManifest(file.readText()) }.getOrNull().also {
            if (it == null) file.delete()
        }
    }

    private fun cacheFile(): File = File(context.filesDir, "lessons_cache.json")

    private fun parseManifest(text: String): LearnManifest =
        if (text.isBlank()) throw IllegalStateException("Empty manifest content")
        else json.decodeFromString(text)

    companion object {
        private const val TAG = "LessonRepository"
    }
}
