package com.mysocial.mesh

import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.FollowPayload
import com.mysocial.mesh.model.LogEntry
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.model.ProfilePayload
import com.mysocial.mesh.model.ReactionPayload
import com.mysocial.mesh.model.ReplyPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
