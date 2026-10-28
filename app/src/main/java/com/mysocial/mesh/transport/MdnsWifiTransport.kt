package com.mysocial.mesh.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.mysocial.mesh.data.AppSettings
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.protocol.StreamPeerConnection
import com.mysocial.mesh.protocol.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-speed local Wi-Fi and offline Hotspot transport using NsdManager (mDNS/DNS-SD)
 * and raw TCP ServerSockets for peer discovery and bulk epidemic gossip synchronization.
 */
class MdnsWifiTransport(
    private val context: Context,
    private val localAuthorId: String,
    private var localDisplayName: String,
    private val syncEngine: GossipSyncEngine,
    private val scope: CoroutineScope,
    private val appSettings: AppSettings? = null
) {
    private val tag = "MdnsWifiTransport"

    fun removePeer(peerId: String) {
        _peersMap.remove(peerId)
        val toRemove = _peersMap.entries.filter { it.value.peerId == peerId }.map { it.key }
        toRemove.forEach { _peersMap.remove(it) }
        updatePeersState()
    }

    fun updateDisplayName(newDisplayName: String) {
        if (newDisplayName.isNotBlank() && newDisplayName != localDisplayName) {
            Log.i(tag, "Updating mDNS display name from '$localDisplayName' to '$newDisplayName'")
            localDisplayName = newDisplayName
            if (isRunning.get()) {
                try {
                    registrationListener?.let { nsdManager?.unregisterService(it) }
                } catch (_: Exception) {}
                registerNsdService()
            }
        }
    }
    private val serviceType = "_mysocial._tcp."
    private val safeLocalId = localAuthorId.replace("@", "").replace(".", "_").take(16)
    private val serviceName = "MySocial_$safeLocalId"

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private var serverSocket: ServerSocket? = null
    private var serverPort: Int = 0
    private var serverJob: Job? = null

    private val isRunning = AtomicBoolean(false)
    private val activeSyncPeers = ConcurrentHashMap.newKeySet<String>()

    private val _peersMap = ConcurrentHashMap<String, DiscoveredPeer>()
    private val serviceNameToPeerId = ConcurrentHashMap<String, String>()
    private var prunerJob: Job? = null
    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    companion object {
        const val DEFAULT_PORT = 42530
        const val ADB_REVERSE_PORT = 42535
    }

    val isEmulator: Boolean by lazy {
        Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("unknown") ||
        Build.MODEL.contains("google_sdk") ||
        Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK built for x86") ||
        Build.MANUFACTURER.contains("Genymotion") ||
        Build.HARDWARE.contains("goldfish") ||
        Build.HARDWARE.contains("ranchu") ||
        Build.PRODUCT.contains("sdk_google") ||
        Build.PRODUCT.contains("google_sdk") ||
        Build.PRODUCT.contains("sdk") ||
        Build.PRODUCT.contains("emulator") ||
        Build.PRODUCT.contains("simulator")
    }

    private val resolveQueue = java.util.concurrent.ConcurrentLinkedQueue<NsdServiceInfo>()
    private val isResolving = AtomicBoolean(false)
    private var emulatorBridgeJob: Job? = null

    /**
     * Start the local ServerSocket and register mDNS service.
     */
    fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        Log.i(tag, "Starting MdnsWifiTransport for $localDisplayName ($localAuthorId) [isEmulator=$isEmulator]")

        try {
            // 1. Bind local server socket (try DEFAULT_PORT first, fallback to ephemeral port)
            serverSocket = try {
                ServerSocket(DEFAULT_PORT).also {
                    serverPort = DEFAULT_PORT
                    Log.i(tag, "Bound ServerSocket on fixed port $DEFAULT_PORT")
                }
            } catch (e: Exception) {
                ServerSocket(0).also {
                    serverPort = it.localPort
                    Log.i(tag, "Fixed port $DEFAULT_PORT busy, bound fallback port $serverPort")
                }
            }

            // 2. Start TCP server listener loop
            startServerAcceptLoop()

            // 3. Register mDNS service
            registerNsdService()

            // 4. Start discovering nearby mDNS services
            discoverServices()

            // 5. Start background stale peer pruner loop
            startStalePeerPruner()

            // 6. If running inside an emulator, start proactive host bridge probe loop
            if (isEmulator) {
                startEmulatorBridgeLoop()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start MdnsWifiTransport: ${e.message}", e)
        }
    }

    private fun startStalePeerPruner() {
        prunerJob?.cancel()
        prunerJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning.get()) {
                delay(3000)
                val now = System.currentTimeMillis()
                val staleThresholdMs = 45_000L
                val staleKeys = _peersMap.entries
                    .filter { now - it.value.lastSeenTimestamp > staleThresholdMs }
                    .map { it.key }
                if (staleKeys.isNotEmpty()) {
                    staleKeys.forEach { key ->
                        _peersMap.remove(key)
                        Log.i(tag, "Pruned offline peer: $key")
                    }
                    updatePeersState()
                }
            }
        }
    }

    private fun startEmulatorBridgeLoop() {
        emulatorBridgeJob = scope.launch(Dispatchers.IO) {
            Log.i(tag, "Emulator detected: Starting Host ADB bridge probing loop")
            delay(1500)
            try {
                syncWithDirectIp("10.0.2.2", DEFAULT_PORT, peerName = "Host ADB Bridge")
            } catch (_: Exception) {}

            while (isActive && isRunning.get()) {
                val intervalSec = appSettings?.syncIntervalSeconds?.value ?: 10
                val intervalMs = (intervalSec.toLong() * 1000L).coerceAtLeast(3000L)
                delay(intervalMs)
                try {
                    syncWithDirectIp("10.0.2.2", DEFAULT_PORT, peerName = "Host ADB Bridge")
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Stop mDNS advertising, discovery, and close the server socket.
     */
    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        Log.i(tag, "Stopping MdnsWifiTransport")

        prunerJob?.cancel()
        prunerJob = null

        emulatorBridgeJob?.cancel()
        emulatorBridgeJob = null

        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.w(tag, "Error stopping service discovery: ${e.message}")
        }
        discoveryListener = null

        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (e: Exception) {
            Log.w(tag, "Error unregistering service: ${e.message}")
        }
        registrationListener = null

        serverJob?.cancel()
        serverJob = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        serviceNameToPeerId.clear()
        _peersMap.clear()
        _discoveredPeers.value = emptyList()
    }

    /**
     * Manual trigger to force immediate sync with a discovered peer.
     */
    fun forceSync(peerId: String) {
        val peer = _peersMap[peerId] ?: _peersMap.values.find { it.peerId == peerId } ?: return
        if (peer.hostAddress == null || peer.port == null) return
        triggerOutboundSync(peer.hostAddress, peer.port, peer.peerId, peer.peerName)
    }

    fun onPeerIdentified(tempKey: String, realAuthorId: String, realDisplayName: String) {
        Log.i(tag, "MdnsWifiTransport peer identified: tempKey=$tempKey, realAuthorId=$realAuthorId, realDisplayName=$realDisplayName")
        val cleanName = if (realDisplayName.isNotBlank() && realDisplayName != "MeshPeer" && realDisplayName != "User") {
            realDisplayName
        } else {
            "Nearby Device"
        }

        val existing = _peersMap.remove(tempKey)
            ?: _peersMap.remove(realAuthorId)
            ?: _peersMap.values.find { it.hostAddress == tempKey || it.peerId == tempKey }

        val updatedPeer = existing?.copy(
            peerId = realAuthorId,
            peerName = cleanName,
            lastSeenTimestamp = System.currentTimeMillis()
        ) ?: DiscoveredPeer(
            peerId = realAuthorId,
            peerName = cleanName,
            transportType = TransportType.MDNS_WIFI,
            hostAddress = if (tempKey.contains(".")) tempKey else null,
            lastSeenTimestamp = System.currentTimeMillis()
        )

        _peersMap[realAuthorId] = updatedPeer
        updatePeersState()
    }

    /**
     * Connect directly to an IP address (e.g. 192.168.1.5 or 10.0.2.2) to sync gossip posts.
     */
    fun syncWithDirectIp(targetHost: String, targetPort: Int = DEFAULT_PORT, peerName: String = "Direct IP Peer") {
        val peerKey = "$targetHost:$targetPort"
        if (!activeSyncPeers.add(peerKey)) return

        scope.launch(Dispatchers.IO) {
            var connection: StreamPeerConnection? = null
            val safeIpKey = targetHost.replace(".", "_")
            val peerId = "direct_$safeIpKey"
            try {
                Log.i(tag, "Direct IP sync attempting connection to $targetHost:$targetPort")
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(java.net.InetSocketAddress(targetHost, targetPort), 3500)
                Log.i(tag, "Connected via Direct IP to $targetHost:$targetPort")

                val displayName = "$peerName ($targetHost)"
                val peer = DiscoveredPeer(
                    peerId = peerId,
                    peerName = displayName,
                    transportType = TransportType.MDNS_WIFI,
                    hostAddress = targetHost,
                    port = targetPort,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isSyncing = true
                )
                // Only put if not already identified with real profile
                if (!_peersMap.values.any { it.hostAddress == targetHost && !it.peerId.startsWith("direct_") }) {
                    _peersMap[peerId] = peer
                    updatePeersState()
                }

                connection = StreamPeerConnection(
                    peerId = peerId,
                    peerName = displayName,
                    transportType = TransportType.MDNS_WIFI,
                    socket = socket
                )
                syncEngine.syncWithPeer(connection)
            } catch (e: Exception) {
                Log.w(tag, "Direct IP sync with $targetHost:$targetPort failed: ${e.message}")
                removePeer(peerId)
                val toRemove = _peersMap.entries.filter { it.value.hostAddress == targetHost }.map { it.key }
                toRemove.forEach { removePeer(it) }
            } finally {
                connection?.close()
                activeSyncPeers.remove(peerKey)
                val current = _peersMap[peerId] ?: _peersMap.values.find { it.hostAddress == targetHost }
                if (current != null) {
                    _peersMap[current.peerId] = current.copy(isSyncing = false)
                    updatePeersState()
                }
            }
        }
    }

    private fun startServerAcceptLoop() {
        serverJob = scope.launch(Dispatchers.IO) {
            val server = serverSocket ?: return@launch
            while (isActive && !server.isClosed) {
                try {
                    val socket = server.accept()
                    launch { handleInboundConnection(socket) }
                } catch (e: Exception) {
                    if (isActive && !server.isClosed) {
                        Log.e(tag, "ServerSocket accept error: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun handleInboundConnection(socket: Socket) {
        val remoteIp = socket.inetAddress.hostAddress ?: "unknown"
        Log.i(tag, "Accepted inbound TCP connection from $remoteIp:${socket.port}")
        val peerId = "peer_$remoteIp"
        val connection = StreamPeerConnection(
            peerId = peerId,
            peerName = "Nearby Device ($remoteIp)",
            transportType = TransportType.MDNS_WIFI,
            socket = socket
        )
        try {
            syncEngine.syncWithPeer(connection)
        } catch (e: Exception) {
            Log.e(tag, "Inbound sync failed with $remoteIp: ${e.message}")
        } finally {
            connection.close()
        }
    }

    private fun registerNsdService() {
        val manager = nsdManager ?: return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = this@MdnsWifiTransport.serviceName
            serviceType = this@MdnsWifiTransport.serviceType
            port = serverPort
            setAttribute("authorId", localAuthorId)
            setAttribute("displayName", localDisplayName)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.i(tag, "NSD Service successfully registered: ${NsdServiceInfo.serviceName} on port $serverPort")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "NSD Service registration failed with errorCode: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.i(tag, "NSD Service unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "NSD Service unregistration failed with errorCode: $errorCode")
            }
        }

        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(tag, "Error calling registerService: ${e.message}")
        }
    }

    private fun discoverServices() {
        val manager = nsdManager ?: return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(tag, "NSD Service discovery started for $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.i(tag, "NSD Service found: ${service.serviceName} (type: ${service.serviceType})")
                if (service.serviceName == serviceName) {
                    // Ignore our own advertisement
                    return
                }
                if (service.serviceName.startsWith("MySocial") || service.serviceType.contains("mysocial", ignoreCase = true)) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.i(tag, "NSD Service lost: ${service.serviceName}")
                val mappedPeerId = serviceNameToPeerId.remove(service.serviceName)
                if (mappedPeerId != null) {
                    removePeer(mappedPeerId)
                } else {
                    val safeId = service.serviceName.removePrefix("MySocial_")
                    val matching = _peersMap.entries.firstOrNull {
                        it.key == service.serviceName ||
                        it.key.contains(safeId) ||
                        it.value.peerId.contains(safeId)
                    }?.key
                    if (matching != null) {
                        removePeer(matching)
                    } else {
                        _peersMap.remove(service.serviceName)
                        updatePeersState()
                    }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(tag, "NSD Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "NSD Start discovery failed with error: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "NSD Stop discovery failed with error: $errorCode")
            }
        }

        try {
            manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Error calling discoverServices: ${e.message}")
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        resolveQueue.offer(serviceInfo)
        processNextResolve()
    }

    private fun processNextResolve() {
        val manager = nsdManager ?: return
        if (!isResolving.compareAndSet(false, true)) return
        val next = resolveQueue.poll()
        if (next == null) {
            isResolving.set(false)
            return
        }

        try {
            manager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(tag, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                    isResolving.set(false)
                    processNextResolve()
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    try {
                        handleResolvedPeer(resolved)
                    } finally {
                        isResolving.set(false)
                        processNextResolve()
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(tag, "Error during resolveService: ${e.message}")
            isResolving.set(false)
            processNextResolve()
        }
    }

    private fun handleResolvedPeer(resolved: NsdServiceInfo) {
        val host: InetAddress = resolved.host ?: return
        val hostAddress = host.hostAddress ?: return
        val port = resolved.port
        val rawAuthorId = resolved.attributes["authorId"]?.let { String(it, Charsets.UTF_8) }
        val peerId = rawAuthorId ?: resolved.serviceName
        val rawDisplayName = resolved.attributes["displayName"]?.let { String(it, Charsets.UTF_8) }
        val peerName = if (!rawDisplayName.isNullOrBlank() && rawDisplayName != "MeshPeer" && rawDisplayName != "User") {
            rawDisplayName
        } else {
            "Nearby Device"
        }

        if (peerId == localAuthorId || rawAuthorId == localAuthorId || resolved.serviceName.contains(safeLocalId)) return

        serviceNameToPeerId[resolved.serviceName] = peerId

        Log.i(tag, "Resolved peer $peerName ($peerId) at $hostAddress:$port")

        val peer = DiscoveredPeer(
            peerId = peerId,
            peerName = peerName,
            transportType = TransportType.MDNS_WIFI,
            hostAddress = hostAddress,
            port = port,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        _peersMap[peerId] = peer
        updatePeersState()

        // Trigger immediate outbound sync upon peer resolution so historical posts sync as soon as peer is around
        // activeSyncPeers deduplicates concurrent sync attempts
        triggerOutboundSync(hostAddress, port, peerId, peerName)
    }

    private fun triggerOutboundSync(hostAddress: String, port: Int, peerId: String, peerName: String) {
        if (!activeSyncPeers.add(peerId)) {
            // Already syncing with this peer
            return
        }

        scope.launch(Dispatchers.IO) {
            updatePeerSyncStatus(peerId, true)
            var connection: StreamPeerConnection? = null
            try {
                val candidateHosts = linkedSetOf<Pair<String, Int>>()
                // If the discovered host is an emulator's private NAT IP (10.0.2.x),
                // physical devices cannot reach it over Wi-Fi. If USB/ADB reverse is active,
                // 127.0.0.1 on the same port connects to the emulator.
                if (hostAddress.startsWith("10.0.2.")) {
                    candidateHosts.add("127.0.0.1" to ADB_REVERSE_PORT)
                    if (port != serverPort) candidateHosts.add("127.0.0.1" to port)
                    if (DEFAULT_PORT != serverPort) candidateHosts.add("127.0.0.1" to DEFAULT_PORT)
                }
                candidateHosts.add(hostAddress to port)
                if (port != DEFAULT_PORT) {
                    candidateHosts.add(hostAddress to DEFAULT_PORT)
                }

                var socket: Socket? = null
                var connectedHost = ""
                var connectedPort = port
                for ((h, p) in candidateHosts) {
                    try {
                        Log.i(tag, "Attempting connection to $peerName at $h:$p")
                        val s = Socket()
                        s.tcpNoDelay = true
                        s.connect(java.net.InetSocketAddress(h, p), 3500)
                        socket = s
                        connectedHost = h
                        connectedPort = p
                        Log.i(tag, "Connected to $peerName at $h:$p")
                        break
                    } catch (e: Exception) {
                        Log.w(tag, "Failed to connect to $h:$p: ${e.message}")
                    }
                }

                if (socket == null) {
                    throw java.io.IOException("Unable to connect to $peerName on any candidate address")
                }

                connection = StreamPeerConnection(
                    peerId = peerId,
                    peerName = peerName,
                    transportType = TransportType.MDNS_WIFI,
                    socket = socket
                )
                syncEngine.syncWithPeer(connection)
            } catch (e: Exception) {
                Log.w(tag, "Outbound sync with $peerId failed: ${e.message}")
                removePeer(peerId)
            } finally {
                connection?.close()
                activeSyncPeers.remove(peerId)
                updatePeerSyncStatus(peerId, false)
            }
        }
    }

    private fun updatePeerSyncStatus(peerId: String, isSyncing: Boolean) {
        val peer = _peersMap[peerId] ?: return
        _peersMap[peerId] = peer.copy(isSyncing = isSyncing)
        updatePeersState()
    }

    private fun updatePeersState() {
        _discoveredPeers.value = _peersMap.values.toList().sortedByDescending { it.lastSeenTimestamp }
    }
}
