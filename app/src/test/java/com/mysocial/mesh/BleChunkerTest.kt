package com.mysocial.mesh

import com.mysocial.mesh.protocol.BleChunker
import com.mysocial.mesh.protocol.BleReassembler
import com.mysocial.mesh.protocol.FrameCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BleChunkerTest {

    @Test
    fun testFrameCodecRoundtrip() {
        val payload = "Hello P2P Mesh Network! Offline Signed Logs.".toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()

        FrameCodec.writeFrame(out, payload)
        val writtenBytes = out.toByteArray()

        assertEquals(4 + payload.size, writtenBytes.size)

        val input = ByteArrayInputStream(writtenBytes)
        val decoded = FrameCodec.readFrame(input)

        assertNotNull(decoded)
        assertArrayEquals(payload, decoded)
    }

    @Test
    fun testBleChunkerAndReassemblerSingleChunk() {
        val payload = "Short payload".toByteArray(Charsets.UTF_8)
        val chunks = BleChunker.chunkPayload(payload, maxChunkSize = 100)

        assertEquals(1, chunks.size)
        assertEquals(BleChunker.FLAG_SINGLE, chunks[0][0])

        val reassembler = BleReassembler()
        val result = reassembler.processChunk(chunks[0])

        assertNotNull(result)
        assertArrayEquals(payload, result)
    }

    @Test
    fun testBleChunkerAndReassemblerMultiChunk() {
        // Create a 1000-byte payload with max chunk size 100 bytes (97 usable bytes per chunk)
        val payload = ByteArray(1000) { (it % 256).toByte() }
        val chunks = BleChunker.chunkPayload(payload, maxChunkSize = 100)

        assertEquals(11, chunks.size) // 1000 / 97 = 10.3 -> 11 chunks
        assertEquals(BleChunker.FLAG_START, chunks[0][0])
        for (i in 1 until chunks.size - 1) {
            assertEquals(BleChunker.FLAG_CONTINUE, chunks[i][0])
        }
        assertEquals(BleChunker.FLAG_END, chunks.last()[0])

        val reassembler = BleReassembler()
        for (i in 0 until chunks.size - 1) {
            val partial = reassembler.processChunk(chunks[i])
            assertNull(partial)
        }

        val finalResult = reassembler.processChunk(chunks.last())
        assertNotNull(finalResult)
        assertArrayEquals(payload, finalResult)
    }

    @Test
    fun testBleReassemblerDetectsDroppedPacket() {
        val payload = ByteArray(500) { (it % 100).toByte() }
        val chunks = BleChunker.chunkPayload(payload, maxChunkSize = 80)

        val reassembler = BleReassembler()
        reassembler.processChunk(chunks[0])
        // Drop chunks[1], skip directly to chunks[2]
        val result = reassembler.processChunk(chunks[2])

        // Should detect out-of-order sequence and reset buffer
        assertNull(result)
    }
}
