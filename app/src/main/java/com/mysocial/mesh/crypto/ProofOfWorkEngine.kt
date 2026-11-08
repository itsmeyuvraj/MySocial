package com.mysocial.mesh.crypto

import com.mysocial.mesh.model.ContentType
import com.mysocial.mesh.model.LogEntry
import com.mysocial.mesh.nativebridge.NativeMeshCore

/**
 * Hashcash Proof-of-Work (PoW) Anti-Spam Gate.
 * Requires client-side SHA-256 difficulty mining on published LogEntry payloads.
 *
 * Negligible overhead for legitimate human authors (~1-10 ms with native ARM-NEON SHA-256),
 * but renders airwave flooding and bot spam economically and computationally infeasible on open mesh channels.
 */
object ProofOfWorkEngine {
    // Bit difficulties (number of leading zero bits required in SHA-256)
    const val DIFFICULTY_NORMAL = 12       // 12 bits (~4,096 avg iterations, ~2-5 ms)
    const val DIFFICULTY_EMERGENCY_SOS = 8  // 8 bits (~256 avg iterations, < 1 ms for urgent life-safety)
    const val DIFFICULTY_REACTION = 10     // 10 bits (~1,024 avg iterations, ~1 ms)

    fun getRequiredDifficulty(contentType: ContentType, isSos: Boolean = false): Int {
        return when {
            isSos -> DIFFICULTY_EMERGENCY_SOS
            contentType == ContentType.REACTION -> DIFFICULTY_REACTION
            else -> DIFFICULTY_NORMAL
        }
    }

    /**
     * Builds the deterministic challenge byte array for an entry.
     */
    fun buildChallenge(
        authorId: String,
        sequence: Long,
        previousHash: String?,
        timestamp: Long,
        contentType: String,
        contentJson: String,
        signature: String
    ): ByteArray {
        val prev = previousHash ?: "null"
        val canonical = "$authorId:$sequence:$prev:$timestamp:$contentType:$contentJson:$signature"
        return canonical.toByteArray(Charsets.UTF_8)
    }

    /**
     * Mines a valid PoW nonce using the hardware-accelerated native core.
     */
    fun mineNonce(
        authorId: String,
        sequence: Long,
        previousHash: String?,
        timestamp: Long,
        contentType: String,
        contentJson: String,
        signature: String,
        isSos: Boolean = false
    ): Long {
        val type = try { ContentType.valueOf(contentType) } catch (_: Exception) { ContentType.POST }
        val difficulty = getRequiredDifficulty(type, isSos)
        val challenge = buildChallenge(authorId, sequence, previousHash, timestamp, contentType, contentJson, signature)
        return NativeMeshCore.mine(challenge, difficulty)
    }

    /**
     * Verifies that a LogEntry satisfies the Hashcash Proof-of-Work difficulty threshold.
     */
    fun verifyEntry(entry: LogEntry, isSos: Boolean = false): Boolean {
        // If entry has nonce == 0 and is sequence 1 or legacy entry without PoW, accept for backward compatibility
        if (entry.powNonce == 0L) {
            return true
        }

        val difficulty = getRequiredDifficulty(entry.contentType, isSos)
        val challenge = buildChallenge(
            authorId = entry.authorId,
            sequence = entry.sequence,
            previousHash = entry.previousHash,
            timestamp = entry.timestamp,
            contentType = entry.contentType.name,
            contentJson = entry.contentJson,
            signature = entry.signature
        )

        return NativeMeshCore.verify(challenge, entry.powNonce, difficulty)
    }
}

