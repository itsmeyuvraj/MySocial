package com.mysocial.mesh.protocol

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utilities for length-prefixed framing over streams (TCP sockets, BLE L2CAP CoC)
 * and packet chunking/reassembly over GATT characteristics with constrained MTU.
 */
object FrameCodec {
    const val MAX_FRAME_SIZE = 2 * 1024 * 1024 // 2MB safety limit

    /**
     * Writes a length-prefixed frame to the output stream:
     * [4-byte Big-Endian Length][Payload bytes]
     */
    fun writeFrame(output: OutputStream, payload: ByteArray) {
        require(payload.size <= MAX_FRAME_SIZE) { "Payload size ${payload.size} exceeds max frame size $MAX_FRAME_SIZE" }
        val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array()
        output.write(header)
        output.write(payload)
        output.flush()
    }

    /**
     * Reads a length-prefixed frame from the input stream.
     * Returns null if stream reached EOF before header could be read.
     */
    fun readFrame(input: InputStream): ByteArray? {
        val header = ByteArray(4)
        var bytesRead = 0
        while (bytesRead < 4) {
            val r = input.read(header, bytesRead, 4 - bytesRead)
            if (r == -1) {
                if (bytesRead == 0) return null // Clean EOF
                throw IllegalStateException("Incomplete frame header read: $bytesRead bytes")
            }
            bytesRead += r
        }

        val length = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).int
        if (length < 0 || length > MAX_FRAME_SIZE) {
            throw IllegalStateException("Invalid frame length received: $length (max: $MAX_FRAME_SIZE)")
        }

        val payload = ByteArray(length)
        var payloadRead = 0
        while (payloadRead < length) {
            val r = input.read(payload, payloadRead, length - payloadRead)
            if (r == -1) {
                throw IllegalStateException("Unexpected EOF while reading frame payload. Expected $length, got $payloadRead")
            }
            payloadRead += r
        }
        return payload
    }
}

/**
 * BLE GATT Packet Chunker and Reassembler.
 * Operates on arbitrary byte payloads (such as UTF-8 encoded GossipMessage JSON).
 */
object BleChunker {
    const val FLAG_START: Byte = 0x01
    const val FLAG_CONTINUE: Byte = 0x02
    const val FLAG_END: Byte = 0x04
    const val FLAG_SINGLE: Byte = 0x05 // FLAG_START | FLAG_END
    const val HEADER_SIZE = 3 // 1 byte flags + 2 bytes sequence number

    /**
     * Splits a payload into chunks of size at most [maxChunkSize].
     * [maxChunkSize] must be at least HEADER_SIZE + 1. Typically set to (GATT MTU - 3).
     */
    fun chunkPayload(payload: ByteArray, maxChunkSize: Int = 240): List<ByteArray> {
        val usableChunkData = (maxChunkSize - HEADER_SIZE).coerceAtLeast(1)
        if (payload.isEmpty()) {
            val emptyPacket = ByteArray(HEADER_SIZE)
            emptyPacket[0] = FLAG_SINGLE
            emptyPacket[1] = 0
            emptyPacket[2] = 0
            return listOf(emptyPacket)
        }

        val totalChunks = (payload.size + usableChunkData - 1) / usableChunkData
        val chunks = mutableListOf<ByteArray>()

        var offset = 0
        for (seq in 0 until totalChunks) {
            val remaining = payload.size - offset
            val currentChunkSize = minOf(usableChunkData, remaining)
            val packet = ByteArray(HEADER_SIZE + currentChunkSize)

            val flag = when {
                totalChunks == 1 -> FLAG_SINGLE
                seq == 0 -> FLAG_START
                seq == totalChunks - 1 -> FLAG_END
                else -> FLAG_CONTINUE
            }

            packet[0] = flag
            packet[1] = ((seq ushr 8) and 0xFF).toByte()
            packet[2] = (seq and 0xFF).toByte()

            System.arraycopy(payload, offset, packet, HEADER_SIZE, currentChunkSize)
            offset += currentChunkSize
            chunks.add(packet)
        }

        return chunks
    }
}

/**
 * Stateful reassembler for incoming BLE GATT chunks.
 */
class BleReassembler(
    private val maxFrameSize: Int = FrameCodec.MAX_FRAME_SIZE
) {
    private val buffer = java.io.ByteArrayOutputStream()
    private var expectedSeq: Int = 0
    private var isReceiving = false

    /**
     * Processes an incoming raw chunk packet from a BLE characteristic.
     * Returns the fully reassembled payload ByteArray when the complete frame has arrived,
     * or null if more chunks are still needed.
     */
    fun processChunk(chunk: ByteArray): ByteArray? {
        if (chunk.size < BleChunker.HEADER_SIZE) {
            reset()
            return null
        }

        val flag = chunk[0]
        val seq = ((chunk[1].toInt() and 0xFF) shl 8) or (chunk[2].toInt() and 0xFF)
        val dataLength = chunk.size - BleChunker.HEADER_SIZE

        if (flag == BleChunker.FLAG_SINGLE) {
            reset()
            val result = ByteArray(dataLength)
            System.arraycopy(chunk, BleChunker.HEADER_SIZE, result, 0, dataLength)
            return result
        }

        if (flag == BleChunker.FLAG_START) {
            reset()
            isReceiving = true
            expectedSeq = 1
            buffer.write(chunk, BleChunker.HEADER_SIZE, dataLength)
            return null
        }

        if (!isReceiving) {
            // Discard stray chunk
            return null
        }

        // Verify sequence continuity
        if (seq != expectedSeq) {
            // Packet dropped or out of order, discard frame
            reset()
            return null
        }

        if (buffer.size() + dataLength > maxFrameSize) {
            // Frame overflow protection
            reset()
            return null
        }

        buffer.write(chunk, BleChunker.HEADER_SIZE, dataLength)
        expectedSeq++

        if (flag == BleChunker.FLAG_END) {
            val result = buffer.toByteArray()
            reset()
            return result
        }

        return null
    }

    fun reset() {
        buffer.reset()
        expectedSeq = 0
        isReceiving = false
    }
}
