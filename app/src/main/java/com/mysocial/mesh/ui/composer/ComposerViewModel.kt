package com.mysocial.mesh.ui.composer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysocial.mesh.data.MediaProcessor
import com.mysocial.mesh.data.MeshRepository
import com.mysocial.mesh.model.MediaAttachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ExpiryOption(val label: String, val durationMs: Long?) {
    FOREVER("Never", null),
    ONE_HOUR("1h", 3600_000L),
    SIX_HOURS("6h", 6 * 3600_000L),
    TWENTY_FOUR_HOURS("24h", 24 * 3600_000L)
}

class ComposerViewModel(
    private val repository: MeshRepository
) : ViewModel() {

    private val _postText = MutableStateFlow("")
    val postText: StateFlow<String> = _postText.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    private val _isPublic = MutableStateFlow(true)
    val isPublic: StateFlow<Boolean> = _isPublic.asStateFlow()

    // Mesh-native mechanics: Ephemeral TTL, SOS beacon, Polls
    private val _expiryOption = MutableStateFlow(ExpiryOption.FOREVER)
    val expiryOption: StateFlow<ExpiryOption> = _expiryOption.asStateFlow()

    private val _isSos = MutableStateFlow(false)
    val isSos: StateFlow<Boolean> = _isSos.asStateFlow()

    private val _isPollEnabled = MutableStateFlow(false)
    val isPollEnabled: StateFlow<Boolean> = _isPollEnabled.asStateFlow()

    private val _pollOptions = MutableStateFlow(listOf("", ""))
    val pollOptions: StateFlow<List<String>> = _pollOptions.asStateFlow()

    val authorId: String = repository.cryptoManager.authorId
    val myProfile = repository.observeMyProfile()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setExpiryOption(option: ExpiryOption) {
        _expiryOption.value = option
    }

    fun setSos(isSos: Boolean) {
        _isSos.value = isSos
    }

    fun setPollEnabled(enabled: Boolean) {
        _isPollEnabled.value = enabled
        if (enabled && _pollOptions.value.size < 2) {
            _pollOptions.value = listOf("", "")
        }
    }

    fun updatePollOption(index: Int, text: String) {
        val current = _pollOptions.value.toMutableList()
        if (index in current.indices) {
            current[index] = text
            _pollOptions.value = current
        }
    }

    fun addPollOption() {
        val current = _pollOptions.value.toMutableList()
        if (current.size < 4) {
            current.add("")
            _pollOptions.value = current
        }
    }

    fun removePollOption(index: Int) {
        val current = _pollOptions.value.toMutableList()
        if (current.size > 2 && index in current.indices) {
            current.removeAt(index)
            _pollOptions.value = current
        }
    }

    fun setPublic(isPublic: Boolean) {
        _isPublic.value = isPublic
    }

    fun updateText(text: String) {
        _postText.value = text
        _statusMessage.value = null
    }

    fun setSelectedImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    fun addTag(tag: String) {
        val clean = tag.trim().replace("#", "")
        if (clean.isNotBlank() && !_tags.value.contains(clean)) {
            _tags.value = _tags.value + clean
        }
    }

    fun removeTag(tag: String) {
        _tags.value = _tags.value - tag
    }

    fun publishPost(context: Context, onSuccess: () -> Unit) {
        val text = _postText.value.trim()
        val imageUri = _selectedImageUri.value
        val validPollOptions = if (_isPollEnabled.value) {
            _pollOptions.value.map { it.trim() }.filter { it.isNotBlank() }.takeIf { it.size >= 2 }
        } else null

        if (text.isBlank() && imageUri == null && validPollOptions == null) return

        viewModelScope.launch {
            _isPublishing.value = true
            _statusMessage.value = null

            try {
                var mediaAttachment: MediaAttachment? = null
                if (imageUri != null) {
                    val processed = MediaProcessor.processImageUri(context, imageUri)
                    repository.registerCompletedBlob(processed)
                    mediaAttachment = MediaAttachment(
                        blobHash = processed.blobHash,
                        mimeType = processed.mimeType,
                        totalBytes = processed.totalBytes,
                        chunkCount = processed.chunkCount,
                        microPreviewBase64 = processed.microPreviewBase64
                    )
                }

                val postBody = when {
                    text.isNotBlank() -> text
                    mediaAttachment != null -> "Shared a photo"
                    validPollOptions != null -> "Participate in this poll"
                    else -> "New update"
                }

                val expiresAt = _expiryOption.value.durationMs?.let { System.currentTimeMillis() + it }

                val result = repository.createPost(
                    text = postBody,
                    tags = _tags.value,
                    media = mediaAttachment,
                    isPublic = _isPublic.value,
                    expiresAt = expiresAt,
                    isSos = _isSos.value,
                    pollOptions = validPollOptions
                )
                _isPublishing.value = false

                if (result.isSuccess) {
                    _postText.value = ""
                    _selectedImageUri.value = null
                    _isPublic.value = true
                    _isSos.value = false
                    _isPollEnabled.value = false
                    _pollOptions.value = listOf("", "")
                    _expiryOption.value = ExpiryOption.FOREVER
                    _statusMessage.value = "Post shared successfully!"
                    onSuccess()
                } else {
                    _statusMessage.value = "Failed to share post: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _isPublishing.value = false
                _statusMessage.value = "Error processing post: ${e.message}"
            }
        }
    }
}
