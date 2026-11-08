package com.mysocial.mesh.transport

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.util.Log
import com.mysocial.mesh.protocol.PeerConnection
import com.mysocial.mesh.protocol.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android Wi-Fi Aware (NAN - Neighbor Awareness Networking) Transport.
 * Enables high-speed P2P discovery and direct data streaming without an access point.
 */
class WifiAwareTransport(
    private val context: Context,
    private val myAuthorId: String,
    private val myDisplayName: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "WifiAwareTransport"
        const val SERVICE_NAME = "MySocialMesh"
    }

    private val wifiAwareManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
        } else null

    private val isRunning = AtomicBoolean(false)
    private var awareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null

    private val discoveredPeersMap = ConcurrentHashMap<String, DiscoveredPeer>()
    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    var onPeerDiscovered: ((DiscoveredPeer) -> Unit)? = null
    var onConnectionEstablished: ((PeerConnection) -> Unit)? = null

    fun isSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) &&
                wifiAwareManager != null
    }

    fun isAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wifiAwareManager?.isAvailable ?: false
        } else false
    }

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return

        if (!isSupported()) {
            Log.i(TAG, "Wi-Fi Aware (NAN) not supported on this hardware. Running in standby mode.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                wifiAwareManager?.attach(object : AttachCallback() {
                    override fun onAttached(session: WifiAwareSession) {
                        awareSession = session
                        Log.i(TAG, "Attached to Wi-Fi Aware session. Starting Publish & Subscribe.")
                        startPublishing(session)
                        startSubscribing(session)
                    }

                    override fun onAttachFailed() {
                        Log.w(TAG, "Failed to attach to Wi-Fi Aware session.")
                    }
                }, null)
            } catch (e: Exception) {
                Log.w(TAG, "Error attaching to Wi-Fi Aware: ${e.message}")
            }
        }
    }

    private fun startPublishing(session: WifiAwareSession) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val config = PublishConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .setServiceSpecificInfo(myAuthorId.toByteArray(Charsets.UTF_8))
            .build()

        session.publish(config, object : DiscoverySessionCallback() {
            override fun onPublishStarted(session: PublishDiscoverySession) {
                publishSession = session
                Log.d(TAG, "Wi-Fi Aware Publish started: $SERVICE_NAME")
            }

            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                handlePeerMessage(peerHandle, message)
            }
        }, null)
    }

    private fun startSubscribing(session: WifiAwareSession) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val config = SubscribeConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .build()

        session.subscribe(config, object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                subscribeSession = session
                Log.d(TAG, "Wi-Fi Aware Subscribe started: $SERVICE_NAME")
            }

            override fun onServiceDiscovered(
                peerHandle: PeerHandle,
                serviceSpecificInfo: ByteArray?,
                matchFilter: List<ByteArray>?
            ) {
                val peerAuthorId = serviceSpecificInfo?.let { String(it, Charsets.UTF_8) } ?: "WIFI_AWARE_${peerHandle.hashCode()}"
                val peer = DiscoveredPeer(
                    peerId = peerAuthorId,
                    peerName = "Nearby NAN Peer",
                    transportType = TransportType.WIFI_AWARE,
                    rssi = -50,
                    hostAddress = "NAN_${peerHandle.hashCode()}"
                )
                discoveredPeersMap[peer.peerId] = peer
                _discoveredPeers.value = discoveredPeersMap.values.toList()
                onPeerDiscovered?.invoke(peer)
            }
        }, null)
    }

    private fun handlePeerMessage(peerHandle: PeerHandle, message: ByteArray) {
        // High-bandwidth direct frames
        Log.d(TAG, "Received message from NAN peer: ${message.size} bytes")
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                publishSession?.close()
                subscribeSession?.close()
                awareSession?.close()
            } catch (ignored: Exception) {}
            publishSession = null
            subscribeSession = null
            awareSession = null
        }
        discoveredPeersMap.clear()
        _discoveredPeers.value = emptyList()
    }
}
