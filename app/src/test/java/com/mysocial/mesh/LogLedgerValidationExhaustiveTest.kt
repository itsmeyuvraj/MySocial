package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.LogValidator
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.data.ValidationResult
import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.LogEntry
import com.mysocial.mesh.model.PostPayload
import com.mysocial.mesh.model.ProfilePayload
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogLedgerValidationExhaustiveTest {

    private lateinit var db: MeshDatabase
    private lateinit var cryptoAlice: CryptoManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = MeshDatabase.buildInMemory(context)
        cryptoAlice = CryptoManager.createInMemory(seed = ByteArray(32) { 55 })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createSignedEntry(
        sequence: Long,
        previousHash: String?,
        contentType: ContentType = ContentType.POST,
        contentJson: String = PostPayload(text = "Hello #$sequence").toJson()
    ): LogEntry {
        val now = System.currentTimeMillis() + sequence
        val signature = cryptoAlice.signEntry(
            sequence = sequence,
            previousHash = previousHash,
            timestamp = now,
            contentType = contentType.name,
            contentJson = contentJson
        )
        val entryHash = CryptoManager.computeEntryHash(
            authorId = cryptoAlice.authorId,
            sequence = sequence,
            previousHash = previousHash,
            timestamp = now,
            contentType = contentType.name,
            contentJson = contentJson,
            signature = signature
        )
        return LogEntry(
            authorId = cryptoAlice.authorId,
            sequence = sequence,
            previousHash = previousHash,
            timestamp = now,
            contentType = contentType,
            contentJson = contentJson,
            signature = signature,
            entryHash = entryHash
        )
    }

    @Test
    fun testGenesisEntryValidationRules() = runBlocking {
        // Valid Genesis entry (seq = 1, previousHash = null)
        val validGenesis = createSignedEntry(sequence = 1L, previousHash = null)
        val result = LogValidator.validate(validGenesis, db)
        assertTrue("Genesis entry must be valid", result is ValidationResult.Valid)

        // Invalid Genesis entry: sequence 1 with non-null previousHash
        val invalidGenesis = LogEntry(
            authorId = validGenesis.authorId,
            sequence = 1L,
            previousHash = "fake_previous_hash",
            timestamp = validGenesis.timestamp,
            contentType = validGenesis.contentType,
            contentJson = validGenesis.contentJson,
            signature = validGenesis.signature,
            entryHash = validGenesis.entryHash
        )
        val invalidResult = LogValidator.validate(invalidGenesis, db)
        assertTrue("Sequence 1 with non-null previousHash must be invalid", invalidResult is ValidationResult.Invalid)

        // Invalid: Sequence 0 or negative
        val seqZeroEntry = LogEntry(
            authorId = validGenesis.authorId,
            sequence = 0L,
            previousHash = null,
            timestamp = validGenesis.timestamp,
            contentType = validGenesis.contentType,
            contentJson = validGenesis.contentJson,
            signature = validGenesis.signature,
            entryHash = validGenesis.entryHash
        )
        val zeroResult = LogValidator.validate(seqZeroEntry, db)
        assertTrue("Sequence 0 must be invalid", zeroResult is ValidationResult.Invalid)
    }

    @Test
    fun testSequentialContinuityAndGaps() = runBlocking {
        // Insert Seq 1
        val entry1 = createSignedEntry(sequence = 1L, previousHash = null)
        db.insertAndProject(entry1)

        // Valid Seq 2 referencing Seq 1
        val entry2 = createSignedEntry(sequence = 2L, previousHash = entry1.entryHash)
        val res2 = LogValidator.validate(entry2, db)
        assertTrue("Sequential continuity (Seq 2 referencing Seq 1) must be valid", res2 is ValidationResult.Valid)
        db.insertAndProject(entry2)

        // Invalid Seq 3: referencing wrong previousHash
        val entry3WrongHash = createSignedEntry(sequence = 3L, previousHash = "wrong_hash_xyz")
        val res3Wrong = LogValidator.validate(entry3WrongHash, db)
        assertTrue("Wrong previousHash must be rejected", res3Wrong is ValidationResult.Invalid)

        // Invalid Seq 4: Skipping sequence 3 (sequence gap)
        val entry4Gap = createSignedEntry(sequence = 4L, previousHash = entry2.entryHash)
        val res4Gap = LogValidator.validate(entry4Gap, db)
        assertTrue("Sequence gap (missing sequence 3) must be rejected", res4Gap is ValidationResult.Invalid)
    }

    @Test
    fun testTamperedSignatureRejection() = runBlocking {
        val entry = createSignedEntry(sequence = 1L, previousHash = null)
        val tamperedSignature = entry.signature.reversed()
        val tamperedEntry = LogEntry(
            authorId = entry.authorId,
            sequence = entry.sequence,
            previousHash = entry.previousHash,
            timestamp = entry.timestamp,
            contentType = entry.contentType,
            contentJson = entry.contentJson,
            signature = tamperedSignature,
            entryHash = CryptoManager.computeEntryHash(
                entry.authorId, entry.sequence, entry.previousHash,
                entry.timestamp, entry.contentType.name, entry.contentJson, tamperedSignature
            )
        )

        val result = LogValidator.validate(tamperedEntry, db)
        assertTrue("Tampered signature must be invalid", result is ValidationResult.Invalid)
    }

    @Test
    fun testTamperedContentJsonRejection() = runBlocking {
        val entry = createSignedEntry(sequence = 1L, previousHash = null)
        val tamperedContent = entry.contentJson + " " // 1 space alteration
        val tamperedEntry = LogEntry(
            authorId = entry.authorId,
            sequence = entry.sequence,
            previousHash = entry.previousHash,
            timestamp = entry.timestamp,
            contentType = entry.contentType,
            contentJson = tamperedContent,
            signature = entry.signature,
            entryHash = CryptoManager.computeEntryHash(
                entry.authorId, entry.sequence, entry.previousHash,
                entry.timestamp, entry.contentType.name, tamperedContent, entry.signature
            )
        )

        val result = LogValidator.validate(tamperedEntry, db)
        assertTrue("Tampered payload must fail cryptographic verification", result is ValidationResult.Invalid)
    }

    @Test
    fun testDuplicateEntryIdempotency() = runBlocking {
        val entry1 = createSignedEntry(sequence = 1L, previousHash = null)
        db.insertAndProject(entry1)

        val revalidate = LogValidator.validate(entry1, db)
        assertTrue("Re-validating already inserted entry must return AlreadyExists", revalidate is ValidationResult.AlreadyExists)
    }

    @Test
    fun testBatchInsertionWithVectorClockCoalescence() = runBlocking {
        val entry1 = createSignedEntry(sequence = 1L, previousHash = null)
        val entry2 = createSignedEntry(sequence = 2L, previousHash = entry1.entryHash)
        val entry3 = createSignedEntry(sequence = 3L, previousHash = entry2.entryHash)

        // Create an invalid entry for another sequence
        val invalidEntry = createSignedEntry(sequence = 5L, previousHash = "bad_hash")

        val batch = listOf(entry1, entry2, entry3, invalidEntry)
        val result = db.insertAndProjectBatch(batch)

        assertEquals("Should accept 3 valid entries", 3, result.acceptedCount)
        assertEquals("Should report 1 error for the invalid entry", 1, result.errors.size)

        // Vector clock in peer_sync_state should be coalesced to 3 for this author
        val syncState = db.peerSyncStateDao().getState(cryptoAlice.authorId)
        assertNotNull(syncState)
        assertEquals("Vector clock should be updated to sequence 3", 3L, syncState!!.highestSequence)
        assertEquals(entry3.entryHash, syncState.lastHash)
    }
}
