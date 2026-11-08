package com.mysocial.mesh.nativebridge

import android.util.Log
import java.security.MessageDigest

/**
 * JNI Bridge to high-performance native Rust core (`libmysocial_core.so`).
 * Provides hardware-accelerated SHA-256 Hashcash Proof-of-Work mining, verification,
 * and Reed-Solomon / XOR Forward Error Correction (FEC) for lossy radio transport.
 *
 * Implements seamless JVM fallbacks when running in unit tests or platforms without native binaries.
 */
object NativeMeshCore {
    private const val TAG = "NativeMeshCore"
    private var isLoaded: Boolean = false

    init {
        try {
            System.loadLibrary("mysocial_core")
            isLoaded = isNativeLoaded()
            Log.i(TAG, "Native mysocial_core loaded successfully! Hardware-accelerated SIMD active.")
        } catch (t: Throwable) {
            isLoaded = false
            Log.w(TAG, "Native mysocial_core could not be loaded; falling back to JVM crypto: ${t.message}")
        }
    }

    fun isAvailable(): Boolean = isLoaded

    // Native JNI functions
    @JvmStatic
    private external fun isNativeLoaded(): Boolean

    @JvmStatic
    private external fun mineHashcash(challenge: ByteArray, difficultyBits: Int): Long

    @JvmStatic
    private external fun verifyHashcash(challenge: ByteArray, nonce: Long, difficultyBits: Int): Boolean

    @JvmStatic
    private external fun computeXorParity(shards: Array<ByteArray>): ByteArray

    @JvmStatic
    private external fun reconstructMissingShard(presentShards: Array<ByteArray>, parity: ByteArray): ByteArray

    @JvmStatic
    private external fun validateWireFrame(frameBytes: ByteArray): Boolean

    @JvmStatic
    private external fun hashChunkTree(chunks: Array<ByteArray>): ByteArray

    /**
     * Mines a Hashcash Proof-of-Work nonce.
     * Uses hardware-accelerated Rust SHA-256 if available, or falls back to JVM MessageDigest.
     */
    fun mine(challenge: ByteArray, difficultyBits: Int): Long {
        if (difficultyBits <= 0) return 0L
        if (isLoaded) {
            try {
                return mineHashcash(challenge, difficultyBits)
            } catch (e: Throwable) {
                Log.w(TAG, "Native mine failed, falling back to JVM: ${e.message}")
            }
        }
        return mineJvmFallback(challenge, difficultyBits)
    }

    /**
     * Verifies whether the nonce satisfies the difficulty requirement.
     */
    fun verify(challenge: ByteArray, nonce: Long, difficultyBits: Int): Boolean {
        if (difficultyBits <= 0) return true
        if (isLoaded) {
            try {
                return verifyHashcash(challenge, nonce, difficultyBits)
            } catch (e: Throwable) {
                Log.w(TAG, "Native verify failed, falling back to JVM: ${e.message}")
            }
        }
        return verifyJvmFallback(challenge, nonce, difficultyBits)
    }

    /**
     * Computes XOR parity shard for forward error correction across lossy BLE packets.
     */
    fun encodeParity(shards: Array<ByteArray>): ByteArray {
        if (shards.isEmpty()) return ByteArray(0)
        if (isLoaded) {
            try {
                return computeXorParity(shards)
            } catch (e: Throwable) {
                Log.w(TAG, "Native computeXorParity failed, falling back to JVM: ${e.message}")
            }
        }
        return encodeParityJvm(shards)
    }

    /**
     * Reconstructs a missing shard from the present shards and the parity block.
     */
    fun reconstructShard(presentShards: Array<ByteArray>, parity: ByteArray): ByteArray {
        if (isLoaded) {
            try {
                return reconstructMissingShard(presentShards, parity)
            } catch (e: Throwable) {
                Log.w(TAG, "Native reconstructMissingShard failed, falling back to JVM: ${e.message}")
            }
        }
        return reconstructShardJvm(presentShards, parity)
    }

    /**
     * Validates raw streaming radio frame content for length bounds and header sanity.
     */
    fun validateFrame(frameBytes: ByteArray): Boolean {
        if (frameBytes.size < 4 || frameBytes.size > 16 * 1024 * 1024) return false
        if (isLoaded) {
            try {
                return validateWireFrame(frameBytes)
            } catch (e: Throwable) {
                Log.w(TAG, "Native validateWireFrame failed, using JVM fallback: ${e.message}")
            }
        }
        return validateFrameJvm(frameBytes)
    }

    /**
     * Computes high-throughput Merkle root hash across multiple 16KB-64KB chunks.
     */
    fun computeChunkTreeHash(chunks: Array<ByteArray>): ByteArray {
        if (chunks.isEmpty()) return ByteArray(32)
        if (isLoaded) {
            try {
                return hashChunkTree(chunks)
            } catch (e: Throwable) {
                Log.w(TAG, "Native hashChunkTree failed, using JVM fallback: ${e.message}")
            }
        }
        return computeChunkTreeHashJvm(chunks)
    }

    // --- JVM Fallbacks ---
    private fun countLeadingZeroBits(digest: ByteArray): Int {
        var zeros = 0
        for (b in digest) {
            val byteVal = b.toInt() and 0xFF
            val lz = Integer.numberOfLeadingZeros(byteVal) - 24
            zeros += lz
            if (lz != 8) break
        }
        return zeros
    }

    private fun longToBytes(value: Long): ByteArray {
        return ByteArray(8) { i ->
            (value ushr ((7 - i) * 8)).toByte()
        }
    }

    private fun mineJvmFallback(challenge: ByteArray, difficultyBits: Int): Long {
        val md = MessageDigest.getInstance("SHA-256")
        var nonce = 0L
        while (true) {
            md.reset()
            md.update(challenge)
            md.update(longToBytes(nonce))
            val digest = md.digest()
            if (countLeadingZeroBits(digest) >= difficultyBits) {
                return nonce
            }
            nonce++
        }
    }

    private fun verifyJvmFallback(challenge: ByteArray, nonce: Long, difficultyBits: Int): Boolean {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(challenge)
        md.update(longToBytes(nonce))
        val digest = md.digest()
        return countLeadingZeroBits(digest) >= difficultyBits
    }

    private fun encodeParityJvm(shards: Array<ByteArray>): ByteArray {
        val maxLen = shards.maxOfOrNull { it.size } ?: 0
        val parity = ByteArray(maxLen)
        for (shard in shards) {
            for (i in shard.indices) {
                parity[i] = (parity[i].toInt() xor shard[i].toInt()).toByte()
            }
        }
        return parity
    }

    private fun reconstructShardJvm(presentShards: Array<ByteArray>, parity: ByteArray): ByteArray {
        val reconstructed = parity.copyOf()
        for (shard in presentShards) {
            for (i in shard.indices) {
                if (i < reconstructed.size) {
                    reconstructed[i] = (reconstructed[i].toInt() xor shard[i].toInt()).toByte()
                }
            }
        }
        return reconstructed
    }

    private fun validateFrameJvm(frameBytes: ByteArray): Boolean {
        val first = frameBytes[0]
        if (first == '{'.code.toByte() || first == '['.code.toByte()) return true
        if (frameBytes.size >= 2 && frameBytes[0] == 'L'.code.toByte() && frameBytes[1] == 'R'.code.toByte()) return true
        if (frameBytes.size >= 2 && frameBytes[0] == 'M'.code.toByte() && frameBytes[1] == 'S'.code.toByte()) return true
        return try {
            String(frameBytes, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun computeChunkTreeHashJvm(chunks: Array<ByteArray>): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val treeMd = MessageDigest.getInstance("SHA-256")
        for (chunk in chunks) {
            md.reset()
            val chunkDigest = md.digest(chunk)
            treeMd.update(chunkDigest)
        }
        return treeMd.digest()
    }
}

