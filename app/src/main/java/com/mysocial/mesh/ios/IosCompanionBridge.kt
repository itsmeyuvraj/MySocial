package com.mysocial.mesh.ios

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.util.Log
import com.mysocial.mesh.lora.LoraPacket
import com.mysocial.mesh.protocol.PeerConnection
import com.mysocial.mesh.protocol.TransportType
import com.mysocial.mesh.transport.DiscoveredPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Cross-Platform iOS Companion Bridge:
 * Manages CoreBluetooth L2CAP Connection-Oriented Channels (CoC) interoperability
 * with iOS MySocial nodes, framing zero-copy binary packets over standard BLE L2CAP PSMs.
 */
class IosCompanionBridge(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "IosCompanionBridge"
        const val L2CAP_PSM = 0x0088 // Registered protocol dynamic PSM for CoreBluetooth L2CAP
        const val MAGIC_HEADER: Int = 0x4D595343 // "MYSC" (MySocial Cross-platform)

        const val TYPE_HANDSHAKE: Short = 0x0001
        const val TYPE_LOG_BATCH: Short = 0x0002
        const val TYPE_BLOOM_FILTER: Short = 0x0003
        const val TYPE_DIRECT_MESSAGE: Short = 0x0004
        const val TYPE_BLOB_CHUNK: Short = 0x0005
        const val TYPE_SOS_EMERGENCY: Short = 0x0006

        /**
         * Encodes a binary L2CAP frame with magic header, type, payload length, payload, and CRC-16.
         */
        fun encodeFrame(frameType: Short, payload: ByteArray): ByteArray {
            val totalSize = 4 + 2 + 4 + payload.size + 2
            val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
            buffer.putInt(MAGIC_HEADER)
            buffer.putShort(frameType)
            buffer.putInt(payload.size)
            buffer.put(payload)

            val crc = LoraPacket.computeCrc16(payload, 0, payload.size)
            buffer.putShort(crc.toShort())
            return buffer.array()
        }

        /**
         * Decodes a binary L2CAP frame, verifying magic header and CRC-16 checksum.
         */
        fun decodeFrame(data: ByteArray): IosFrame? {
            if (data.size < 12) return null
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            val magic = buffer.getInt()
            if (magic != MAGIC_HEADER) return null

            val frameType = buffer.getShort()
            val payloadLen = buffer.getInt()
            if (payloadLen < 0 || buffer.remaining() < payloadLen + 2) return null

            val payload = ByteArray(payloadLen)
            buffer.get(payload)
            val receivedCrc = buffer.getShort().toInt() and 0xFFFF
            val computedCrc = LoraPacket.computeCrc16(payload, 0, payload.size)
            if (receivedCrc != computedCrc) {
                Log.w(TAG, "L2CAP frame CRC mismatch: expected $computedCrc, received $receivedCrc")
                return null
            }

            return IosFrame(frameType, payload)
        }
    }

    data class IosFrame(
        val frameType: Short,
        val payload: ByteArray
    )

    private val _connectedIosPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val connectedIosPeers: StateFlow<List<DiscoveredPeer>> = _connectedIosPeers.asStateFlow()

    private val _isBridgeActive = MutableStateFlow(false)
    val isBridgeActive: StateFlow<Boolean> = _isBridgeActive.asStateFlow()

    fun start() {
        _isBridgeActive.value = true
        Log.i(TAG, "iOS L2CAP Companion Bridge active on PSM 0x${Integer.toHexString(L2CAP_PSM)}")
    }

    fun stop() {
        _isBridgeActive.value = false
        _connectedIosPeers.value = emptyList()
    }

    /**
     * Connects an Android BluetoothDevice to an iOS device over L2CAP CoC socket (API 29+).
     */
    fun openL2capChannel(device: BluetoothDevice, onConnected: (BluetoothSocket) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                try {
                    val socket = device.createInsecureL2capChannel(L2CAP_PSM)
                    socket.connect()
                    Log.i(TAG, "Connected to iOS device via L2CAP CoC: ${device.address}")
                    onConnected(socket)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to connect iOS L2CAP channel: ${e.message}")
                }
            }
        }
    }
}
