package com.mysocial.mesh

import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.utils.TrustQrUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustQrUtilsTest {

    @Test
    fun testTrustQrGenerationAndVerification() {
        val alice = CryptoManager.createInMemory(seed = ByteArray(32) { 5 })
        val qrPayload = TrustQrUtils.createTrustPayload(
            authorId = alice.authorId,
            displayName = "Alice Wonderland",
            crypto = alice
        )

        assertTrue(qrPayload.startsWith("mysocial://trust?id="))
        assertTrue(qrPayload.contains("&name="))
        assertTrue(qrPayload.contains("&sig="))

        val parsed = TrustQrUtils.parseAndVerifyTrustPayload(qrPayload)
        assertNotNull(parsed)
        assertEquals(alice.authorId, parsed!!.authorId)
        assertEquals("Alice Wonderland", parsed.displayName)
        assertTrue("Signature must verify successfully", parsed.isValidSignature)
    }

    @Test
    fun testTamperedTrustPayloadFails() {
        val alice = CryptoManager.createInMemory(seed = ByteArray(32) { 5 })
        val qrPayload = TrustQrUtils.createTrustPayload(
            authorId = alice.authorId,
            displayName = "Alice",
            crypto = alice
        )

        // Tamper with the name
        val tampered = qrPayload.replace("name=Alice", "name=Mallory")
        val parsed = TrustQrUtils.parseAndVerifyTrustPayload(tampered)
        assertNotNull(parsed)
        assertFalse("Tampered payload must fail signature verification", parsed!!.isValidSignature)
    }
}

