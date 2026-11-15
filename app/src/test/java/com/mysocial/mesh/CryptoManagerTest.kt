package com.mysocial.mesh

import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.FollowPayload
import com.mysocial.mesh.model.LogEntry
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.model.ProfilePayload
import com.mysocial.mesh.model.ReactionPayload
import com.mysocial.mesh.model.ReplyPayload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CryptoManagerTest {

    @Test
    fun testIdentityGenerationAndExtraction() {
        val crypto = CryptoManager.createInMemory()
        assertNotNull(crypto.authorId)
        assertTrue(crypto.authorId.startsWith("@"))
        assertTrue(crypto.authorId.endsWith(".ed25519"))
        assertEquals(32, crypto.publicKeyBytes.size)

        val extracted = CryptoManager.extractPublicKeyBytes(crypto.authorId)
        assertEquals(32, extracted.size)
        assertEquals(crypto.authorId, CryptoManager.formatAuthorId(extracted))
    }

    @Test
    fun testDeterministicDeviceIdentityPersistence() {
        val seedA = ByteArray(32) { (it * 7).toByte() }
        val privParams1 = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(seedA, 0)
        val pubParams1 = privParams1.generatePublicKey()
        val authorId1 = CryptoManager.formatAuthorId(pubParams1.encoded)

        // Simulating second install/generation with same device seed
        val privParams2 = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(seedA, 0)
        val pubParams2 = privParams2.generatePublicKey()
        val authorId2 = CryptoManager.formatAuthorId(pubParams2.encoded)

        assertEquals("Author IDs must be strictly identical across reinstalls", authorId1, authorId2)
        assertTrue(authorId1.startsWith("@"))
        assertTrue(authorId1.endsWith(".ed25519"))
        assertEquals(32, pubParams1.encoded.size)
        assertEquals(32, privParams1.encoded.size)
    }

    @Test
    fun testCanonicalSigningAndVerification() {
        val alice = CryptoManager.createInMemory()
        val bob = CryptoManager.createInMemory()

        val post = PostPayload(text = "Hello P2P Mesh!", tags = listOf("mesh", "offline"))
        val contentJson = post.toJson()
        val timestamp = 1700000000000L
        val sequence = 1L
        val previousHash: String? = null

        val canonical = alice.buildCanonicalPayload(
            authorId = alice.authorId,
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = ContentType.POST.name,
            contentJson = contentJson
        )

        val signature = alice.sign(canonical)
        assertNotNull(signature)
        assertTrue(signature.isNotBlank())

        // Verification should succeed with Alice's public key
        val isValid = CryptoManager.verify(alice.authorId, canonical, signature)
        assertTrue("Signature should verify against Alice's authorId", isValid)

        // Verification should fail with Bob's authorId
        val isValidWithBob = CryptoManager.verify(bob.authorId, canonical, signature)
        assertFalse("Signature must fail against Bob's authorId", isValidWithBob)

        // Verification should fail if payload is tampered
        val tamperedPayload = canonical + " "
        val isTamperedValid = CryptoManager.verify(alice.authorId, tamperedPayload, signature)
        assertFalse("Tampered payload must fail signature verification", isTamperedValid)
    }

    @Test
    fun testEntryHashDeterminism() {
        val alice = CryptoManager.createInMemory()
        val post = PostPayload(text = "Deterministic Hash Test")
        val contentJson = post.toJson()
        val timestamp = 1700000001000L
        val sequence = 1L
        val previousHash: String? = null

        val signature = alice.signEntry(
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = ContentType.POST.name,
            contentJson = contentJson
        )

        val hash1 = CryptoManager.computeEntryHash(
            authorId = alice.authorId,
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = ContentType.POST.name,
            contentJson = contentJson,
            signature = signature
        )

        val hash2 = CryptoManager.computeEntryHash(
            authorId = alice.authorId,
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = ContentType.POST.name,
            contentJson = contentJson,
            signature = signature
        )

        assertEquals(64, hash1.length) // 32-byte hex is 64 chars
        assertEquals(hash1, hash2)
    }

    @Test
    fun testPayloadJsonRoundtrips() {
        val post = PostPayload("Hello world", listOf("test", "p2p"))
        val postJson = post.toJson()
        val postParsed = PostPayload.fromJson(postJson)
        assertEquals(post, postParsed)

        val reply = ReplyPayload("Reply text", "root123", "parent456")
        val replyJson = reply.toJson()
        val replyParsed = ReplyPayload.fromJson(replyJson)
        assertEquals(reply, replyParsed)

        val reaction = ReactionPayload("target123", "🔥")
        val reactionJson = reaction.toJson()
        val reactionParsed = ReactionPayload.fromJson(reactionJson)
        assertEquals(reaction, reactionParsed)

        val profile = ProfilePayload("Alice", "Building offline networks", "seed123")
        val profileJson = profile.toJson()
        val profileParsed = ProfilePayload.fromJson(profileJson)
        assertEquals(profile, profileParsed)

        val follow = FollowPayload("@target.ed25519", true)
        val followJson = follow.toJson()
        val followParsed = FollowPayload.fromJson(followJson)
        assertEquals(follow, followParsed)
    }

    @Test
    fun testSequentialLogEntryCreation() {
        val alice = CryptoManager.createInMemory()

        // Entry 1: Profile update
        val profile = ProfilePayload("Alice M.", "Mesh Pioneer", "avatar_seed_1")
        val pJson = profile.toJson()
        val t1 = 1700000010000L
        val sig1 = alice.signEntry(1L, null, t1, ContentType.PROFILE.name, pJson)
        val hash1 = CryptoManager.computeEntryHash(alice.authorId, 1L, null, t1, ContentType.PROFILE.name, pJson, sig1)

        val entry1 = LogEntry(
            authorId = alice.authorId,
            sequence = 1L,
            previousHash = null,
            timestamp = t1,
            contentType = ContentType.PROFILE,
            contentJson = pJson,
            signature = sig1,
            entryHash = hash1
        )

        assertEquals(1L, entry1.sequence)
        assertEquals(null, entry1.previousHash)
        assertTrue(CryptoManager.verify(
            alice.authorId,
            alice.buildCanonicalPayload(alice.authorId, 1L, null, t1, ContentType.PROFILE.name, pJson),
            sig1
        ))

        // Entry 2: Post referencing hash1 as previousHash
        val post = PostPayload("My first mesh post!", listOf("intro"))
        val postJson = post.toJson()
        val t2 = 1700000020000L
        val sig2 = alice.signEntry(2L, hash1, t2, ContentType.POST.name, postJson)
        val hash2 = CryptoManager.computeEntryHash(alice.authorId, 2L, hash1, t2, ContentType.POST.name, postJson, sig2)

        val entry2 = LogEntry(
            authorId = alice.authorId,
            sequence = 2L,
            previousHash = hash1,
            timestamp = t2,
            contentType = ContentType.POST,
            contentJson = postJson,
            signature = sig2,
            entryHash = hash2
        )

        assertEquals(2L, entry2.sequence)
        assertEquals(hash1, entry2.previousHash)
        assertTrue(CryptoManager.verify(
            alice.authorId,
            alice.buildCanonicalPayload(alice.authorId, 2L, hash1, t2, ContentType.POST.name, postJson),
            sig2
        ))
    }

    @Test
    fun testComputeSharedSecretSymmetryAndCorrectness() {
        val alice = CryptoManager.createInMemory()
        val bob = CryptoManager.createInMemory()
        val charlie = CryptoManager.createInMemory()

        // Alice computes shared secret with Bob
        val aliceBobSecret = alice.computeSharedSecret(bob.authorId)
        assertNotNull("Alice to Bob shared secret must not be null", aliceBobSecret)
        assertEquals(32, aliceBobSecret!!.size)

        // Bob computes shared secret with Alice
        val bobAliceSecret = bob.computeSharedSecret(alice.authorId)
        assertNotNull("Bob to Alice shared secret must not be null", bobAliceSecret)
        assertEquals(32, bobAliceSecret!!.size)

        // Both parties must arrive at identical 32-byte shared secret (Diffie-Hellman agreement)
        assertArrayEquals("Alice and Bob shared secrets must match identically", aliceBobSecret, bobAliceSecret)

        // Shared secret with Charlie must differ (cryptographic isolation)
        val aliceCharlieSecret = alice.computeSharedSecret(charlie.authorId)
        assertNotNull(aliceCharlieSecret)
        assertFalse("Alice-Charlie secret must differ from Alice-Bob secret", aliceBobSecret.contentEquals(aliceCharlieSecret))
    }

    @Test
    fun testComputeSharedSecretMalformedInputsSafeHandling() {
        val alice = CryptoManager.createInMemory()

        // Empty string
        assertNull(alice.computeSharedSecret(""))

        // Whitespace only
        assertNull(alice.computeSharedSecret("   "))

        // Malformed base64
        assertNull(alice.computeSharedSecret("@invalid_base64_symbols!#.ed25519"))

        // Truncated key (too short for 32-byte Ed25519)
        assertNull(alice.computeSharedSecret("@YWxpY2U=.ed25519"))

        // Normal username without key formatting
        assertNull(alice.computeSharedSecret("AliceSmith"))

        // Missing .ed25519 suffix
        assertNull(alice.computeSharedSecret("@dGVzdF9rZXk="))
    }

    @Test
    fun testDirectMessageEncryptionDecryptionThroughCryptoManager() {
        val alice = CryptoManager.createInMemory()
        val bob = CryptoManager.createInMemory()
        val charlie = CryptoManager.createInMemory()

        // 1. Standard text message
        val standardText = "Hello Bob! This is an offline mesh message."
        val payload1 = alice.encryptDirectMessage(bob.authorId, standardText)
        assertEquals(bob.authorId, payload1.recipientId)
        val decrypted1 = bob.decryptDirectMessage(payload1)
        assertEquals(standardText, decrypted1)

        // 2. High-Unicode, emojis, and multiline text
        val unicodeText = "Mesh Alert! 🚨 Fire detected at 40°N, 73°W 🔥\n日本語テスト • привет • नमस्ते 🌟 \uD83D\uDD12"
        val payload2 = alice.encryptDirectMessage(bob.authorId, unicodeText)
        val decrypted2 = bob.decryptDirectMessage(payload2)
        assertEquals(unicodeText, decrypted2)

        // 3. Large payload boundary test (64 KB)
        val largeText = "A".repeat(65536)
        val payload3 = alice.encryptDirectMessage(bob.authorId, largeText)
        val decrypted3 = bob.decryptDirectMessage(payload3)
        assertEquals(largeText, decrypted3)

        // 4. Unauthorized recipient Charlie cannot decrypt
        val unauthorizedDecrypted = charlie.decryptDirectMessage(payload1)
        assertNull("Charlie must NOT be able to decrypt message intended for Bob", unauthorizedDecrypted)

        // 5. Sender Alice cannot decrypt her own outgoing message with static key (ephemeral ECDH property)
        val senderDecrypted = alice.decryptDirectMessage(payload1)
        assertNull("Sender static private key cannot decrypt ephemeral-static message", senderDecrypted)
    }

    @Test
    fun testDirectMessageTamperResistance() {
        val alice = CryptoManager.createInMemory()
        val bob = CryptoManager.createInMemory()

        val text = "Transfer 100 mesh tokens to node X"
        val payload = alice.encryptDirectMessage(bob.authorId, text)

        // Tamper 1: Tampered ciphertext (flip first character)
        val tamperedCipher = (if (payload.ciphertextBase64[0] == 'A') 'B' else 'A') + payload.ciphertextBase64.substring(1)
        assertNull(bob.decryptDirectMessage(payload.copy(ciphertextBase64 = tamperedCipher)))

        // Tamper 2: Tampered ephemeral public key
        val tamperedPub = (if (payload.ephemeralPublicKeyBase64[0] == 'A') 'B' else 'A') + payload.ephemeralPublicKeyBase64.substring(1)
        assertNull(bob.decryptDirectMessage(payload.copy(ephemeralPublicKeyBase64 = tamperedPub)))

        // Tamper 3: Tampered IV
        val tamperedIv = (if (payload.ivBase64[0] == 'A') 'B' else 'A') + payload.ivBase64.substring(1)
        assertNull(bob.decryptDirectMessage(payload.copy(ivBase64 = tamperedIv)))
    }

    @Test
    fun testAuthorIdExtractionVariantsAndEdgeCases() {
        val alice = CryptoManager.createInMemory()
        val originalAuthorId = alice.authorId

        // Extract with standard format: @<base64>.ed25519
        val bytes1 = CryptoManager.extractPublicKeyBytes(originalAuthorId)
        assertEquals(32, bytes1.size)
        assertEquals(originalAuthorId, CryptoManager.formatAuthorId(bytes1))

        // Extract with leading/trailing whitespace
        val bytes2 = CryptoManager.extractPublicKeyBytes("  $originalAuthorId  \n")
        assertArrayEquals(bytes1, bytes2)

        // Extract from raw Base64 without '@' or '.ed25519'
        val rawBase64 = java.util.Base64.getEncoder().encodeToString(alice.publicKeyBytes)
        val bytes3 = CryptoManager.extractPublicKeyBytes(rawBase64)
        assertArrayEquals(bytes1, bytes3)
    }

    @Test
    fun testSignatureVerificationCacheIntegrityAndCollisionDefense() {
        val alice = CryptoManager.createInMemory()
        val bob = CryptoManager.createInMemory()

        val payloadA = "CanonicalPayload_A_12345"
        val payloadB = "CanonicalPayload_B_67890"

        val sigA = alice.sign(payloadA)

        // 1. Initial verification (uncached)
        assertTrue(CryptoManager.verify(alice.authorId, payloadA, sigA))

        // 2. Subsequent verification (LRU cached)
        assertTrue(CryptoManager.verify(alice.authorId, payloadA, sigA))

        // 3. Cache collision defense: Verifying payload B with sig A must return false
        assertFalse(CryptoManager.verify(alice.authorId, payloadB, sigA))

        // 4. Verifying payload A with Bob's authorId must return false
        assertFalse(CryptoManager.verify(bob.authorId, payloadA, sigA))

        // 5. Corrupted signature string length (not 64 bytes) must return false safely
        assertFalse(CryptoManager.verify(alice.authorId, payloadA, "dG9vX3Nob3J0"))

        // 6. Invalid base64 in signature must return false safely
        assertFalse(CryptoManager.verify(alice.authorId, payloadA, "!!!not_base64!!!"))
    }

    @Test
    fun testCanonicalPayloadFormattingRobustness() {
        val authorId = "@alice.ed25519"
        val seq = 42L
        val timestamp = 1700000000000L
        val contentType = "POST"

        // Null previousHash
        val canonical1 = CryptoManager.buildCanonicalPayload(
            authorId = authorId,
            sequence = seq,
            previousHash = null,
            timestamp = timestamp,
            contentType = contentType,
            contentJson = "{\"text\":\"Hello\"}"
        )
        assertEquals("@alice.ed25519:42:null:1700000000000:POST:{\"text\":\"Hello\"}", canonical1)

        // Non-null previousHash
        val canonical2 = CryptoManager.buildCanonicalPayload(
            authorId = authorId,
            sequence = seq,
            previousHash = "prev_hash_abc123",
            timestamp = timestamp,
            contentType = contentType,
            contentJson = "{\"text\":\"colon:in:text:and\nnewlines\"}"
        )
        assertEquals("@alice.ed25519:42:prev_hash_abc123:1700000000000:POST:{\"text\":\"colon:in:text:and\nnewlines\"}", canonical2)
    }

    @Test
    fun testNistStandardSha256Vectors() {
        // NIST test vector: Empty string
        val emptyHex = CryptoManager.sha256Hex("")
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHex)

        // NIST test vector: "abc"
        val abcHex = CryptoManager.sha256Hex("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", abcHex)

        // NIST test vector: "message digest"
        val mdHex = CryptoManager.sha256Hex("message digest")
        assertEquals("f7846f55cf23e14eebeab5b4e1550cad5b509e3348fbc4efa3a1413d393cb650", mdHex)
    }

    @Test
    fun testConcurrentCryptographicOperationsThreadSafety() {
        val numThreads = 16
        val iterationsPerThread = 50
        val executor = Executors.newFixedThreadPool(numThreads)
        val successCount = AtomicInteger(0)

        for (i in 0 until numThreads) {
            executor.submit {
                for (j in 0 until iterationsPerThread) {
                    val alice = CryptoManager.createInMemory()
                    val bob = CryptoManager.createInMemory()

                    // Shared secret
                    val s1 = alice.computeSharedSecret(bob.authorId)
                    val s2 = bob.computeSharedSecret(alice.authorId)
                    if (s1 == null || s2 == null || !s1.contentEquals(s2)) return@submit

                    // Sign and verify
                    val payload = "thread_${i}_iteration_${j}_payload"
                    val sig = alice.sign(payload)
                    if (!CryptoManager.verify(alice.authorId, payload, sig)) return@submit

                    // Hash
                    val h = CryptoManager.sha256Hex(payload)
                    if (h.length != 64) return@submit

                    successCount.incrementAndGet()
                }
            }
        }

        executor.shutdown()
        assertTrue("Executor should terminate within 15 seconds", executor.awaitTermination(15, TimeUnit.SECONDS))
        assertEquals(numThreads * iterationsPerThread, successCount.get())
    }
}
