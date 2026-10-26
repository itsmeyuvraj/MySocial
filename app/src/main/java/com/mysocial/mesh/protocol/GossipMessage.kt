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
        val hasMore: Boolean = false
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

    fun encode(): String = CanonicalJson.encodeToString(serializer(), this)

    companion object {
        fun decode(json: String): GossipMessage =
            CanonicalJson.decodeFromString(serializer(), json)
    }
}
