package com.mysocial.mesh

import com.mysocial.mesh.model.CanonicalJson
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.DirectMessageReceiptPayload
import com.mysocial.mesh.model.FollowPayload
import com.mysocial.mesh.model.MediaAttachment
import com.mysocial.mesh.model.PostDeletePayload
import com.mysocial.mesh.model.PostEditPayload
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.model.ProfilePayload
import com.mysocial.mesh.model.ReactionPayload
import com.mysocial.mesh.model.ReplyPayload
import com.mysocial.mesh.protocol.GossipMessage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireProtocolSerializationTest {

    @Test
    fun testHelloVectorSerializationAndBackwardCompatibility() {
        // Modern HelloVector with app update fields
        val modernHello = GossipMessage.HelloVector(
            peerId = "@alice.ed25519",
            peerName = "Alice",
            vectorClock = mapOf("@alice.ed25519" to 10L, "@bob.ed25519" to 5L),
            openMeshMode = true,
            appVersionCode = 41L,
            appVersionName = "1.0.41",
            apkServerPort = 42531
        )
        val json = modernHello.encode()
        val decoded = GossipMessage.decode(json) as GossipMessage.HelloVector

        assertEquals("@alice.ed25519", decoded.peerId)
        assertEquals("Alice", decoded.peerName)
        assertEquals(10L, decoded.vectorClock["@alice.ed25519"])
        assertEquals(41L, decoded.appVersionCode)
        assertEquals("1.0.41", decoded.appVersionName)
        assertEquals(42531, decoded.apkServerPort)

        // Legacy HelloVector missing appVersionCode, appVersionName, apkServerPort
        val legacyJson = """{"peerId":"@legacy.ed25519","vectorClock":{"@legacy.ed25519":1}}"""
        val legacyDecoded = CanonicalJson.decodeFromString<GossipMessage.HelloVector>(legacyJson)

        assertEquals("@legacy.ed25519", legacyDecoded.peerId)
        assertEquals(1L, legacyDecoded.vectorClock["@legacy.ed25519"])
        assertEquals(0L, legacyDecoded.appVersionCode)
        assertEquals("", legacyDecoded.appVersionName)
        assertEquals(0, legacyDecoded.apkServerPort)
    }

    @Test
    fun testBlobWireMessagesSerialization() {
        // BlobReq with bitfield helper
        val bitfield = com.mysocial.mesh.protocol.BlobBitfieldHelper.encodeMissingIndices(listOf(0, 2), 4)
        val req = GossipMessage.BlobReq(blobHash = "hash123", missingChunksBitfield = bitfield)
        val reqJson = req.encode()
        val reqDecoded = GossipMessage.decode(reqJson) as GossipMessage.BlobReq
        assertEquals("hash123", reqDecoded.blobHash)
        assertEquals(listOf(0, 2), reqDecoded.getMissingIndices(4))

        // BlobHave
        val have = GossipMessage.BlobHave(blobHashes = listOf("hash1", "hash2"))
        val haveJson = have.encode()
        val haveDecoded = GossipMessage.decode(haveJson) as GossipMessage.BlobHave
        assertEquals(listOf("hash1", "hash2"), haveDecoded.blobHashes)

        // BlobChunk
        val chunk = GossipMessage.BlobChunk(
            blobHash = "hash123",
            chunkIndex = 0,
            totalChunks = 4,
            bytesBase64 = "SGVsbG8gV29ybGQ=" // "Hello World"
        )
        val chunkJson = chunk.encode()
        val chunkDecoded = GossipMessage.decode(chunkJson) as GossipMessage.BlobChunk
        assertEquals(0, chunkDecoded.chunkIndex)
        assertEquals(4, chunkDecoded.totalChunks)
        assertEquals("SGVsbG8gV29ybGQ=", chunkDecoded.bytesBase64)
    }

    @Test
    fun testPostPayloadExhaustiveFields() {
        val attachment = MediaAttachment(
            blobHash = "blob_photo_1",
            mimeType = "image/webp",
            totalBytes = 150_000L,
            chunkCount = 10,
            microPreviewBase64 = "data:image/webp;base64,AAA"
        )
        val fullPost = PostPayload(
            text = "Check out this mountain summit! #hiking #nature",
            tags = listOf("hiking", "nature"),
            media = attachment,
            isPublic = false, // Friends only
            expiresAt = 1800000000000L,
            isSos = false,
            pollOptions = listOf("Option A", "Option B", "Option C")
        )
        val json = fullPost.toJson()
        val decoded = PostPayload.fromJson(json)

        assertEquals(fullPost.text, decoded.text)
        assertEquals(listOf("hiking", "nature"), decoded.tags)
        assertNotNull(decoded.media)
        assertEquals("blob_photo_1", decoded.media!!.blobHash)
        assertEquals(false, decoded.isPublic)
        assertEquals(1800000000000L, decoded.expiresAt)
        assertFalse(decoded.isSos)
        assertEquals(3, decoded.pollOptions!!.size)
        assertEquals("Option A", decoded.pollOptions!![0])
    }

    @Test
    fun testAllContentTypeEnumsAreDeterministicallySerialized() {
        for (type in ContentType.values()) {
            val json = CanonicalJson.encodeToString(type)
            val decoded = CanonicalJson.decodeFromString<ContentType>(json)
            assertEquals("ContentType $type must round-trip cleanly", type, decoded)
        }
    }

    @Test
    fun testPostEditAndDeletePayloads() {
        val edit = PostEditPayload(targetHash = "target_1", newText = "Updated text content")
        val editDecoded = PostEditPayload.fromJson(edit.toJson())
        assertEquals("target_1", editDecoded.targetHash)
        assertEquals("Updated text content", editDecoded.newText)

        val delete = PostDeletePayload(targetHash = "target_2")
        val deleteDecoded = PostDeletePayload.fromJson(delete.toJson())
        assertEquals("target_2", deleteDecoded.targetHash)
    }

    @Test
    fun testReplyAndReactionPayloads() {
        val reply = ReplyPayload(text = "Great photo!", rootHash = "root_100", parentHash = "parent_100")
        val replyDecoded = ReplyPayload.fromJson(reply.toJson())
        assertEquals("Great photo!", replyDecoded.text)
        assertEquals("root_100", replyDecoded.rootHash)
        assertEquals("parent_100", replyDecoded.parentHash)

        val reaction = ReactionPayload(targetHash = "target_100", emoji = "🔥", pollOptionIndex = 1)
        val reactionDecoded = ReactionPayload.fromJson(reaction.toJson())
        assertEquals("target_100", reactionDecoded.targetHash)
        assertEquals("🔥", reactionDecoded.emoji)
        assertEquals(1, reactionDecoded.pollOptionIndex)
    }

    @Test
    fun testProfileAndFollowPayloads() {
        val profile = ProfilePayload(displayName = "Yuvraj", bio = "Offline Mesh Pioneer", avatarSeed = "seed123")
        val profileDecoded = ProfilePayload.fromJson(profile.toJson())
        assertEquals("Yuvraj", profileDecoded.displayName)
        assertEquals("Offline Mesh Pioneer", profileDecoded.bio)
        assertEquals("seed123", profileDecoded.avatarSeed)

        val follow = FollowPayload(targetAuthorId = "@bob.ed25519", following = true)
        val followDecoded = FollowPayload.fromJson(follow.toJson())
        assertEquals("@bob.ed25519", followDecoded.targetAuthorId)
        assertTrue(followDecoded.following)
    }
}
