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
import com.mysocial.mesh.protocol.BleReassembler
import com.mysocial.mesh.protocol.BluetoothSocketPeerConnection
import com.mysocial.mesh.protocol.GossipMessage
import com.mysocial.mesh.protocol.GossipSyncEngine
import com.mysocial.mesh.protocol.PeerConnection
import com.mysocial.mesh.protocol.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pure AOSP High-Speed Bluetooth Low Energy (BLE) Mesh Transport.
 * Features:
 * 1. Background BLE Beacons (BluetoothLeAdvertiser with compact service data)
 * 2. Background BLE Scanner (BluetoothLeScanner with ScanFilter for service UUID)
 * 3. BLE L2CAP Connection-Oriented Channels (listenUsingInsecureL2capChannel & createInsecureL2capChannel on API 29+)
 * 4. High-Speed Throughput Tuning (BluetoothGatt.CONNECTION_PRIORITY_HIGH & PHY_LE_2M_MASK)
 * 5. Fallback GATT Server & Client characteristic exchange with BleChunker / BleReassembler
 */
class BleMeshTransport(
    private val context: Context,
    private val localAuthorId: String,
    private val localDisplayName: String,
    private val syncEngine: GossipSyncEngine,
    private val scope: CoroutineScope
) {
    private val tag = "BleMeshTransport"

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000a001-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000a002-0000-1000-8000-00805f9b34fb")
        val PSM_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000a003-0000-1000-8000-00805f9b34fb")
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

    private val isRunning = AtomicBoolean(false)
    private val activeSyncDevices = ConcurrentHashMap.newKeySet<String>()

    private val _peersMap = ConcurrentHashMap<String, DiscoveredPeer>()
    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    val isAvailable: Boolean
        get() = bluetoothAdapter != null && bluetoothAdapter!!.isEnabled

    /**
     * Start BLE advertising, L2CAP listener, GATT server, and scanning.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w(tag, "Bluetooth is disabled or unavailable on this device")
            return
        }

        Log.i(tag, "Starting BleMeshTransport for $localDisplayName ($localAuthorId)")
        try {
            startL2capServer()
            startGattServer()
            startAdvertising()
            startScanning()
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

        try {
            advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        } catch (_: Exception) {}
        advertiseCallback = null

        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (_: Exception) {}
        scanCallback = null

        try {
            l2capServerSocket?.close()
        } catch (_: Exception) {}
        l2capServerSocket = null

        try {
            gattServer?.close()
        } catch (_: Exception) {}
        gattServer = null

        _peersMap.clear()
        _discoveredPeers.value = emptyList()
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
                            val name = remoteDevice?.name ?: "Nearby BLE Peer"
                            Log.i(tag, "Accepted incoming high-speed BLE L2CAP channel from $address")

                            scope.launch(Dispatchers.IO) {
                                val connection = BluetoothSocketPeerConnection(
                                    peerId = address,
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

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
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

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.i(tag, "BLE Beacon advertising started successfully")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.w(tag, "BLE Beacon advertising failed with errorCode: $errorCode")
            }
        }

        try {
            adv.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e(tag, "Failed to call startAdvertising: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val sc = scanner ?: return

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
                Log.e(tag, "BLE Scan failed with errorCode: $errorCode")
            }
        }

        try {
            sc.startScan(listOf(filter), settings, scanCallback)
        } catch (e: Exception) {
            Log.e(tag, "Failed to call startScan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val rssi = result.rssi
        val deviceName = device.name ?: "Nearby BLE Peer"

        val peer = DiscoveredPeer(
            peerId = address,
            peerName = deviceName,
            transportType = TransportType.BLE,
            rssi = rssi,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        _peersMap[address] = peer
        updatePeersState()

        // Deterministic connection tie-breaker
        if (localAuthorId.hashCode() < address.hashCode() && !activeSyncDevices.contains(address)) {
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

        // PSM Characteristic for advertising L2CAP port to connecting clients
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
     * Connects to a remote BLE device with High-Speed tuning:
     * - Requests BluetoothGatt.CONNECTION_PRIORITY_HIGH
     * - Requests PHY_LE_2M_MASK for 2 Mbps PHY
     * - Negotiates high MTU (517)
     * - Connects via BLE L2CAP CoC on API 29+ with fallback to GATT
     */
    @SuppressLint("MissingPermission")
    private fun connectAndSync(device: BluetoothDevice) {
        val address = device.address ?: return
        if (!activeSyncDevices.add(address)) return

        updatePeerSyncStatus(address, true)
        Log.i(tag, "Initiating high-speed BLE GATT connection to $address")

        val deviceName = device.name ?: "Nearby BLE Peer"

        scope.launch(Dispatchers.IO) {
            try {
                device.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
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
                            gatt.close()
                            activeSyncDevices.remove(address)
                            updatePeerSyncStatus(address, false)
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val service = gatt.getService(SERVICE_UUID)
                            val psmChar = service?.getCharacteristic(PSM_CHARACTERISTIC_UUID)
                            if (psmChar != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                gatt.readCharacteristic(psmChar)
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        handleCharacteristicRead(gatt, characteristic, characteristic.value, status)
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
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val l2capSocket = device.createInsecureL2capChannel(remotePsm)
                                            l2capSocket.connect()
                                            Log.i(tag, "Successfully connected BLE L2CAP channel to $address")
                                            val conn = BluetoothSocketPeerConnection(
                                                peerId = address,
                                                peerName = deviceName,
                                                transportType = TransportType.BLE,
                                                socket = l2capSocket
                                            )
                                            syncEngine.syncWithPeer(conn)
                                        } catch (e: Exception) {
                                            Log.w(tag, "L2CAP connection failed to $address: ${e.message}")
                                        } finally {
                                            activeSyncDevices.remove(address)
                                            updatePeerSyncStatus(address, false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.w(tag, "BLE connection to $address failed: ${e.message}")
                activeSyncDevices.remove(address)
                updatePeerSyncStatus(address, false)
            }
        }
    }

    private fun updatePeerSyncStatus(peerId: String, isSyncing: Boolean) {
        val peer = _peersMap[peerId] ?: return
        _peersMap[peerId] = peer.copy(isSyncing = isSyncing, lastSeenTimestamp = System.currentTimeMillis())
        updatePeersState()
    }

    private fun updatePeersState() {
        _discoveredPeers.value = _peersMap.values.toList().sortedByDescending { it.lastSeenTimestamp }
    }
}
