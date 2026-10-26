package com.mysocial.mesh.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.PostWithAuthor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.mysocial.mesh.transport.DiscoveredPeer

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class FeedViewModel(
    private val repository: MeshRepository
) : ViewModel() {

    private val _openMeshFilter = MutableStateFlow(true)
    val openMeshFilter: StateFlow<Boolean> = _openMeshFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(3000)
        }
    }

    val activeNearbyPeers: StateFlow<List<DiscoveredPeer>> = combine(
        repository.discoveredPeers,
        ticker
    ) { peers, now ->
        peers.filter { now - it.lastSeenTimestamp <= 15_000L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlineAuthorIds: StateFlow<Set<String>> = activeNearbyPeers
        .map { peers -> peers.map { it.peerId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val followedAuthorIds: StateFlow<Set<String>> = repository.observeFollowedAuthorIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val feedPosts: StateFlow<List<PostWithAuthor>> = _openMeshFilter
        .flatMapLatest { openFilter -> repository.observeFeed(openFilter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh(onSync: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                onSync?.invoke()
            } catch (_: Exception) {}
            kotlinx.coroutines.delay(1000)
            _isRefreshing.value = false
        }
    }

    fun setFilter(openMesh: Boolean) {
        _openMeshFilter.value = openMesh
    }

    fun likePost(targetHash: String) {
        viewModelScope.launch {
            repository.reactToPost(targetHash, emoji = "❤️")
        }
    }

    val currentAuthorId: String = repository.cryptoManager.authorId

    fun replyToPost(text: String, rootHash: String, parentHash: String) {
        viewModelScope.launch {
            repository.createReply(text, rootHash, parentHash)
        }
    }

    fun observeReplies(postHash: String): kotlinx.coroutines.flow.Flow<List<PostWithAuthor>> {
        return repository.observeReplies(postHash)
    }

    fun deletePost(entryHash: String) {
        viewModelScope.launch {
            repository.deletePost(entryHash)
        }
    }

    fun editPost(entryHash: String, newText: String) {
        viewModelScope.launch {
            repository.editPost(entryHash, newText)
        }
    }

    fun prioritizeBlobDownload(blobHash: String) {
        viewModelScope.launch {
            repository.prioritizeBlobDownload(blobHash)
        }
    }

    fun toggleFollow(targetAuthorId: String, following: Boolean) {
        viewModelScope.launch {
            repository.toggleFollow(targetAuthorId, following)
        }
    }
}
