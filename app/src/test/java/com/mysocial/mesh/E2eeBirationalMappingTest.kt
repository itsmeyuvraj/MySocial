package com.mysocial.mesh

import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.crypto.DirectMessagePayload
import com.mysocial.mesh.crypto.E2eeManager
import org.bouncycastle.math.ec.rfc7748.X25519
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class E2eeBirationalMappingTest {

    private val random = SecureRandom()

    @Test
    fun testBirationalMappingDeterminismAndUniqueness() {
        val alice = CryptoManager.createInMemory()
        val bob = CryptoManager.createInMemory()

        // Determinism: Same Ed25519 public key converts to exact same X25519 coordinate
        val xPubAlice1 = E2eeManager.ed25519PubToX25519(alice.publicKeyBytes)
        val xPubAlice2 = E2eeManager.ed25519PubToX25519(alice.publicKeyBytes)
        assertEquals(32, xPubAlice1.size)
        assertArrayEquals("Birational map must be strictly deterministic", xPubAlice1, xPubAlice2)

        // Uniqueness: Distinct Ed25519 public keys map to distinct X25519 coordinates
        val xPubBob = E2eeManager.ed25519PubToX25519(bob.publicKeyBytes)
        assertEquals(32, xPubBob.size)
        assertFalse("Distinct public keys must map to distinct X25519 points", xPubAlice1.contentEquals(xPubBob))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEd25519PubToX25519RejectsShortKeys() {
        E2eeManager.ed25519PubToX25519(ByteArray(31))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEd25519PubToX25519RejectsLongKeys() {
        E2eeManager.ed25519PubToX25519(ByteArray(33))
    }

    @Test
    fun testEd25519PrivToX25519ClampingRulesRFC8032() {
        val seed = ByteArray(32).also { random.nextBytes(it) }
        val clampedScalar = E2eeManager.ed25519PrivToX25519(seed)

        assertEquals(32, clampedScalar.size)

        // RFC 8032 / Curve25519 scalar clamping invariants:
        // 1. Lowest 3 bits of byte 0 must be cleared (multiple of 8 to clear cofactor)
        val byte0 = clampedScalar[0].toInt() and 0xFF
        assertEquals("Lowest 3 bits of byte 0 must be 0", 0, byte0 and 0x07)

        // 2. Highest bit of byte 31 must be cleared
        val byte31 = clampedScalar[31].toInt() and 0xFF
        assertEquals("Highest bit of byte 31 must be 0", 0, byte31 and 0x80)

        // 3. Second highest bit of byte 31 must be set
        assertEquals("Second highest bit of byte 31 must be 1", 0x40, byte31 and 0x40)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEd25519PrivToX25519RejectsInvalidSeedSize() {
        E2eeManager.ed25519PrivToX25519(ByteArray(16))
    }

    @Test
    fun testCrossPartyDiffieHellmanEquivalence() {
        val seedAlice = ByteArray(32) { (it + 1).toByte() }
        val seedBob = ByteArray(32) { (it + 50).toByte() }

        val alicePriv = E2eeManager.ed25519PrivToX25519(seedAlice)
        val bobPriv = E2eeManager.ed25519PrivToX25519(seedBob)

        val alicePub = ByteArray(32)
        X25519.generatePublicKey(alicePriv, 0, alicePub, 0)

        val bobPub = ByteArray(32)
        X25519.generatePublicKey(bobPriv, 0, bobPub, 0)

        // Alice computes shared secret using Bob's public key
        val secretAlice = ByteArray(32)
        val okAlice = X25519.calculateAgreement(alicePriv, 0, bobPub, 0, secretAlice, 0)
        assertTrue(okAlice)

        // Bob computes shared secret using Alice's public key
        val secretBob = ByteArray(32)
        val okBob = X25519.calculateAgreement(bobPriv, 0, alicePub, 0, secretBob, 0)
        assertTrue(okBob)

        // Must be identical 32 bytes
        assertArrayEquals("Diffie-Hellman secret agreement must be identical both ways", secretAlice, secretBob)
    }

    @Test
    fun testDirectMessagePayloadSerializationAndRoundtrip() {
        val payload = DirectMessagePayload(
            messageId = "msg_test_uuid_9999",
            recipientId = "@test_recipient_author_id.ed25519",
            ephemeralPublicKeyBase64 = "dGVzdF9lcGhlbWVyYWxfa2V5X2Jhc2U2NA==",
            ivBase64 = "dGVzdF9pdl8xMg==",
            ciphertextBase64 = "dGVzdF9jaXBoZXJ0ZXh0X2Flc19nY21fYmFzZTY0",
            timestamp = 1705000000000L
        )

        val json = payload.toJson()
        assertNotNull(json)
        assertTrue(json.contains("msg_test_uuid_9999"))
        assertTrue(json.contains("@test_recipient_author_id.ed25519"))

        val decoded = DirectMessagePayload.fromJson(json)
        assertEquals(payload.messageId, decoded.messageId)
        assertEquals(payload.recipientId, decoded.recipientId)
        assertEquals(payload.ephemeralPublicKeyBase64, decoded.ephemeralPublicKeyBase64)
        assertEquals(payload.ivBase64, decoded.ivBase64)
        assertEquals(payload.ciphertextBase64, decoded.ciphertextBase64)
        assertEquals(payload.timestamp, decoded.timestamp)
        assertEquals(payload, decoded)
    }
}
