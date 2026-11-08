package com.mysocial.mesh.lora

import java.nio.ByteBuffer

/**
 * High-efficiency binary packet framing for Sub-GHz LoRa radios (868/915 MHz).
 * Tailored for low-bandwidth, high-range (5-15 km) emergency relays.
 *
 * Frame structure:
 * [0..1] MAGIC: 'L', 'R' (0x4C, 0x52)
 * [2]    VERSION: 0x01
 * [3]    PACKET_TYPE: 0x01=SOS, 0x02=SHORT_POST, 0x03=HEARTBEAT, 0x04=SYNC_VECTOR
 * [4]    HOP_COUNT: 0..7
 * [5]    PAYLOAD_LENGTH: 0..240
 * [6..N] PAYLOAD
 * [N+1..N+2] CRC-16 (CCITT-FALSE, 0x1021)
 */
data class LoraPacket(
    val packetType: Byte,
    val hopCount: Int,
    val payload: ByteArray,
    val snr: Float = 0f,
    val rssi: Int = 0
) {
    fun encode(): ByteArray {
        val payloadLen = payload.size.coerceAtMost(MAX_PAYLOAD_SIZE)
        val frameSize = HEADER_SIZE + payloadLen + CRC_SIZE
        val buffer = ByteBuffer.allocate(frameSize)

        buffer.put(MAGIC_0)
        buffer.put(MAGIC_1)
        buffer.put(VERSION)
        buffer.put(packetType)
        buffer.put((hopCount and 0xFF).toByte())
        buffer.put((payloadLen and 0xFF).toByte())
        buffer.put(payload, 0, payloadLen)

        val dataToCrc = buffer.array()
        val crc = computeCrc16(dataToCrc, 0, HEADER_SIZE + payloadLen)
        buffer.putShort(crc.toShort())

        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LoraPacket
        if (packetType != other.packetType) return false
        if (hopCount != other.hopCount) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = packetType.toInt()
        result = 31 * result + hopCount
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val MAGIC_0: Byte = 0x4C // 'L'
        const val MAGIC_1: Byte = 0x52 // 'R'
        const val VERSION: Byte = 0x01

        const val TYPE_SOS_ALERT: Byte = 0x01
        const val TYPE_SHORT_POST: Byte = 0x02
        const val TYPE_NODE_HEARTBEAT: Byte = 0x03
        const val TYPE_SYNC_VECTOR: Byte = 0x04

        const val HEADER_SIZE = 6
        const val CRC_SIZE = 2
        const val MAX_PAYLOAD_SIZE = 240
        const val MAX_FRAME_SIZE = HEADER_SIZE + MAX_PAYLOAD_SIZE + CRC_SIZE // 248 bytes, fits in 250 LoRa MTU

        fun computeCrc16(data: ByteArray, offset: Int, length: Int): Int {
            var crc = 0xFFFF
            for (i in offset until (offset + length)) {
                val b = data[i].toInt() and 0xFF
                crc = crc xor (b shl 8)
                for (j in 0 until 8) {
                    crc = if ((crc and 0x8000) != 0) {
                        (crc shl 1) xor 0x1021
                    } else {
                        crc shl 1
                    }
                    crc = crc and 0xFFFF
                }
            }
            return crc
        }

        fun decode(bytes: ByteArray, snr: Float = 0f, rssi: Int = 0): LoraPacket? {
            if (bytes.size < HEADER_SIZE + CRC_SIZE) return null
            if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1) return null
            if (bytes[2] != VERSION) return null

            val type = bytes[3]
            val hopCount = bytes[4].toInt() and 0xFF
            val payloadLen = bytes[5].toInt() and 0xFF

            if (bytes.size < HEADER_SIZE + payloadLen + CRC_SIZE) return null

            val expectedCrc = computeCrc16(bytes, 0, HEADER_SIZE + payloadLen)
            val buffer = ByteBuffer.wrap(bytes, HEADER_SIZE + payloadLen, CRC_SIZE)
            val actualCrc = buffer.short.toInt() and 0xFFFF

            if (expectedCrc != actualCrc) {
                return null // CRC corrupted over lossy radio link
            }

            val payload = ByteArray(payloadLen)
            System.arraycopy(bytes, HEADER_SIZE, payload, 0, payloadLen)

            return LoraPacket(
                packetType = type,
                hopCount = hopCount,
                payload = payload,
                snr = snr,
                rssi = rssi
            )
        }
    }
}

