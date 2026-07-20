package com.codeshell.app.call

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays a classic ringback pattern for the outgoing caller using [ToneGenerator]
 * (no bundled audio asset).
 */
class CallRingbackPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var toneGenerator: ToneGenerator? = null

    @Synchronized
    fun start() {
        stop()
        loopJob = scope.launch {
            val tone = runCatching {
                ToneGenerator(AudioManager.STREAM_VOICE_CALL, TONE_VOLUME)
            }.getOrNull() ?: return@launch
            toneGenerator = tone
            while (isActive) {
                tone.startTone(ToneGenerator.TONE_SUP_RINGTONE, TONE_DURATION_MS)
                delay(TONE_DURATION_MS.toLong())
                if (!isActive) break
                delay(RINGBACK_PAUSE_MS)
            }
        }
    }

    @Synchronized
    fun stop() {
        loopJob?.cancel()
        loopJob = null
        toneGenerator?.let { tone ->
            runCatching {
                tone.stopTone()
                tone.release()
            }
        }
        toneGenerator = null
    }

    companion object {
        private const val TONE_VOLUME = 70
        private const val TONE_DURATION_MS = 1_000
        private const val RINGBACK_PAUSE_MS = 2_000L
    }
}
