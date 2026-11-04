package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.crypto.ProofOfWorkEngine
import com.mysocial.mesh.data.LogValidator
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.data.ValidationResult
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.LogEntry
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.nativebridge.NativeMeshCore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProofOfWorkAndNativeTest {

    private lateinit var context: Context
    private lateinit var db: MeshDatabase
    private lateinit var cryptoManager: CryptoManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = MeshDatabase.buildInMemory(context)
        cryptoManager = CryptoManager.createInMemory(seed = ByteArray(32) { 42 })
        db.cryptoManager = cryptoManager
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testMineAndVerifyHashcashNonce() {
        val authorId = cryptoManager.authorId
        val sequence = 1L
        val previousHash: String? = null
        val timestamp = System.currentTimeMillis()
        val contentType = ContentType.POST.name
        val contentJson = PostPayload(text = "Decentralized mesh test post").toJson()

        val signature = cryptoManager.signEntry(
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = contentType,
            contentJson = contentJson
        )

        val entryHash = CryptoManager.computeEntryHash(
            authorId = authorId,
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = contentType,
            contentJson = contentJson,
            signature = signature
        )

        val minedNonce = ProofOfWorkEngine.mineNonce(
            authorId = authorId,
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = contentType,
            contentJson = contentJson,
            signature = signature,
            isSos = false
        )

        val validEntry = LogEntry(
            authorId = authorId,
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = ContentType.POST,
            contentJson = contentJson,
            signature = signature,
            entryHash = entryHash,
            powNonce = minedNonce
        )

        assertTrue(ProofOfWorkEngine.verifyEntry(validEntry, isSos = false))

        // Tampered nonce must fail
        val tamperedEntry = validEntry.copy(powNonce = minedNonce xor 0xDEADBEEF)
        assertFalse(ProofOfWorkEngine.verifyEntry(tamperedEntry, isSos = false))
    }

    @Test
    fun testEmergencySosDifficultyVerification() {
        val authorId = cryptoManager.authorId
        val sequence = 1L
        val previousHash: String? = null
        val timestamp = System.currentTimeMillis()
        val contentType = ContentType.POST.name
        val contentJson = PostPayload(text = "🚨 Flood evacuation alert", isSos = true).toJson()

        val signature = cryptoManager.signEntry(
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = contentType,
            contentJson = contentJson
        )

        val entryHash = CryptoManager.computeEntryHash(
            authorId = authorId,
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = contentType,
            contentJson = contentJson,
            signature = signature
        )

        val sosNonce = ProofOfWorkEngine.mineNonce(
            authorId = authorId,
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = contentType,
            contentJson = contentJson,
            signature = signature,
            isSos = true
        )

        val sosEntry = LogEntry(
            authorId = authorId,
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = ContentType.POST,
            contentJson = contentJson,
            signature = signature,
            entryHash = entryHash,
            powNonce = sosNonce
        )

        assertTrue(ProofOfWorkEngine.verifyEntry(sosEntry, isSos = true))
    }

    @Test
    fun testBackwardCompatibilityLegacyZeroNonce() {
        val authorId = cryptoManager.authorId
        val legacyEntry = LogEntry(
            authorId = authorId,
            sequence = 1L,
            previousHash = null,
            timestamp = System.currentTimeMillis(),
            contentType = ContentType.POST,
            contentJson = "{}",
            signature = "legacy_signature",
            entryHash = "legacy_hash",
            powNonce = 0L // Legacy entry
        )

        assertTrue(ProofOfWorkEngine.verifyEntry(legacyEntry))
    }

    @Test
    fun testFecParityEncodingAndReconstruction() {
        val chunk1 = "Hello, Mesh Radio!".toByteArray(Charsets.UTF_8)
        val chunk2 = "Bluetooth LE 2Mbps".toByteArray(Charsets.UTF_8)
        val chunk3 = "Zero Server P2P Net".toByteArray(Charsets.UTF_8)

        val shards = arrayOf(chunk1, chunk2, chunk3)
        val parity = NativeMeshCore.encodeParity(shards)
        assertTrue(parity.isNotEmpty())

        // Simulate dropping chunk2 during BLE transmission
        val presentShards = arrayOf(chunk1, chunk3)
        val recoveredChunk2 = NativeMeshCore.reconstructShard(presentShards, parity)

        // Verify recovered chunk matches original dropped chunk
        assertEquals(String(chunk2, Charsets.UTF_8), String(recoveredChunk2.copyOf(chunk2.size), Charsets.UTF_8))
    }

    @Test
    fun testLogValidatorEnforcesProofOfWork(): Unit = runBlocking {
        val authorId = cryptoManager.authorId
        val sequence = 1L
        val timestamp = System.currentTimeMillis()
        val contentType = ContentType.POST.name
        val contentJson = PostPayload(text = "Validating PoW Gate").toJson()

        val signature = cryptoManager.signEntry(
            sequence = sequence,
            previousHash = null,
            timestamp = timestamp,
            contentType = contentType,
            contentJson = contentJson
        )

        val entryHash = CryptoManager.computeEntryHash(
            authorId = authorId,
            sequence = sequence,
            previousHash = null,
            timestamp = timestamp,
            contentType = contentType,
            contentJson = contentJson,
            signature = signature
        )

        val minedNonce = ProofOfWorkEngine.mineNonce(
            authorId = authorId,
            sequence = sequence,
            previousHash = null,
            timestamp = timestamp,
            contentType = contentType,
            contentJson = contentJson,
            signature = signature,
            isSos = false
        )

        val validEntry = LogEntry(
            authorId = authorId,
            sequence = sequence,
            previousHash = null,
            timestamp = timestamp,
            contentType = ContentType.POST,
            contentJson = contentJson,
            signature = signature,
            entryHash = entryHash,
            powNonce = minedNonce
        )

        val validResult = LogValidator.validate(validEntry, db)
        assertTrue(validResult is ValidationResult.Valid)

        // Invalid non-zero nonce must be rejected by LogValidator
        val badEntry = validEntry.copy(powNonce = 999999999L)
        val invalidResult = LogValidator.validate(badEntry, db)
        assertTrue(invalidResult is ValidationResult.Invalid)
        assertTrue((invalidResult as ValidationResult.Invalid).reason.contains("Proof-of-Work"))
    }
}
