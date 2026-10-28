package com.mysocial.mesh.protocol

import com.mysocial.mesh.model.CanonicalJson
import com.mysocial.mesh.model.LogEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Protocol Wire Framing for P2P Epidemic Gossip Sync.
 * Encoded to / decoded from deterministic canonical JSON.
 */
@Serializable
sealed interface GossipMessage {

    /**
     * Step 1: Exchange Vector Clocks.
     * Contains the sender's identifier, display name, and full vector clock map (authorId -> highestSequence).
     */
    @Serializable
    @SerialName("HELLO_VECTOR")
    data class HelloVector(
        val peerId: String,
        val peerName: String = "",
        val vectorClock: Map<String, Long>,
        val openMeshMode: Boolean = true
    ) : GossipMessage

    /**
     * Step 2: Request missing log deltas.
     * Maps authorId to the first missing sequence number required (inclusive).
     */
    @Serializable
    @SerialName("DELTA_REQUEST")
    data class DeltaRequest(
        val requests: Map<String, Long> // authorId -> fromSequence (inclusive)
    ) : GossipMessage

    /**
     * Step 3: Stream missing log entries in sequential order.
     */
    @Serializable
    @SerialName("LOG_BATCH")
    data class LogBatch(
        val entries: List<LogEntry>,
        val hasMore: Boolean = false,
        val hopCounts: List<Int> = emptyList()
    ) : GossipMessage

    /**
     * Sent when all requested deltas have been transmitted.
     */
    @Serializable
    @SerialName("SYNC_COMPLETE")
    data class SyncComplete(
        val entriesSent: Int = 0
    ) : GossipMessage

    /**
     * Error notification during handshake or sync.
     */
    @Serializable
    @SerialName("ERROR")
    data class Error(
        val reason: String
    ) : GossipMessage

    // -------------------------------------------------------------
    // PRIORITY 1: CONTENT-ADDRESSED CHUNKED BLOB TRANSFER
    // -------------------------------------------------------------

    /**
     * Priority 1: Broadcasts a list of completed blobHashes the node holds.
     */
    @Serializable
    @SerialName("BLOB_HAVE")
    data class BlobHave(
        val blobHashes: List<String>
    ) : GossipMessage

    /**
     * Priority 1: Requests only missing 16 KB chunks for an incomplete media post.
     */
    @Serializable
    @SerialName("BLOB_REQ")
    data class BlobReq(
        val blobHash: String,
        val missingChunksBitfield: String
    ) : GossipMessage {
        fun getMissingIndices(totalChunks: Int): List<Int> {
            return BlobBitfieldHelper.decodeMissingIndices(missingChunksBitfield, totalChunks)
        }
    }

    /**
     * Priority 1: Streams one 16 KB chunk at a time.
     */
    @Serializable
    @SerialName("BLOB_CHUNK")
    data class BlobChunk(
        val blobHash: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        val bytesBase64: String
    ) : GossipMessage

    fun encode(): String = CanonicalJson.encodeToString(serializer(), this)

    companion object {
        fun decode(json: String): GossipMessage =
            CanonicalJson.decodeFromString(serializer(), json)
    }
}

/**
 * Encodes and decodes bitfields for partial chunk requests over the mesh.
 */
object BlobBitfieldHelper {
    fun encodeMissingIndices(missing: List<Int>, totalChunks: Int): String {
        if (totalChunks <= 0) return ""
        val byteCount = (totalChunks + 7) / 8
        val bytes = ByteArray(byteCount)
        for (idx in missing) {
            if (idx in 0 until totalChunks) {
                val byteIdx = idx / 8
                val bitIdx = idx % 8
                bytes[byteIdx] = (bytes[byteIdx].toInt() or (1 shl bitIdx)).toByte()
            }
        }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun decodeMissingIndices(bitfieldHex: String, totalChunks: Int): List<Int> {
        if (bitfieldHex.isBlank() || totalChunks <= 0) return emptyList()
        val bytes = try {
            bitfieldHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (_: Exception) {
            return emptyList()
        }
        val missing = mutableListOf<Int>()
        for (idx in 0 until totalChunks) {
            val byteIdx = idx / 8
            val bitIdx = idx % 8
            if (byteIdx < bytes.size) {
                if ((bytes[byteIdx].toInt() and (1 shl bitIdx)) != 0) {
                    missing.add(idx)
                }
            }
        }
        return missing
    }
}
