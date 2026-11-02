package com.mysocial.mesh.ui.messages

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.BlobManifestEntity
import com.mysocial.mesh.data.MediaProcessor
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.DirectMessageEntity
import com.mysocial.mesh.model.ProfileViewEntity
import com.mysocial.mesh.utils.VoicePlayer
import com.mysocial.mesh.utils.VoiceRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

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

    // Voice Note Recorder & Player
    val voiceRecorder = VoiceRecorder(viewModelScope)
    val voicePlayer = VoicePlayer(viewModelScope)
    val isRecording: StateFlow<Boolean> = voiceRecorder.isRecording
    val recordingDurationMs: StateFlow<Long> = voiceRecorder.recordingDurationMs

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

    fun sendImageMessage(context: Context, imageUri: Uri, caption: String = "") {
        if (_isSending.value) return

        viewModelScope.launch {
            _isSending.value = true
            _errorMessage.value = null
            try {
                val processed = MediaProcessor.processImageUri(context, imageUri)
                repository.registerCompletedBlob(processed)

                val result = repository.sendDirectMessageWithMedia(
                    recipientAuthorId = peerAuthorId,
                    text = caption,
                    mediaType = "IMAGE",
                    blobHash = processed.blobHash,
                    mimeType = processed.mimeType,
                    totalBytes = processed.totalBytes,
                    chunkCount = processed.chunkCount,
                    microPreviewBase64 = processed.microPreviewBase64
                )
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to send photo"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to process photo: ${e.message}"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun startVoiceRecording(context: Context): Boolean {
        return voiceRecorder.startRecording(context)
    }

    fun cancelVoiceRecording() {
        voiceRecorder.cancelRecording()
    }

    fun finishAndSendVoiceRecording(context: Context) {
        val pair = voiceRecorder.stopRecording() ?: return
        val (audioFile, durationMs) = pair

        viewModelScope.launch {
            _isSending.value = true
            _errorMessage.value = null
            try {
                val processed = MediaProcessor.processAudioFile(context, audioFile, durationMs)
                repository.registerCompletedBlob(processed)

                val result = repository.sendDirectMessageWithMedia(
                    recipientAuthorId = peerAuthorId,
                    text = "",
                    mediaType = "AUDIO",
                    blobHash = processed.blobHash,
                    mimeType = processed.mimeType,
                    totalBytes = processed.totalBytes,
                    chunkCount = processed.chunkCount,
                    durationMs = durationMs
                )
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to send voice message"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to send voice message: ${e.message}"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun observeBlobManifest(blobHash: String): Flow<BlobManifestEntity?> = repository.observeBlobManifest(blobHash)

    fun prioritizeBlob(blobHash: String) {
        viewModelScope.launch {
            repository.prioritizeBlob(blobHash)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorder.cancelRecording()
        voicePlayer.release()
    }
}
