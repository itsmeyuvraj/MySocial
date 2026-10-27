package com.mysocial.mesh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.data.MeshDatabase
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.transport.BleMeshTransport
import com.mysocial.mesh.transport.DiscoveredPeer
import com.mysocial.mesh.transport.MdnsWifiTransport
import com.mysocial.mesh.ui.MainActivity
import com.mysocial.mesh.utils.PeerNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Android Foreground Service for P2P Epidemic Gossip Mesh Synchronization.
 * Runs continuously with `foregroundServiceType="connectedDevice"` to maintain
 * background BLE beacons, Wi-Fi mDNS discovery, and store-carry-forward gossip syncing.
 */
class MeshSyncService : Service() {

    private val tag = "MeshSyncService"
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var db: MeshDatabase
    private lateinit var cryptoManager: CryptoManager
    private lateinit var syncEngine: GossipSyncEngine

    private var wifiTransport: MdnsWifiTransport? = null
    private var bleTransport: BleMeshTransport? = null
    private var periodicSyncJob: Job? = null

    private val _isMeshActive = MutableStateFlow(false)
    val isMeshActive: StateFlow<Boolean> = _isMeshActive.asStateFlow()

    private val _allDiscoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val allDiscoveredPeers: StateFlow<List<DiscoveredPeer>> = _allDiscoveredPeers.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): MeshSyncService = this@MeshSyncService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private lateinit var appSettings: com.mysocial.mesh.data.AppSettings

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "MeshSyncService onCreate")
        createNotificationChannel()

        appSettings = com.mysocial.mesh.data.AppSettings.getInstance(this)
        db = MeshDatabase.getInstance(this)
        cryptoManager = CryptoManager.getInstance(this)
        val initialName = android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: "User"
        syncEngine = GossipSyncEngine(
            db = db,
            cryptoManager = cryptoManager,
            myDisplayName = initialName,
            openMeshMode = true,
            context = this
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_STOP -> stopMesh()
            ACTION_START -> startMesh()
        }
        return START_STICKY
    }

    private fun startMesh() {
        if (_isMeshActive.value) return
        Log.i(tag, "Starting Mesh Engine and Transports")
        _isMeshActive.value = true

        val initialNotification = buildNotification("Mesh Active • Discovering peers...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        val initialName = syncEngine.myDisplayName

        // Initialize transports
        wifiTransport = MdnsWifiTransport(
            context = this,
            localAuthorId = cryptoManager.authorId,
            localDisplayName = initialName,
            syncEngine = syncEngine,
            scope = serviceScope,
            appSettings = appSettings
        ).also { it.start() }

        bleTransport = BleMeshTransport(
            context = this,
            localAuthorId = cryptoManager.authorId,
            localDisplayName = initialName,
            syncEngine = syncEngine,
            scope = serviceScope
        ).also { it.start() }

        // Update peer list immediately when real profile name is received during handshake
        syncEngine.addPeerIdentifiedListener { connectionPeerId, remoteAuthorId, remoteDisplayName ->
            if (remoteAuthorId == cryptoManager.authorId) {
                // Ignore and remove self loopback connection
                wifiTransport?.removePeer(connectionPeerId)
                _allDiscoveredPeers.value = _allDiscoveredPeers.value.filter {
                    it.peerId != connectionPeerId && it.peerId != remoteAuthorId
                }
            } else {
                wifiTransport?.onPeerIdentified(connectionPeerId, remoteAuthorId, remoteDisplayName)
                bleTransport?.onPeerIdentified(connectionPeerId, remoteAuthorId, remoteDisplayName)
                _allDiscoveredPeers.value = _allDiscoveredPeers.value
                    .filter { it.peerId != cryptoManager.authorId }
                    .map { peer ->
                        if (peer.peerId == connectionPeerId || peer.peerId == remoteAuthorId || peer.peerId.startsWith("peer_") || peer.peerId.startsWith("direct_")) {
                            peer.copy(peerId = remoteAuthorId, peerName = remoteDisplayName)
                        } else {
                            peer
                        }
                    }
            }
        }

        // Observe profile updates so transports and engine stay in sync with user's profile
        serviceScope.launch(Dispatchers.IO) {
            val current = db.profileDao().getProfile(cryptoManager.authorId)
            val name = current?.displayName ?: initialName
            syncEngine.myDisplayName = name
            wifiTransport?.updateDisplayName(name)
            bleTransport?.updateDisplayName(name)

            // Initial log sequence
            val latestEntry = db.logEntryDao().getLatestEntryForAuthor(cryptoManager.authorId)
            if (latestEntry != null) {
                bleTransport?.updateLatestSequence(latestEntry.sequence)
            }

            // Reactively observe log sequences so new posts/actions immediately update BLE 5.0 advertisement
            serviceScope.launch(Dispatchers.IO) {
                db.logEntryDao().observeLatestSequenceForAuthor(cryptoManager.authorId).collect { maxSeq ->
                    if (maxSeq != null && maxSeq > 0) {
                        bleTransport?.updateLatestSequence(maxSeq)
                    }
                }
            }

            db.profileDao().observeProfile(cryptoManager.authorId).collect { profile ->
                if (profile != null && profile.displayName.isNotBlank()) {
                    syncEngine.myDisplayName = profile.displayName
                    wifiTransport?.updateDisplayName(profile.displayName)
                    bleTransport?.updateDisplayName(profile.displayName)
                }
            }
        }

        // Observe combined peers and update foreground notification
        serviceScope.launch {
            val wifiPeers = wifiTransport?.discoveredPeers ?: MutableStateFlow(emptyList())
            val blePeers = bleTransport?.discoveredPeers ?: MutableStateFlow(emptyList())
            val ticker = kotlinx.coroutines.flow.flow {
                while (isActive) {
                    emit(System.currentTimeMillis())
                    delay(3000)
                }
            }

            var lastStatusText: String? = null
            var cachedPostCount = -1
            var lastPostCountQueryTime = 0L
            var wasPeersEmpty = true

            combine(wifiPeers, blePeers, ticker) { w, b, now ->
                (w + b)
                    .filter { now - it.lastSeenTimestamp <= 15_000L }
                    .distinctBy { it.peerId }
                    .sortedByDescending { it.lastSeenTimestamp }
            }.collect { combined ->
                _allDiscoveredPeers.value = combined
                if (combined.isNotEmpty() && wasPeersEmpty) {
                    PeerNotificationHelper.notifyPeerNearby(this@MeshSyncService, combined.firstOrNull()?.peerName)
                }
                wasPeersEmpty = combined.isEmpty()
                val now = System.currentTimeMillis()
                if (cachedPostCount == -1 || now - lastPostCountQueryTime > 30_000L) {
                    cachedPostCount = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        try { db.postViewDao().getTotalPostsCount() } catch (_: Exception) { 0 }
                    }
                    lastPostCountQueryTime = now
                }
                val statusText = if (combined.isNotEmpty()) {
                    "Mesh Active • ${combined.size} Peers Nearby • $cachedPostCount Posts Cached"
                } else {
                    "Mesh Active • Searching for nearby devices"
                }
                if (statusText != lastStatusText) {
                    lastStatusText = statusText
                    updateNotification(statusText)
                }
            }
        }

        // Start automatic periodic background sync pulse with user-configured interval
        startPeriodicSync()

        // Dynamically adjust sync frequency when setting changes
        serviceScope.launch {
            appSettings.syncIntervalSeconds.collect { seconds ->
                Log.i(tag, "Configured sync interval updated to $seconds seconds")
                if (_isMeshActive.value) {
                    startPeriodicSync()
                }
            }
        }
    }

    private fun startPeriodicSync() {
        periodicSyncJob?.cancel()
        periodicSyncJob = serviceScope.launch {
            while (isActive && _isMeshActive.value) {
                val intervalMs = (appSettings.syncIntervalSeconds.value.toLong() * 1000L).coerceAtLeast(3000L)
                delay(intervalMs)
                forceSyncAll()
            }
        }
    }

    private fun stopMesh() {
        Log.i(tag, "Stopping Mesh Engine")
        _isMeshActive.value = false
        periodicSyncJob?.cancel()
        periodicSyncJob = null
        wifiTransport?.stop()
        wifiTransport = null
        bleTransport?.stop()
        bleTransport = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun forceSyncAll() {
        wifiTransport?.discoveredPeers?.value?.forEach { peer ->
            wifiTransport?.forceSync(peer.peerId)
        }
        bleTransport?.discoveredPeers?.value?.forEach { peer ->
            bleTransport?.forceSync(peer.peerId)
        }
        if (wifiTransport?.isEmulator == true) {
            wifiTransport?.syncWithDirectIp("10.0.2.2", com.mysocial.mesh.transport.MdnsWifiTransport.DEFAULT_PORT, "Host ADB Bridge")
        }
    }

    fun syncWithDirectIp(targetHost: String, targetPort: Int = 42530) {
        wifiTransport?.syncWithDirectIp(targetHost, targetPort)
    }

    fun getSyncEngine(): GossipSyncEngine = syncEngine

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MySocial P2P Mesh")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MySocial P2P Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors nearby BLE and Wi-Fi peers for offline social sync"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(tag, "App task removed by user, cleanly stopping mesh service")
        stopMesh()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMesh()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "com.mysocial.mesh.action.START"
        const val ACTION_STOP = "com.mysocial.mesh.action.STOP"
        private const val CHANNEL_ID = "mysocial_mesh_channel"
        private const val NOTIFICATION_ID = 4040

        fun start(context: Context) {
            val intent = Intent(context, MeshSyncService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MeshSyncService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
