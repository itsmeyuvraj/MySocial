package com.mysocial.mesh.transport

import com.mysocial.mesh.protocol.TransportType

/**
 * Represents a nearby peer discovered via BLE beacons or Local Wi-Fi / Hotspot mDNS.
 */
data class DiscoveredPeer(
    val peerId: String,
    val peerName: String,
    val transportType: TransportType,
    val hostAddress: String? = null,
    val port: Int? = null,
    val rssi: Int? = null,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val isSyncing: Boolean = false
) {
    val displayId: String
        get() = if (peerId.length > 16) "${peerId.take(8)}...${peerId.takeLast(6)}" else peerId
}
