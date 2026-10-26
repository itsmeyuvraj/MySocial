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
import kotlinx.coroutines.launch

class FeedViewModel(
    private val repository: MeshRepository
) : ViewModel() {

    private val _openMeshFilter = MutableStateFlow(true)
    val openMeshFilter: StateFlow<Boolean> = _openMeshFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
}
