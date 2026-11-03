package com.mysocial.mesh

import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.crypto.E2eeManager
import com.mysocial.mesh.model.DirectMessageContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class E2eeMessagingTest {

    @Test
    fun testAliceToBobE2eeEncryptionAndDecryption() {
        val alice = CryptoManager.createInMemory()
        val bob = CryptoManager.createInMemory()
        val charlie = CryptoManager.createInMemory()

        val plaintext = "Hey Bob, this is a secret 1-on-1 mesh message! 🔐"

        // Alice encrypts for Bob
        val payload = alice.encryptDirectMessage(bob.authorId, plaintext)

        assertEquals(bob.authorId, payload.recipientId)
        assertNotNull(payload.messageId)
        assertNotNull(payload.ephemeralPublicKeyBase64)
        assertNotNull(payload.ciphertextBase64)

        // Bob decrypts successfully
        val decryptedByBob = bob.decryptDirectMessage(payload)
        assertEquals("Bob should successfully decrypt Alice's message", plaintext, decryptedByBob)

        // Charlie (stranger / mule) attempts to decrypt Alice's message for Bob
        val decryptedByCharlie = charlie.decryptDirectMessage(payload)
        assertNull("Charlie must NOT be able to decrypt messages intended for Bob", decryptedByCharlie)

        // Alice attempts to decrypt her own outgoing payload with her private key
        // (Since ephemeral key was derived for Bob, only Bob has the matching static private key)
        val decryptedByAlice = alice.decryptDirectMessage(payload)
        assertNull("Ephemeral static ECDH is asymmetric: only recipient can decrypt", decryptedByAlice)
    }

    @Test
    fun testTamperedPayloadFailsGcmAuthentication() {
        val alice = CryptoManager.createInMemory()
        val bob = CryptoManager.createInMemory()

        val plaintext = "Confidential coordinates: 40.7128, -74.0060"
        val payload = alice.encryptDirectMessage(bob.authorId, plaintext)

        // Tamper with the ciphertext Base64 deterministically
        val firstChar = payload.ciphertextBase64.first()
        val replacementChar = if (firstChar == 'A') 'B' else 'A'
        val tamperedCiphertext = replacementChar + payload.ciphertextBase64.substring(1)
        val tamperedPayload = payload.copy(ciphertextBase64 = tamperedCiphertext)

        val decrypted = bob.decryptDirectMessage(tamperedPayload)
        assertNull("Tampered GCM ciphertext must fail authentication and return null", decrypted)
    }

    @Test
    fun testDirectMessageWithMediaContentSerializationAndDecryption() {
        val alice = CryptoManager.createInMemory()
        val bob = CryptoManager.createInMemory()

        val content = DirectMessageContent(
            text = "Listen to this audio note!",
            mediaType = "AUDIO",
            mediaBlobHash = "a1b2c3d4e5f67890abcdef1234567890abcdef1234567890abcdef1234567890",
            mediaMimeType = "audio/m4a",
            mediaTotalBytes = 64500L,
            mediaChunkCount = 4,
            mediaDurationMs = 4200L
        )

        val payload = alice.encryptDirectMessage(bob.authorId, content.toJson())
        val decryptedByBob = bob.decryptDirectMessage(payload)
        assertNotNull(decryptedByBob)

        val parsed = DirectMessageContent.fromDecryptedString(decryptedByBob!!)
        assertEquals("Listen to this audio note!", parsed.text)
        assertEquals("AUDIO", parsed.mediaType)
        assertEquals(content.mediaBlobHash, parsed.mediaBlobHash)
        assertEquals(4200L, parsed.mediaDurationMs)
    }

    @Test
    fun testDirectMessageContentBackwardCompatibility() {
        val rawLegacyText = "Just an old raw text message"
        val parsed = DirectMessageContent.fromDecryptedString(rawLegacyText)
        assertEquals(rawLegacyText, parsed.text)
        assertNull(parsed.mediaType)
        assertNull(parsed.mediaBlobHash)
    }

    @Test
    fun testDirectMessageReceiptPayloadSerialization() {
        val payload = com.mysocial.mesh.model.DirectMessageReceiptPayload(
            messageId = "msg_12345",
            originalSenderId = "@Alice12345.ed25519",
            recipientId = "@Bob67890.ed25519",
            status = 2, // DELIVERED
            timestamp = 1700000000000L
        )

        val json = payload.toJson()
        val parsed = com.mysocial.mesh.model.DirectMessageReceiptPayload.fromJson(json)

        assertEquals("msg_12345", parsed.messageId)
        assertEquals("@Alice12345.ed25519", parsed.originalSenderId)
        assertEquals("@Bob67890.ed25519", parsed.recipientId)
        assertEquals(2, parsed.status)
        assertEquals(1700000000000L, parsed.timestamp)

        // Read receipt status = 3
        val readPayload = payload.copy(status = 3)
        val readJson = readPayload.toJson()
        val readParsed = com.mysocial.mesh.model.DirectMessageReceiptPayload.fromJson(readJson)
        assertEquals(3, readParsed.status)
    }

    @Test
    fun testTamperedIvFailsDecryption() {
        val alice = CryptoManager.createInMemory()
        val bob = CryptoManager.createInMemory()

        val plaintext = "Secret data"
        val payload = alice.encryptDirectMessage(bob.authorId, plaintext)

        // Alter the IV
        val firstChar = payload.ivBase64.first()
        val replacementChar = if (firstChar == 'A') 'B' else 'A'
        val tamperedIv = replacementChar + payload.ivBase64.substring(1)
        val tamperedPayload = payload.copy(ivBase64 = tamperedIv)

        val decrypted = bob.decryptDirectMessage(tamperedPayload)
        assertNull("Tampered IV must cause GCM authentication failure", decrypted)
    }

    @Test
    fun testTamperedEphemeralPublicKeyFailsDecryption() {
        val alice = CryptoManager.createInMemory()
        val bob = CryptoManager.createInMemory()

        val plaintext = "Secret data"
        val payload = alice.encryptDirectMessage(bob.authorId, plaintext)

        // Alter the ephemeral public key
        val firstChar = payload.ephemeralPublicKeyBase64.first()
        val replacementChar = if (firstChar == 'A') 'B' else 'A'
        val tamperedKey = replacementChar + payload.ephemeralPublicKeyBase64.substring(1)
        val tamperedPayload = payload.copy(ephemeralPublicKeyBase64 = tamperedKey)

        val decrypted = bob.decryptDirectMessage(tamperedPayload)
        assertNull("Tampered ephemeral key must produce wrong shared secret and fail MAC", decrypted)
    }

    @Test
    fun testLargePayloadEncryptionAndDecryption() {
        val alice = CryptoManager.createInMemory()
        val bob = CryptoManager.createInMemory()

        // 50,000 character string
        val largeText = "A".repeat(50_000)
        val payload = alice.encryptDirectMessage(bob.authorId, largeText)
        val decrypted = bob.decryptDirectMessage(payload)

        assertEquals("Large payload (50KB) must encrypt and decrypt without truncation", largeText, decrypted)
    }

    @Test
    fun testEmptyStringEncryptionAndDecryption() {
        val alice = CryptoManager.createInMemory()
        val bob = CryptoManager.createInMemory()

        val emptyText = ""
        val payload = alice.encryptDirectMessage(bob.authorId, emptyText)
        val decrypted = bob.decryptDirectMessage(payload)

        assertEquals("", decrypted)
    }

    @Test
    fun testBirationalMappingDeterminism() {
        val pubBytes = ByteArray(32) { (it * 7).toByte() }
        val xPub1 = E2eeManager.ed25519PubToX25519(pubBytes)
        val xPub2 = E2eeManager.ed25519PubToX25519(pubBytes)

        assertEquals(32, xPub1.size)
        org.junit.Assert.assertArrayEquals("Birational map must be strictly deterministic", xPub1, xPub2)
    }
}

