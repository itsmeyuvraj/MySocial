package com.mysocial.mesh.lora

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class LoraConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SIMULATED
}

data class LoraBridgeInfo(
    val frequency: String = "915 MHz (US/Americas)",
    val deviceModel: String = "Meshtastic Heltec v3 / LilyGO T-Beam",
    val deviceAddress: String? = null,
    val isEnabled: Boolean = true,
    val batteryPercent: Int = 92,
    val snr: Float = 9.5f,
    val rssi: Int = -68
)

/**
 * Manages the Bluetooth Serial Port Profile (SPP) / USB-OTG connection to Meshtastic-compatible
 * Sub-GHz LoRa companion hardware nodes (Heltec Wireless Stick Lite v3, LilyGO T-Beam ESP32-S3).
 *
 * Bridges long-distance (5-15 km) emergency alerts and short text gossips across isolated
 * smartphone mesh clusters without internet, cell towers, or external infrastructure.
 */
class LoraCompanionBridge private constructor(private val context: Context) {

    private val tag = "LoraCompanionBridge"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(LoraConnectionState.DISCONNECTED)
    val connectionState: StateFlow<LoraConnectionState> = _connectionState.asStateFlow()

    private val _bridgeInfo = MutableStateFlow(LoraBridgeInfo())
    val bridgeInfo: StateFlow<LoraBridgeInfo> = _bridgeInfo.asStateFlow()

    private val _relayedPacketsCount = MutableStateFlow(0)
    val relayedPacketsCount: StateFlow<Int> = _relayedPacketsCount.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readerJob: Job? = null

    var onPacketReceived: ((LoraPacket) -> Unit)? = null

    fun setFrequency(newFrequency: String) {
        _bridgeInfo.value = _bridgeInfo.value.copy(frequency = newFrequency)
    }

    fun setEnabled(enabled: Boolean) {
        _bridgeInfo.value = _bridgeInfo.value.copy(isEnabled = enabled)
        if (!enabled && _connectionState.value != LoraConnectionState.DISCONNECTED) {
            disconnect()
        }
    }

    /**
     * Connects to a paired hardware companion node via Bluetooth SPP.
     */
    suspend fun connectBluetoothSpp(deviceAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnect()
            _connectionState.value = LoraConnectionState.CONNECTING

            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(IllegalStateException("Bluetooth adapter not available"))

            val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)
            val sppSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            sppSocket.connect()

            socket = sppSocket
            inputStream = sppSocket.inputStream
            outputStream = sppSocket.outputStream

            _connectionState.value = LoraConnectionState.CONNECTED
            _bridgeInfo.value = _bridgeInfo.value.copy(
                deviceAddress = deviceAddress,
                deviceModel = device.name ?: "Heltec v3 / LilyGO"
            )

            startReaderLoop()
            Log.i(tag, "Connected to LoRa companion node: ${device.name} ($deviceAddress)")
            Result.success(Unit)
        } catch (e: Exception) {
            disconnect()
            Log.e(tag, "Failed to connect to LoRa device: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Activates simulated mode for testing or emulation without physical LoRa hardware.
     */
    fun connectSimulated(model: String = "Heltec v3 (Simulated IoT Seed)"): Result<Unit> {
        disconnect()
        _connectionState.value = LoraConnectionState.SIMULATED
        _bridgeInfo.value = _bridgeInfo.value.copy(
            deviceModel = model,
            deviceAddress = "SIM:LORA:SEED"
        )
        Log.i(tag, "LoRa companion bridge running in SIMULATED mode ($model)")
        return Result.success(Unit)
    }

    fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        try { inputStream?.close() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inputStream = null
        outputStream = null
        socket = null
        _connectionState.value = LoraConnectionState.DISCONNECTED
    }

    /**
     * Broadcasts a high-priority emergency SOS beacon over Sub-GHz LoRa.
     */
    suspend fun broadcastSosAlert(
        authorId: String,
        text: String,
        entryHash: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!_bridgeInfo.value.isEnabled) return@withContext false
        if (_connectionState.value == LoraConnectionState.DISCONNECTED) return@withContext false

        try {
            val json = JSONObject().apply {
                put("type", "SOS")
                put("author", authorId)
                put("hash", entryHash)
                put("text", text.take(160))
                put("time", System.currentTimeMillis())
            }
            val payload = json.toString().toByteArray(Charsets.UTF_8)
            val packet = LoraPacket(
                packetType = LoraPacket.TYPE_SOS_ALERT,
                hopCount = 0,
                payload = payload
            )
            val raw = packet.encode()
            sendRawFrame(raw)
            _relayedPacketsCount.value += 1
            Log.i(tag, "Broadcast SOS alert over LoRa Sub-GHz (${raw.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error sending LoRa SOS alert: ${e.message}", e)
            false
        }
    }

    /**
     * Broadcasts a short text post over Sub-GHz LoRa.
     */
    suspend fun broadcastShortPost(
        authorId: String,
        text: String,
        entryHash: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!_bridgeInfo.value.isEnabled) return@withContext false
        if (_connectionState.value == LoraConnectionState.DISCONNECTED) return@withContext false

        try {
            val json = JSONObject().apply {
                put("type", "POST")
                put("author", authorId)
                put("hash", entryHash)
                put("text", text.take(160))
                put("time", System.currentTimeMillis())
            }
            val payload = json.toString().toByteArray(Charsets.UTF_8)
            val packet = LoraPacket(
                packetType = LoraPacket.TYPE_SHORT_POST,
                hopCount = 0,
                payload = payload
            )
            val raw = packet.encode()
            sendRawFrame(raw)
            _relayedPacketsCount.value += 1
            Log.i(tag, "Broadcast short post over LoRa (${raw.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error sending LoRa post: ${e.message}", e)
            false
        }
    }

    /**
     * Ingests a raw LoRa byte frame, decodes it, and triggers processing.
     * Accessible for testing or simulated companion feeds.
     */
    fun ingestRawFrame(rawBytes: ByteArray, snr: Float = 9.5f, rssi: Int = -70): Boolean {
        val packet = LoraPacket.decode(rawBytes, snr = snr, rssi = rssi) ?: return false
        _relayedPacketsCount.value += 1
        _bridgeInfo.value = _bridgeInfo.value.copy(snr = snr, rssi = rssi)
        onPacketReceived?.invoke(packet)
        return true
    }

    private fun sendRawFrame(bytes: ByteArray) {
        val out = outputStream
        if (out != null) {
            synchronized(out) {
                out.write(bytes)
                out.flush()
            }
        }
    }

    private fun startReaderLoop() {
        readerJob = scope.launch {
            val inStream = inputStream ?: return@launch
            val buffer = ByteArray(512)
            while (isActive && _connectionState.value == LoraConnectionState.CONNECTED) {
                try {
                    val bytesRead = inStream.read(buffer)
                    if (bytesRead > 0) {
                        val frame = ByteArray(bytesRead)
                        System.arraycopy(buffer, 0, frame, 0, bytesRead)
                        val packet = LoraPacket.decode(frame)
                        if (packet != null) {
                            _relayedPacketsCount.value += 1
                            onPacketReceived?.invoke(packet)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.w(tag, "LoRa reader loop error: ${e.message}")
                        disconnect()
                    }
                    break
                }
            }
        }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        @Volatile
        private var INSTANCE: LoraCompanionBridge? = null

        fun getInstance(context: Context): LoraCompanionBridge {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LoraCompanionBridge(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

