package com.mysocial.mesh

import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.crypto.E2eeManager
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

        // Tamper with the ciphertext Base64
        val tamperedCiphertext = payload.ciphertextBase64.replaceFirst("A", "B")
        val tamperedPayload = payload.copy(ciphertextBase64 = tamperedCiphertext)

        val decrypted = bob.decryptDirectMessage(tamperedPayload)
        assertNull("Tampered GCM ciphertext must fail authentication and return null", decrypted)
    }
}

