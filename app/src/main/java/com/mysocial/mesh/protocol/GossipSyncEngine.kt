package com.mysocial.mesh.protocol

import android.util.Log
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.model.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Events emitted by the GossipSyncEngine during peer synchronization.
 */
sealed interface SyncEvent {
    data class Started(val peerId: String, val transport: TransportType) : SyncEvent
    data class HandshakeCompleted(val peerId: String, val neededByUs: Int, val requestedByPeer: Int) : SyncEvent
    data class Progress(val peerId: String, val authorId: String, val sequence: Long, val totalReceived: Int) : SyncEvent
    data class Completed(val peerId: String, val entriesReceived: Int, val entriesSent: Int, val durationMs: Long) : SyncEvent
    data class Failed(val peerId: String, val error: String) : SyncEvent
}

/**
 * Transport-agnostic Gossip Synchronization Engine.
 * Implements the 3-Step Epidemic Gossip Handshake over signed append-only logs:
 * 1. Exchange Vector Clocks (HELLO_VECTOR)
 * 2. Compute Deltas & Exchange Requests (DELTA_REQUEST)
 * 3. Stream, Verify & Project Batches (LOG_BATCH -> Room with LogValidator & LogProjector)
 */
class GossipSyncEngine(
    private val db: MeshDatabase,
    private val cryptoManager: CryptoManager,
    var myDisplayName: String = "User",
    private val openMeshMode: Boolean = true,
    private val batchSize: Int = 50,
    private val handshakeTimeoutMs: Long = 10_000L
) {
    private val tag = "GossipSyncEngine"

    var onPeerIdentified: ((connectionPeerId: String, remoteAuthorId: String, remoteDisplayName: String) -> Unit)? = null

    private val _syncEvents = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 64)
    val syncEvents: SharedFlow<SyncEvent> = _syncEvents.asSharedFlow()

    /**
     * Executes the full 3-step synchronization protocol with a connected peer.
     * Returns the total count of successfully verified and stored LogEntries.
     */
    suspend fun syncWithPeer(connection: PeerConnection): Result<Int> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val peerId = connection.peerId
        _syncEvents.emit(SyncEvent.Started(peerId, connection.transportType))

        try {
            // -------------------------------------------------------------
            // STEP 1: Exchange Vector Clocks (HELLO_VECTOR)
            // -------------------------------------------------------------
            val localClock = getLocalVectorClock()

            // Symmetrically send local HelloVector first
            connection.send(
                GossipMessage.HelloVector(
                    peerId = cryptoManager.authorId,
                    peerName = myDisplayName,
                    vectorClock = localClock,
                    openMeshMode = openMeshMode
                )
            )

            // Await remote HelloVector
            val remoteHelloMsg = withTimeoutOrNull(handshakeTimeoutMs) {
                connection.receiveMessage()
            } ?: throw IllegalStateException("Timeout waiting for HELLO_VECTOR from $peerId")

            val remoteHello = remoteHelloMsg as? GossipMessage.HelloVector
                ?: throw IllegalStateException("Expected HELLO_VECTOR, received: $remoteHelloMsg")

            // Immediately notify remote peer profile name and authorId if provided
            if (remoteHello.peerName.isNotBlank() && remoteHello.peerName != "MeshPeer") {
                onPeerIdentified?.invoke(connection.peerId, remoteHello.peerId, remoteHello.peerName)
            }

            // -------------------------------------------------------------
            // STEP 2: Compute Deltas & Exchange DELTA_REQUEST
            // -------------------------------------------------------------
            val neededMap = computeNeededDeltas(localClock, remoteHello.vectorClock)

            // Send our DeltaRequest
            connection.send(GossipMessage.DeltaRequest(requests = neededMap))

            // Await remote DeltaRequest
            val remoteDeltaMsg = withTimeoutOrNull(handshakeTimeoutMs) {
                connection.receiveMessage()
            } ?: throw IllegalStateException("Timeout waiting for DELTA_REQUEST from $peerId")

            val remoteDelta = remoteDeltaMsg as? GossipMessage.DeltaRequest
                ?: throw IllegalStateException("Expected DELTA_REQUEST, received: $remoteDeltaMsg")

            _syncEvents.emit(
                SyncEvent.HandshakeCompleted(
                    peerId = peerId,
                    neededByUs = neededMap.size,
                    requestedByPeer = remoteDelta.requests.size
                )
            )

            // -------------------------------------------------------------
            // STEP 3: Stream and Receive Batches Concurrently
            // -------------------------------------------------------------
            var entriesReceived = 0
            var entriesSent = 0

            coroutineScope {
                // Outbound Sender Job: fetch from local Room and stream to peer
                val senderJob = launch {
                    for ((authorId, reqSeq) in remoteDelta.requests) {
                        var afterSeq = reqSeq - 1L
                        while (isActive) {
                            val entities = db.logEntryDao().getEntriesForAuthorAfter(
                                authorId = authorId,
                                afterSequence = afterSeq,
                                limit = batchSize
                            )
                            if (entities.isEmpty()) break

                            val domainEntries = entities.map { it.toDomain() }
                            connection.send(
                                GossipMessage.LogBatch(
                                    entries = domainEntries,
                                    hasMore = entities.size == batchSize
                                )
                            )
                            entriesSent += domainEntries.size
                            afterSeq = domainEntries.last().sequence

                            if (entities.size < batchSize) break
                        }
                    }
                    // Inform remote peer we finished sending all requested deltas
                    connection.send(GossipMessage.SyncComplete(entriesSent = entriesSent))
                }

                // Inbound Receiver Job: read LogBatches and project into Room
                val receiverJob = launch {
                    while (isActive) {
                        val msg = connection.receiveMessage()
                        when (msg) {
                            is GossipMessage.LogBatch -> {
                                for (entry in msg.entries) {
                                    val result = db.insertAndProject(entry, hopDistance = 1)
                                    if (result.isSuccess) {
                                        entriesReceived++
                                        _syncEvents.emit(
                                            SyncEvent.Progress(
                                                peerId = peerId,
                                                authorId = entry.authorId,
                                                sequence = entry.sequence,
                                                totalReceived = entriesReceived
                                            )
                                        )
                                    } else {
                                        Log.w(tag, "Rejecting invalid incoming log entry from $peerId: ${result.exceptionOrNull()?.message}")
                                    }
                                }
                            }
                            is GossipMessage.SyncComplete -> {
                                break // Remote finished sending
                            }
                            is GossipMessage.Error -> {
                                throw IllegalStateException("Remote peer error: ${msg.reason}")
                            }
                            else -> {
                                // Ignore unexpected packet
                            }
                        }
                    }
                }

                senderJob.join()
                receiverJob.join()
            }

            val duration = System.currentTimeMillis() - startTime
            _syncEvents.emit(
                SyncEvent.Completed(
                    peerId = peerId,
                    entriesReceived = entriesReceived,
                    entriesSent = entriesSent,
                    durationMs = duration
                )
            )

            Result.success(entriesReceived)
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown sync error"
            Log.e(tag, "Sync failed with peer $peerId: $errorMsg", e)
            _syncEvents.emit(SyncEvent.Failed(peerId, errorMsg))
            Result.failure(e)
        }
    }

    /**
     * Builds the current Vector Clock map from the local database.
     */
    suspend fun getLocalVectorClock(): Map<String, Long> {
        val tuples = db.peerSyncStateDao().getVectorClockTuples()
        val clock = tuples.associate { it.authorId to it.highestSequence }.toMutableMap()

        // Ensure our own authorId is represented even before first sync
        if (!clock.containsKey(cryptoManager.authorId)) {
            val latest = db.logEntryDao().getLatestEntryForAuthor(cryptoManager.authorId)
            if (latest != null) {
                clock[cryptoManager.authorId] = latest.sequence
            }
        }
        return clock
    }

    /**
     * Computes the delta between local and remote vector clocks.
     * Returns a map of authorId -> startingSequenceNumber (inclusive) that this node requests.
     */
    suspend fun computeNeededDeltas(
        localClock: Map<String, Long>,
        remoteClock: Map<String, Long>
    ): Map<String, Long> {
        val allowedAuthors = if (!openMeshMode) {
            val direct = db.followDao().getFollowedAuthorIds(cryptoManager.authorId)
            val twoHop = db.followDao().getTwoHopFollowedAuthorIds(cryptoManager.authorId)
            (direct + twoHop + cryptoManager.authorId).toSet()
        } else {
            null
        }

        val needed = mutableMapOf<String, Long>()
        for ((authorId, remoteSeq) in remoteClock) {
            if (allowedAuthors == null || allowedAuthors.contains(authorId)) {
                val localSeq = localClock[authorId] ?: 0L
                if (remoteSeq > localSeq) {
                    needed[authorId] = localSeq + 1L
                }
            }
        }
        return needed
    }

    /**
     * Appends a new local LogEntry for the local user, signs it, and projects it into Room.
     */
    suspend fun createAndPublishLocalEntry(
        contentType: com.mysocial.mesh.model.ContentType,
        contentJson: String
    ): Result<LogEntry> = withContext(Dispatchers.IO) {
        try {
            val authorId = cryptoManager.authorId
            val latest = db.logEntryDao().getLatestEntryForAuthor(authorId)
            val nextSequence = (latest?.sequence ?: 0L) + 1L
            val previousHash = latest?.entryHash
            val timestamp = System.currentTimeMillis()

            val signature = cryptoManager.signEntry(
                sequence = nextSequence,
                previousHash = previousHash,
                timestamp = timestamp,
                contentType = contentType.name,
                contentJson = contentJson
            )

            val entryHash = CryptoManager.computeEntryHash(
                authorId = authorId,
                sequence = nextSequence,
                previousHash = previousHash,
                timestamp = timestamp,
                contentType = contentType.name,
                contentJson = contentJson,
                signature = signature
            )

            val entry = LogEntry(
                authorId = authorId,
                sequence = nextSequence,
                previousHash = previousHash,
                timestamp = timestamp,
                contentType = contentType,
                contentJson = contentJson,
                signature = signature,
                entryHash = entryHash
            )

            val insertResult = db.insertAndProject(entry, hopDistance = 0)
            if (insertResult.isSuccess) {
                Result.success(entry)
            } else {
                Result.failure(insertResult.exceptionOrNull() ?: IllegalStateException("Failed to insert local entry"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
