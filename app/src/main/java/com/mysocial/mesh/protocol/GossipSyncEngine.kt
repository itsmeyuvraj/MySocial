package com.mysocial.mesh.protocol

import android.content.Context
import android.util.Base64
import android.util.Log
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.BlobChunkEntity
import com.mysocial.mesh.data.MediaProcessor
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
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Events emitted by the GossipSyncEngine during peer synchronization.
 */
sealed interface SyncEvent {
    data class Started(val peerId: String, val transport: TransportType) : SyncEvent
    data class HandshakeCompleted(val peerId: String, val neededByUs: Int, val requestedByPeer: Int) : SyncEvent
    data class Progress(val peerId: String, val authorId: String, val sequence: Long, val totalReceived: Int) : SyncEvent
    data class BlobProgress(val peerId: String, val blobHash: String, val receivedChunks: Int, val totalChunks: Int) : SyncEvent
    data class BlobCompleted(val peerId: String, val blobHash: String) : SyncEvent
    data class Completed(val peerId: String, val entriesReceived: Int, val entriesSent: Int, val durationMs: Long) : SyncEvent
    data class Failed(val peerId: String, val error: String) : SyncEvent
}

/**
 * Transport-agnostic Gossip Synchronization Engine with Priority Multiplexing.
 *
 * Priority 0 (Highest):
 * 1. Exchange Vector Clocks (HELLO_VECTOR)
 * 2. Compute Deltas & Exchange Requests (DELTA_REQUEST)
 * 3. Stream, Verify & Project Batches (LOG_BATCH -> Room with LogValidator & LogProjector)
 * All text posts, comments, likes, and microPreviewBase64 thumbnails sync here first.
 *
 * Priority 1 (Background):
 * Content-Addressed Resumable Chunked Blob Transfer (BLOB_HAVE, BLOB_REQ, BLOB_CHUNK).
 * Streams 16 KB chunks between text sync operations and verifies SHA-256 upon assembly.
 */
class GossipSyncEngine(
    private val db: MeshDatabase,
    private val cryptoManager: CryptoManager,
    var myDisplayName: String = "User",
    private val openMeshMode: Boolean = true,
    private val batchSize: Int = 50,
    private val handshakeTimeoutMs: Long = 10_000L,
    private val context: Context? = null
) {
    private val tag = "GossipSyncEngine"

    private val peerIdentifiedListeners = java.util.concurrent.CopyOnWriteArrayList<(String, String, String) -> Unit>()

    fun addPeerIdentifiedListener(listener: (connectionPeerId: String, remoteAuthorId: String, remoteDisplayName: String) -> Unit) {
        peerIdentifiedListeners.add(listener)
    }

    var onPeerIdentified: ((connectionPeerId: String, remoteAuthorId: String, remoteDisplayName: String) -> Unit)?
        get() = peerIdentifiedListeners.firstOrNull()
        set(value) {
            if (value != null) {
                if (!peerIdentifiedListeners.contains(value)) {
                    peerIdentifiedListeners.add(value)
                }
            }
        }

    fun notifyPeerIdentified(connectionPeerId: String, remoteAuthorId: String, remoteDisplayName: String) {
        for (listener in peerIdentifiedListeners) {
            try {
                listener(connectionPeerId, remoteAuthorId, remoteDisplayName)
            } catch (e: Exception) {
                Log.w(tag, "Error invoking peer identified listener: ${e.message}")
            }
        }
    }

    private val _syncEvents = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 64)
    val syncEvents: SharedFlow<SyncEvent> = _syncEvents.asSharedFlow()

    // Active connections for on-demand prioritized downloads
    private val activeConnections = ConcurrentHashMap<String, PeerConnection>()

    /**
     * Executes the priority-multiplexed synchronization protocol with a connected peer:
     * - Priority 0: LogEntries (text, replies, reactions, microPreviewBase64 thumbnails)
     * - Priority 1: Large media blobs (16 KB chunked, resumable)
     */
    suspend fun syncWithPeer(connection: PeerConnection): Result<Int> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val peerId = connection.peerId
        activeConnections[peerId] = connection
        _syncEvents.emit(SyncEvent.Started(peerId, connection.transportType))

        try {
            // =============================================================
            // PRIORITY 0: LOG ENTRY SYNC (Text, Profiles, Reactions, Thumbs)
            // =============================================================

            // Step 1: Exchange Vector Clocks (HELLO_VECTOR)
            val localClock = getLocalVectorClock()

            connection.send(
                GossipMessage.HelloVector(
                    peerId = cryptoManager.authorId,
                    peerName = myDisplayName,
                    vectorClock = localClock,
                    openMeshMode = openMeshMode
                )
            )

            val remoteHelloMsg = withTimeoutOrNull(handshakeTimeoutMs) {
                connection.receiveMessage()
            } ?: throw IllegalStateException("Timeout waiting for HELLO_VECTOR from $peerId")

            val remoteHello = remoteHelloMsg as? GossipMessage.HelloVector
                ?: throw IllegalStateException("Expected HELLO_VECTOR, received: $remoteHelloMsg")

            if (remoteHello.peerName.isNotBlank() && remoteHello.peerName != "MeshPeer") {
                notifyPeerIdentified(connection.peerId, remoteHello.peerId, remoteHello.peerName)
            }

            // Step 2: Compute Deltas & Exchange DELTA_REQUEST
            val neededMap = computeNeededDeltas(localClock, remoteHello.vectorClock)
            connection.send(GossipMessage.DeltaRequest(requests = neededMap))

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

            // Step 3: Stream and Receive Log Batches Concurrently
            var entriesReceived = 0
            var entriesSent = 0

            coroutineScope {
                val senderJob = launch {
                    for ((authorId, reqSeq) in remoteDelta.requests) {
                        var afterSeq = reqSeq - 1L
                        while (isActive && connection.isConnected) {
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
                    connection.send(GossipMessage.SyncComplete(entriesSent = entriesSent))
                }

                val receiverJob = launch {
                    while (isActive && connection.isConnected) {
                        val msg = connection.receiveMessage()
                        when (msg) {
                            is GossipMessage.LogBatch -> {
                                val batchResult = db.insertAndProjectBatch(msg.entries, hopDistance = 1)
                                if (batchResult.acceptedCount > 0) {
                                    entriesReceived += batchResult.acceptedCount
                                    val last = msg.entries.last()
                                    _syncEvents.emit(
                                        SyncEvent.Progress(
                                            peerId = peerId,
                                            authorId = last.authorId,
                                            sequence = last.sequence,
                                            totalReceived = entriesReceived
                                        )
                                    )
                                }
                                for (err in batchResult.errors) {
                                    Log.w(tag, "Rejecting invalid incoming log entry from $peerId: $err")
                                }
                            }
                            is GossipMessage.SyncComplete -> {
                                break // Priority 0 inbound sync complete
                            }
                            is GossipMessage.Error -> {
                                throw IllegalStateException("Remote peer error: ${msg.reason}")
                            }
                            // Store any incoming blob messages if interleaved
                            is GossipMessage.BlobChunk -> {
                                handleBlobChunk(peerId, msg)
                            }
                            is GossipMessage.BlobReq -> {
                                handleBlobReq(connection, msg)
                            }
                            is GossipMessage.BlobHave -> {
                                handleBlobHave(connection, msg)
                            }
                            else -> {}
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

            // =============================================================
            // PRIORITY 1: CHUNKED BLOB ENGINE (16 KB Chunks, Resumable)
            // =============================================================
            if (connection.isConnected) {
                syncBlobsWithPeer(connection)
            }

            Result.success(entriesReceived)
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown sync error"
            Log.e(tag, "Sync failed with peer $peerId: $errorMsg", e)
            _syncEvents.emit(SyncEvent.Failed(peerId, errorMsg))
            Result.failure(e)
        } finally {
            activeConnections.remove(peerId)
        }
    }

    /**
     * Executes Priority 1 Blob Synchronization over the active connection.
     * Streams 16 KB chunks between text sync checks.
     */
    suspend fun syncBlobsWithPeer(connection: PeerConnection) = withContext(Dispatchers.IO) {
        val peerId = connection.peerId
        try {
            // 1. Broadcast completed blobs this node holds (BLOB_HAVE)
            val completedHashes = db.blobDao().getCompletedBlobHashes()
            if (completedHashes.isNotEmpty()) {
                connection.send(GossipMessage.BlobHave(blobHashes = completedHashes))
            }

            // 2. Request missing chunks for any incomplete media posts
            requestMissingBlobs(connection)

            // 3. Process incoming blob streaming packets
            while (connection.isConnected) {
                val msg = withTimeoutOrNull(4000L) {
                    connection.receiveMessage()
                } ?: break // Idle timeout for blob exchange

                when (msg) {
                    is GossipMessage.BlobHave -> {
                        handleBlobHave(connection, msg)
                    }
                    is GossipMessage.BlobReq -> {
                        handleBlobReq(connection, msg)
                    }
                    is GossipMessage.BlobChunk -> {
                        handleBlobChunk(peerId, msg)
                    }
                    else -> {}
                }
                yield()
            }
        } catch (e: Exception) {
            Log.d(tag, "Blob sync finished or timed out with $peerId: ${e.message}")
        }
    }

    /**
     * Inspects incomplete manifests in Room and requests missing 16 KB chunks from peer.
     */
    private suspend fun requestMissingBlobs(connection: PeerConnection) {
        val incomplete = db.blobDao().getIncompleteManifests()
        for (manifest in incomplete) {
            if (!connection.isConnected) break
            val received = db.blobDao().getReceivedChunkIndices(manifest.blobHash).toSet()
            val missing = (0 until manifest.chunkCount).filter { !received.contains(it) }
            if (missing.isNotEmpty()) {
                val bitfield = BlobBitfieldHelper.encodeMissingIndices(missing, manifest.chunkCount)
                connection.send(
                    GossipMessage.BlobReq(
                        blobHash = manifest.blobHash,
                        missingChunksBitfield = bitfield
                    )
                )
            }
        }
    }

    /**
     * Handles peer's BLOB_HAVE: requests missing chunks for matching incomplete manifests.
     */
    private suspend fun handleBlobHave(connection: PeerConnection, have: GossipMessage.BlobHave) {
        val incomplete = db.blobDao().getIncompleteManifests()
        for (manifest in incomplete) {
            if (have.blobHashes.contains(manifest.blobHash)) {
                val received = db.blobDao().getReceivedChunkIndices(manifest.blobHash).toSet()
                val missing = (0 until manifest.chunkCount).filter { !received.contains(it) }
                if (missing.isNotEmpty()) {
                    val bitfield = BlobBitfieldHelper.encodeMissingIndices(missing, manifest.chunkCount)
                    connection.send(
                        GossipMessage.BlobReq(
                            blobHash = manifest.blobHash,
                            missingChunksBitfield = bitfield
                        )
                    )
                }
            }
        }
    }

    /**
     * Handles peer's BLOB_REQ: streams requested 16 KB chunks sequentially.
     */
    private suspend fun handleBlobReq(connection: PeerConnection, req: GossipMessage.BlobReq) {
        val manifest = db.blobDao().getManifest(req.blobHash) ?: return
        val totalChunks = manifest.chunkCount

        // Locate blob file
        val file = if (manifest.localFilePath != null) {
            File(manifest.localFilePath)
        } else if (context != null) {
            MediaProcessor.getBlobFile(context, req.blobHash)
        } else {
            File(File(System.getProperty("java.io.tmpdir"), "blobs"), "${req.blobHash}.blob")
        }

        if (!file.exists() || !file.canRead()) return

        val missingIndices = req.getMissingIndices(totalChunks)
        for (chunkIdx in missingIndices) {
            if (!connection.isConnected) break
            val chunkBytes = MediaProcessor.readChunkFromFile(file, chunkIdx) ?: continue
            val b64 = Base64.encodeToString(chunkBytes, Base64.NO_WRAP)
            connection.send(
                GossipMessage.BlobChunk(
                    blobHash = req.blobHash,
                    chunkIndex = chunkIdx,
                    totalChunks = totalChunks,
                    bytesBase64 = b64
                )
            )
            yield()
        }
    }

    /**
     * Handles incoming BLOB_CHUNK: stores chunk in Room, verifies SHA-256 upon completion.
     */
    private suspend fun handleBlobChunk(peerId: String, chunk: GossipMessage.BlobChunk) {
        val bytes = Base64.decode(chunk.bytesBase64, Base64.DEFAULT)
        db.blobDao().upsertChunk(
            BlobChunkEntity(
                blobHash = chunk.blobHash,
                chunkIndex = chunk.chunkIndex,
                data = bytes
            )
        )

        val totalReceived = db.blobDao().getReceivedChunksCount(chunk.blobHash)
        _syncEvents.emit(
            SyncEvent.BlobProgress(
                peerId = peerId,
                blobHash = chunk.blobHash,
                receivedChunks = totalReceived,
                totalChunks = chunk.totalChunks
            )
        )

        if (totalReceived >= chunk.totalChunks) {
            assembleAndVerifyBlob(peerId, chunk.blobHash, chunk.totalChunks)
        }
    }

    /**
     * Assembles 16 KB chunks in order, verifies computed SHA-256 matches blobHash,
     * writes to files/blobs/<blobHash>.blob, and marks manifest isComplete = true.
     */
    private suspend fun assembleAndVerifyBlob(peerId: String, blobHash: String, totalChunks: Int) {
        val allChunks = db.blobDao().getAllChunksForBlob(blobHash)
        if (allChunks.size < totalChunks) return

        val blobsDir = if (context != null) {
            MediaProcessor.getBlobsDirectory(context)
        } else {
            val dir = File(System.getProperty("java.io.tmpdir"), "blobs")
            if (!dir.exists()) dir.mkdirs()
            dir
        }
        val tempFile = File(blobsDir, "$blobHash.tmp")
        val blobFile = File(blobsDir, "$blobHash.blob")

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        var writeSuccess = false
        try {
            java.io.FileOutputStream(tempFile).use { fos ->
                for (chunk in allChunks) {
                    digest.update(chunk.data)
                    fos.write(chunk.data)
                }
                fos.flush()
            }
            writeSuccess = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to stream chunks for blob $blobHash: ${e.message}")
            tempFile.delete()
            return
        }

        if (writeSuccess) {
            val hashBytes = digest.digest()
            val hexChars = CharArray(hashBytes.size * 2)
            val hexArray = "0123456789abcdef".toCharArray()
            for (i in hashBytes.indices) {
                val v = hashBytes[i].toInt() and 0xFF
                hexChars[i * 2] = hexArray[v ushr 4]
                hexChars[i * 2 + 1] = hexArray[v and 0x0F]
            }
            val computedHash = String(hexChars)

            if (computedHash.equals(blobHash, ignoreCase = true)) {
                if (blobFile.exists()) blobFile.delete()
                tempFile.renameTo(blobFile)

                // Clean up chunk rows from database to save storage
                db.blobDao().deleteChunksForBlob(blobHash)
                db.blobDao().markComplete(blobHash, blobFile.absolutePath)

                Log.i(tag, "Successfully verified and assembled blob $blobHash (${blobFile.length()} bytes)")
                _syncEvents.emit(SyncEvent.BlobCompleted(peerId, blobHash))
            } else {
                tempFile.delete()
                Log.e(tag, "SHA-256 verification failed for blob $blobHash: computed $computedHash")
            }
        }
    }

    /**
     * Prioritizes downloading a specific blob across all currently active peer connections.
     * Triggered when the user taps "Tap to prioritize download".
     */
    suspend fun prioritizeBlob(blobHash: String) = withContext(Dispatchers.IO) {
        val manifest = db.blobDao().getManifest(blobHash) ?: return@withContext
        if (manifest.isComplete) return@withContext

        val received = db.blobDao().getReceivedChunkIndices(blobHash).toSet()
        val missing = (0 until manifest.chunkCount).filter { !received.contains(it) }
        if (missing.isEmpty()) return@withContext

        val bitfield = BlobBitfieldHelper.encodeMissingIndices(missing, manifest.chunkCount)
        val req = GossipMessage.BlobReq(blobHash = blobHash, missingChunksBitfield = bitfield)

        for ((_, conn) in activeConnections) {
            if (conn.isConnected) {
                try {
                    conn.send(req)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Builds the current Vector Clock map from the local database.
     */
    suspend fun getLocalVectorClock(): Map<String, Long> {
        val tuples = db.peerSyncStateDao().getVectorClockTuples()
        val clock = tuples.associate { it.authorId to it.highestSequence }.toMutableMap()

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
