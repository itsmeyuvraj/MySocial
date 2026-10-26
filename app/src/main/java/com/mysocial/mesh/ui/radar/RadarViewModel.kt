package com.mysocial.mesh.ui.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.protocol.SyncEvent
import com.mysocial.mesh.service.MeshSyncService
import com.mysocial.mesh.transport.DiscoveredPeer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

class RadarViewModel(
    private val repository: MeshRepository,
    private val getService: () -> MeshSyncService?
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(3000)
        }
    }

    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = combine(
        repository.discoveredPeers,
        repository.observeAllProfiles(),
        ticker
    ) { peers, profiles, now ->
        val myAuthorId = repository.cryptoManager.authorId
        val myProfile = profiles.find { it.authorId == myAuthorId }
        val myDisplayName = myProfile?.displayName
        val profileMap = profiles.associateBy { it.authorId }
        val otherProfiles = profiles.filter { it.authorId != myAuthorId }

        peers
            .filter { peer ->
                peer.peerId != myAuthorId &&
                (now - peer.lastSeenTimestamp <= 15_000L) &&
                (myDisplayName.isNullOrBlank() || (peer.peerName != myDisplayName && !peer.peerName.startsWith(myDisplayName)))
            }
            .distinctBy { it.peerId }
            .map { peer ->
                val matchedProfile = profileMap[peer.peerId]
                if (matchedProfile != null && matchedProfile.displayName.isNotBlank()) {
                    peer.copy(peerName = matchedProfile.displayName)
                } else if (peer.peerName.startsWith("Host ADB") ||
                    peer.peerName.startsWith("Direct IP") ||
                    peer.peerName.startsWith("Wi-Fi Peer") ||
                    peer.peerName.startsWith("Nearby Device") ||
                    peer.peerName == "MeshPeer"
                ) {
                    val fallback = otherProfiles.firstOrNull()?.displayName
                    if (!fallback.isNullOrBlank()) {
                        peer.copy(peerName = fallback)
                    } else {
                        peer.copy(peerName = "Nearby Peer")
                    }
                } else {
                    peer
                }
            }
            .filter { peer ->
                peer.peerId != myAuthorId &&
                (myDisplayName.isNullOrBlank() || peer.peerName != myDisplayName)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncLogs: StateFlow<List<String>> = repository.activityLogs

    fun forceSyncAll() {
        getService()?.forceSyncAll()
    }

    fun forceSyncPeer(peerId: String) {
        getService()?.forceSyncAll()
    }

    fun syncWithDirectIp(targetHost: String, targetPort: Int = 42530) {
        getService()?.syncWithDirectIp(targetHost, targetPort)
    }
}
