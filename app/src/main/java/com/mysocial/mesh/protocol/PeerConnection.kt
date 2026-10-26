package com.mysocial.mesh.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

enum class TransportType {
    BLE,
    MDNS_WIFI
}

/**
 * Transport-agnostic abstraction for a peer-to-peer duplex connection stream.
 */
interface PeerConnection {
    val peerId: String
    val peerName: String
    val transportType: TransportType
    val isConnected: Boolean

    suspend fun send(message: GossipMessage)
    suspend fun receiveMessage(): GossipMessage
    fun receiveFlow(): Flow<GossipMessage> = flow {
        while (isConnected) {
            emit(receiveMessage())
        }
    }
    suspend fun close()
}

/**
 * Stream-based PeerConnection implementation over raw TCP Socket or BLE L2CAP CoC BluetoothSocket.
 * Uses FrameCodec 4-byte length prefixing.
 */
class StreamPeerConnection(
    override val peerId: String,
    override val peerName: String,
    override val transportType: TransportType,
    private val socket: Socket
) : PeerConnection {

    private val closed = AtomicBoolean(false)
    private val incomingChannel = Channel<GossipMessage>(Channel.UNLIMITED)
    private val inputStream = BufferedInputStream(socket.getInputStream())
    private val outputStream = BufferedOutputStream(socket.getOutputStream())

    override val isConnected: Boolean
        get() = !closed.get() && socket.isConnected && !socket.isClosed

    init {
        // Start background reader thread
        Thread({
            try {
                while (isConnected) {
                    val frame = FrameCodec.readFrame(inputStream) ?: break
                    val json = String(frame, Charsets.UTF_8)
                    val message = GossipMessage.decode(json)
                    incomingChannel.trySend(message)
                }
            } catch (e: Exception) {
                // Connection closed or error
            } finally {
                incomingChannel.close()
            }
        }, "StreamPeerReader-$peerId").start()
    }

    override suspend fun send(message: GossipMessage) = withContext(Dispatchers.IO) {
        if (!isConnected) throw IllegalStateException("Connection to $peerId is closed")
        val json = message.encode()
        val bytes = json.toByteArray(Charsets.UTF_8)
        synchronized(outputStream) {
            FrameCodec.writeFrame(outputStream, bytes)
        }
    }

    override suspend fun receiveMessage(): GossipMessage = withContext(Dispatchers.IO) {
        incomingChannel.receive()
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        if (closed.compareAndSet(false, true)) {
            incomingChannel.close()
            try { socket.close() } catch (_: Exception) {}
        }
    }
}

/**
 * PeerConnection implementation over BLE L2CAP CoC BluetoothSocket.
 * Uses FrameCodec 4-byte length prefixing over raw BLE L2CAP channel.
 */
class BluetoothSocketPeerConnection(
    override val peerId: String,
    override val peerName: String,
    override val transportType: TransportType = TransportType.BLE,
    private val socket: android.bluetooth.BluetoothSocket
) : PeerConnection {

    private val closed = AtomicBoolean(false)
    private val incomingChannel = Channel<GossipMessage>(Channel.UNLIMITED)
    private val inputStream = BufferedInputStream(socket.inputStream)
    private val outputStream = BufferedOutputStream(socket.outputStream)

    override val isConnected: Boolean
        get() = !closed.get() && socket.isConnected

    init {
        Thread({
            try {
                while (isConnected) {
                    val frame = FrameCodec.readFrame(inputStream) ?: break
                    val json = String(frame, Charsets.UTF_8)
                    val message = GossipMessage.decode(json)
                    incomingChannel.trySend(message)
                }
            } catch (_: Exception) {
            } finally {
                incomingChannel.close()
            }
        }, "BluetoothPeerReader-$peerId").start()
    }

    override suspend fun send(message: GossipMessage) = withContext(Dispatchers.IO) {
        if (!isConnected) throw IllegalStateException("Connection to $peerId is closed")
        val json = message.encode()
        val bytes = json.toByteArray(Charsets.UTF_8)
        synchronized(outputStream) {
            FrameCodec.writeFrame(outputStream, bytes)
        }
    }

    override suspend fun receiveMessage(): GossipMessage = withContext(Dispatchers.IO) {
        incomingChannel.receive()
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        if (closed.compareAndSet(false, true)) {
            incomingChannel.close()
            try { socket.close() } catch (_: Exception) {}
        }
    }
}

/**
 * In-Memory Duplex Loopback PeerConnection for testing and intra-process mesh simulation.
 */
class MemoryPeerConnection(
    override val peerId: String,
    override val peerName: String,
    override val transportType: TransportType,
    private val outgoingChannel: Channel<GossipMessage>,
    private val incomingChannel: Channel<GossipMessage>
) : PeerConnection {

    private val closed = AtomicBoolean(false)
    override val isConnected: Boolean get() = !closed.get()

    override suspend fun send(message: GossipMessage) {
        if (!isConnected) throw IllegalStateException("Connection closed")
        outgoingChannel.send(message)
    }

    override suspend fun receiveMessage(): GossipMessage {
        return incomingChannel.receive()
    }

    override suspend fun close() {
        if (closed.compareAndSet(false, true)) {
            incomingChannel.close()
        }
    }

    companion object {
        /**
         * Creates a paired duplex pair of connections between peer A and peer B.
         */
        fun createPaired(
            peerAId: String,
            peerAName: String,
            peerBId: String,
            peerBName: String,
            transportType: TransportType = TransportType.MDNS_WIFI
        ): Pair<PeerConnection, PeerConnection> {
            val aToB = Channel<GossipMessage>(Channel.UNLIMITED)
            val bToA = Channel<GossipMessage>(Channel.UNLIMITED)

            val connA = MemoryPeerConnection(
                peerId = peerBId,
                peerName = peerBName,
                transportType = transportType,
                outgoingChannel = aToB,
                incomingChannel = bToA
            )

            val connB = MemoryPeerConnection(
                peerId = peerAId,
                peerName = peerAName,
                transportType = transportType,
                outgoingChannel = bToA,
                incomingChannel = aToB
            )

            return Pair(connA, connB)
        }
    }
}
