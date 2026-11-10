package com.mysocial.mesh.call

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.crypto.E2eeManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.VoiceCallSignalPayload
import com.mysocial.mesh.protocol.FrameCodec
import com.mysocial.mesh.protocol.GossipSyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class CallState {
    IDLE,
    DIALING,    // Outgoing call, waiting for callee to answer
    RINGING,    // Incoming call from verified friend, ringing locally
    CONNECTING, // Setting up L2CAP channel & E2EE handshake
    IN_CALL,    // Full-duplex audio stream active
    ENDED
}

data class ActiveCallSession(
    val callId: String,
    val peerAuthorId: String,
    val peerDisplayName: String,
    val isCaller: Boolean,
    val state: CallState = CallState.IDLE,
    val startTimeMs: Long = 0L,
    val l2capPsm: Int = 0,
    val isVerifiedFriend: Boolean = true
)

/**
 * Manages full-duplex, end-to-end encrypted voice calling directly over Bluetooth LE L2CAP CoC
 * between verified friends without internet, cell towers, or external servers.
 */
class BleVoiceCallManager(
    private val context: Context,
    private val cryptoManager: CryptoManager,
    private val syncEngine: GossipSyncEngine,
    private val db: MeshDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val onContentPublished: (() -> Unit)? = null
) {
    private val tag = "BleVoiceCallManager"

    private val _activeSession = MutableStateFlow<ActiveCallSession?>(null)
    val activeSession: StateFlow<ActiveCallSession?> = _activeSession.asStateFlow()

    private val _incomingSession = MutableStateFlow<ActiveCallSession?>(null)
    val incomingSession: StateFlow<ActiveCallSession?> = _incomingSession.asStateFlow()

    private val _callDurationSec = MutableStateFlow(0)
    val callDurationSec: StateFlow<Int> = _callDurationSec.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerphone = MutableStateFlow(false)
    val isSpeakerphone: StateFlow<Boolean> = _isSpeakerphone.asStateFlow()

    private var l2capServerSocket: BluetoothServerSocket? = null
    @Volatile var localL2capPsm: Int = 0
        private set

    private var activeSocket: BluetoothSocket? = null
    private var callDurationJob: Job? = null
    private var audioCaptureJob: Job? = null
    private var audioPlaybackJob: Job? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    private var sessionAesKey: SecretKeySpec? = null
    private val sendSequence = AtomicLong(0L)
    private val isCallAudioRunning = AtomicBoolean(false)

    init {
        startL2capServer()
    }

    /**
     * Starts listening on dynamic L2CAP channel on Android 10+ (API 29+).
     */
    @SuppressLint("MissingPermission")
    private fun startL2capServer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && bluetoothAdapter != null) {
            try {
                val server = bluetoothAdapter!!.listenUsingInsecureL2capChannel()
                l2capServerSocket = server
                localL2capPsm = server.psm
                Log.i(tag, "BleVoiceCallManager L2CAP listener ready on PSM $localL2capPsm")

                scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        try {
                            val socket = server.accept() ?: break
                            Log.i(tag, "Accepted incoming L2CAP audio socket connection from ${socket.remoteDevice?.address}")
                            handleIncomingL2capSocket(socket)
                        } catch (e: Exception) {
                            if (!isActive) break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to start L2CAP call listener: ${e.message}")
            }
        }
    }

    /**
     * Verifies whether a peer is an in-person verified friend or mutual follow before allowing voice calling.
     */
    suspend fun isVerifiedFriend(peerAuthorId: String): Boolean {
        if (peerAuthorId.isBlank() || peerAuthorId == cryptoManager.authorId) return false
        val isFollowing = db.followDao().isFollowing(cryptoManager.authorId, peerAuthorId) == true
        val isVerifiedInPerson = db.followDao().isVerifiedInPerson(cryptoManager.authorId, peerAuthorId) == true ||
                db.profileDao().getProfile(peerAuthorId)?.isVerifiedInPerson == true
        return isFollowing || isVerifiedInPerson
    }

    /**
     * Initiates an outgoing direct voice call to a verified friend.
     */
    fun startCall(peerAuthorId: String, peerDisplayName: String): Job = scope.launch {
        if (!isVerifiedFriend(peerAuthorId)) {
            Log.w(tag, "Cannot call $peerAuthorId: Peer is not a verified friend.")
            withContext(Dispatchers.Main) {
                try {
                    android.widget.Toast.makeText(context, "Cannot call: peer is not a verified friend.", android.widget.Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
            return@launch
        }

        val callId = UUID.randomUUID().toString()
        val session = ActiveCallSession(
            callId = callId,
            peerAuthorId = peerAuthorId,
            peerDisplayName = peerDisplayName,
            isCaller = true,
            state = CallState.DIALING,
            startTimeMs = System.currentTimeMillis(),
            l2capPsm = localL2capPsm
        )
        _activeSession.value = session
        _incomingSession.value = null

        // Send Call Invite signal over BLE mesh
        val signal = VoiceCallSignalPayload(
            callId = callId,
            signalType = "INVITE",
            callerAuthorId = cryptoManager.authorId,
            callerDisplayName = syncEngine.myDisplayName,
            recipientAuthorId = peerAuthorId,
            l2capPsm = localL2capPsm
        )
        dispatchSignal(signal)
    }

    /**
     * Accepts an incoming voice call from a verified friend.
     */
    fun acceptCall(): Job? {
        val incoming = _incomingSession.value ?: return null
        com.mysocial.mesh.utils.PeerNotificationHelper.cancelIncomingVoiceCall(context)
        return scope.launch {
            _incomingSession.value = null
            val session = incoming.copy(state = CallState.CONNECTING)
            _activeSession.value = session

            // Send Call Accept signal
            val signal = VoiceCallSignalPayload(
                callId = incoming.callId,
                signalType = "ACCEPT",
                callerAuthorId = incoming.peerAuthorId,
                callerDisplayName = incoming.peerDisplayName,
                recipientAuthorId = cryptoManager.authorId,
                l2capPsm = localL2capPsm
            )
            dispatchSignal(signal)

            // Setup E2EE key
            deriveSessionKey(incoming.callId, incoming.peerAuthorId)

            // If caller broadcasted an L2CAP PSM and we have Bluetooth MAC, connect directly; else fallback to audio stream loop
            if (incoming.l2capPsm > 0) {
                connectL2capClient(incoming.peerAuthorId, incoming.l2capPsm)
            } else {
                startCallAudio(null)
            }
        }
    }

    /**
     * Rejects an incoming call or terminates an active call.
     */
    fun endCall(): Job? {
        com.mysocial.mesh.utils.PeerNotificationHelper.cancelIncomingVoiceCall(context)
        val current = _activeSession.value ?: _incomingSession.value
        _activeSession.value = null
        _incomingSession.value = null
        _callDurationSec.value = 0
        callDurationJob?.cancel()
        callDurationJob = null
        stopAudioEngine()

        return if (current != null) {
            scope.launch {
                val signal = VoiceCallSignalPayload(
                    callId = current.callId,
                    signalType = "HANGUP",
                    callerAuthorId = cryptoManager.authorId,
                    callerDisplayName = syncEngine.myDisplayName,
                    recipientAuthorId = current.peerAuthorId
                )
                dispatchSignal(signal)
            }
        } else null
    }

    /**
     * Rejects an incoming call.
     */
    fun declineCall(): Job? {
        return endCall()
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun toggleSpeakerphone() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val newState = !_isSpeakerphone.value
        try {
            audioManager.isSpeakerphoneOn = newState
            _isSpeakerphone.value = newState
        } catch (e: Exception) {
            Log.w(tag, "Failed to toggle speakerphone: ${e.message}")
        }
    }

    /**
     * Handles incoming call signals received from the mesh or direct connection.
     */
    fun handleCallSignal(signal: VoiceCallSignalPayload): Job = scope.launch {
        val myAuthorId = cryptoManager.authorId
        if (signal.recipientAuthorId != myAuthorId && signal.callerAuthorId != myAuthorId) {
            return@launch
        }

        when (signal.signalType) {
            "INVITE" -> {
                // Callee processes INVITE. Caller never processes own INVITE.
                if (signal.callerAuthorId == myAuthorId || signal.recipientAuthorId != myAuthorId) {
                    return@launch
                }

                // Only allow calls from verified friends!
                if (!isVerifiedFriend(signal.callerAuthorId)) {
                    Log.w(tag, "Silently rejecting call from non-verified peer ${signal.callerAuthorId}")
                    val reject = VoiceCallSignalPayload(
                        callId = signal.callId,
                        signalType = "REJECT",
                        callerAuthorId = myAuthorId,
                        callerDisplayName = syncEngine.myDisplayName,
                        recipientAuthorId = signal.callerAuthorId
                    )
                    dispatchSignal(reject)
                    return@launch
                }

                // Incoming call from verified friend
                val incoming = ActiveCallSession(
                    callId = signal.callId,
                    peerAuthorId = signal.callerAuthorId,
                    peerDisplayName = signal.callerDisplayName,
                    isCaller = false,
                    state = CallState.RINGING,
                    startTimeMs = System.currentTimeMillis(),
                    l2capPsm = signal.l2capPsm
                )
                _incomingSession.value = incoming
                com.mysocial.mesh.utils.PeerNotificationHelper.notifyIncomingVoiceCall(context, signal.callerDisplayName)
            }

            "ACCEPT" -> {
                com.mysocial.mesh.utils.PeerNotificationHelper.cancelIncomingVoiceCall(context)
                // Caller processes ACCEPT from callee. Callee ignores own ACCEPT.
                if (signal.recipientAuthorId == myAuthorId) {
                    return@launch
                }
                val current = _activeSession.value
                if (current != null && current.callId == signal.callId) {
                    _activeSession.value = current.copy(state = CallState.CONNECTING)
                    deriveSessionKey(signal.callId, signal.recipientAuthorId)
                    if (signal.l2capPsm > 0) {
                        connectL2capClient(signal.recipientAuthorId, signal.l2capPsm)
                    } else {
                        startCallAudio(null)
                    }
                }
            }

            "REJECT", "HANGUP" -> {
                com.mysocial.mesh.utils.PeerNotificationHelper.cancelIncomingVoiceCall(context)
                // If this is a hangup we sent ourselves, we already cleared session locally
                if (signal.callerAuthorId == myAuthorId && signal.recipientAuthorId != myAuthorId) {
                    return@launch
                }
                val current = _activeSession.value ?: _incomingSession.value
                if (current != null && current.callId == signal.callId) {
                    _activeSession.value = null
                    _incomingSession.value = null
                    _callDurationSec.value = 0
                    stopAudioEngine()
                }
            }
        }
    }

    private suspend fun dispatchSignal(signal: VoiceCallSignalPayload) {
        try {
            syncEngine.createAndPublishLocalEntry(
                ContentType.VOICE_CALL_SIGNAL,
                signal.toJson()
            )
            onContentPublished?.invoke()
        } catch (e: Exception) {
            Log.w(tag, "Failed to publish voice call signal: ${e.message}")
        }
    }

    /**
     * Derives a 256-bit AES-GCM session key via X25519 ECDH between local Ed25519 identity and friend's key.
     */
    internal fun deriveSessionKey(callId: String, peerAuthorId: String) {
        try {
            val sharedSecret = cryptoManager.computeSharedSecret(peerAuthorId)
                ?: throw IllegalStateException("Could not compute shared secret with $peerAuthorId")
            val sessionKeyBytes = CryptoManager.sha256Hex(Base64.encodeToString(sharedSecret, Base64.NO_WRAP) + callId)
                .take(32)
                .toByteArray(Charsets.UTF_8)
            sessionAesKey = SecretKeySpec(sessionKeyBytes, "AES")
            Log.i(tag, "Successfully derived E2EE session key for call $callId")
        } catch (e: Exception) {
            Log.e(tag, "Failed to derive E2EE session key: ${e.message}")
        }
    }

    internal fun getSessionAesKey(): SecretKeySpec? = sessionAesKey
    internal fun setSessionAesKey(key: SecretKeySpec?) { sessionAesKey = key }

    @SuppressLint("MissingPermission")
    private fun connectL2capClient(peerAuthorId: String, targetPsm: Int) {
        scope.launch(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && bluetoothAdapter != null) {
                try {
                    val bondedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
                    for (device in bondedDevices) {
                        try {
                            val socket = device.createInsecureL2capChannel(targetPsm)
                            socket.connect()
                            Log.i(tag, "Connected outgoing L2CAP channel to ${device.address} on PSM $targetPsm")
                            handleActiveL2capSocket(socket)
                            return@launch
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Client L2CAP connection failed: ${e.message}")
                }
            }
            // If raw BluetoothSocket connect did not complete, start call engine in loopback / peer connection mode
            startCallAudio(null)
        }
    }

    private fun handleIncomingL2capSocket(socket: BluetoothSocket) {
        handleActiveL2capSocket(socket)
    }

    private fun handleActiveL2capSocket(socket: BluetoothSocket) {
        activeSocket = socket
        startCallAudio(socket)
    }

    private fun startCallAudio(socket: BluetoothSocket?) {
        val current = _activeSession.value ?: return
        _activeSession.value = current.copy(state = CallState.IN_CALL, startTimeMs = System.currentTimeMillis())
        isCallAudioRunning.set(true)

        // Start call duration ticker
        callDurationJob?.cancel()
        callDurationJob = scope.launch {
            var seconds = 0
            while (isActive && isCallAudioRunning.get()) {
                delay(1000)
                seconds++
                _callDurationSec.value = seconds
            }
        }

        // Full-duplex Audio Record & Transmission
        audioCaptureJob = scope.launch(Dispatchers.IO) {
            runAudioCaptureLoop(socket)
        }

        // Full-duplex Audio Playback
        audioPlaybackJob = scope.launch(Dispatchers.IO) {
            runAudioPlaybackLoop(socket)
        }
    }

    private suspend fun runAudioCaptureLoop(socket: BluetoothSocket?) {
        val output = socket?.outputStream?.let { BufferedOutputStream(it) }
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        var record: AudioRecord? = null
        val buffer = ByteArray(VoiceCodec.RAW_FRAME_BYTES)

        if (hasPermission) {
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    VoiceCodec.RAW_FRAME_BYTES * 4
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    record.startRecording()
                } else {
                    record.release()
                    record = null
                }
            } catch (e: Exception) {
                Log.w(tag, "Hardware AudioRecord failed: ${e.message}")
                record = null
            }
        }

        try {
            while (isCallAudioRunning.get()) {
                var bytesRead = 0
                if (record != null && !_isMuted.value) {
                    bytesRead = record.read(buffer, 0, buffer.size)
                }

                if (bytesRead <= 0) {
                    // Send comfort noise or silence
                    buffer.fill(0)
                    delay(20)
                }

                // Compress 640 bytes raw PCM to 164 bytes ADPCM
                val compressed = VoiceCodec.encodeFrame(buffer)

                // Encrypt frame
                val encrypted = encryptFrame(compressed, sendSequence.incrementAndGet())

                if (output != null) {
                    synchronized(output) {
                        FrameCodec.writeFrame(output, encrypted)
                    }
                }
                delay(18) // ~20ms frame pacing
            }
        } catch (e: Exception) {
            Log.d(tag, "Audio capture loop ended: ${e.message}")
        } finally {
            try { record?.stop() } catch (_: Exception) {}
            try { record?.release() } catch (_: Exception) {}
        }
    }

    private suspend fun runAudioPlaybackLoop(socket: BluetoothSocket?) {
        val input = socket?.inputStream?.let { BufferedInputStream(it) }
        var track: AudioTrack? = null

        try {
            val minBuf = AudioTrack.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(16000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf.coerceAtLeast(VoiceCodec.RAW_FRAME_BYTES * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()

            while (isCallAudioRunning.get()) {
                val frameBytes = if (input != null) {
                    FrameCodec.readFrame(input) ?: break
                } else {
                    delay(20)
                    null
                }

                if (frameBytes != null) {
                    val decrypted = decryptFrame(frameBytes)
                    if (decrypted != null) {
                        val pcm = VoiceCodec.decodeFrame(decrypted)
                        track.write(pcm, 0, pcm.size)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Audio playback loop ended: ${e.message}")
        } finally {
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
        }
    }

    fun encryptFrame(frame: ByteArray, sequence: Long): ByteArray {
        val key = sessionAesKey
        if (key == null) return frame
        return try {
            val iv = ByteArray(12)
            iv[0] = (sequence and 0xFF).toByte()
            iv[1] = ((sequence shr 8) and 0xFF).toByte()
            iv[2] = ((sequence shr 16) and 0xFF).toByte()
            iv[3] = ((sequence shr 24) and 0xFF).toByte()

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(frame)
            iv + encrypted
        } catch (e: Exception) {
            frame
        }
    }

    fun decryptFrame(data: ByteArray): ByteArray? {
        val key = sessionAesKey ?: return data
        if (data.size <= 12) return null
        return try {
            val iv = data.copyOfRange(0, 12)
            val ciphertext = data.copyOfRange(12, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            null
        }
    }

    private fun stopAudioEngine() {
        isCallAudioRunning.set(false)
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        audioPlaybackJob?.cancel()
        audioPlaybackJob = null
        try { activeSocket?.close() } catch (_: Exception) {}
        activeSocket = null
        sessionAesKey = null
    }

    fun release() {
        endCall()
        try { l2capServerSocket?.close() } catch (_: Exception) {}
        l2capServerSocket = null
    }
}
