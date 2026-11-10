package com.mysocial.mesh.ui.feed

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.MediaProcessor
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.CanonicalJson
import com.mysocial.mesh.model.PostWithAuthor
import com.mysocial.mesh.utils.toBbid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.mysocial.mesh.transport.DiscoveredPeer

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext

class FeedViewModel(
    private val repository: MeshRepository
) : ViewModel() {

    val currentAuthorId: String = repository.cryptoManager.authorId

    val userLikedPostHashes: StateFlow<Set<String>> = repository.observeUserLikedPostHashes(currentAuthorId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _openMeshFilter = MutableStateFlow(true)
    val openMeshFilter: StateFlow<Boolean> = _openMeshFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openSearch() {
        _isSearchActive.value = true
    }

    fun closeSearch() {
        _isSearchActive.value = false
        _searchQuery.value = ""
    }

    private val ticker = flow {
        while (currentCoroutineContext().isActive) {
            emit(System.currentTimeMillis())
            delay(3000)
        }
    }

    val activeNearbyPeers: StateFlow<List<DiscoveredPeer>> = combine(
        repository.discoveredPeers,
        ticker
    ) { peers, now ->
        peers.filter { now - it.lastSeenTimestamp <= 45_000L }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlineAuthorIds: StateFlow<Set<String>> = activeNearbyPeers
        .map { peers -> peers.map { it.peerId }.toSet() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val followedAuthorIds: StateFlow<Set<String>> = repository.observeFollowedAuthorIds()
        .map { it.toSet() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val pinnedHashtags: StateFlow<Set<String>> = (repository.appSettings?.pinnedHashtags ?: flowOf(emptySet()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val mutedHashtags: StateFlow<Set<String>> = (repository.appSettings?.mutedHashtags ?: flowOf(emptySet()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val compactFeedMode: StateFlow<Boolean> =
        repository.appSettings?.compactFeedMode
            ?: MutableStateFlow(false).asStateFlow()

    val showHopBadges: StateFlow<Boolean> =
        repository.appSettings?.showHopBadges
            ?: MutableStateFlow(true).asStateFlow()

    val reportedPosts: StateFlow<Set<String>> =
        repository.appSettings?.reportedPosts
            ?: MutableStateFlow(emptySet<String>()).asStateFlow()

    val unreadMessagesCount: StateFlow<Int> = repository.observeTotalUnreadMessagesCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun pinHashtag(tag: String) {
        viewModelScope.launch {
            repository.appSettings?.pinHashtag(tag)
        }
    }

    fun unpinHashtag(tag: String) {
        viewModelScope.launch {
            repository.appSettings?.unpinHashtag(tag)
        }
    }

    fun muteHashtag(tag: String) {
        viewModelScope.launch {
            repository.appSettings?.muteHashtag(tag)
        }
    }

    fun unmuteHashtag(tag: String) {
        viewModelScope.launch {
            repository.appSettings?.unmuteHashtag(tag)
        }
    }

    fun votePoll(targetHash: String, optionIndex: Int) {
        viewModelScope.launch {
            repository.votePoll(targetHash, optionIndex)
        }
    }

    val activeStories: StateFlow<List<com.mysocial.mesh.model.StoryEntity>> =
        repository.storyManager.observeActiveStories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun publishStory(text: String, imageUri: Uri? = null, context: Context? = null) {
        viewModelScope.launch {
            var mediaBlobHash: String? = null
            if (imageUri != null && context != null) {
                try {
                    val processed = MediaProcessor.processImageUri(context, imageUri)
                    repository.registerCompletedBlob(processed)
                    mediaBlobHash = processed.blobHash
                } catch (e: Exception) {
                    Log.e("FeedViewModel", "Failed to process story image: ${e.message}", e)
                }
            }
            val trimmed = text.trim()
            val storyText = when {
                trimmed.isNotBlank() -> trimmed
                mediaBlobHash != null -> ""
                else -> "Story update"
            }
            repository.storyManager.publishStory(
                text = storyText,
                authorDisplayName = "Me",
                mediaBlobHash = mediaBlobHash
            )
        }
    }

    fun markStoryViewed(storyId: String) {
        viewModelScope.launch {
            repository.storyManager.markStoryViewed(storyId)
        }
    }

    private val _selectedEmergencyChannel = MutableStateFlow<String?>(null)
    val selectedEmergencyChannel: StateFlow<String?> = _selectedEmergencyChannel.asStateFlow()

    fun selectEmergencyChannel(channel: String?) {
        _selectedEmergencyChannel.value = channel
    }

    val isPttRecording: StateFlow<Boolean> = repository.pttWalkieTalkieManager?.isRecording
        ?: MutableStateFlow(false)

    val isPttPlaying: StateFlow<Boolean> = repository.pttWalkieTalkieManager?.isPlaying
        ?: MutableStateFlow(false)

    fun startPtt() {
        val manager = repository.pttWalkieTalkieManager ?: return
        val channel = _selectedEmergencyChannel.value ?: com.mysocial.mesh.emergency.EmergencyChannels.DEFAULT_CHANNEL
        manager.setChannel(channel)
        manager.startRecording()
    }

    fun stopPtt() {
        val manager = repository.pttWalkieTalkieManager ?: return
        viewModelScope.launch {
            val channel = _selectedEmergencyChannel.value ?: com.mysocial.mesh.emergency.EmergencyChannels.DEFAULT_CHANNEL
            val name = repository.syncEngine.myDisplayName.ifBlank { "Responder" }
            manager.stopAndBroadcast(displayName = name, channel = channel)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val feedPosts: StateFlow<List<PostWithAuthor>> = combine(
        _openMeshFilter.flatMapLatest { openFilter -> repository.observeFeed(openFilter) },
        _searchQuery,
        mutedHashtags,
        _selectedEmergencyChannel,
        reportedPosts
    ) { rawPosts, query, muted, emergencyChannel, reported ->
        val nonReported = if (reported.isEmpty()) rawPosts else rawPosts.filter { !reported.contains(it.post.entryHash) }
        val channelFiltered = if (emergencyChannel != null) {
            nonReported.filter { postWithAuthor ->
                postWithAuthor.post.text.contains(emergencyChannel, ignoreCase = true) ||
                postWithAuthor.post.tagsJson.contains(emergencyChannel, ignoreCase = true)
            }
        } else {
            nonReported
        }
        val posts = if (!_openMeshFilter.value) {
            channelFiltered.filter { it.post.authorId != currentAuthorId }
        } else {
            channelFiltered
        }
        if (query.isBlank()) {
            if (!_openMeshFilter.value || muted.isEmpty()) {
                posts
            } else {
                posts.filter { postWithAuthor ->
                    val authorId = postWithAuthor.post.authorId
                    if (authorId == currentAuthorId) true
                    else {
                        val tags = try {
                            CanonicalJson.decodeFromString<List<String>>(postWithAuthor.post.tagsJson)
                        } catch (_: Exception) {
                            emptyList()
                        }
                        if (tags.isNotEmpty() && tags.all { muted.contains(it.lowercase()) }) {
                            false
                        } else {
                            true
                        }
                    }
                }
            }
        } else {
            val q = query.trim()
            val cleanQ = q.removePrefix("#").removePrefix("@").trim()
            posts.filter { postWithAuthor ->
                val post = postWithAuthor.post
                val textMatch = post.text.contains(q, ignoreCase = true)
                val authorNameMatch = postWithAuthor.authorProfile?.displayName?.contains(q, ignoreCase = true) == true
                val authorIdMatch = post.authorId.contains(cleanQ, ignoreCase = true)
                val bbidMatch = post.authorId.toBbid().replace("-", "").contains(cleanQ.replace("-", ""), ignoreCase = true)
                val tagMatch = post.tagsJson.contains(cleanQ, ignoreCase = true)
                val bioMatch = postWithAuthor.authorProfile?.bio?.contains(q, ignoreCase = true) == true
                textMatch || authorNameMatch || authorIdMatch || bbidMatch || tagMatch || bioMatch
            }
        }
    }
        .distinctUntilChanged()
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

    fun replyToPost(text: String, rootHash: String, parentHash: String) {
        viewModelScope.launch {
            repository.createReply(text, rootHash, parentHash)
        }
    }

    fun observeReplies(postHash: String): kotlinx.coroutines.flow.Flow<List<PostWithAuthor>> {
        return combine(
            repository.observeReplies(postHash),
            reportedPosts
        ) { replies, reported ->
            if (reported.isEmpty()) replies else replies.filter { !reported.contains(it.post.entryHash) }
        }
    }

    fun reportPost(entryHash: String, reason: String = "Inappropriate content", alsoBlockAuthor: Boolean = false, authorId: String = "") {
        viewModelScope.launch {
            repository.appSettings?.reportPost(entryHash)
            if (alsoBlockAuthor && authorId.isNotBlank() && authorId != currentAuthorId) {
                repository.blockAuthor(authorId, reason = "Reported post: $reason")
            }
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

    fun toggleFollow(targetAuthorId: String, following: Boolean) {
        viewModelScope.launch {
            repository.toggleFollow(targetAuthorId, following)
        }
    }

    fun blockAuthor(targetAuthorId: String) {
        viewModelScope.launch {
            repository.blockAuthor(targetAuthorId, reason = "Blocked from feed")
        }
    }
}
