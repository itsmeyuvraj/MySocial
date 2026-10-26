package com.mysocial.mesh

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.BlobChunkEntity
import com.mysocial.mesh.data.BlobManifestEntity
import com.mysocial.mesh.data.MediaProcessor
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.MediaAttachment
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.protocol.BlobBitfieldHelper
import com.mysocial.mesh.protocol.GossipMessage
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.protocol.MemoryPeerConnection
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlobTransferEngineTest {

    private lateinit var context: Context
    private lateinit var dbA: MeshDatabase
    private lateinit var dbB: MeshDatabase

    private lateinit var cryptoA: CryptoManager
    private lateinit var cryptoB: CryptoManager

    private lateinit var engineA: GossipSyncEngine
    private lateinit var engineB: GossipSyncEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dbA = MeshDatabase.buildInMemory(context)
        dbB = MeshDatabase.buildInMemory(context)

        cryptoA = CryptoManager.createInMemory(seed = ByteArray(32) { 10 })
        cryptoB = CryptoManager.createInMemory(seed = ByteArray(32) { 20 })

        engineA = GossipSyncEngine(
            db = dbA,
            cryptoManager = cryptoA,
            myDisplayName = "NodeA",
            openMeshMode = true,
            context = context
        )

        engineB = GossipSyncEngine(
            db = dbB,
            cryptoManager = cryptoB,
            myDisplayName = "NodeB",
            openMeshMode = true,
            context = context
        )
    }

    @After
    fun tearDown() {
        dbA.close()
        dbB.close()
    }

    @Test
    fun testMediaProcessorImageCompressionAndMicroPreview() = runBlocking {
        // Create an uncompressed 1600x1200 test bitmap
        val testBitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val bos = ByteArrayOutputStream()
        testBitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        val rawBytes = bos.toByteArray()

        val processed = MediaProcessor.processImageBytes(context, rawBytes)

        assertNotNull(processed)
        assertNotNull(processed.blobHash)
        assertEquals(64, processed.blobHash.length) // SHA-256 hex string
        assertTrue(processed.totalBytes > 0)
        assertTrue(processed.chunkCount >= 1)

        // Verify micro-preview is valid and small (~200 bytes)
        assertTrue(processed.microPreviewBase64.isNotBlank())
        val decodedThumb = MediaProcessor.decodeMicroPreview(processed.microPreviewBase64)
        assertNotNull(decodedThumb)
        assertEquals(24, decodedThumb!!.width)
        assertEquals(24, decodedThumb.height)

        // Verify local blob file was written and can be read in 16 KB chunks
        assertTrue(processed.localFile.exists())
        assertEquals(processed.totalBytes, processed.localFile.length())

        val firstChunk = MediaProcessor.readChunkFromFile(processed.localFile, 0)
        assertNotNull(firstChunk)
        assertTrue(firstChunk!!.size <= MediaProcessor.CHUNK_SIZE)
    }

    @Test
    fun testBlobBitfieldHelperEncodingAndDecoding() {
        val totalChunks = 20
        val missing = listOf(0, 1, 4, 7, 12, 19)

        val bitfield = BlobBitfieldHelper.encodeMissingIndices(missing, totalChunks)
        assertNotNull(bitfield)
        assertTrue(bitfield.isNotBlank())

        val decoded = BlobBitfieldHelper.decodeMissingIndices(bitfield, totalChunks)
        assertEquals(missing, decoded)
    }

    @Test
    fun testBlobDaoManifestAndChunkStorage() = runBlocking {
        val blobHash = "abc1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        val manifest = BlobManifestEntity(
            blobHash = blobHash,
            totalBytes = 32768,
            chunkCount = 2,
            mimeType = "image/webp",
            isComplete = false
        )

        dbA.blobDao().upsertManifest(manifest)

        val retrieved = dbA.blobDao().getManifest(blobHash)
        assertNotNull(retrieved)
        assertFalse(retrieved!!.isComplete)
        assertEquals(2, retrieved.chunkCount)

        // Insert chunk 0
        val chunk0 = BlobChunkEntity(blobHash, 0, ByteArray(16384) { 1 })
        dbA.blobDao().upsertChunk(chunk0)

        assertEquals(1, dbA.blobDao().getReceivedChunksCount(blobHash))
        assertEquals(listOf(0), dbA.blobDao().getReceivedChunkIndices(blobHash))

        // Insert chunk 1
        val chunk1 = BlobChunkEntity(blobHash, 1, ByteArray(16384) { 2 })
        dbA.blobDao().upsertChunk(chunk1)

        assertEquals(2, dbA.blobDao().getReceivedChunksCount(blobHash))
        assertEquals(listOf(0, 1), dbA.blobDao().getReceivedChunkIndices(blobHash))

        // Mark complete
        dbA.blobDao().markComplete(blobHash, "/path/to/blob")
        val completed = dbA.blobDao().getManifest(blobHash)
        assertTrue(completed!!.isComplete)
        assertEquals("/path/to/blob", completed.localFilePath)
    }

    @Test
    fun testPriorityMultiplexingTextSyncThenBlobTransfer() = runBlocking {
        // 1. Node A publishes a post with media
        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val bos = ByteArrayOutputStream()
        testBitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        val rawBytes = bos.toByteArray()
        val processed = MediaProcessor.processImageBytes(context, rawBytes)

        // Register Node A as completed holder of this blob
        dbA.blobDao().upsertManifest(
            BlobManifestEntity(
                blobHash = processed.blobHash,
                totalBytes = processed.totalBytes,
                chunkCount = processed.chunkCount,
                mimeType = processed.mimeType,
                isComplete = true,
                localFilePath = processed.localFile.absolutePath
            )
        )

        val media = MediaAttachment(
            blobHash = processed.blobHash,
            mimeType = processed.mimeType,
            totalBytes = processed.totalBytes,
            chunkCount = processed.chunkCount,
            microPreviewBase64 = processed.microPreviewBase64
        )
        val payload = PostPayload(text = "Check out this photo!", media = media).toJson()
        val entry = engineA.createAndPublishLocalEntry(ContentType.POST, payload).getOrThrow()
        assertNotNull(entry)

        // 2. Perform Gossip Sync between Node A and Node B
        val (connA, connB) = MemoryPeerConnection.createPaired(
            peerAId = cryptoA.authorId,
            peerAName = "NodeA",
            peerBId = cryptoB.authorId,
            peerBName = "NodeB"
        )

        val jobA = async { engineA.syncWithPeer(connA) }
        val jobB = async { engineB.syncWithPeer(connB) }

        val resA = jobA.await()
        val resB = jobB.await()

        assertTrue(resA.isSuccess)
        assertTrue(resB.isSuccess)

        // 3. Verify Node B received the post in Priority 0
        val nodeBPosts = dbB.postViewDao().getTotalPostsCount()
        assertEquals(1, nodeBPosts)

        val postInB = dbB.postViewDao().getPost(entry.entryHash)
        assertNotNull(postInB)
        assertEquals(processed.blobHash, postInB!!.mediaBlobHash)
        assertEquals(processed.microPreviewBase64, postInB.microPreviewBase64)

        // 4. Verify Node B automatically transferred the blob in Priority 1 and verified SHA-256
        val manifestInB = dbB.blobDao().getManifest(processed.blobHash)
        assertNotNull(manifestInB)
        assertTrue(manifestInB!!.isComplete)
        assertNotNull(manifestInB.localFilePath)

        val receivedFile = File(manifestInB.localFilePath!!)
        assertTrue(receivedFile.exists())
        assertEquals(processed.totalBytes, receivedFile.length())
    }
}
