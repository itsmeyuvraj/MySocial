package com.mysocial.mesh.ui.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.emergency.EmergencyChannels
import com.mysocial.mesh.model.PttVoiceBurstPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WalkieTalkieViewModel(
    private val repository: MeshRepository
) : ViewModel() {

    val isRecording: StateFlow<Boolean> = repository.pttWalkieTalkieManager?.isRecording
        ?: MutableStateFlow(false)

    val isPlaying: StateFlow<Boolean> = repository.pttWalkieTalkieManager?.isPlaying
        ?: MutableStateFlow(false)

    val lastReceivedBurst: StateFlow<PttVoiceBurstPayload?> = repository.pttWalkieTalkieManager?.lastReceivedBurst
        ?: MutableStateFlow(null)

    val discoveredPeersCount: StateFlow<Int> = repository.discoveredPeers
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val currentAuthorId: String = repository.cryptoManager.authorId

    /**
     * Unfiltered stream of all recent PTT voice transmissions (local and remote).
     */
    val voiceBursts: StateFlow<List<PttVoiceBurstPayload>> = repository.observePttVoiceBursts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startPtt() {
        val manager = repository.pttWalkieTalkieManager ?: return
        manager.setChannel(EmergencyChannels.DEFAULT_CHANNEL)
        manager.startRecording()
    }

    fun stopPtt() {
        val manager = repository.pttWalkieTalkieManager ?: return
        viewModelScope.launch {
            val name = repository.syncEngine.myDisplayName.ifBlank { "Responder" }
            manager.stopAndBroadcast(displayName = name, channel = EmergencyChannels.DEFAULT_CHANNEL)
        }
    }

    fun replayBurst(burst: PttVoiceBurstPayload) {
        repository.pttWalkieTalkieManager?.playVoiceBurst(burst, forceReplay = true)
    }

    fun clearAllVoices() {
        viewModelScope.launch {
            repository.clearAllPttVoiceBursts()
        }
    }
}
