package com.mysocial.mesh.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * High-performance, zero-dependency audio recorder using native Android AAC/MPEG-4.
 * Produces tiny, high-fidelity voice notes optimized for offline mesh synchronization.
 */
class VoiceRecorder(
    private val scope: CoroutineScope
) {
    private val tag = "VoiceRecorder"
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L
    private var tickerJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    fun startRecording(context: Context): Boolean {
        if (_isRecording.value) return false
        try {
            val audioDir = File(context.cacheDir, "voice_notes").apply { if (!exists()) mkdirs() }
            val file = File(audioDir, "rec_${System.currentTimeMillis()}.m4a")
            currentFile = file

            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000) // 64 kbps voice profile
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = rec
            startTimeMs = System.currentTimeMillis()
            _isRecording.value = true
            _recordingDurationMs.value = 0L

            tickerJob = scope.launch(Dispatchers.Default) {
                while (isActive && _isRecording.value) {
                    _recordingDurationMs.value = System.currentTimeMillis() - startTimeMs
                    delay(100L)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start audio recording", e)
            cleanup()
            return false
        }
    }

    fun stopRecording(): Pair<File, Long>? {
        if (!_isRecording.value) return null
        val duration = System.currentTimeMillis() - startTimeMs
        val file = currentFile

        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping recorder (likely too short): ${e.message}")
        } finally {
            cleanup()
        }

        return if (file != null && file.exists() && file.length() > 0 && duration >= 400L) {
            Pair(file, duration)
        } else {
            file?.delete()
            null
        }
    }

    fun cancelRecording() {
        val file = currentFile
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        cleanup()
        file?.delete()
    }

    private fun cleanup() {
        tickerJob?.cancel()
        tickerJob = null
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        _isRecording.value = false
        _recordingDurationMs.value = 0L
    }
}

/**
 * Manages playback of voice notes with position tracking and auto-cleanup.
 */
class VoicePlayer(
    private val scope: CoroutineScope
) {
    private val tag = "VoicePlayer"
    private var player: MediaPlayer? = null
    private var tickerJob: Job? = null

    private val _currentPlayingMessageId = MutableStateFlow<String?>(null)
    val currentPlayingMessageId: StateFlow<String?> = _currentPlayingMessageId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    fun togglePlay(messageId: String, file: File) {
        if (_currentPlayingMessageId.value == messageId) {
            if (_isPlaying.value) {
                pause()
            } else {
                resume()
            }
        } else {
            play(messageId, file)
        }
    }

    fun play(messageId: String, file: File) {
        stop()
        if (!file.exists()) {
            Log.w(tag, "Audio file does not exist: ${file.absolutePath}")
            return
        }

        try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stop()
                }
                start()
            }
            player = mp
            _currentPlayingMessageId.value = messageId
            _isPlaying.value = true

            val totalDuration = mp.duration.toFloat().coerceAtLeast(1f)

            tickerJob = scope.launch(Dispatchers.Default) {
                while (isActive && _isPlaying.value) {
                    try {
                        val current = player?.currentPosition ?: 0
                        _currentPositionMs.value = current.toLong()
                        _playbackProgress.value = (current / totalDuration).coerceIn(0f, 1f)
                    } catch (_: Exception) {}
                    delay(50L)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start media player", e)
            stop()
        }
    }

    fun pause() {
        try {
            player?.pause()
        } catch (_: Exception) {}
        _isPlaying.value = false
        tickerJob?.cancel()
    }

    fun resume() {
        try {
            player?.start()
            _isPlaying.value = true
            val totalDuration = (player?.duration ?: 1).toFloat().coerceAtLeast(1f)
            tickerJob = scope.launch(Dispatchers.Default) {
                while (isActive && _isPlaying.value) {
                    try {
                        val current = player?.currentPosition ?: 0
                        _currentPositionMs.value = current.toLong()
                        _playbackProgress.value = (current / totalDuration).coerceIn(0f, 1f)
                    } catch (_: Exception) {}
                    delay(50L)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to resume player", e)
            stop()
        }
    }

    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}
        player = null
        _currentPlayingMessageId.value = null
        _isPlaying.value = false
        _playbackProgress.value = 0f
        _currentPositionMs.value = 0L
    }

    fun release() {
        stop()
    }
}

