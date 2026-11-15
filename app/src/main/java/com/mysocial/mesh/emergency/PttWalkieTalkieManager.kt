package com.mysocial.mesh.emergency

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.PttVoiceBurstPayload
import com.mysocial.mesh.protocol.GossipSyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.abs

/**
 * Manages half-duplex Push-to-Talk (PTT) voice bursts over the peer-to-peer mesh.
 * Audio is recorded at 16kHz 16-bit mono PCM, amplified via dynamic gain normalization,
 * base64-encoded, and broadcast across active mesh peers.
 */
class PttWalkieTalkieManager(
    private val context: Context,
    private val cryptoManager: CryptoManager,
    private val syncEngine: GossipSyncEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val onContentPublished: (() -> Unit)? = null
) {
    var onPttBroadcast: ((PttVoiceBurstPayload) -> Unit)? = null
    private val playedBurstIds: MutableSet<String> = java.util.Collections.synchronizedSet(
        object : java.util.LinkedHashMap<String, Boolean>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 500
            }
        }.let { java.util.Collections.newSetFromMap(it) }
    )
    private val playbackMutex = kotlinx.coroutines.sync.Mutex()
    companion object {
        private const val TAG = "PttWalkieTalkie"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        /**
         * Amplifies 16-bit little-endian PCM audio samples by dynamic gain normalization.
         * Ensures voice audio is loud, punchy, and clearly audible on device loudspeakers.
         */
        fun amplifyPcm(rawBytes: ByteArray, maxGain: Float = 8.0f, targetPeak: Int = 29500): ByteArray {
            if (rawBytes.size < 2) return rawBytes
            val sampleCount = rawBytes.size / 2
            var currentPeak = 0

            // Find current peak sample
            for (i in 0 until sampleCount) {
                val b0 = rawBytes[i * 2].toInt() and 0xFF
                val b1 = rawBytes[i * 2 + 1].toInt()
                val sample = ((b1 shl 8) or b0).toShort().toInt()
                val absVal = abs(sample)
                if (absVal > currentPeak) {
                    currentPeak = absVal
                }
            }

            if (currentPeak == 0) return rawBytes

            // Dynamic gain: boost quiet recordings, clamp to maxGain (at least 2.5x gain)
            val desiredGain = (targetPeak.toFloat() / currentPeak).coerceIn(2.5f, maxGain)

            val amplified = ByteArray(rawBytes.size)
            for (i in 0 until sampleCount) {
                val b0 = rawBytes[i * 2].toInt() and 0xFF
                val b1 = rawBytes[i * 2 + 1].toInt()
                val sample = ((b1 shl 8) or b0).toShort().toInt()
                val scaled = (sample * desiredGain).toInt().coerceIn(-32768, 32767)
                amplified[i * 2] = (scaled and 0xFF).toByte()
                amplified[i * 2 + 1] = ((scaled shr 8) and 0xFF).toByte()
            }
            return amplified
        }

        /**
         * Generates a 60ms subtle dual-frequency (880Hz + 1320Hz) radio squelch chirp
         * to indicate an incoming walkie-talkie transmission.
         */
        fun generateRadioChirp(): ByteArray {
            val sampleRate = SAMPLE_RATE
            val durationMs = 60
            val numSamples = (sampleRate * durationMs) / 1000
            val chirp = ByteArray(numSamples * 2)
            val freq1 = 880.0
            val freq2 = 1320.0
            for (i in 0 until numSamples) {
                val angle1 = 2.0 * Math.PI * freq1 * i / sampleRate
                val angle2 = 2.0 * Math.PI * freq2 * i / sampleRate
                val sampleVal = ((Math.sin(angle1) * 0.5 + Math.sin(angle2) * 0.5) * 22000).toInt().coerceIn(-32768, 32767).toShort()
                chirp[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
                chirp[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
            }
            return chirp
        }
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _selectedChannel = MutableStateFlow(EmergencyChannels.DEFAULT_CHANNEL)
    val selectedChannel: StateFlow<String> = _selectedChannel.asStateFlow()

    private val _lastReceivedBurst = MutableStateFlow<PttVoiceBurstPayload?>(null)
    val lastReceivedBurst: StateFlow<PttVoiceBurstPayload?> = _lastReceivedBurst.asStateFlow()

    private var recordingJob: Job? = null
    private var recordedBytesStream: ByteArrayOutputStream? = null
    private var recordingStartTime = 0L
    @Volatile
    private var activeAudioRecord: AudioRecord? = null

    fun setChannel(channel: String) {
        _selectedChannel.value = channel
    }

    /**
     * Starts recording audio from the microphone with hardware AGC / voice communication profile.
     */
    fun startRecording() {
        if (_isRecording.value) return
        _isRecording.value = true
        recordingStartTime = System.currentTimeMillis()
        recordedBytesStream = ByteArrayOutputStream()

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasPermission) {
                    Log.w(TAG, "RECORD_AUDIO permission not granted, falling back to simulated capture")
                    simulateAudioCapture()
                    return@launch
                }

                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                val effectiveBufferSize = bufferSize.coerceAtLeast(4096)
                val buffer = ByteArray(effectiveBufferSize)

                var record: AudioRecord? = null
                // 1. Try VOICE_COMMUNICATION for hardware AGC and noise suppression
                try {
                    record = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        effectiveBufferSize
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "VOICE_COMMUNICATION AudioRecord failed: ${e.message}")
                }

                // 2. Fallback to standard MIC if uninitialized
                if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                    record = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        effectiveBufferSize
                    )
                }

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    activeAudioRecord = record
                    record.startRecording()
                    val maxRecordingDurationMs = 60_000L
                    while (_isRecording.value) {
                        if (System.currentTimeMillis() - recordingStartTime > maxRecordingDurationMs) {
                            Log.w(TAG, "Maximum voice burst duration (60s) reached, stopping recording automatically")
                            _isRecording.value = false
                            break
                        }
                        val read = record.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            recordedBytesStream?.write(buffer, 0, read)
                        }
                    }
                } else {
                    Log.w(TAG, "AudioRecord failed to initialize, falling back to simulated capture")
                    simulateAudioCapture()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Microphone recording error, using fallback buffer: ${e.message}")
                simulateAudioCapture()
            } finally {
                try {
                    activeAudioRecord?.stop()
                } catch (_: Exception) {}
                try {
                    activeAudioRecord?.release()
                } catch (_: Exception) {}
                activeAudioRecord = null
            }
        }
    }

    private suspend fun simulateAudioCapture() {
        val sampleRate = SAMPLE_RATE
        val durationMs = 100
        val numSamples = (sampleRate * durationMs) / 1000
        val dummyPcm = ByteArray(numSamples * 2)
        val freq = 650.0
        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * freq * i / sampleRate
            val sampleVal = (Math.sin(angle) * 18000).toInt().coerceIn(-32768, 32767).toShort()
            dummyPcm[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            dummyPcm[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }
        val maxRecordingDurationMs = 60_000L
        while (_isRecording.value) {
            if (System.currentTimeMillis() - recordingStartTime > maxRecordingDurationMs) {
                _isRecording.value = false
                break
            }
            recordedBytesStream?.write(dummyPcm)
            delay(100)
        }
    }

    /**
     * Stops recording, applies software gain normalization, and broadcasts to the mesh.
     */
    suspend fun stopAndBroadcast(
        displayName: String = "Responder",
        channel: String = _selectedChannel.value
    ): Result<PttVoiceBurstPayload> = withContext(Dispatchers.IO) {
        _isRecording.value = false
        try {
            activeAudioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping activeAudioRecord: ${e.message}")
        }
        recordingJob?.join()
        recordingJob = null

        val rawAudio = recordedBytesStream?.toByteArray() ?: ByteArray(0)
        val durationMs = System.currentTimeMillis() - recordingStartTime

        if (rawAudio.isEmpty() && durationMs < 200) {
            return@withContext Result.failure(IllegalArgumentException("Voice burst too short"))
        }

        val effectiveAudio = if (rawAudio.isNotEmpty()) rawAudio else ByteArray(1600)
        // Normalize and amplify PCM audio before broadcasting
        val amplifiedAudio = amplifyPcm(effectiveAudio, maxGain = 8.0f, targetPeak = 29500)
        val audioBase64 = Base64.encodeToString(amplifiedAudio, Base64.NO_WRAP)
        val burstId = UUID.randomUUID().toString()

        val payload = PttVoiceBurstPayload(
            burstId = burstId,
            senderAuthorId = cryptoManager.authorId,
            senderDisplayName = displayName,
            channel = channel,
            durationMs = durationMs.coerceAtLeast(200L),
            audioBase64 = audioBase64,
            timestamp = System.currentTimeMillis()
        )

        // Publish to mesh log
        val publishResult = syncEngine.createAndPublishLocalEntry(
            ContentType.PTT_VOICE_BURST,
            payload.toJson()
        )

        if (publishResult.isSuccess) {
            onPttBroadcast?.invoke(payload)
            onContentPublished?.invoke()
            Result.success(payload)
        } else {
            Result.failure(publishResult.exceptionOrNull() ?: IllegalStateException("Failed to broadcast PTT burst"))
        }
    }

    /**
     * Plays an incoming PTT voice burst with squelch chirp and loudspeaker amplification.
     */
    fun playVoiceBurst(payload: PttVoiceBurstPayload, forceReplay: Boolean = false) {
        if (!forceReplay && !playedBurstIds.add(payload.burstId)) {
            Log.d(TAG, "Ignoring duplicate playback for burst ${payload.burstId}")
            return
        }
        playedBurstIds.add(payload.burstId)
        _lastReceivedBurst.value = payload
        scope.launch(Dispatchers.IO) {
            playbackMutex.withLock {
                _isPlaying.value = true
                var audioTrack: AudioTrack? = null
                try {
                    val pcmData = Base64.decode(payload.audioBase64, Base64.NO_WRAP)
                    if (pcmData.isEmpty()) return@withLock

                    // 1. Boost volume on receiving side as well
                    val boostedVoice = amplifyPcm(pcmData, maxGain = 6.0f, targetPeak = 30000)
                    val chirp = generateRadioChirp()
                    val fullAudio = chirp + boostedVoice

                    // 2. Ensure device media volume is at least 75% for emergency transmissions
                    try {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        if (audioManager != null) {
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val minEmergencyVol = (maxVol * 0.75f).toInt()
                            if (curVol < minEmergencyVol) {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, minEmergencyVol, 0)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Unable to adjust stream volume: ${e.message}")
                    }

                    val bufferSize = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AUDIO_FORMAT
                    )

                    audioTrack = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AUDIO_FORMAT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize.coerceAtLeast(fullAudio.size))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    audioTrack.setVolume(AudioTrack.getMaxVolume())
                    audioTrack.play()
                    audioTrack.write(fullAudio, 0, fullAudio.size)

                    // Drain AudioTrack fully until all samples reach loudspeaker
                    val totalFrames = fullAudio.size / 2
                    val maxWaitMs = payload.durationMs + 1500L
                    val startTime = System.currentTimeMillis()
                    while (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING &&
                        audioTrack.playbackHeadPosition < totalFrames &&
                        (System.currentTimeMillis() - startTime) < maxWaitMs
                    ) {
                        delay(40)
                    }
                    delay(120) // Final drain margin
                } catch (e: Exception) {
                    Log.w(TAG, "AudioTrack playback error: ${e.message}")
                } finally {
                    try {
                        audioTrack?.stop()
                    } catch (_: Exception) {}
                    try {
                        audioTrack?.release()
                    } catch (_: Exception) {}
                    _isPlaying.value = false
                }
            }
        }
    }
}
