package com.mysocial.mesh.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.DirectMessageEntity
import com.mysocial.mesh.model.ProfileViewEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    val peerAuthorId: String,
    private val repository: MeshRepository
) : ViewModel() {

    val messages: StateFlow<List<DirectMessageEntity>> = repository.observeMessagesForPeer(peerAuthorId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val peerProfile: StateFlow<ProfileViewEntity?> = repository.observeProfile(peerAuthorId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPeerOnline: StateFlow<Boolean> = repository.discoveredPeers
        .combine(MutableStateFlow(peerAuthorId)) { peers, id ->
            peers.any { it.peerId == id }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        markAsRead()
    }

    fun markAsRead() {
        viewModelScope.launch {
            repository.markConversationAsRead(peerAuthorId)
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isSending.value) return

        viewModelScope.launch {
            _isSending.value = true
            _errorMessage.value = null
            val result = repository.sendDirectMessage(peerAuthorId, trimmed)
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to send encrypted message"
            }
            _isSending.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
