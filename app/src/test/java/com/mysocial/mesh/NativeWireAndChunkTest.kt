package com.mysocial.mesh

import com.mysocial.mesh.nativebridge.NativeMeshCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeWireAndChunkTest {

    @Test
    fun testWireFrameContentValidation() {
        // Valid JSON frames
        val jsonMsg = "{\"type\":\"HELLO_VECTOR\",\"peerId\":\"user_123\"}".toByteArray(Charsets.UTF_8)
        assertTrue(NativeMeshCore.validateFrame(jsonMsg))

        // Valid LoRa magic frame
        val loraMsg = byteArrayOf('L'.code.toByte(), 'R'.code.toByte(), 0x01, 0x01, 0x00, 0x04, 0x54, 0x45, 0x53, 0x54)
        assertTrue(NativeMeshCore.validateFrame(loraMsg))

        // Valid MySocial binary magic frame
        val msMsg = byteArrayOf('M'.code.toByte(), 'S'.code.toByte(), 0x01, 0x00, 0x10)
        assertTrue(NativeMeshCore.validateFrame(msMsg))

        // Frame too small (< 4 bytes)
        assertFalse(NativeMeshCore.validateFrame(byteArrayOf(0x01, 0x02)))
        assertFalse(NativeMeshCore.validateFrame(byteArrayOf()))
    }

    @Test
    fun testChunkTreeHashDeterminism() {
        val chunk1 = "Media chunk bytes 0 to 16384".toByteArray(Charsets.UTF_8)
        val chunk2 = "Media chunk bytes 16384 to 32768".toByteArray(Charsets.UTF_8)
        val chunk3 = "Media chunk bytes 32768 to 49152".toByteArray(Charsets.UTF_8)

        val chunks = arrayOf(chunk1, chunk2, chunk3)

        val hash1 = NativeMeshCore.computeChunkTreeHash(chunks)
        val hash2 = NativeMeshCore.computeChunkTreeHash(chunks)

        assertEquals(32, hash1.size)
        assertEquals(32, hash2.size)
        assertTrue(hash1.contentEquals(hash2))

        // Modifying any chunk changes the tree hash
        val modifiedChunk2 = "Corrupted chunk bytes".toByteArray(Charsets.UTF_8)
        val modifiedChunks = arrayOf(chunk1, modifiedChunk2, chunk3)
        val modifiedHash = NativeMeshCore.computeChunkTreeHash(modifiedChunks)

        assertFalse(hash1.contentEquals(modifiedHash))
    }

    @Test
    fun testEmptyChunkTreeHashReturns32Zeros() {
        val emptyHash = NativeMeshCore.computeChunkTreeHash(emptyArray())
        assertEquals(32, emptyHash.size)
        assertTrue(emptyHash.all { it == 0.toByte() })
    }
}

