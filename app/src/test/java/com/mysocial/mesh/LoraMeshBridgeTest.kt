package com.mysocial.mesh

import com.mysocial.mesh.lora.LoraCompanionBridge
import com.mysocial.mesh.lora.LoraConnectionState
import com.mysocial.mesh.lora.LoraPacket
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoraMeshBridgeTest {

    @Test
    fun testLoraPacketEncodingAndDecoding() {
        val payloadText = "Emergency SOS: Flash flood at Bridge 4"
        val payload = payloadText.toByteArray(Charsets.UTF_8)
        val packet = LoraPacket(
            packetType = LoraPacket.TYPE_SOS_ALERT,
            hopCount = 2,
            payload = payload,
            snr = 10.5f,
            rssi = -65
        )

        val encoded = packet.encode()
        assertTrue(encoded.size > LoraPacket.HEADER_SIZE + LoraPacket.CRC_SIZE)

        val decoded = LoraPacket.decode(encoded, snr = 10.5f, rssi = -65)
        assertNotNull(decoded)
        assertEquals(LoraPacket.TYPE_SOS_ALERT, decoded!!.packetType)
        assertEquals(2, decoded.hopCount)
        assertArrayEquals(payload, decoded.payload)
        assertEquals("Emergency SOS: Flash flood at Bridge 4", String(decoded.payload, Charsets.UTF_8))
        assertEquals(10.5f, decoded.snr)
        assertEquals(-65, decoded.rssi)
    }

    @Test
    fun testLoraPacketCrcCorruptionRejection() {
        val payload = "Important message".toByteArray(Charsets.UTF_8)
        val packet = LoraPacket(
            packetType = LoraPacket.TYPE_SHORT_POST,
            hopCount = 1,
            payload = payload
        )

        val encoded = packet.encode()
        // Corrupt a byte in the payload
        encoded[LoraPacket.HEADER_SIZE + 2] = (encoded[LoraPacket.HEADER_SIZE + 2].toInt() xor 0xFF).toByte()

        val decoded = LoraPacket.decode(encoded)
        assertNull("Corrupted packet with bad CRC must be rejected", decoded)
    }

    @Test
    fun testLoraPacketTruncatedOrInvalidMagicRejection() {
        // Less than header + crc
        assertNull(LoraPacket.decode(byteArrayOf(0x4C, 0x52)))

        // Invalid magic bytes
        val badMagic = byteArrayOf(0x00, 0x00, 0x01, 0x01, 0x00, 0x01, 0x41, 0x12, 0x34)
        assertNull(LoraPacket.decode(badMagic))
    }

    @Test
    fun testCrc16DeterministicComputation() {
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        val crc = LoraPacket.computeCrc16(data, 0, data.size)
        // Standard CCITT-FALSE for "123456789" is 0x29B1
        assertEquals(0x29B1, crc)
    }

    @Test
    fun testSosJsonPayloadExtraction() {
        val json = JSONObject().apply {
            put("type", "SOS")
            put("author", "ed25519_test_author_xyz")
            put("hash", "sha256_hash_123456")
            put("text", "Urgent medical assistance required at Sector 7")
            put("time", 1700000000000L)
        }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val packet = LoraPacket(
            packetType = LoraPacket.TYPE_SOS_ALERT,
            hopCount = 0,
            payload = bytes
        )

        val encoded = packet.encode()
        val decoded = LoraPacket.decode(encoded)
        assertNotNull(decoded)

        val parsedJson = JSONObject(String(decoded!!.payload, Charsets.UTF_8))
        assertEquals("SOS", parsedJson.getString("type"))
        assertEquals("ed25519_test_author_xyz", parsedJson.getString("author"))
        assertEquals("sha256_hash_123456", parsedJson.getString("hash"))
        assertEquals("Urgent medical assistance required at Sector 7", parsedJson.getString("text"))
    }
}

