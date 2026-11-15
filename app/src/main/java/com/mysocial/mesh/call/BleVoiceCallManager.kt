package com.mysocial.mesh.call

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
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
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.mysocial.mesh.utils.OfflineApkServer

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
    val isVerifiedFriend: Boolean = true,
    val audioPort: Int = 0,
    val hostAddress: String = ""
)

interface CallAudioStream {
    val inputStream: InputStream
    val outputStream: OutputStream
    fun close()
}

class BluetoothCallAudioStream(private val socket: BluetoothSocket) : CallAudioStream {
    override val inputStream: InputStream get() = socket.inputStream
    override val outputStream: OutputStream get() = socket.outputStream
    override fun close() = socket.close()
}

class TcpCallAudioStream(private val socket: Socket) : CallAudioStream {
    override val inputStream: InputStream get() = socket.inputStream
    override val outputStream: OutputStream get() = socket.outputStream
    override fun close() = socket.close()
}

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

    var onVoiceCallBroadcast: ((VoiceCallSignalPayload) -> Unit)? = null

    private var l2capServerSocket: BluetoothServerSocket? = null
    @Volatile var localL2capPsm: Int = 0
        private set

    private var tcpAudioServerSocket: ServerSocket? = null
    @Volatile var localAudioPort: Int = 0
        private set

    @Volatile private var activeAudioStream: CallAudioStream? = null
    private var callDurationJob: Job? = null
    private var audioCaptureJob: Job? = null
    private var audioPlaybackJob: Job? = null
    private var incomingRingTimeoutJob: Job? = null
    private var outgoingDialTimeoutJob: Job? = null

    // Bounded set of recently terminated/handled call IDs to prevent stale replay and ghost ringing
    private val terminatedCallIds = java.util.Collections.synchronizedSet(object : LinkedHashSet<String>() {
        override fun add(element: String): Boolean {
            if (size > 200) {
                val iterator = iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            return super.add(element)
        }
    })

    companion object {
        private const val MAX_SIGNAL_AGE_MS = 30_000L
        private const val CALL_RING_TIMEOUT_MS = 30_000L
        private const val CALL_DIAL_TIMEOUT_MS = 30_000L
        private const val DEFAULT_AUDIO_PORT = 42531
        private const val ADB_REVERSE_AUDIO_PORT = 42536
        private const val ADB_EMULATOR_TO_PHONE_AUDIO_PORT = 42541
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    private var sessionAesKey: SecretKeySpec? = null
    private val sendSequence = AtomicLong(0L)
    private val isCallAudioRunning = AtomicBoolean(false)

    init {
        startL2capServer()
        startTcpAudioServer()
    }

    @Synchronized
    private fun setActiveStream(stream: CallAudioStream): Boolean {
        if (activeAudioStream != null) {
            return false
        }
        activeAudioStream = stream
        return true
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
                    var currentServer = server
                    while (isActive) {
                        try {
                            val socket = currentServer.accept() ?: break
                            Log.i(tag, "Accepted incoming L2CAP audio socket connection from ${socket.remoteDevice?.address}")
                            handleIncomingL2capSocket(socket)
                        } catch (e: Exception) {
                            if (!isActive) break
                            Log.d(tag, "L2CAP audio server socket closed or error: ${e.message}")
                            try { currentServer.close() } catch (_: Exception) {}
                            delay(5000)
                            if (!isActive) break
                            try {
                                if (bluetoothAdapter?.isEnabled == true) {
                                    val newServer = bluetoothAdapter!!.listenUsingInsecureL2capChannel()
                                    l2capServerSocket = newServer
                                    localL2capPsm = newServer.psm
                                    currentServer = newServer
                                } else {
                                    delay(5000)
                                }
                            } catch (_: Exception) {
                                delay(5000)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to start L2CAP call listener: ${e.message}")
            }
        }
    }

    /**
     * Starts listening for direct TCP audio streams over Wi-Fi / Local Network.
     */
    private fun startTcpAudioServer() {
        try {
            val server = try {
                ServerSocket(DEFAULT_AUDIO_PORT)
            } catch (_: Exception) {
                ServerSocket(0)
            }
            tcpAudioServerSocket = server
            localAudioPort = server.localPort
            Log.i(tag, "BleVoiceCallManager TCP audio listener ready on port $localAudioPort")

            scope.launch(Dispatchers.IO) {
                while (isActive && !server.isClosed) {
                    try {
                        val socket = server.accept()
                        socket.tcpNoDelay = true
                        socket.soTimeout = 0
                        Log.i(tag, "Accepted incoming TCP audio socket connection from ${socket.inetAddress?.hostAddress}:${socket.port}")
                        handleIncomingTcpSocket(socket)
                    } catch (e: Exception) {
                        if (!isActive || server.isClosed) break
                        Log.w(tag, "TCP audio server accept error: ${e.message}")
                        delay(1000)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to start TCP audio server: ${e.message}")
        }
    }

    private fun handleIncomingTcpSocket(socket: Socket) {
        val stream = TcpCallAudioStream(socket)
        if (setActiveStream(stream)) {
            Log.i(tag, "Accepted TCP audio stream from ${socket.inetAddress?.hostAddress}:${socket.port}")
            startCallAudio(stream)
        } else {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleIncomingL2capSocket(socket: BluetoothSocket) {
        val stream = BluetoothCallAudioStream(socket)
        if (setActiveStream(stream)) {
            Log.i(tag, "Accepted L2CAP audio stream from ${socket.remoteDevice?.address}")
            startCallAudio(stream)
        } else {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    /**
     * Verifies whether a peer is an in-person verified friend, mutual follow, or existing chat contact before allowing voice calling.
     */
    suspend fun isVerifiedFriend(peerAuthorId: String): Boolean {
        if (peerAuthorId.isBlank() || peerAuthorId == cryptoManager.authorId) return false
        val isFollowing = db.followDao().isFollowing(cryptoManager.authorId, peerAuthorId) == true
        val isVerifiedInPerson = db.followDao().isVerifiedInPerson(cryptoManager.authorId, peerAuthorId) == true ||
                db.profileDao().getProfile(peerAuthorId)?.isVerifiedInPerson == true
        val hasDirectMessage = db.directMessageDao().hasConversationWith(peerAuthorId) > 0
        return isFollowing || isVerifiedInPerson || hasDirectMessage
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
        val localIp = OfflineApkServer.findLocalIpAddress() ?: ""
        val session = ActiveCallSession(
            callId = callId,
            peerAuthorId = peerAuthorId,
            peerDisplayName = peerDisplayName,
            isCaller = true,
            state = CallState.DIALING,
            startTimeMs = System.currentTimeMillis(),
            l2capPsm = localL2capPsm,
            audioPort = localAudioPort,
            hostAddress = localIp
        )
        _activeSession.value = session
        _incomingSession.value = null

        // Derive E2EE session key upfront for caller
        deriveSessionKey(callId, peerAuthorId)

        outgoingDialTimeoutJob?.cancel()
        outgoingDialTimeoutJob = scope.launch {
            delay(CALL_DIAL_TIMEOUT_MS)
            if (_activeSession.value?.callId == callId && _activeSession.value?.state == CallState.DIALING) {
                Log.i(tag, "Outgoing call $callId timed out after 30s with no answer.")
                endCall()
                withContext(Dispatchers.Main) {
                    try {
                        android.widget.Toast.makeText(context, "No answer", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                }
            }
        }

        // Send Call Invite signal over BLE mesh & TCP
        val signal = VoiceCallSignalPayload(
            callId = callId,
            signalType = "INVITE",
            callerAuthorId = cryptoManager.authorId,
            callerDisplayName = syncEngine.myDisplayName,
            recipientAuthorId = peerAuthorId,
            l2capPsm = localL2capPsm,
            audioPort = localAudioPort,
            hostAddress = localIp
        )
        dispatchSignal(signal)
    }

    /**
     * Accepts an incoming voice call from a verified friend.
     */
    fun acceptCall(): Job? {
        val incoming = _incomingSession.value ?: return null
        com.mysocial.mesh.utils.PeerNotificationHelper.cancelIncomingVoiceCall(context)
        incomingRingTimeoutJob?.cancel()
        incomingRingTimeoutJob = null
        return scope.launch {
            _incomingSession.value = null
            val session = incoming.copy(state = CallState.CONNECTING)
            _activeSession.value = session

            val localIp = OfflineApkServer.findLocalIpAddress() ?: ""
            // Send Call Accept signal
            val signal = VoiceCallSignalPayload(
                callId = incoming.callId,
                signalType = "ACCEPT",
                callerAuthorId = incoming.peerAuthorId,
                callerDisplayName = incoming.peerDisplayName,
                recipientAuthorId = cryptoManager.authorId,
                l2capPsm = localL2capPsm,
                audioPort = localAudioPort,
                hostAddress = localIp
            )
            dispatchSignal(signal)

            // Setup E2EE key
            deriveSessionKey(incoming.callId, incoming.peerAuthorId)

            connectAudioStream(
                peerAuthorId = incoming.peerAuthorId,
                targetHost = incoming.hostAddress,
                targetPort = incoming.audioPort,
                targetL2capPsm = incoming.l2capPsm
            )
        }
    }

    /**
     * Rejects an incoming call or terminates an active call.
     */
    fun endCall(): Job? {
        com.mysocial.mesh.utils.PeerNotificationHelper.cancelIncomingVoiceCall(context)
        incomingRingTimeoutJob?.cancel()
        incomingRingTimeoutJob = null
        outgoingDialTimeoutJob?.cancel()
        outgoingDialTimeoutJob = null
        val current = _activeSession.value ?: _incomingSession.value
        current?.callId?.let { terminatedCallIds.add(it) }
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
        setSpeakerphoneInternal(audioManager, newState)
        _isSpeakerphone.value = newState
    }

    private fun setSpeakerphoneInternal(audioManager: AudioManager, enable: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (enable) {
                    val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        audioManager.setCommunicationDevice(speakerDevice)
                    }
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = enable
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to set speakerphone to $enable: ${e.message}")
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

        // 1. Freshness check: Call signals older than 30s or > 15s in future are stale/invalid.
        val now = System.currentTimeMillis()
        val signalAgeMs = now - signal.timestamp
        if (signalAgeMs > MAX_SIGNAL_AGE_MS || signalAgeMs < -15_000L) {
            Log.d(tag, "Ignoring stale/clock-skewed call signal ${signal.signalType} (age: ${signalAgeMs}ms, callId: ${signal.callId})")
            return@launch
        }

        // 2. Terminated call check: If call was already ended/rejected, drop duplicate/delayed signals.
        if (terminatedCallIds.contains(signal.callId)) {
            Log.d(tag, "Ignoring signal ${signal.signalType} for already terminated call ${signal.callId}")
            return@launch
        }

        when (signal.signalType) {
            "INVITE" -> {
                // Callee processes INVITE. Caller never processes own INVITE.
                if (signal.callerAuthorId == myAuthorId || signal.recipientAuthorId != myAuthorId) {
                    return@launch
                }

                // If this specific call is already ringing or active, ignore duplicate invite
                if (_incomingSession.value?.callId == signal.callId || _activeSession.value?.callId == signal.callId) {
                    return@launch
                }

                // If already in an active call, reject as busy
                if (_activeSession.value != null && _activeSession.value?.state != CallState.IDLE) {
                    Log.i(tag, "Rejecting incoming call from ${signal.callerAuthorId}: device busy")
                    val busyReject = VoiceCallSignalPayload(
                        callId = signal.callId,
                        signalType = "REJECT",
                        callerAuthorId = myAuthorId,
                        callerDisplayName = syncEngine.myDisplayName,
                        recipientAuthorId = signal.callerAuthorId
                    )
                    dispatchSignal(busyReject)
                    terminatedCallIds.add(signal.callId)
                    return@launch
                }

                // Only allow calls from verified friends / contacts!
                if (!isVerifiedFriend(signal.callerAuthorId)) {
                    Log.w(tag, "Silently rejecting call from non-verified peer ${signal.callerAuthorId}")
                    terminatedCallIds.add(signal.callId)
                    // Only dispatch reject if signal is very fresh (< 15s) to avoid spamming the mesh
                    if (signalAgeMs in -15_000L..15_000L) {
                        val reject = VoiceCallSignalPayload(
                            callId = signal.callId,
                            signalType = "REJECT",
                            callerAuthorId = myAuthorId,
                            callerDisplayName = syncEngine.myDisplayName,
                            recipientAuthorId = signal.callerAuthorId
                        )
                        dispatchSignal(reject)
                    }
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
                    l2capPsm = signal.l2capPsm,
                    audioPort = signal.audioPort,
                    hostAddress = signal.hostAddress
                )
                _incomingSession.value = incoming
                com.mysocial.mesh.utils.PeerNotificationHelper.notifyIncomingVoiceCall(context, signal.callerDisplayName)

                // Callee 30s ringing timeout
                incomingRingTimeoutJob?.cancel()
                incomingRingTimeoutJob = scope.launch {
                    delay(CALL_RING_TIMEOUT_MS)
                    if (_incomingSession.value?.callId == signal.callId) {
                        Log.i(tag, "Incoming call ${signal.callId} timed out after 30s ringing.")
                        _incomingSession.value = null
                        terminatedCallIds.add(signal.callId)
                        com.mysocial.mesh.utils.PeerNotificationHelper.cancelIncomingVoiceCall(context)
                    }
                }
            }

            "ACCEPT" -> {
                com.mysocial.mesh.utils.PeerNotificationHelper.cancelIncomingVoiceCall(context)
                outgoingDialTimeoutJob?.cancel()
                outgoingDialTimeoutJob = null

                // Caller processes ACCEPT from callee. Callee ignores own ACCEPT.
                if (signal.recipientAuthorId == myAuthorId) {
                    return@launch
                }
                val current = _activeSession.value
                if (current != null && current.callId == signal.callId) {
                    val targetState = if (current.state == CallState.IN_CALL || activeAudioStream != null) {
                        CallState.IN_CALL
                    } else {
                        CallState.CONNECTING
                    }
                    _activeSession.value = current.copy(
                        state = targetState,
                        audioPort = signal.audioPort,
                        hostAddress = signal.hostAddress,
                        l2capPsm = signal.l2capPsm
                    )
                    deriveSessionKey(signal.callId, signal.recipientAuthorId)
                    if (targetState != CallState.IN_CALL && activeAudioStream == null) {
                        connectAudioStream(
                            peerAuthorId = signal.recipientAuthorId,
                            targetHost = signal.hostAddress,
                            targetPort = signal.audioPort,
                            targetL2capPsm = signal.l2capPsm
                        )
                    }
                }
            }

            "REJECT", "HANGUP" -> {
                com.mysocial.mesh.utils.PeerNotificationHelper.cancelIncomingVoiceCall(context)
                incomingRingTimeoutJob?.cancel()
                incomingRingTimeoutJob = null
                outgoingDialTimeoutJob?.cancel()
                outgoingDialTimeoutJob = null
                terminatedCallIds.add(signal.callId)

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
            onVoiceCallBroadcast?.invoke(signal)
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
    private fun connectAudioStream(
        peerAuthorId: String,
        targetHost: String,
        targetPort: Int,
        targetL2capPsm: Int
    ) {
        scope.launch(Dispatchers.IO) {
            if (activeAudioStream != null) return@launch

            // Deterministic role assignment to avoid dual-connect collisions:
            // Peer with greater authorId is the primary connector (dials outbound immediately).
            // Peer with smaller authorId waits up to 2.5s for inbound connection before dialing fallback.
            val isConnector = cryptoManager.authorId > peerAuthorId
            if (!isConnector) {
                var waitCount = 0
                while (activeAudioStream == null && waitCount < 15 && _activeSession.value?.state == CallState.CONNECTING) {
                    delay(200)
                    waitCount++
                }
                if (activeAudioStream != null) {
                    Log.i(tag, "Inbound audio stream established as listener; skipping outbound dial.")
                    return@launch
                }
                Log.d(tag, "Listener wait timeout reached (2.5s); attempting outbound connection as fallback.")
            }

            // 1. Try Bluetooth L2CAP if available and permitted
            if (targetL2capPsm > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && bluetoothAdapter != null) {
                try {
                    val bondedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
                    for (device in bondedDevices) {
                        try {
                            val btSocket = device.createInsecureL2capChannel(targetL2capPsm)
                            btSocket.connect()
                            Log.i(tag, "Connected outgoing L2CAP channel to ${device.address} on PSM $targetL2capPsm")
                            val stream = BluetoothCallAudioStream(btSocket)
                            if (setActiveStream(stream)) {
                                startCallAudio(stream)
                                return@launch
                            } else {
                                btSocket.close()
                            }
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.d(tag, "Client L2CAP connection failed: ${e.message}")
                }
            }

            // 2. Candidate TCP endpoints
            val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                    Build.MODEL.contains("google_sdk", ignoreCase = true) ||
                    Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
                    Build.HARDWARE.contains("ranchu", ignoreCase = true)

            val localIp = OfflineApkServer.findLocalIpAddress() ?: ""
            val candidateHosts = mutableSetOf<Pair<String, Int>>()

            if (isEmulator) {
                candidateHosts.add(Pair("10.0.2.2", ADB_EMULATOR_TO_PHONE_AUDIO_PORT))
                if (targetHost.isNotBlank() && !targetHost.startsWith("10.0.2.") && targetHost != "127.0.0.1") {
                    val port = if (targetPort > 0) targetPort else DEFAULT_AUDIO_PORT
                    candidateHosts.add(Pair(targetHost, port))
                }
            } else {
                candidateHosts.add(Pair("127.0.0.1", ADB_REVERSE_AUDIO_PORT))
                if (targetHost.isNotBlank() && targetHost != "127.0.0.1" && targetHost != localIp && !targetHost.startsWith("10.0.2.")) {
                    val port = if (targetPort > 0) targetPort else DEFAULT_AUDIO_PORT
                    candidateHosts.add(Pair(targetHost, port))
                }
            }

            for ((host, port) in candidateHosts) {
                if (activeAudioStream != null) break
                try {
                    val s = Socket()
                    s.tcpNoDelay = true
                    s.soTimeout = 0
                    s.connect(InetSocketAddress(host, port), 2500)
                    Log.i(tag, "Connected outgoing TCP audio socket to $host:$port")
                    val stream = TcpCallAudioStream(s)
                    if (setActiveStream(stream)) {
                        startCallAudio(stream)
                        return@launch
                    } else {
                        s.close()
                    }
                } catch (e: Exception) {
                    Log.d(tag, "Audio connect to $host:$port failed: ${e.message}")
                }
            }

            // 3. Wait up to 8s for incoming connection if outbound hasn't connected
            var waitCount = 0
            while (activeAudioStream == null && waitCount < 40 && _activeSession.value?.state == CallState.CONNECTING) {
                delay(200)
                waitCount++
            }

            // 4. Fallback if still null
            if (activeAudioStream == null && _activeSession.value?.state == CallState.CONNECTING) {
                Log.w(tag, "No audio stream connected after waiting; falling back to local audio")
                startCallAudio(null)
            }
        }
    }

    private fun startCallAudio(stream: CallAudioStream?) {
        outgoingDialTimeoutJob?.cancel()
        outgoingDialTimeoutJob = null
        incomingRingTimeoutJob?.cancel()
        incomingRingTimeoutJob = null
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
            runAudioCaptureLoop(stream)
        }

        // Full-duplex Audio Playback
        audioPlaybackJob = scope.launch(Dispatchers.IO) {
            runAudioPlaybackLoop(stream)
        }
    }

    private suspend fun runAudioCaptureLoop(stream: CallAudioStream?) {
        val output = stream?.outputStream?.let { BufferedOutputStream(it) }
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        var record: AudioRecord? = null
        val buffer = ByteArray(VoiceCodec.RAW_FRAME_BYTES)

        if (hasPermission) {
            val minBuf = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(VoiceCodec.RAW_FRAME_BYTES * 4)

            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    record = null
                }
            } catch (e: Exception) {
                Log.w(tag, "VOICE_COMMUNICATION AudioRecord failed: ${e.message}")
                record = null
            }

            if (record == null) {
                try {
                    record = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        16000,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuf
                    )
                    if (record.state != AudioRecord.STATE_INITIALIZED) {
                        record.release()
                        record = null
                    }
                } catch (e: Exception) {
                    Log.w(tag, "MIC AudioRecord fallback failed: ${e.message}")
                    record = null
                }
            }

            try {
                record?.startRecording()
            } catch (e: Exception) {
                Log.w(tag, "Failed to start AudioRecord: ${e.message}")
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
            }
        } catch (e: Exception) {
            Log.d(tag, "Audio capture loop ended: ${e.message}")
        } finally {
            try { record?.stop() } catch (_: Exception) {}
            try { record?.release() } catch (_: Exception) {}
        }
    }

    private suspend fun runAudioPlaybackLoop(stream: CallAudioStream?) {
        val input = stream?.inputStream?.let { BufferedInputStream(it) }
        var track: AudioTrack? = null

        val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("google_sdk", ignoreCase = true) ||
                Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
                Build.HARDWARE.contains("ranchu", ignoreCase = true)

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            try {
                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager?.let { setSpeakerphoneInternal(it, _isSpeakerphone.value) }

                // Ensure volume is adequate for hearing
                val streamType = if (isEmulator || _isSpeakerphone.value) AudioManager.STREAM_MUSIC else AudioManager.STREAM_VOICE_CALL
                val maxVol = audioManager?.getStreamMaxVolume(streamType) ?: 0
                val curVol = audioManager?.getStreamVolume(streamType) ?: 0
                if (maxVol > 0 && curVol < (maxVol * 0.7f).toInt()) {
                    audioManager?.setStreamVolume(streamType, (maxVol * 0.75f).toInt(), 0)
                }
            } catch (_: Exception) {}

            val minBuf = AudioTrack.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val usage = if (isEmulator || _isSpeakerphone.value) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
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

            track.setVolume(AudioTrack.getMaxVolume())
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
                        val amplified = com.mysocial.mesh.emergency.PttWalkieTalkieManager.amplifyPcm(pcm, maxGain = 6.0f, targetPeak = 30000)
                        track.write(amplified, 0, amplified.size)
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
            if (data.size == VoiceCodec.ENCODED_FRAME_BYTES) data else null
        }
    }

    private fun stopAudioEngine() {
        isCallAudioRunning.set(false)
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        audioPlaybackJob?.cancel()
        audioPlaybackJob = null
        try { activeAudioStream?.close() } catch (_: Exception) {}
        activeAudioStream = null
        sessionAesKey = null
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.let { setSpeakerphoneInternal(it, false) }
        } catch (_: Exception) {}
    }

    fun release() {
        incomingRingTimeoutJob?.cancel()
        incomingRingTimeoutJob = null
        outgoingDialTimeoutJob?.cancel()
        outgoingDialTimeoutJob = null
        endCall()
        try { l2capServerSocket?.close() } catch (_: Exception) {}
        l2capServerSocket = null
        try { tcpAudioServerSocket?.close() } catch (_: Exception) {}
        tcpAudioServerSocket = null
    }
}
