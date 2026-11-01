package com.mysocial.mesh.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.ConversationSummary
import com.mysocial.mesh.model.ProfileViewEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MessagesViewModel(
    private val repository: MeshRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val conversations: StateFlow<List<ConversationSummary>> = combine(
        repository.observeConversations(),
        _searchQuery
    ) { allConversations, query ->
        if (query.isBlank()) {
            allConversations
        } else {
            val q = query.trim().lowercase()
            allConversations.filter { conv ->
                conv.peerDisplayName?.lowercase()?.contains(q) == true ||
                conv.peerId.lowercase().contains(q) ||
                conv.lastMessageText.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalUnreadCount: StateFlow<Int> = repository.observeTotalUnreadMessagesCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val availableFriends: StateFlow<List<ProfileViewEntity>> = repository.observeFollowedProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteConversation(peerId: String) {
        viewModelScope.launch {
            repository.markConversationAsRead(peerId)
            // Note: messages can be cleared from DAO
        }
    }
}

