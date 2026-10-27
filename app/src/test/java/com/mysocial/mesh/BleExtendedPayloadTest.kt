package com.mysocial.mesh

import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.protocol.BleExtendedPayload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleExtendedPayloadTest {

    @Test
    fun testFullPayloadRoundtrip() {
        val rawKey = ByteArray(32) { (it * 7).toByte() }
        val authorId = CryptoManager.formatAuthorId(rawKey)
        val displayName = "Alice Cooper"
        val psm = 0x1005
        val seq = 123456789L
        val broadcastData = "MeshAnnouncement:Hello".toByteArray(Charsets.UTF_8)

        val payload = BleExtendedPayload(
            authorPublicKey = rawKey,
            authorId = authorId,
            displayName = displayName,
            l2capPsm = psm,
            latestSequence = seq,
            broadcastData = broadcastData,
            supportsL2cap = true,
            supports2MPhy = true
        )

        val encoded = payload.encode()
        assertTrue("Encoded payload size must be greater than legacy 31 bytes", encoded.size > 31)

        val decoded = BleExtendedPayload.decode(encoded)
        assertNotNull(decoded)
        assertFalse(decoded!!.isLegacyFallback)
        assertEquals(authorId, decoded.authorId)
        assertArrayEquals(rawKey, decoded.authorPublicKey)
        assertEquals(displayName, decoded.displayName)
        assertEquals(psm, decoded.l2capPsm)
        assertEquals(seq, decoded.latestSequence)
        assertArrayEquals(broadcastData, decoded.broadcastData)
        assertTrue(decoded.supportsL2cap)
        assertTrue(decoded.supports2MPhy)
    }

    @Test
    fun testMinimalPayloadRoundtrip() {
        val payload = BleExtendedPayload(
            displayName = "Bob",
            l2capPsm = 4097,
            supportsL2cap = true,
            supports2MPhy = false
        )

        val encoded = payload.encode()
        val decoded = BleExtendedPayload.decode(encoded)

        assertNotNull(decoded)
        assertEquals("Bob", decoded!!.displayName)
        assertEquals(4097, decoded.l2capPsm)
        assertNull(decoded.authorId)
        assertNull(decoded.latestSequence)
        assertNull(decoded.broadcastData)
        assertTrue(decoded.supportsL2cap)
        assertFalse(decoded.supports2MPhy)
    }

    @Test
    fun testAuthorIdDerivedFromFormattedString() {
        val rawKey = ByteArray(32) { (it * 3 + 1).toByte() }
        val authorId = CryptoManager.formatAuthorId(rawKey)

        // Pass only authorId without explicit authorPublicKey
        val payload = BleExtendedPayload(
            authorId = authorId,
            displayName = "Charlie"
        )

        val encoded = payload.encode()
        val decoded = BleExtendedPayload.decode(encoded)

        assertNotNull(decoded)
        assertEquals(authorId, decoded!!.authorId)
        assertArrayEquals(rawKey, decoded.authorPublicKey)
        assertEquals("Charlie", decoded.displayName)
    }

    @Test
    fun testLegacy4ByteFallbackDetection() {
        val legacyBytes = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val decoded = BleExtendedPayload.decode(legacyBytes)

        assertNotNull(decoded)
        assertTrue(decoded!!.isLegacyFallback)
        assertArrayEquals(legacyBytes, decoded.legacyAuthorHashPrefix)
        assertNull(decoded.authorId)
        assertNull(decoded.l2capPsm)
    }

    @Test
    fun testInvalidOrCorruptedPayloadReturnsNull() {
        // Less than 4 bytes
        assertNull(BleExtendedPayload.decode(byteArrayOf(0x4D, 0x53)))

        // Invalid magic bytes
        val invalidMagic = byteArrayOf(0x00, 0x01, 0x01, 0x00, 0x05)
        assertNull(BleExtendedPayload.decode(invalidMagic))

        // Truncated body when flag indicates 32-byte pubkey present
        val truncatedWithPubKeyFlag = byteArrayOf(
            BleExtendedPayload.MAGIC_BYTE_0,
            BleExtendedPayload.MAGIC_BYTE_1,
            BleExtendedPayload.PROTOCOL_VERSION,
            BleExtendedPayload.FLAG_HAS_PUBKEY.toByte(),
            0x01, 0x02, 0x03 // only 3 bytes instead of 32
        )
        assertNull(BleExtendedPayload.decode(truncatedWithPubKeyFlag))

        // Truncated body when flag indicates PSM present
        val truncatedWithPsm = byteArrayOf(
            BleExtendedPayload.MAGIC_BYTE_0,
            BleExtendedPayload.MAGIC_BYTE_1,
            BleExtendedPayload.PROTOCOL_VERSION,
            BleExtendedPayload.FLAG_HAS_PSM.toByte(),
            0x01 // only 1 byte instead of 4
        )
        assertNull(BleExtendedPayload.decode(truncatedWithPsm))

        // Truncated name length
        val truncatedName = byteArrayOf(
            BleExtendedPayload.MAGIC_BYTE_0,
            BleExtendedPayload.MAGIC_BYTE_1,
            BleExtendedPayload.PROTOCOL_VERSION,
            BleExtendedPayload.FLAG_HAS_NAME.toByte(),
            0x0A, // name length says 10 bytes
            'A'.code.toByte(), 'B'.code.toByte() // but only 2 bytes provided
        )
        assertNull(BleExtendedPayload.decode(truncatedName))
    }

    @Test
    fun testLargeBroadcastDataHandling() {
        val largeData = ByteArray(200) { (it % 128).toByte() }
        val payload = BleExtendedPayload(
            displayName = "SuperNode",
            broadcastData = largeData
        )

        val encoded = payload.encode()
        val decoded = BleExtendedPayload.decode(encoded)

        assertNotNull(decoded)
        assertEquals("SuperNode", decoded!!.displayName)
        assertArrayEquals(largeData, decoded.broadcastData)
    }

    @Test
    fun testDisplayNameTruncation() {
        val longName = "A".repeat(100)
        val payload = BleExtendedPayload(displayName = longName)
        val encoded = payload.encode()
        val decoded = BleExtendedPayload.decode(encoded)

        assertNotNull(decoded)
        assertEquals(BleExtendedPayload.MAX_NAME_BYTES, decoded!!.displayName!!.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun testSequenceNumberAndPsmBoundaries() {
        val payload = BleExtendedPayload(
            l2capPsm = 0x7FFF,
            latestSequence = Long.MAX_VALUE,
            displayName = "PeerNode"
        )
        val encoded = payload.encode()
        val decoded = BleExtendedPayload.decode(encoded)

        assertNotNull(decoded)
        assertEquals(0x7FFF, decoded!!.l2capPsm)
        assertEquals(Long.MAX_VALUE, decoded.latestSequence)
    }

    @Test
    fun testEqualsAndHashCode() {
        val key = ByteArray(32) { 1 }
        val p1 = BleExtendedPayload(authorPublicKey = key, displayName = "Peer", l2capPsm = 100)
        val p2 = BleExtendedPayload(authorPublicKey = key.clone(), displayName = "Peer", l2capPsm = 100)
        val p3 = BleExtendedPayload(authorPublicKey = key, displayName = "Other", l2capPsm = 100)

        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())
        assertNotEquals(p1, p3)
    }
}
