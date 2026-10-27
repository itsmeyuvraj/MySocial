package com.mysocial.mesh.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.mysocial.mesh.crypto.CryptoManager
import com.mysocial.mesh.protocol.BleChunker
import com.mysocial.mesh.protocol.BleExtendedPayload
import com.mysocial.mesh.protocol.BleReassembler
import com.mysocial.mesh.protocol.BluetoothSocketPeerConnection
import com.mysocial.mesh.protocol.GossipMessage
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.protocol.PeerConnection
import com.mysocial.mesh.protocol.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pure AOSP High-Speed Bluetooth Low Energy (BLE) Mesh Transport.
 *
 * Bluetooth 5.0+ Extended Advertising & High-Speed Throughput Architecture:
 * 1. BLE Extended Advertising (API 26+, Bluetooth 5.0+):
 *    - Auxiliary advertising packets on secondary data channels via PHY LE 2M (2 Mbps)
 *    - Expanded broadcast payload throughput (up to 254 bytes per PDU, up to 1650 bytes chained)
 *    - Broadcasts full 32-byte Ed25519 author identity, real display name, dynamic L2CAP PSM port,
 *      latest sequence clock, and arbitrary ephemeral broadcast payloads without connections.
 * 2. Extended BLE Scanner:
 *    - Configured with `ScanSettings.Builder.setLegacy(false)` and `PHY_LE_ALL_SUPPORTED` to listen
 *      simultaneously to both Bluetooth 5.0 Extended Advertising and legacy Bluetooth 4.x beacons.
 * 3. Fast-Path Direct L2CAP CoC (Connection-Oriented Channels):
 *    - Discovered peers broadcast their dynamic L2CAP PSM directly in the extended advertising payload.
 *    - Enables connecting immediately to the high-speed L2CAP socket without lengthy GATT service
 *      discovery, MTU renegotiation, and characteristic read roundtrips.
 * 4. Resilient Multi-Tier Hardware Fallback:
 *    - Automatic runtime inspection of `isLeExtendedAdvertisingSupported` & `isLe2MPhySupported`.
 *    - Seamless automatic fallback to Legacy Advertising & Scanning for older hardware or controllers.
 * 5. High-Speed GATT & L2CAP Server:
 *    - Insecure L2CAP CoC server (`listenUsingInsecureL2capChannel` on API 29+)
 *    - Fallback GATT Server with PSM characteristic and BleChunker / BleReassembler packet pipeline.
 */
class BleMeshTransport(
    private val context: Context,
    private val localAuthorId: String,
    private var localDisplayName: String,
    private val syncEngine: GossipSyncEngine,
    private val scope: CoroutineScope
) {
    private val tag = "BleMeshTransport"

    private val isRunning = AtomicBoolean(false)
    private val activeSyncDevices = ConcurrentHashMap.newKeySet<String>()
    private val lastSyncAttempt = ConcurrentHashMap<String, Long>()
    private val lastEmittedTimes = ConcurrentHashMap<String, Long>()
    private val lastRssiMap = ConcurrentHashMap<String, Int>()
    private val activeGattClients = ConcurrentHashMap<String, BluetoothGatt>()

    // Bluetooth 5.0 Extended Advertising & Fast-Path Maps
    private val peerPsmMap = ConcurrentHashMap<String, Int>()
    private val peerPhyMap = ConcurrentHashMap<String, String>()
    private val peerSequenceMap = ConcurrentHashMap<String, Long>()

    // Dynamic broadcast state
    private var currentDisplayName: String = localDisplayName
    private var currentLatestSequence: Long = 0L
    private var currentBroadcastData: ByteArray? = null

    private val _broadcastDataFlow = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 64)
    val broadcastDataFlow: SharedFlow<Pair<String, ByteArray>> = _broadcastDataFlow.asSharedFlow()

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000a001-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000a002-0000-1000-8000-00805f9b34fb")
        val PSM_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000a003-0000-1000-8000-00805f9b34fb")
        private const val SYNC_COOLDOWN_MS = 25_000L
        private const val RSSI_THROTTLE_DELTA_DBM = 6
        private const val PEER_UPDATE_THROTTLE_MS = 2_000L
        private const val GATT_CONNECT_TIMEOUT_MS = 15_000L
    }

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }
    private val advertiser: BluetoothLeAdvertiser? by lazy {
        bluetoothAdapter?.bluetoothLeAdvertiser
    }
    private val scanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private var gattServer: BluetoothGattServer? = null
    private var l2capServerSocket: BluetoothServerSocket? = null
    private var localL2capPsm: Int = 0

    private val _peersMap = ConcurrentHashMap<String, DiscoveredPeer>()
    private var prunerJob: Job? = null
    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    // Advertising handles
    private var advertisingSetCallback: AdvertisingSetCallback? = null
    private var currentAdvertisingSet: AdvertisingSet? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    private var isExtendedAdvertisingActive: Boolean = false
    private var isScanningExtended: Boolean = false

    val isAvailable: Boolean
        get() = bluetoothAdapter != null && bluetoothAdapter!!.isEnabled

    val isExtendedAdvertisingSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                bluetoothAdapter?.isLeExtendedAdvertisingSupported == true

    val is2MPhySupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                bluetoothAdapter?.isLe2MPhySupported == true

    val isCodedPhySupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                bluetoothAdapter?.isLeCodedPhySupported == true

    val maxAdvertisingDataLength: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bluetoothAdapter != null) {
            bluetoothAdapter!!.leMaximumAdvertisingDataLength
        } else {
            31
        }

    /**
     * Start BLE advertising (Extended with PHY 2M or Legacy fallback), L2CAP listener, GATT server, and scanning.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w(tag, "Bluetooth is disabled or unavailable on this device")
            return
        }

        Log.i(tag, "Starting BleMeshTransport for $currentDisplayName ($localAuthorId). ExtendedAdvSupported=$isExtendedAdvertisingSupported, 2MPhySupported=$is2MPhySupported, maxAdvDataLen=$maxAdvertisingDataLength")
        try {
            startL2capServer()
            startGattServer()
            startAdvertising()
            startScanning()
            startStalePeerPruner()
        } catch (e: SecurityException) {
            Log.e(tag, "Bluetooth permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e(tag, "Error starting BleMeshTransport: ${e.message}", e)
        }
    }

    /**
     * Stop BLE advertising, scanning, L2CAP listener, and close GATT server.
     */
    @SuppressLint("MissingPermission")
    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        Log.i(tag, "Stopping BleMeshTransport")

        prunerJob?.cancel()
        prunerJob = null

        // Stop Extended Advertising Set if active
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && advertisingSetCallback != null) {
                advertiser?.stopAdvertisingSet(advertisingSetCallback)
            }
        } catch (_: Exception) {}
        advertisingSetCallback = null
        currentAdvertisingSet = null
        isExtendedAdvertisingActive = false

        // Stop Legacy Advertising if active
        try {
            advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        } catch (_: Exception) {}
        advertiseCallback = null

        // Stop Scanner
        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (_: Exception) {}
        scanCallback = null
        isScanningExtended = false

        try {
            l2capServerSocket?.close()
        } catch (_: Exception) {}
        l2capServerSocket = null

        try {
            gattServer?.close()
        } catch (_: Exception) {}
        gattServer = null

        activeGattClients.values.forEach { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: Exception) {}
        }
        activeGattClients.clear()
        activeSyncDevices.clear()
        lastSyncAttempt.clear()
        lastEmittedTimes.clear()
        lastRssiMap.clear()
        peerPsmMap.clear()
        peerPhyMap.clear()
        peerSequenceMap.clear()

        _peersMap.clear()
        _discoveredPeers.value = emptyList()
    }

    private fun startStalePeerPruner() {
        prunerJob?.cancel()
        prunerJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning.get()) {
                delay(3000)
                val now = System.currentTimeMillis()
                val staleThresholdMs = 15_000L
                val staleKeys = _peersMap.entries
                    .filter { now - it.value.lastSeenTimestamp > staleThresholdMs }
                    .map { it.key }
                if (staleKeys.isNotEmpty()) {
                    staleKeys.forEach { key ->
                        _peersMap.remove(key)
                        Log.i(tag, "Pruned offline BLE peer: $key")
                    }
                    updatePeersState()
                }
            }
        }
    }

    /**
     * Starts BLE L2CAP Connection-Oriented Channel listener on API 29+.
     */
    @SuppressLint("MissingPermission")
    private fun startL2capServer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && bluetoothAdapter != null) {
            try {
                val server = bluetoothAdapter!!.listenUsingInsecureL2capChannel()
                l2capServerSocket = server
                localL2capPsm = server.psm
                Log.i(tag, "BLE L2CAP CoC Server listening on dynamic PSM $localL2capPsm")

                Thread({
                    while (isRunning.get()) {
                        try {
                            val socket = server.accept() ?: break
                            val remoteDevice = socket.remoteDevice
                            val address = remoteDevice?.address ?: "BLE_L2CAP"
                            val cachedPeer = _peersMap[address] ?: _peersMap.values.find { it.hostAddress == address || it.peerId == address }
                            val name = cachedPeer?.peerName ?: remoteDevice?.name ?: "Nearby BLE Peer"
                            val peerId = cachedPeer?.peerId ?: address
                            Log.i(tag, "Accepted incoming high-speed BLE L2CAP channel from $name ($address)")

                            scope.launch(Dispatchers.IO) {
                                val connection = BluetoothSocketPeerConnection(
                                    peerId = peerId,
                                    peerName = name,
                                    transportType = TransportType.BLE,
                                    socket = socket
                                )
                                syncEngine.syncWithPeer(connection)
                            }
                        } catch (e: Exception) {
                            if (!isRunning.get()) break
                        }
                    }
                }, "BleL2capServerThread").start()
            } catch (e: Exception) {
                Log.w(tag, "L2CAP server channel initialization failed: ${e.message}")
            }
        }
    }

    /**
     * Starts BLE advertising. Automatically utilizes Bluetooth 5.0 Extended Advertising
     * with secondary 2M PHY when supported, falling back to legacy advertising.
     */
    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (isExtendedAdvertisingSupported) {
            Log.i(tag, "Hardware supports BLE Extended Advertising (BT 5.0+). Initializing Extended Advertising Set.")
            startExtendedAdvertising()
        } else {
            Log.i(tag, "BLE Extended Advertising not supported by hardware. Starting Legacy BLE Advertising.")
            startLegacyAdvertising()
        }
    }

    /**
     * Bluetooth 5.0 Extended Advertising with secondary 2M PHY channel for higher broadcast throughput.
     */
    @SuppressLint("MissingPermission")
    private fun startExtendedAdvertising() {
        val adv = advertiser ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startLegacyAdvertising()
            return
        }

        val secondaryPhy = if (is2MPhySupported) {
            BluetoothDevice.PHY_LE_2M
        } else {
            BluetoothDevice.PHY_LE_1M
        }

        val params = AdvertisingSetParameters.Builder()
            .setLegacyMode(false) // Bluetooth 5.0 Extended Advertising Mode
            .setConnectable(true)
            .setInterval(AdvertisingSetParameters.INTERVAL_LOW) // 100ms interval for fast discovery
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
            .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
            .setSecondaryPhy(secondaryPhy)
            .build()

        val payloadBytes = buildExtendedPayloadBytes()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), payloadBytes)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val cb = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: AdvertisingSet?,
                txPower: Int,
                status: Int
            ) {
                if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                    currentAdvertisingSet = advertisingSet
                    isExtendedAdvertisingActive = true
                    val phyName = if (secondaryPhy == BluetoothDevice.PHY_LE_2M) "2M PHY" else "1M PHY"
                    Log.i(tag, "BLE 5.0 Extended Advertising started successfully (PHY=$phyName, txPower=$txPower dBm, payload=${payloadBytes.size} bytes)")
                } else {
                    Log.w(tag, "Extended Advertising start failed with status $status. Falling back to legacy advertising.")
                    currentAdvertisingSet = null
                    isExtendedAdvertisingActive = false
                    startLegacyAdvertising()
                }
            }

            override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
                Log.i(tag, "BLE Extended Advertising set stopped")
                currentAdvertisingSet = null
                isExtendedAdvertisingActive = false
            }

            override fun onAdvertisingDataSet(advertisingSet: AdvertisingSet?, status: Int) {
                if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                    Log.d(tag, "BLE Extended Advertising broadcast payload updated successfully")
                } else {
                    Log.w(tag, "Failed to update BLE Extended Advertising data: status=$status")
                }
            }
        }
        advertisingSetCallback = cb

        try {
            adv.startAdvertisingSet(
                params,
                data,
                null, // scanResponse must be null for non-legacy connectable advertising
                null, // periodicParameters
                null, // periodicData
                cb
            )
        } catch (e: Exception) {
            Log.w(tag, "startAdvertisingSet call failed: ${e.message}. Falling back to legacy advertising.")
            advertisingSetCallback = null
            startLegacyAdvertising()
        }
    }

    /**
     * Fallback Legacy BLE Advertising (Bluetooth 4.x) for devices without Extended Advertising.
     */
    @SuppressLint("MissingPermission")
    private fun startLegacyAdvertising() {
        val adv = advertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val authorHashPrefix = CryptoManager.sha256Hex(localAuthorId).take(8).chunked(2)
            .map { it.toInt(16).toByte() }.toByteArray()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), authorHashPrefix)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.i(tag, "BLE Legacy Beacon advertising started successfully")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.w(tag, "BLE Legacy Beacon advertising failed with errorCode: $errorCode")
            }
        }
        advertiseCallback = cb

        try {
            adv.startAdvertising(settings, data, cb)
        } catch (e: Exception) {
            Log.e(tag, "Failed to call startAdvertising: ${e.message}")
        }
    }

    /**
     * Builds the high-throughput broadcast payload containing identity, capabilities, and data.
     */
    private fun buildExtendedPayloadBytes(): ByteArray {
        val pubKeyBytes = try {
            CryptoManager.extractPublicKeyBytes(localAuthorId)
        } catch (_: Exception) {
            null
        }

        val payload = BleExtendedPayload(
            authorPublicKey = pubKeyBytes,
            authorId = localAuthorId,
            displayName = currentDisplayName,
            l2capPsm = if (localL2capPsm > 0) localL2capPsm else null,
            latestSequence = if (currentLatestSequence > 0) currentLatestSequence else null,
            broadcastData = currentBroadcastData,
            supportsL2cap = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && l2capServerSocket != null),
            supports2MPhy = is2MPhySupported
        )

        return payload.encode()
    }

    /**
     * Dynamically updates the display name broadcasted in the extended advertising payload.
     */
    @SuppressLint("MissingPermission")
    fun updateDisplayName(name: String) {
        if (name.isBlank() || name == currentDisplayName) return
        currentDisplayName = name
        refreshAdvertisingData()
    }

    /**
     * Updates the latest author log sequence number broadcasted to nearby peers.
     */
    @SuppressLint("MissingPermission")
    fun updateLatestSequence(sequence: Long) {
        if (sequence <= currentLatestSequence) return
        currentLatestSequence = sequence
        refreshAdvertisingData()
    }

    /**
     * Broadcasts arbitrary high-throughput data payload (e.g. status announcement)
     * over BLE Extended Advertising secondary 2M PHY channel without requiring a connection.
     */
    @SuppressLint("MissingPermission")
    fun broadcastPayload(data: ByteArray?) {
        currentBroadcastData = data
        refreshAdvertisingData()
    }

    @SuppressLint("MissingPermission")
    private fun refreshAdvertisingData() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isExtendedAdvertisingActive && currentAdvertisingSet != null) {
            try {
                val newBytes = buildExtendedPayloadBytes()
                val data = AdvertiseData.Builder()
                    .addServiceUuid(ParcelUuid(SERVICE_UUID))
                    .addServiceData(ParcelUuid(SERVICE_UUID), newBytes)
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .build()
                currentAdvertisingSet?.setAdvertisingData(data)
            } catch (e: Exception) {
                Log.w(tag, "Error refreshing extended advertising data: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val sc = scanner ?: return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)

        if (isExtendedAdvertisingSupported) {
            try {
                settingsBuilder.setLegacy(false) // Listen to both legacy and extended advertising packets
                settingsBuilder.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                isScanningExtended = true
            } catch (e: Exception) {
                Log.w(tag, "Failed to configure extended scan settings: ${e.message}")
                isScanningExtended = false
            }
        }

        val settings = settingsBuilder.build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(tag, "BLE Scan failed with errorCode: $errorCode")
                if (errorCode == ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED && isScanningExtended) {
                    Log.w(tag, "Extended scanning unsupported by controller. Falling back to legacy scan mode.")
                    isScanningExtended = false
                    startLegacyScanning()
                }
            }
        }

        try {
            sc.startScan(listOf(filter), settings, scanCallback)
            Log.i(tag, "BLE scanner started (extendedMode=$isScanningExtended)")
        } catch (e: Exception) {
            Log.w(tag, "Failed to startScan with extended settings: ${e.message}. Retrying with legacy.")
            isScanningExtended = false
            startLegacyScanning()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLegacyScanning() {
        val sc = scanner ?: return
        try {
            scanCallback?.let { sc.stopScan(it) }
        } catch (_: Exception) {}

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(tag, "BLE Legacy Scan failed with errorCode: $errorCode")
            }
        }

        try {
            sc.startScan(listOf(filter), settings, scanCallback)
            Log.i(tag, "BLE Legacy Scanner started successfully")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start legacy scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val rssi = result.rssi
        val deviceName = device.name ?: "Nearby BLE Peer"
        val now = System.currentTimeMillis()

        // Extract Extended Advertising telemetry
        val isExtended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) !result.isLegacy else false
        val secondaryPhy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.secondaryPhy else 0
        val phyDescription = when (secondaryPhy) {
            BluetoothDevice.PHY_LE_2M -> "LE 2M"
            BluetoothDevice.PHY_LE_CODED -> "LE Coded"
            BluetoothDevice.PHY_LE_1M -> "LE 1M"
            else -> if (isExtended) "LE Ext" else "LE Legacy"
        }

        // Decode service data payload
        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))
        val extPayload = serviceData?.let { BleExtendedPayload.decode(it) }

        // Loopback protection: ignore self advertisements
        if (extPayload?.authorId == localAuthorId) {
            return
        }

        // Resolved identity
        val resolvedAuthorId = extPayload?.authorId ?: address
        val resolvedDisplayName = extPayload?.displayName?.takeIf { it.isNotBlank() } ?: deviceName

        // Cache remote capabilities & fast-path port
        extPayload?.l2capPsm?.let { psm ->
            peerPsmMap[address] = psm
            peerPsmMap[resolvedAuthorId] = psm
        }
        peerPhyMap[address] = phyDescription
        peerPhyMap[resolvedAuthorId] = phyDescription

        extPayload?.latestSequence?.let { seq ->
            peerSequenceMap[address] = seq
            peerSequenceMap[resolvedAuthorId] = seq
        }

        // Emit high-throughput broadcast data if present
        extPayload?.broadcastData?.let { data ->
            if (data.isNotEmpty()) {
                _broadcastDataFlow.tryEmit(resolvedAuthorId to data)
            }
        }

        val lastEmitted = lastEmittedTimes[address] ?: 0L
        val lastRssi = lastRssiMap[address] ?: (rssi - 100)
        val rssiChanged = kotlin.math.abs(rssi - lastRssi) >= RSSI_THROTTLE_DELTA_DBM
        val timeElapsed = (now - lastEmitted) >= PEER_UPDATE_THROTTLE_MS

        val existingPeer = _peersMap[resolvedAuthorId] ?: _peersMap[address]
        if (existingPeer != null && !rssiChanged && !timeElapsed) {
            _peersMap[resolvedAuthorId] = existingPeer.copy(
                lastSeenTimestamp = now,
                rssi = rssi,
                isExtendedAdvertising = isExtended,
                phyType = phyDescription,
                l2capPsm = extPayload?.l2capPsm ?: existingPeer.l2capPsm,
                latestSequence = extPayload?.latestSequence ?: existingPeer.latestSequence
            )
        } else {
            lastEmittedTimes[address] = now
            lastRssiMap[address] = rssi
            val peer = DiscoveredPeer(
                peerId = resolvedAuthorId,
                peerName = resolvedDisplayName,
                transportType = TransportType.BLE,
                rssi = rssi,
                lastSeenTimestamp = now,
                isExtendedAdvertising = isExtended,
                phyType = phyDescription,
                l2capPsm = extPayload?.l2capPsm,
                latestSequence = extPayload?.latestSequence
            )
            if (resolvedAuthorId != address) {
                _peersMap.remove(address)
            }
            _peersMap[resolvedAuthorId] = peer
            updatePeersState()
        }

        // Connect and sync with cooldown to prevent rapid reconnection loops
        val lastAttempt = lastSyncAttempt[address] ?: 0L
        if (!activeSyncDevices.contains(address) &&
            !activeSyncDevices.contains(resolvedAuthorId) &&
            (now - lastAttempt >= SYNC_COOLDOWN_MS)
        ) {
            connectAndSync(device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        val bm = bluetoothManager ?: return
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)

        // PSM Characteristic for advertising L2CAP port to connecting legacy clients
        val psmCharacteristic = BluetoothGattCharacteristic(
            PSM_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(psmCharacteristic)

        gattServer = bm.openGattServer(context, object : BluetoothGattServerCallback() {
            private val reassembler = BleReassembler()

            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    reassembler.reset()
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == PSM_CHARACTERISTIC_UUID) {
                    val psmBytes = ByteBuffer.allocate(4).putInt(localL2capPsm).array()
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, psmBytes)
                } else {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, byteArrayOf())
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }

                val completePayload = reassembler.processChunk(value)
                if (completePayload != null) {
                    val json = String(completePayload, Charsets.UTF_8)
                    Log.d(tag, "Received complete BLE message: ${json.take(40)}...")
                }
            }
        })

        gattServer?.addService(service)
    }

    /**
     * Connects to a remote BLE device:
     * - Fast-Path: Directly connects via BLE L2CAP CoC on API 29+ when remote PSM was broadcasted in BLE Extended Advertising
     * - Fallback: Connects via GATT, requests CONNECTION_PRIORITY_HIGH & 2M PHY, discovers PSM characteristic, then connects L2CAP
     */
    @SuppressLint("MissingPermission")
    private fun connectAndSync(device: BluetoothDevice) {
        val address = device.address ?: return
        val now = System.currentTimeMillis()
        if (!activeSyncDevices.add(address)) return
        lastSyncAttempt[address] = now

        val knownPsm = peerPsmMap[address]
        val cachedPeer = _peersMap[address] ?: _peersMap.values.find { it.hostAddress == address || it.peerId == address }
        val peerId = cachedPeer?.peerId ?: address
        val peerName = cachedPeer?.peerName ?: (device.name ?: "Nearby BLE Peer")

        updatePeerSyncStatus(address, true)
        updatePeerSyncStatus(peerId, true)

        // Fast-path: If L2CAP PSM was broadcasted in BLE Extended Advertisement, connect directly without GATT discovery
        if (knownPsm != null && knownPsm > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.i(tag, "Fast-path: Connecting directly via BLE L2CAP CoC to $address on known PSM $knownPsm (Extended Advertising)")
            scope.launch(Dispatchers.IO) {
                try {
                    val l2capSocket = device.createInsecureL2capChannel(knownPsm)
                    l2capSocket.connect()
                    Log.i(tag, "Successfully connected direct BLE L2CAP channel to $address (bypassed GATT roundtrips)")
                    val conn = BluetoothSocketPeerConnection(
                        peerId = peerId,
                        peerName = peerName,
                        transportType = TransportType.BLE,
                        socket = l2capSocket
                    )
                    syncEngine.syncWithPeer(conn)
                } catch (e: Exception) {
                    Log.w(tag, "Direct L2CAP connection failed to $address: ${e.message}. Falling back to GATT discovery.")
                    peerPsmMap.remove(address)
                    peerPsmMap.remove(peerId)
                    connectViaGattDiscovery(device, address, peerId, peerName)
                } finally {
                    activeSyncDevices.remove(address)
                    activeSyncDevices.remove(peerId)
                    updatePeerSyncStatus(address, false)
                    updatePeerSyncStatus(peerId, false)
                }
            }
            return
        }

        connectViaGattDiscovery(device, address, peerId, peerName)
    }

    @SuppressLint("MissingPermission")
    private fun connectViaGattDiscovery(
        device: BluetoothDevice,
        address: String,
        peerId: String,
        peerName: String
    ) {
        Log.i(tag, "Initiating BLE GATT discovery connection to $address")

        scope.launch(Dispatchers.IO) {
            var gattClient: BluetoothGatt? = null
            var timeoutJob: Job? = null
            try {
                timeoutJob = scope.launch(Dispatchers.IO) {
                    delay(GATT_CONNECT_TIMEOUT_MS)
                    if (activeSyncDevices.contains(address)) {
                        Log.w(tag, "GATT connection watchdog timed out for $address, closing socket")
                        try {
                            gattClient?.disconnect()
                            gattClient?.close()
                        } catch (_: Exception) {}
                        activeGattClients.remove(address)
                        activeSyncDevices.remove(address)
                        activeSyncDevices.remove(peerId)
                        updatePeerSyncStatus(address, false)
                        updatePeerSyncStatus(peerId, false)
                    }
                }

                gattClient = device.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.w(tag, "GATT connection failed with status $status for $address")
                            timeoutJob?.cancel()
                            try { gatt.close() } catch (_: Exception) {}
                            activeGattClients.remove(address)
                            activeSyncDevices.remove(address)
                            activeSyncDevices.remove(peerId)
                            updatePeerSyncStatus(address, false)
                            updatePeerSyncStatus(peerId, false)
                            return
                        }

                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.i(tag, "GATT connected to $address, requesting CONNECTION_PRIORITY_HIGH and 2M PHY")

                            // 1. Request High Connection Priority
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

                            // 2. Request 2M PHY on API 26+
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                gatt.setPreferredPhy(
                                    BluetoothDevice.PHY_LE_2M_MASK,
                                    BluetoothDevice.PHY_LE_2M_MASK,
                                    BluetoothDevice.PHY_OPTION_NO_PREFERRED
                                )
                            }

                            // 3. Request high MTU
                            gatt.requestMtu(517)

                            gatt.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            timeoutJob?.cancel()
                            try { gatt.close() } catch (_: Exception) {}
                            activeGattClients.remove(address)
                            activeSyncDevices.remove(address)
                            activeSyncDevices.remove(peerId)
                            updatePeerSyncStatus(address, false)
                            updatePeerSyncStatus(peerId, false)
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val service = gatt.getService(SERVICE_UUID)
                            val psmChar = service?.getCharacteristic(PSM_CHARACTERISTIC_UUID)
                            if (psmChar != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                gatt.readCharacteristic(psmChar)
                            } else {
                                timeoutJob?.cancel()
                                try { gatt.disconnect(); gatt.close() } catch (_: Exception) {}
                                activeGattClients.remove(address)
                                activeSyncDevices.remove(address)
                                activeSyncDevices.remove(peerId)
                                updatePeerSyncStatus(address, false)
                                updatePeerSyncStatus(peerId, false)
                            }
                        } else {
                            timeoutJob?.cancel()
                            try { gatt.disconnect(); gatt.close() } catch (_: Exception) {}
                            activeGattClients.remove(address)
                            activeSyncDevices.remove(address)
                            activeSyncDevices.remove(peerId)
                            updatePeerSyncStatus(address, false)
                            updatePeerSyncStatus(peerId, false)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        @Suppress("DEPRECATION")
                        val v = characteristic.value
                        handleCharacteristicRead(gatt, characteristic, v, status)
                    }

                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                        status: Int
                    ) {
                        handleCharacteristicRead(gatt, characteristic, value, status)
                    }

                    private fun handleCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray?,
                        status: Int
                    ) {
                        if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == PSM_CHARACTERISTIC_UUID) {
                            if (value != null && value.size >= 4) {
                                val remotePsm = ByteBuffer.wrap(value).int
                                if (remotePsm > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    Log.i(tag, "Attempting BLE L2CAP CoC channel connection to $address on PSM $remotePsm")
                                    timeoutJob?.cancel()
                                    peerPsmMap[address] = remotePsm
                                    peerPsmMap[peerId] = remotePsm

                                    // Close initial GATT discovery client as high-speed L2CAP channel connects
                                    try { gatt.disconnect(); gatt.close() } catch (_: Exception) {}
                                    activeGattClients.remove(address)

                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val l2capSocket = device.createInsecureL2capChannel(remotePsm)
                                            l2capSocket.connect()
                                            Log.i(tag, "Successfully connected BLE L2CAP channel to $address")
                                            val conn = BluetoothSocketPeerConnection(
                                                peerId = peerId,
                                                peerName = peerName,
                                                transportType = TransportType.BLE,
                                                socket = l2capSocket
                                            )
                                            syncEngine.syncWithPeer(conn)
                                        } catch (e: Exception) {
                                            Log.w(tag, "L2CAP connection failed to $address: ${e.message}")
                                        } finally {
                                            activeSyncDevices.remove(address)
                                            activeSyncDevices.remove(peerId)
                                            updatePeerSyncStatus(address, false)
                                            updatePeerSyncStatus(peerId, false)
                                        }
                                    }
                                    return
                                }
                            }
                        }
                        timeoutJob?.cancel()
                        try { gatt.disconnect(); gatt.close() } catch (_: Exception) {}
                        activeGattClients.remove(address)
                        activeSyncDevices.remove(address)
                        activeSyncDevices.remove(peerId)
                        updatePeerSyncStatus(address, false)
                        updatePeerSyncStatus(peerId, false)
                    }
                })

                if (gattClient != null) {
                    activeGattClients[address] = gattClient
                } else {
                    timeoutJob.cancel()
                    activeSyncDevices.remove(address)
                    activeSyncDevices.remove(peerId)
                    updatePeerSyncStatus(address, false)
                    updatePeerSyncStatus(peerId, false)
                }
            } catch (e: Exception) {
                Log.w(tag, "BLE connection to $address failed: ${e.message}")
                timeoutJob?.cancel()
                try { gattClient?.close() } catch (_: Exception) {}
                activeGattClients.remove(address)
                activeSyncDevices.remove(address)
                activeSyncDevices.remove(peerId)
                updatePeerSyncStatus(address, false)
                updatePeerSyncStatus(peerId, false)
            }
        }
    }

    /**
     * Manual or periodic trigger to force immediate sync with a discovered BLE peer.
     */
    fun forceSync(peerId: String) {
        val peer = _peersMap[peerId] ?: _peersMap.values.find { it.peerId == peerId } ?: return
        val mac = if (peer.peerId.contains(":")) peer.peerId else (peer.hostAddress ?: peerId)
        val device = try {
            bluetoothAdapter?.getRemoteDevice(mac)
        } catch (_: Exception) {
            null
        } ?: return
        connectAndSync(device)
    }

    /**
     * Updates internal peer mapping when real Ed25519 authorId and displayName are resolved during handshake.
     */
    fun onPeerIdentified(tempKey: String, realAuthorId: String, realDisplayName: String) {
        val cleanName = if (realDisplayName.isNotBlank() && realDisplayName != "MeshPeer" && realDisplayName != "User") {
            realDisplayName
        } else {
            "Nearby BLE Peer"
        }
        val existing = _peersMap.remove(tempKey)
            ?: _peersMap.remove(realAuthorId)
            ?: _peersMap.values.find { it.peerId == tempKey }
        if (existing != null) {
            _peersMap[realAuthorId] = existing.copy(
                peerId = realAuthorId,
                peerName = cleanName,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            updatePeersState()
        }
    }

    private fun updatePeerSyncStatus(peerId: String, isSyncing: Boolean) {
        val peer = _peersMap[peerId] ?: return
        _peersMap[peerId] = peer.copy(isSyncing = isSyncing, lastSeenTimestamp = System.currentTimeMillis())
        updatePeersState()
    }

    private fun updatePeersState() {
        _discoveredPeers.value = _peersMap.values.toList().distinctBy { it.peerId }.sortedByDescending { it.lastSeenTimestamp }
    }
}
